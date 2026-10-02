import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { CatalogService } from '../../../core/catalog/catalog.service';
import {
  EvidenceMode, FIELD_TYPE_LABELS, FormDefinition, FormField, FormFieldType, FormPrefill, TopicFormVersion,
  emptyDefinition, keyFromLabel,
} from '../../../core/catalog/form.models';
import { ButtonComponent, DynamicFormComponent, StatusBadgeComponent } from '../../../shared/components';

const TYPES = Object.keys(FIELD_TYPE_LABELS) as FormFieldType[];

/**
 * Construtor do formulário de um tópico (ADR-013, Fase D). Só o rascunho é editável; publicar congela a
 * versão e o Portal passa a usá-la. Chamados já abertos continuam presos à versão com que nasceram.
 */
@Component({
  selector: 'app-form-builder',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe, MatIconModule, ButtonComponent, StatusBadgeComponent, DynamicFormComponent],
  template: `
    <section class="builder card" aria-label="Formulário do tópico">
      <header class="b-head">
        <h3 class="b-title">Formulário — {{ topicName }}</h3>
        <button nxButton type="button" (click)="closed.emit()" aria-label="Fechar o construtor"><mat-icon>close</mat-icon></button>
      </header>

      @if (loading()) {
        <p class="muted">Carregando…</p>
      } @else {
        <div class="versions" aria-label="Versões">
          @for (v of versions(); track v.id) {
            <span class="ver">
              v{{ v.version }}
              <nx-status-badge [tone]="v.status === 'PUBLISHED' ? 'success' : v.status === 'DRAFT' ? 'info' : 'neutral'">
                {{ statusLabel(v.status) }}
              </nx-status-badge>
              @if (v.publishedAt) { <span class="when">{{ v.publishedAt | date:'dd/MM/yyyy HH:mm' }}</span> }
            </span>
          } @empty {
            <span class="muted">Este tópico ainda não tem formulário: o chamado é aberto só com título e descrição.</span>
          }
        </div>

        @if (errorSummary() && !draft()) { <p class="inline-error" role="alert">{{ errorSummary() }}</p> }

        @if (!draft()) {
          <div class="b-actions">
            <button nxButton variant="primary" type="button" [loading]="busy()" (click)="createDraft()">
              <mat-icon>edit</mat-icon>
              {{ published() ? 'Editar (novo rascunho a partir da v' + published()!.version + ')' : 'Criar formulário' }}
            </button>
          </div>
          @if (published()) {
            <h4 class="sub">Versão publicada (v{{ published()!.version }})</h4>
            <ol class="readonly">
              @for (f of published()!.definition.fields; track f.key) {
                <li><strong>{{ f.label }}</strong>
                  <span class="muted"> · {{ typeLabel(f.type) }}@if (f.required) { · obrigatório }@if (f.visibleWhen) { · condicional }</span></li>
              } @empty { <li class="muted">Sem campos.</li> }
            </ol>
          }
        } @else {
          @if (errorSummary()) { <p class="inline-error" role="alert">{{ errorSummary() }}</p> }

          <div class="b-grid">
            <div class="editor">
              <h4 class="sub">Rascunho v{{ draft()!.version }}</h4>
              <ol class="fields">
                @for (f of fields; track f; let i = $index) {
                  <li class="frow" [class.has-error]="rowErrors()[i]">
                    <div class="frow-head">
                      <button type="button" class="toggle" (click)="expanded.set(expanded() === i ? -1 : i)"
                              [attr.aria-expanded]="expanded() === i" [attr.aria-label]="'Editar campo ' + (f.label || i + 1)">
                        <mat-icon aria-hidden="true">{{ expanded() === i ? 'expand_more' : 'chevron_right' }}</mat-icon>
                        <span class="f-label">{{ f.label || 'Campo sem rótulo' }}</span>
                        <span class="f-meta">{{ typeLabel(f.type) }}@if (f.required) { · obrigatório }</span>
                      </button>
                      <button nxButton type="button" [disabled]="i === 0" (click)="move(i, -1)" aria-label="Mover para cima"><mat-icon>arrow_upward</mat-icon></button>
                      <button nxButton type="button" [disabled]="i === fields.length - 1" (click)="move(i, 1)" aria-label="Mover para baixo"><mat-icon>arrow_downward</mat-icon></button>
                      <button nxButton type="button" (click)="remove(i)" aria-label="Remover campo"><mat-icon>delete</mat-icon></button>
                    </div>
                    @if (rowErrors()[i]) { <p class="inline-error" role="alert">{{ rowErrors()[i] }}</p> }

                    @if (expanded() === i) {
                      <div class="fedit">
                        <div class="two">
                          <label class="field"><span class="label">Rótulo (como o solicitante lê) *</span>
                            <input class="input" [(ngModel)]="f.label" [ngModelOptions]="{standalone: true}" (ngModelChange)="onLabel(f)" /></label>
                          <label class="field"><span class="label">Tipo</span>
                            <select class="input" [(ngModel)]="f.type" [ngModelOptions]="{standalone: true}" (ngModelChange)="onType(f)">
                              @for (t of types; track t) { <option [value]="t">{{ typeLabel(t) }}</option> }
                            </select></label>
                        </div>
                        <div class="two">
                          <label class="field"><span class="label">Chave (identificador estável)</span>
                            <input class="input mono" [ngModel]="f.key" [ngModelOptions]="{standalone: true}" (ngModelChange)="renameKey(f, $event, true)" /></label>
                          <label class="check"><input type="checkbox" [(ngModel)]="f.required" [ngModelOptions]="{standalone: true}" />
                            {{ f.type === 'BOOLEAN' ? 'Obrigatório (precisa confirmar)' : 'Obrigatório' }}</label>
                        </div>
                        <label class="field"><span class="label">Texto de ajuda</span>
                          <input class="input" [(ngModel)]="f.helpText" [ngModelOptions]="{standalone: true}" /></label>

                        @if (f.type === 'TEXT' || f.type === 'TEXTAREA' || f.type === 'NUMBER') {
                          <label class="field"><span class="label">Texto de exemplo (placeholder)</span>
                            <input class="input" [(ngModel)]="f.placeholder" [ngModelOptions]="{standalone: true}" /></label>
                        }
                        @if (f.type === 'TEXT' || f.type === 'TEXTAREA') {
                          <div class="three">
                            <label class="field"><span class="label">Tamanho mínimo</span>
                              <input class="input" type="number" min="0" [(ngModel)]="f.minLength" [ngModelOptions]="{standalone: true}" /></label>
                            <label class="field"><span class="label">Tamanho máximo</span>
                              <input class="input" type="number" min="1" [(ngModel)]="f.maxLength" [ngModelOptions]="{standalone: true}" /></label>
                            <label class="field"><span class="label">Formato (regex)</span>
                              <input class="input mono" [(ngModel)]="f.pattern" [ngModelOptions]="{standalone: true}" /></label>
                          </div>
                        }
                        @if (f.type === 'NUMBER') {
                          <div class="two">
                            <label class="field"><span class="label">Mínimo</span>
                              <input class="input" type="number" [(ngModel)]="f.min" [ngModelOptions]="{standalone: true}" /></label>
                            <label class="field"><span class="label">Máximo</span>
                              <input class="input" type="number" [(ngModel)]="f.max" [ngModelOptions]="{standalone: true}" /></label>
                          </div>
                        }
                        @if (f.type === 'SELECT' || f.type === 'MULTISELECT') {
                          <label class="field"><span class="label">Opções (uma por linha; "valor | rótulo" ou só o rótulo)</span>
                            <textarea class="input mono" rows="4" [ngModel]="optionsText(f)" [ngModelOptions]="{standalone: true}"
                                      (ngModelChange)="setOptions(f, $event)"></textarea></label>
                        }
                        @if (f.type !== 'MULTISELECT' && f.type !== 'LOCATION') {
                          <label class="field"><span class="label">Valor padrão</span>
                            <input class="input" [ngModel]="defaultText(f)" [ngModelOptions]="{standalone: true}"
                                   (ngModelChange)="setDefault(f, $event)" /></label>
                        }
                        @if (prefillOptions(f).length) {
                          <label class="field"><span class="label">Pré-preencher com o perfil do solicitante</span>
                            <select class="input" [(ngModel)]="f.prefill" [ngModelOptions]="{standalone: true}">
                              <option [ngValue]="null">Não pré-preencher</option>
                              @for (p of prefillOptions(f); track p.value) { <option [ngValue]="p.value">{{ p.label }}</option> }
                            </select></label>
                        }
                        @if (i > 0) {
                          <fieldset class="cond">
                            <legend>Condição de exibição</legend>
                            <div class="two">
                              <label class="field"><span class="label">Mostrar só quando o campo…</span>
                                <select class="input" [ngModel]="f.visibleWhen?.field ?? ''" [ngModelOptions]="{standalone: true}"
                                        (ngModelChange)="setCondField(f, $event)">
                                  <option value="">Sempre visível</option>
                                  @for (o of fields.slice(0, i); track o.key) { <option [value]="o.key">{{ o.label || o.key }}</option> }
                                </select></label>
                              @if (f.visibleWhen) {
                                <label class="field"><span class="label">…for igual a (valor)</span>
                                  <input class="input mono" [(ngModel)]="f.visibleWhen.equals" [ngModelOptions]="{standalone: true}" /></label>
                              }
                            </div>
                          </fieldset>
                        }
                      </div>
                    }
                  </li>
                } @empty {
                  <li class="muted frow">Nenhum campo ainda. Sem campos, o chamado do tópico só pede título e descrição.</li>
                }
              </ol>
              <div><button nxButton type="button" (click)="addField()"><mat-icon>add</mat-icon>Adicionar campo</button></div>

              <fieldset class="evid">
                <legend>Evidência (anexo)</legend>
                <div class="two">
                  <label class="field"><span class="label">Política</span>
                    <select class="input" [(ngModel)]="evidenceMode" [ngModelOptions]="{standalone: true}">
                      <option value="NONE">Sem anexo</option>
                      <option value="OPTIONAL">Anexo opcional</option>
                      <option value="REQUIRED">Anexo obrigatório</option>
                    </select></label>
                  @if (evidenceMode !== 'NONE') {
                    <label class="field"><span class="label">O que anexar</span>
                      <input class="input" [(ngModel)]="evidenceHint" [ngModelOptions]="{standalone: true}" placeholder="Ex.: foto da tela de erro" /></label>
                  }
                </div>
              </fieldset>
            </div>

            <aside class="preview" aria-label="Pré-visualização">
              <div class="preview-head">
                <h4 class="sub">Pré-visualização</h4>
                <button nxButton type="button" (click)="refreshPreview()"><mat-icon>refresh</mat-icon>Atualizar</button>
              </div>
              @if (previewDef(); as pd) {
                <nx-dynamic-form [definition]="pd" idPrefix="pv" />
                @if (pd.evidence.mode !== 'NONE') {
                  <p class="muted"><mat-icon class="mi" aria-hidden="true">attach_file</mat-icon>
                    Anexo {{ pd.evidence.mode === 'REQUIRED' ? 'obrigatório' : 'opcional' }}@if (pd.evidence.hint) { : {{ pd.evidence.hint }} }</p>
                }
              }
            </aside>
          </div>

          <div class="b-actions">
            @if (confirmDiscard()) {
              <span class="muted">Descartar o rascunho v{{ draft()!.version }}?</span>
              <button nxButton type="button" (click)="confirmDiscard.set(false)">Manter</button>
              <button nxButton type="button" [loading]="busy()" (click)="discard()">Descartar</button>
            } @else {
              <button nxButton type="button" (click)="confirmDiscard.set(true)">Descartar rascunho</button>
            }
            <span class="spacer"></span>
            <button nxButton type="button" [loading]="busy()" (click)="save(false)"><mat-icon>save</mat-icon>Salvar rascunho</button>
            <button nxButton variant="primary" type="button" [loading]="busy()" (click)="save(true)">
              <mat-icon>publish</mat-icon>Publicar v{{ draft()!.version }}
            </button>
          </div>
          @if (savedAt()) { <p class="muted">Rascunho salvo às {{ savedAt() | date:'HH:mm:ss' }}.</p> }
        }
      }
    </section>
  `,
  styles: [`
    :host { display: block; }
    .builder { display: flex; flex-direction: column; gap: var(--sp-5); }
    .b-head { display: flex; align-items: center; justify-content: space-between; }
    .b-title { margin: 0; font-size: var(--fs-md); font-weight: 600; }
    .sub { margin: 0; font-size: var(--fs-sm); font-weight: 600; }
    .muted { margin: 0; color: var(--text-muted); font-size: var(--fs-sm); }
    .mono { font-family: var(--font-mono); }
    .mi { font-size: 16px; width: 16px; height: 16px; vertical-align: middle; }
    .versions { display: flex; gap: var(--sp-5); flex-wrap: wrap; align-items: center; }
    .ver { display: inline-flex; gap: var(--sp-3); align-items: center; font-family: var(--font-mono); font-size: var(--fs-sm); }
    .when { color: var(--text-muted); }
    .b-actions { display: flex; gap: var(--sp-4); align-items: center; flex-wrap: wrap; }
    .spacer { flex: 1; }
    .readonly { margin: 0; padding-left: var(--sp-7); display: flex; flex-direction: column; gap: var(--sp-2); }
    .b-grid { display: grid; grid-template-columns: minmax(0, 3fr) minmax(0, 2fr); gap: var(--sp-7); align-items: start; }
    @media (max-width: 1000px) { .b-grid { grid-template-columns: 1fr; } }
    .editor, .preview { display: flex; flex-direction: column; gap: var(--sp-5); min-width: 0; }
    .preview { border-left: 1px solid var(--border); padding-left: var(--sp-6); }
    @media (max-width: 1000px) { .preview { border-left: 0; padding-left: 0; } }
    .preview-head { display: flex; align-items: center; justify-content: space-between; }
    .fields { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; border: 1px solid var(--border); }
    .frow { padding: var(--sp-3) var(--sp-4); }
    .frow + .frow { border-top: 1px solid var(--border); }
    .frow.has-error { box-shadow: inset 3px 0 0 var(--danger, #b3261e); }
    .frow-head { display: flex; align-items: center; gap: var(--sp-2); }
    .toggle { all: unset; flex: 1; min-width: 0; display: flex; align-items: center; gap: var(--sp-3); cursor: pointer; padding: var(--sp-2) 0; }
    .toggle:focus-visible { outline: 2px solid var(--action); outline-offset: 2px; }
    .f-label { font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .f-meta { color: var(--text-muted); font-size: var(--fs-xs); white-space: nowrap; }
    .fedit { display: flex; flex-direction: column; gap: var(--sp-5); padding: var(--sp-5) 0 var(--sp-4) var(--sp-7); }
    .two { display: grid; grid-template-columns: 1fr 1fr; gap: var(--sp-5); align-items: end; }
    .three { display: grid; grid-template-columns: 1fr 1fr 2fr; gap: var(--sp-5); }
    .check { display: flex; gap: var(--sp-3); align-items: center; }
    .cond, .evid { border: 1px solid var(--border); padding: var(--sp-5); margin: 0; display: flex; flex-direction: column; gap: var(--sp-4); }
    legend { padding: 0 var(--sp-3); font-size: var(--fs-xs); text-transform: uppercase; letter-spacing: .06em; color: var(--text-muted); }
    .inline-error { margin: 0; }
  `]
})
export class FormBuilderComponent implements OnChanges {
  @Input({ required: true }) topicId!: string;
  @Input() topicName = '';
  @Output() closed = new EventEmitter<void>();

  readonly types = TYPES;
  versions = signal<TopicFormVersion[]>([]);
  loading = signal(true);
  busy = signal(false);
  expanded = signal(-1);
  confirmDiscard = signal(false);
  savedAt = signal<Date | null>(null);
  errorSummary = signal<string | null>(null);
  rowErrors = signal<Record<number, string>>({});
  previewDef = signal<FormDefinition | null>(null);

  draft = signal<TopicFormVersion | null>(null);
  published = signal<TopicFormVersion | null>(null);

  fields: FormField[] = [];
  evidenceMode: EvidenceMode = 'NONE';
  evidenceHint = '';
  /** Campos cuja chave o administrador mexeu: deixam de acompanhar o rótulo. */
  readonly keyTouched = new Set<FormField>();

  constructor(private catalog: CatalogService) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['topicId']) {
      this.load();
    }
  }

  statusLabel(s: string): string {
    return s === 'PUBLISHED' ? 'Publicada' : s === 'DRAFT' ? 'Rascunho' : 'Arquivada';
  }

  typeLabel(t: FormFieldType): string {
    return FIELD_TYPE_LABELS[t];
  }

  // ----------------------------------------------------------------- campos

  addField(): void {
    this.fields.push({ key: '', label: '', type: 'TEXT', required: false, options: [], visibleWhen: null, prefill: null });
    this.expanded.set(this.fields.length - 1);
  }

  remove(i: number): void {
    this.fields.splice(i, 1);
    this.expanded.set(-1);
    // Condições que apontavam para o campo removido deixam de fazer sentido.
    const keys = new Set(this.fields.map(f => f.key));
    this.fields.forEach(f => { if (f.visibleWhen && !keys.has(f.visibleWhen.field)) f.visibleWhen = null; });
  }

  move(i: number, delta: number): void {
    const j = i + delta;
    if (j < 0 || j >= this.fields.length) return;
    [this.fields[i], this.fields[j]] = [this.fields[j], this.fields[i]];
    this.expanded.set(j);
  }

  onLabel(f: FormField): void {
    if (!this.keyTouched.has(f)) {
      this.renameKey(f, this.uniqueKey(keyFromLabel(f.label || ''), f), false);
    }
  }

  /** Muda a chave e leva junto as condições de exibição que apontavam para a chave antiga. */
  renameKey(f: FormField, next: string, byUser: boolean): void {
    const previous = f.key;
    f.key = next;
    if (byUser) this.keyTouched.add(f);
    if (previous && previous !== next) {
      this.fields.forEach(o => {
        if (o.visibleWhen && o.visibleWhen.field === previous) o.visibleWhen = { ...o.visibleWhen, field: next };
      });
    }
  }

  onType(f: FormField): void {
    if ((f.type === 'SELECT' || f.type === 'MULTISELECT') && (!f.options || f.options.length === 0)) {
      f.options = [];
    }
    f.defaultValue = null;
    f.prefill = null;
  }

  optionsText(f: FormField): string {
    return (f.options ?? []).map(o => o.value === o.label ? o.label : `${o.value} | ${o.label}`).join('\n');
  }

  setOptions(f: FormField, text: string): void {
    f.options = text.split('\n').map(l => l.trim()).filter(Boolean).map(line => {
      const [a, b] = line.split('|').map(s => s.trim());
      return b !== undefined && b !== '' ? { value: a, label: b } : { value: keyFromLabel(a) || a, label: a };
    });
  }

  defaultText(f: FormField): string {
    return f.defaultValue === null || f.defaultValue === undefined ? '' : String(f.defaultValue);
  }

  setDefault(f: FormField, text: string): void {
    if (text === '') { f.defaultValue = null; return; }
    f.defaultValue = f.type === 'NUMBER' ? Number(text) : f.type === 'BOOLEAN' ? text === 'true' : text;
  }

  prefillOptions(f: FormField): { value: FormPrefill; label: string }[] {
    if (f.type === 'LOCATION') return [{ value: 'USER_LOCATION', label: 'Localidade do solicitante' }];
    if (f.type === 'TEXT') {
      return [
        { value: 'USER_PHONE', label: 'Telefone' },
        { value: 'USER_DEPARTMENT', label: 'Departamento' },
        { value: 'USER_JOB_TITLE', label: 'Cargo' },
      ];
    }
    return [];
  }

  setCondField(f: FormField, key: string): void {
    f.visibleWhen = key ? { field: key, equals: f.visibleWhen?.equals ?? '' } : null;
  }

  private uniqueKey(base: string, self: FormField): string {
    if (!base) return '';
    const taken = new Set(this.fields.filter(x => x !== self).map(x => x.key));
    let key = base, n = 2;
    while (taken.has(key)) key = `${base.slice(0, 36)}${n++}`;
    return key;
  }

  // ------------------------------------------------------------ ações

  createDraft(): void {
    this.busy.set(true);
    this.catalog.createFormDraft(this.topicId).subscribe({
      next: () => { this.busy.set(false); this.load(); },
      error: (e: HttpErrorResponse) => this.fail(e),
    });
  }

  save(publish: boolean): void {
    const d = this.draft();
    if (!d) return;
    this.busy.set(true);
    this.errorSummary.set(null);
    this.rowErrors.set({});
    this.catalog.saveFormDraft(d.id, this.currentDefinition()).subscribe({
      next: saved => {
        this.draft.set(saved);
        this.savedAt.set(new Date());
        if (!publish) { this.busy.set(false); this.refreshPreview(); this.reloadVersions(); return; }
        this.catalog.publishForm(d.id).subscribe({
          next: () => { this.busy.set(false); this.load(); },
          error: (e: HttpErrorResponse) => this.fail(e),
        });
      },
      error: (e: HttpErrorResponse) => this.fail(e),
    });
  }

  discard(): void {
    const d = this.draft();
    if (!d) return;
    this.busy.set(true);
    this.catalog.discardFormDraft(d.id).subscribe({
      next: () => { this.busy.set(false); this.confirmDiscard.set(false); this.load(); },
      error: (e: HttpErrorResponse) => this.fail(e),
    });
  }

  refreshPreview(): void {
    this.previewDef.set(structuredClone(this.currentDefinition()));
  }

  private currentDefinition(): FormDefinition {
    return {
      fields: this.fields.map(f => ({
        ...f,
        helpText: f.helpText || null,
        placeholder: f.placeholder || null,
        pattern: f.pattern || null,
        options: f.type === 'SELECT' || f.type === 'MULTISELECT' ? (f.options ?? []) : [],
        visibleWhen: f.visibleWhen && f.visibleWhen.field ? f.visibleWhen : null,
      })),
      evidence: { mode: this.evidenceMode, hint: this.evidenceMode === 'NONE' ? null : (this.evidenceHint || null) },
    };
  }

  private fail(e: HttpErrorResponse): void {
    this.busy.set(false);
    const ext = (e.error?.extensions ?? {}) as Record<string, string>;
    const rows: Record<number, string> = {};
    let general = '';
    for (const [k, msg] of Object.entries(ext)) {
      const m = /^fields\[(\d+)\]\.(\w+)$/.exec(k);
      if (m) {
        const i = Number(m[1]);
        rows[i] = rows[i] ? `${rows[i]} · ${msg}` : msg;
      } else {
        general = general ? `${general} · ${msg}` : msg;
      }
    }
    this.rowErrors.set(rows);
    const detail = e.error?.detail;
    this.errorSummary.set(general || (Object.keys(rows).length
      ? 'Corrija os campos marcados antes de salvar.'
      : e.status === 422 && typeof detail === 'string' ? detail : 'Não foi possível concluir a operação.'));
    const first = Object.keys(rows)[0];
    if (first !== undefined) this.expanded.set(Number(first));
  }

  // ------------------------------------------------------------ carga

  private load(): void {
    this.loading.set(true);
    this.errorSummary.set(null);
    this.rowErrors.set({});
    this.catalog.formVersions(this.topicId).subscribe({
      next: list => {
        this.applyVersions(list);
        this.loading.set(false);
      },
      error: () => { this.loading.set(false); this.errorSummary.set('Não foi possível carregar as versões do formulário.'); },
    });
  }

  private reloadVersions(): void {
    this.catalog.formVersions(this.topicId).subscribe(list => this.versions.set(list));
  }

  private applyVersions(list: TopicFormVersion[]): void {
    this.versions.set(list);
    this.published.set(list.find(v => v.status === 'PUBLISHED') ?? null);
    const draft = list.find(v => v.status === 'DRAFT') ?? null;
    this.draft.set(draft);
    this.keyTouched.clear();
    const def = draft?.definition ?? emptyDefinition();
    this.fields = structuredClone(def.fields).map(f => ({ ...f, options: f.options ?? [] }));
    this.fields.forEach(f => this.keyTouched.add(f));
    this.evidenceMode = def.evidence?.mode ?? 'NONE';
    this.evidenceHint = def.evidence?.hint ?? '';
    this.expanded.set(-1);
    this.previewDef.set(draft ? structuredClone(def) : null);
  }
}
