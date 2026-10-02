import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { CatalogService, PortalArea, PortalTopic } from '../../core/catalog/catalog.service';
import type { FormDefinition, FormPrefill } from '../../core/catalog/form.models';
import { PortalLocation, PortalService } from '../../core/portal/portal.service';
import { EVIDENCE_ACCEPT, EvidenceDto, EvidenceService } from '../../core/ticketing/evidence.service';
import { ButtonComponent, DynamicFormComponent, EmptyStateComponent } from '../../shared/components';

/**
 * Novo pedido (ADR-013): o solicitante escolhe o assunto e responde ao formulário daquele tópico. Fila,
 * prioridade e prazo são decididos pelo sistema e nunca aparecem aqui.
 */
@Component({
  selector: 'app-portal-new',
  standalone: true,
  imports: [FormsModule, RouterLink, MatIconModule, ButtonComponent, DynamicFormComponent, EmptyStateComponent],
  template: `
    <a class="back" routerLink="/portal"><mat-icon aria-hidden="true">arrow_back</mat-icon>Meus pedidos</a>
    <h1 class="title">Novo pedido</h1>

    @if (error()) {
      <nx-empty-state variant="error" [heading]="error()!" />
    } @else if (loading()) {
      <p class="muted" role="status">Carregando os assuntos…</p>
    } @else if (!topic()) {
      <section aria-labelledby="pick">
        <h2 id="pick" class="step">1. Sobre o que é o pedido?</h2>
        <label class="search">
          <mat-icon aria-hidden="true">search</mat-icon>
          <span class="sr-only">Buscar assunto</span>
          <input class="input" type="search" [(ngModel)]="query" name="q" placeholder="Buscar, por exemplo: senha, VPN, impressora" />
        </label>
        @for (a of filtered(); track a.id) {
          <section class="area" [attr.aria-label]="a.name">
            <h3>{{ a.name }}</h3>
            @if (a.description) { <p class="muted">{{ a.description }}</p> }
            <div class="topics">
              @for (t of a.topics; track t.id) {
                <button type="button" class="topic" (click)="choose(a, t)">
                  <b>{{ t.name }}</b>
                  @if (t.description) { <span>{{ t.description }}</span> }
                </button>
              }
            </div>
          </section>
        } @empty {
          <p class="muted">Nenhum assunto encontrado para "{{ query }}".</p>
        }
      </section>
    } @else {
      <section aria-labelledby="fill">
        <h2 id="fill" class="step">2. Conte o que está acontecendo</h2>
        <p class="chosen">
          <span class="muted">Assunto:</span> <b>{{ areaName() }} › {{ topic()!.name }}</b>
          <button type="button" class="linklike" (click)="reset()">Alterar</button>
        </p>

        <form class="form" (ngSubmit)="submit()" novalidate>
          <label class="field">
            <span class="label">Resumo do pedido <span class="req" aria-hidden="true">*</span></span>
            <input class="input" name="title" [(ngModel)]="title" maxlength="500" required
                   [attr.aria-invalid]="fieldErrors()['title'] ? 'true' : null" placeholder="Em uma frase, o que você precisa?" />
            @if (fieldErrors()['title']) { <span class="err" role="alert">{{ fieldErrors()['title'] }}</span> }
          </label>

          <label class="field">
            <span class="label">Detalhes</span>
            <textarea class="input" name="description" rows="4" [(ngModel)]="description" maxlength="10000"
                      placeholder="O que já tentou? Desde quando acontece?"></textarea>
          </label>

          @if (locations().length > 0) {
            <label class="field">
              <span class="label">Onde você está?</span>
              <select class="input" name="location" [(ngModel)]="locationId">
                <option value="">Não informar</option>
                @for (l of locations(); track l.id) { <option [value]="l.id">{{ l.name }}</option> }
              </select>
            </label>
          }

          @if (definition(); as def) {
            @if (def.fields.length > 0) {
              <nx-dynamic-form [definition]="def" [errors]="fieldErrors()" [prefill]="prefill()" [locations]="locations()"
                               [showRequired]="submitted()" (answersChange)="answers.set($event)"
                               (completeChange)="complete.set($event)" />
            }

            @if (def.evidence.mode !== 'NONE') {
              <fieldset class="evidence">
                <legend>Anexos @if (def.evidence.mode === 'REQUIRED') { <span class="req">(obrigatório)</span> } @else { <span class="muted">(opcional)</span> }</legend>
                @if (def.evidence.hint) { <p class="muted">{{ def.evidence.hint }}</p> }
                <ul class="files">
                  @for (f of staged(); track f.id) {
                    <li>
                      <mat-icon aria-hidden="true">attach_file</mat-icon>
                      <span class="fname">{{ f.fileName }}</span>
                      <span class="muted mono">{{ evidence.formatSize(f.fileSize) }}</span>
                      <button type="button" class="linklike" (click)="unstage(f)" [attr.aria-label]="'Remover ' + f.fileName">Remover</button>
                    </li>
                  }
                </ul>
                <label class="pick">
                  <mat-icon aria-hidden="true">upload_file</mat-icon>
                  {{ uploading() ? 'Enviando…' : 'Escolher arquivo' }}
                  <input type="file" hidden [accept]="accept" (change)="pick($event)" [disabled]="uploading()" />
                </label>
                <span class="muted hint">Imagens, PDF ou texto, até 10 MB cada.</span>
                @if (fieldErrors()['evidence']) { <span class="err" role="alert">{{ fieldErrors()['evidence'] }}</span> }
                @if (fileError()) { <span class="err" role="alert">{{ fileError() }}</span> }
              </fieldset>
            }
          }

          @if (formError()) { <p class="err" role="alert">{{ formError() }}</p> }
          <div class="actions">
            <a nxButton routerLink="/portal">Cancelar</a>
            <button nxButton variant="primary" type="submit" [loading]="submitting()" [disabled]="submitting() || uploading()">
              <mat-icon>send</mat-icon>Enviar pedido
            </button>
          </div>
        </form>
      </section>
    }
  `,
  styles: [`
    :host { display: block; max-width: 720px; }
    .back { display: inline-flex; align-items: center; gap: var(--sp-2); color: var(--text-muted); text-decoration: none; font-size: var(--fs-sm); }
    .back mat-icon { width: 16px; height: 16px; font-size: 16px; }
    .title { margin: var(--sp-4) 0 var(--sp-7); font-size: 1.5rem; font-weight: var(--fw-semibold); }
    .step { margin: 0 0 var(--sp-5); font-size: 1rem; font-weight: var(--fw-semibold); }
    .muted { color: var(--text-muted); font-size: var(--fs-sm); }
    .search { position: relative; display: block; margin-bottom: var(--sp-7); }
    .search mat-icon { position: absolute; left: var(--sp-4); top: 50%; transform: translateY(-50%); color: var(--text-muted); }
    .search input { padding-inline-start: 40px; }
    .area { margin-bottom: var(--sp-7); }
    .area h3 { margin: 0 0 var(--sp-2); font-size: var(--fs-sm); text-transform: uppercase; letter-spacing: .06em; color: var(--text-muted); }
    .topics { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: var(--sp-4); margin-top: var(--sp-4); }
    .topic { text-align: start; background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-s); padding: var(--sp-5); cursor: pointer; display: grid; gap: var(--sp-2); font: inherit; color: var(--text); }
    .topic:hover { border-color: var(--signal); }
    .topic:focus-visible { outline: 2px solid var(--signal); outline-offset: 2px; }
    .topic span { font-size: var(--fs-sm); color: var(--text-muted); }
    .chosen { display: flex; gap: var(--sp-4); align-items: baseline; flex-wrap: wrap; margin: 0 0 var(--sp-6); }
    .linklike { background: none; border: 0; padding: 0; font: inherit; font-size: var(--fs-sm); color: var(--signal); cursor: pointer; }
    .linklike:hover { text-decoration: underline; }
    .form { display: grid; gap: var(--sp-6); }
    .field { display: grid; gap: var(--sp-2); }
    .req { color: var(--critical); }
    .err { color: var(--critical); font-size: var(--fs-sm); margin: 0; }
    .evidence { border: 1px solid var(--border); padding: var(--sp-5); display: grid; gap: var(--sp-4); }
    .evidence legend { font-weight: var(--fw-medium); padding: 0 var(--sp-3); }
    .evidence p { margin: 0; }
    .files { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--sp-3); }
    .files li { display: flex; align-items: center; gap: var(--sp-4); font-size: var(--fs-sm); }
    .files mat-icon { width: 16px; height: 16px; font-size: 16px; color: var(--text-muted); }
    .fname { overflow-wrap: anywhere; }
    .pick { display: inline-flex; align-items: center; gap: var(--sp-3); color: var(--signal); font-size: var(--fs-sm); font-weight: var(--fw-medium); cursor: pointer; }
    .pick:focus-within { outline: 2px solid var(--signal); outline-offset: 2px; }
    .pick mat-icon { width: 18px; height: 18px; font-size: 18px; }
    .hint { font-size: var(--fs-xs); }
    .actions { display: flex; justify-content: flex-end; gap: var(--sp-4); }
  `],
})
export class PortalNewComponent implements OnInit {
  private catalog = inject(CatalogService);
  private portal = inject(PortalService);
  protected evidence = inject(EvidenceService);
  private router = inject(Router);

  readonly accept = EVIDENCE_ACCEPT;

  areas = signal<PortalArea[]>([]);
  locations = signal<PortalLocation[]>([]);
  prefill = signal<Partial<Record<FormPrefill, string>>>({});
  loading = signal(true);
  error = signal<string | null>(null);

  query = '';
  topic = signal<PortalTopic | null>(null);
  private area = signal<PortalArea | null>(null);
  areaName = computed(() => this.area()?.name ?? '');
  definition = signal<FormDefinition | null>(null);

  title = '';
  description = '';
  locationId = '';
  answers = signal<Record<string, unknown>>({});
  complete = signal(true);
  staged = signal<EvidenceDto[]>([]);
  uploading = signal(false);
  fileError = signal<string | null>(null);

  submitted = signal(false);
  submitting = signal(false);
  fieldErrors = signal<Record<string, string>>({});
  formError = signal<string | null>(null);

  filtered = computed(() => {
    const q = this.query.trim().toLowerCase();
    const list = this.areas();
    if (!q) return list.filter(a => a.topics.length > 0);
    return list
      .map(a => ({ ...a, topics: a.topics.filter(t => `${a.name} ${t.name} ${t.description ?? ''}`.toLowerCase().includes(q)) }))
      .filter(a => a.topics.length > 0);
  });

  ngOnInit(): void {
    this.catalog.portal().subscribe({
      next: c => {
        this.areas.set(c.areas);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Não foi possível carregar os assuntos.');
        this.loading.set(false);
      },
    });
    // Localidades e perfil só ajudam a preencher: sem eles o formulário continua funcionando.
    this.portal.locations().subscribe({ next: l => this.locations.set(l), error: () => this.locations.set([]) });
    this.portal.profile().subscribe({
      next: p => {
        this.locationId = p.defaultLocationId ?? '';
        this.prefill.set({
          USER_LOCATION: p.defaultLocationId ?? undefined,
          USER_PHONE: p.phone ?? undefined,
          USER_DEPARTMENT: p.department ?? undefined,
          USER_JOB_TITLE: p.jobTitle ?? undefined,
        });
      },
      error: () => undefined,
    });
  }

  choose(area: PortalArea, topic: PortalTopic): void {
    this.fieldErrors.set({});
    this.formError.set(null);
    this.catalog.portalForm(topic.id).subscribe({
      next: def => {
        this.area.set(area);
        this.topic.set(topic);
        this.definition.set(def);
        this.complete.set(def.fields.every(f => !f.required));
      },
      error: () => this.error.set('Não foi possível abrir o formulário deste assunto.'),
    });
  }

  reset(): void {
    this.topic.set(null);
    this.area.set(null);
    this.definition.set(null);
    this.answers.set({});
    this.staged().forEach(f => this.evidence.remove(f.id).subscribe({ error: () => undefined }));
    this.staged.set([]);
  }

  pick(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    const problem = this.evidence.clientError(file);
    if (problem) {
      this.fileError.set(problem);
      return;
    }
    this.fileError.set(null);
    this.uploading.set(true);
    this.evidence.stage(file).subscribe({
      next: e => {
        this.staged.update(list => [...list, e]);
        this.uploading.set(false);
        this.fieldErrors.update(m => ({ ...m, evidence: '' }));
        this.formError.set(null);
      },
      error: (err: HttpErrorResponse) => {
        this.fileError.set(this.first(err) ?? 'Não foi possível enviar o arquivo.');
        this.uploading.set(false);
      },
    });
  }

  unstage(f: EvidenceDto): void {
    this.staged.update(list => list.filter(x => x.id !== f.id));
    this.evidence.remove(f.id).subscribe({ error: () => undefined });
  }

  submit(): void {
    if (this.submitting()) return;
    this.submitted.set(true);
    this.formError.set(null);
    const errors: Record<string, string> = {};
    if (!this.title.trim()) errors['title'] = 'Diga, em uma frase, o que você precisa.';
    if (this.definition()?.evidence.mode === 'REQUIRED' && this.staged().length === 0) {
      errors['evidence'] = 'Este assunto pede ao menos um anexo.';
    }
    if (!this.complete()) errors['_form'] = 'Preencha os campos obrigatórios do formulário.';
    this.fieldErrors.set(errors);
    if (Object.keys(errors).length > 0) {
      this.formError.set(errors['_form'] ?? 'Revise os campos destacados.');
      return;
    }

    this.submitting.set(true);
    this.portal.create({
      topicId: this.topic()!.id,
      title: this.title.trim(),
      description: this.description.trim() || undefined,
      locationId: this.locationId || undefined,
      formAnswers: this.answers(),
      evidenceIds: this.staged().map(f => f.id),
    }).subscribe({
      next: d => this.router.navigate(['/portal', d.ticket.id]),
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        const ext = err.error?.extensions as Record<string, string> | undefined;
        if (err.status === 422 && ext && Object.keys(ext).length > 0) {
          this.fieldErrors.set(ext);
          this.formError.set('Revise os campos destacados.');
        } else {
          this.formError.set(typeof err.error?.detail === 'string' && err.status === 422
            ? err.error.detail : 'Não foi possível enviar o pedido. Tente novamente.');
        }
      },
    });
  }

  private first(err: HttpErrorResponse): string | null {
    const ext = err.error?.extensions as Record<string, string> | undefined;
    return ext ? Object.values(ext)[0] ?? null : null;
  }
}
