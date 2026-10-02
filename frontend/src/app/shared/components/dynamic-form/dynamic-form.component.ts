import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import type { FormDefinition, FormField, FormPrefill } from '../../../core/catalog/form.models';

export interface DynamicFormLocation {
  id: string;
  name: string;
}

/**
 * Desenha o formulário de um tópico a partir da definição publicada (ADR-013, Fase D). Mostra e esconde campos
 * por {@code visibleWhen}, aplica padrões e pré-preenchimento e emite só as respostas dos campos visíveis.
 * Validar de verdade é com o servidor: aqui só se evita enviar o que já se sabe que será recusado e se mostram
 * os erros por campo que o servidor devolve.
 */
@Component({
  selector: 'nx-dynamic-form',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="dyn">
      @for (f of visibleFields(); track f.key) {
        <div class="dyn-field" [class.has-error]="errorFor(f)">
          @if (f.type === 'BOOLEAN') {
            <label class="dyn-check">
              <input type="checkbox" [id]="id(f)" [checked]="values()[f.key] === true"
                     [attr.aria-invalid]="errorFor(f) ? 'true' : null" [attr.aria-describedby]="describedBy(f)"
                     (change)="set(f, $any($event.target).checked)" />
              <span>{{ f.label }}@if (f.required) { <span class="req" aria-hidden="true"> *</span> }</span>
            </label>
          } @else {
            <label class="label" [attr.for]="id(f)" [id]="id(f) + '-l'">
              {{ f.label }}@if (f.required) { <span class="req" aria-hidden="true"> *</span> }
            </label>

            @switch (f.type) {
              @case ('TEXTAREA') {
                <textarea class="input" rows="4" [id]="id(f)" [value]="text(f)" [placeholder]="f.placeholder || ''"
                          [attr.maxlength]="f.maxLength || null" [attr.aria-required]="f.required"
                          [attr.aria-invalid]="errorFor(f) ? 'true' : null" [attr.aria-describedby]="describedBy(f)"
                          (input)="set(f, $any($event.target).value)"></textarea>
              }
              @case ('NUMBER') {
                <input class="input" type="number" inputmode="decimal" [id]="id(f)" [value]="text(f)"
                       [attr.min]="f.min" [attr.max]="f.max" [placeholder]="f.placeholder || ''"
                       [attr.aria-required]="f.required" [attr.aria-invalid]="errorFor(f) ? 'true' : null"
                       [attr.aria-describedby]="describedBy(f)" (input)="set(f, $any($event.target).value)" />
              }
              @case ('DATE') {
                <input class="input" type="date" [id]="id(f)" [value]="text(f)" [attr.aria-required]="f.required"
                       [attr.aria-invalid]="errorFor(f) ? 'true' : null" [attr.aria-describedby]="describedBy(f)"
                       (input)="set(f, $any($event.target).value)" />
              }
              @case ('SELECT') {
                <select class="input" [id]="id(f)" [attr.aria-required]="f.required"
                        [attr.aria-invalid]="errorFor(f) ? 'true' : null" [attr.aria-describedby]="describedBy(f)"
                        (change)="set(f, $any($event.target).value)">
                  <option value="" [selected]="!text(f)">Selecione…</option>
                  @for (o of f.options || []; track o.value) {
                    <option [value]="o.value" [selected]="text(f) === o.value">{{ o.label }}</option>
                  }
                </select>
              }
              @case ('LOCATION') {
                <select class="input" [id]="id(f)" [attr.aria-required]="f.required"
                        [attr.aria-invalid]="errorFor(f) ? 'true' : null" [attr.aria-describedby]="describedBy(f)"
                        (change)="set(f, $any($event.target).value)">
                  <option value="" [selected]="!text(f)">Selecione a localidade…</option>
                  @for (l of locations; track l.id) {
                    <option [value]="l.id" [selected]="text(f) === l.id">{{ l.name }}</option>
                  }
                </select>
              }
              @case ('MULTISELECT') {
                <div class="dyn-multi" role="group" [attr.aria-labelledby]="id(f) + '-l'"
                     [attr.aria-describedby]="describedBy(f)">
                  @for (o of f.options || []; track o.value) {
                    <label class="dyn-check">
                      <input type="checkbox" [checked]="isChosen(f, o.value)" (change)="toggle(f, o.value, $any($event.target).checked)" />
                      <span>{{ o.label }}</span>
                    </label>
                  }
                </div>
              }
              @default {
                <input class="input" type="text" [id]="id(f)" [value]="text(f)" [placeholder]="f.placeholder || ''"
                       [attr.maxlength]="f.maxLength || null" [attr.aria-required]="f.required"
                       [attr.aria-invalid]="errorFor(f) ? 'true' : null" [attr.aria-describedby]="describedBy(f)"
                       (input)="set(f, $any($event.target).value)" />
              }
            }
          }
          @if (f.helpText) {
            <p class="dyn-help" [id]="id(f) + '-h'">{{ f.helpText }}</p>
          }
          @if (errorFor(f)) {
            <p class="dyn-error" [id]="id(f) + '-e'" role="alert">{{ errorFor(f) }}</p>
          }
        </div>
      }
      @if (visibleFields().length === 0) {
        <p class="dyn-empty">Este formulário não tem campos.</p>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .dyn { display: flex; flex-direction: column; gap: var(--sp-6); }
    .dyn-field { display: flex; flex-direction: column; gap: var(--sp-3); }
    .dyn-check { display: inline-flex; align-items: center; gap: var(--sp-3); }
    .dyn-multi { display: flex; flex-direction: column; gap: var(--sp-3); }
    .req { color: var(--danger, #b3261e); }
    .dyn-help { margin: 0; font-size: var(--fs-xs); color: var(--text-muted); }
    .dyn-error { margin: 0; font-size: var(--fs-xs); color: var(--danger, #b3261e); }
    .dyn-empty { margin: 0; color: var(--text-muted); font-size: var(--fs-sm); }
    .has-error .input { border-color: var(--danger, #b3261e); }
  `]
})
export class DynamicFormComponent implements OnChanges {
  @Input({ required: true }) definition!: FormDefinition;
  /** Erros por campo devolvidos pelo servidor (chave do campo -> mensagem). */
  @Input() errors: Record<string, string> = {};
  /** Dados do perfil usados quando o campo declara {@code prefill}. */
  @Input() prefill: Partial<Record<FormPrefill, string>> = {};
  @Input() locations: DynamicFormLocation[] = [];
  /** Prefixo dos ids dos controles, para o formulário poder aparecer duas vezes na tela. */
  @Input() idPrefix = 'df';
  /** Mostra "obrigatório" nos campos vazios (o host liga depois da primeira tentativa de envio). */
  @Input() showRequired = false;
  @Output() answersChange = new EventEmitter<Record<string, unknown>>();
  /** Verdadeiro quando todos os campos visíveis obrigatórios têm resposta. */
  @Output() completeChange = new EventEmitter<boolean>();

  values = signal<Record<string, unknown>>({});
  private definitionSig = signal<FormDefinition>({ fields: [], evidence: { mode: 'NONE' } });

  visibleFields = computed(() => {
    const answered = this.values();
    return this.definitionSig().fields.filter(f => this.isVisible(f, answered));
  });

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['definition'] && this.definition) {
      this.definitionSig.set(this.definition);
      const initial: Record<string, unknown> = {};
      for (const f of this.definition.fields) {
        const fromProfile = f.prefill ? this.prefill[f.prefill] : undefined;
        const value = fromProfile ?? f.defaultValue;
        if (value !== undefined && value !== null && value !== '') {
          initial[f.key] = value;
        }
      }
      this.values.set(initial);
      this.emit();
    }
  }

  id(f: FormField): string {
    return `${this.idPrefix}-${f.key}`;
  }

  text(f: FormField): string {
    const v = this.values()[f.key];
    return v === undefined || v === null ? '' : String(v);
  }

  isChosen(f: FormField, value: string): boolean {
    const v = this.values()[f.key];
    return Array.isArray(v) && v.includes(value);
  }

  errorFor(f: FormField): string | null {
    const server = this.errors[f.key];
    if (server) return server;
    if (this.showRequired && f.required && this.isEmpty(f)) return 'Campo obrigatório';
    return null;
  }

  describedBy(f: FormField): string | null {
    const ids: string[] = [];
    if (f.helpText) ids.push(this.id(f) + '-h');
    if (this.errorFor(f)) ids.push(this.id(f) + '-e');
    return ids.length ? ids.join(' ') : null;
  }

  set(f: FormField, raw: unknown): void {
    let value: unknown = raw;
    if (f.type === 'NUMBER') {
      value = raw === '' ? undefined : Number(raw);
    } else if (typeof raw === 'string' && raw === '') {
      value = undefined;
    }
    this.update(f.key, value);
  }

  toggle(f: FormField, optionValue: string, checked: boolean): void {
    const current = (this.values()[f.key] as string[] | undefined) ?? [];
    const next = checked ? [...new Set([...current, optionValue])] : current.filter(v => v !== optionValue);
    this.update(f.key, next.length ? next : undefined);
  }

  private update(key: string, value: unknown): void {
    this.values.update(v => {
      const next = { ...v };
      if (value === undefined) delete next[key]; else next[key] = value;
      return next;
    });
    this.emit();
  }

  private isEmpty(f: FormField): boolean {
    const v = this.values()[f.key];
    if (f.type === 'BOOLEAN') return v !== true;
    return v === undefined || v === null || v === '' || (Array.isArray(v) && v.length === 0);
  }

  private isVisible(f: FormField, answered: Record<string, unknown>): boolean {
    const vw = f.visibleWhen;
    if (!vw) return true;
    const other = answered[vw.field];
    if (Array.isArray(other)) return other.some(v => String(v) === vw.equals);
    return other !== undefined && other !== null && String(other) === vw.equals;
  }

  /** Só as respostas de campos visíveis; esconder um campo descarta o que foi digitado nele. */
  private emit(): void {
    const answered = this.values();
    const out: Record<string, unknown> = {};
    let complete = true;
    for (const f of this.definitionSig().fields) {
      if (!this.isVisible(f, answered)) continue;
      if (answered[f.key] !== undefined) out[f.key] = answered[f.key];
      if (f.required && this.isEmpty(f)) complete = false;
    }
    this.answersChange.emit(out);
    this.completeChange.emit(complete);
  }
}
