import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { TicketService, TicketPriority, TicketType } from '../../../core/ticketing/ticket.service';
import { CatalogService, PortalArea } from '../../../core/catalog/catalog.service';
import type { FormDefinition } from '../../../core/catalog/form.models';
import { DynamicFormComponent } from '../../../shared/components';
import { HttpErrorResponse } from '@angular/common/http';
import { EvidenceDto, EvidenceService, EVIDENCE_ACCEPT } from '../../../core/ticketing/evidence.service';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-ticket-create',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatIconModule, DynamicFormComponent],
  template: `
    <h1 class="page-title">Criar Ticket</h1>

    <form class="card form" [formGroup]="form" (ngSubmit)="onSubmit()">
      <label class="field">
        <span class="field-label">Assunto</span>
        <select [value]="topicId()" (change)="chooseTopic($any($event.target).value)">
          <option value="">Sem assunto (chamado avulso)</option>
          @for (a of areas(); track a.id) {
            <optgroup [label]="a.name">
              @for (t of a.topics; track t.id) { <option [value]="t.id">{{ t.name }}</option> }
            </optgroup>
          }
        </select>
        <span class="field-hint">O assunto define o formulário, a fila e o SLA, como no Portal.</span>
      </label>

      <label class="field">
        <span class="field-label">Título*</span>
        <input type="text" formControlName="title" placeholder="Descreva o problema em poucas palavras" />
        @if (form.controls['title'].touched && form.controls['title'].invalid) {
          <span class="field-error">Informe um título (até 500 caracteres)</span>
        }
      </label>

      <label class="field">
        <span class="field-label">Descrição</span>
        <textarea formControlName="description" rows="5" placeholder="Detalhe o problema, passos para reproduzir, impacto…"></textarea>
      </label>

      @if (definition(); as def) {
        @if (def.fields.length > 0) {
          <nx-dynamic-form [definition]="def" [errors]="fieldErrors()" [showRequired]="submitted()"
                           (answersChange)="answers.set($event)" (completeChange)="complete.set($event)" />
        }
      }

      @if (definition(); as def) {
        @if (def.evidence.mode !== 'NONE') {
          <fieldset class="evidence">
            <legend class="field-label">Anexos @if (def.evidence.mode === 'REQUIRED') { <span class="field-hint">(obrigatório)</span> } @else { <span class="field-hint">(opcional)</span> }</legend>
            @if (def.evidence.hint) { <p class="field-hint">{{ def.evidence.hint }}</p> }
            <ul class="files">
              @for (f of staged(); track f.id) {
                <li>
                  <mat-icon aria-hidden="true">attach_file</mat-icon>
                  <span>{{ f.fileName }}</span>
                  <span class="field-hint">{{ evidence.formatSize(f.fileSize) }}</span>
                  <button type="button" class="btn-secondary" (click)="unstage(f)" [attr.aria-label]="'Remover ' + f.fileName">Remover</button>
                </li>
              }
            </ul>
            <label class="pick">
              <mat-icon aria-hidden="true">upload_file</mat-icon>
              {{ uploading() ? 'Enviando…' : 'Escolher arquivo' }}
              <input type="file" hidden [accept]="accept" (change)="pick($event)" [disabled]="uploading()" />
            </label>
            @if (fieldErrors()['evidence']) { <span class="field-error" role="alert">{{ fieldErrors()['evidence'] }}</span> }
            @if (fileError()) { <span class="field-error" role="alert">{{ fileError() }}</span> }
          </fieldset>
        }
      }

      <div class="field-row">
        <label class="field">
          <span class="field-label">Tipo</span>
          <select formControlName="ticketType">
            <option value="INCIDENT">Incidente</option>
            <option value="PROBLEM">Problema</option>
            <option value="CHANGE">Mudança</option>
          </select>
        </label>

        <label class="field">
          <span class="field-label">Prioridade</span>
          <select formControlName="priority">
            <option value="LOW">Baixa</option>
            <option value="MEDIUM">Média</option>
            <option value="HIGH">Alta</option>
            <option value="CRITICAL">Crítica</option>
          </select>
        </label>
      </div>

      <div class="field-row">
        <label class="field">
          <span class="field-label">Urgência</span>
          <select formControlName="urgency">
            <option value="LOW">Baixa</option>
            <option value="MEDIUM">Média</option>
            <option value="HIGH">Alta</option>
            <option value="CRITICAL">Crítica</option>
          </select>
        </label>

        <label class="field">
          <span class="field-label">Impacto</span>
          <select formControlName="impact">
            <option value="LOW">Baixo</option>
            <option value="MEDIUM">Médio</option>
            <option value="HIGH">Alto</option>
            <option value="CRITICAL">Crítico</option>
          </select>
        </label>
      </div>

      @if (error()) {
        <div class="form-error">
          <mat-icon>error_outline</mat-icon>
          <span>{{ error() }}</span>
        </div>
      }

      <div class="form-actions">
        <button type="button" class="btn-secondary" (click)="cancel()">Cancelar</button>
        <button type="submit" class="btn-primary" [disabled]="form.invalid || submitting()">
          @if (submitting()) {
            <mat-icon class="spin">progress_activity</mat-icon>
          } @else {
            <mat-icon>check</mat-icon>
          }
          <span>Criar Ticket</span>
        </button>
      </div>
    </form>
  `,
  styles: [`
    :host { display: block; }
    .evidence { display: grid; gap: var(--sp-4); margin: 0; padding: var(--sp-5); border: 1px solid var(--border); border-radius: var(--radius-s); }
    .files { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--sp-3); }
    .files li { display: flex; align-items: center; gap: var(--sp-4); font-size: var(--fs-sm); }
    .files mat-icon { width: 16px; height: 16px; font-size: 16px; }
    .pick { display: inline-flex; align-items: center; gap: var(--sp-3); cursor: pointer; font-size: var(--fs-sm); font-weight: var(--fw-medium); color: var(--signal); }
    .field-hint { font-size: var(--fs-xs); color: var(--text-muted); }

    .page-title {
      margin: 0 0 20px;
      font-size: 1.5rem;
      font-weight: 600;
      color: var(--text);
    }

    .form {
      display: flex;
      flex-direction: column;
      gap: 16px;
      max-width: 640px;
    }

    .field {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .field-row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 16px;
    }

    .field-label {
      font-size: 12.5px;
      font-weight: 600;
      color: var(--text-muted);
    }

    input, textarea, select {
      font-family: var(--sans);
      font-size: 13px;
      color: var(--text);
      background: var(--surface-2);
      border: 1px solid var(--border);
      border-radius: var(--radius-s);
      padding: 9px 12px;
      outline: none;

      &:focus { border-color: var(--accent); }
      &::placeholder { color: var(--text-faint); }
    }

    textarea { resize: vertical; font-family: var(--sans); }

    .field-error {
      font-size: 11.5px;
      color: var(--critical);
    }

    .form-error {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 10px 12px;
      border-radius: var(--radius-s);
      background: var(--critical-soft);
      color: var(--critical);
      font-size: 12.5px;

      mat-icon { font-size: 18px; width: 18px; height: 18px; }
    }

    .form-actions {
      display: flex;
      justify-content: flex-end;
      gap: 10px;
      margin-top: 4px;
    }

    .btn-primary, .btn-secondary {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      height: 38px;
      padding: 0 18px;
      border-radius: var(--radius-s);
      font-weight: 500;
      font-size: 13px;
      border: none;
      cursor: pointer;

      mat-icon { font-size: 18px; width: 18px; height: 18px; }
    }

    .btn-primary {
      background: var(--accent);
      color: #fff;

      &:hover:not(:disabled) { filter: brightness(1.08); }
      &:disabled { opacity: 0.5; cursor: default; }
    }

    .btn-secondary {
      background: var(--surface-2);
      color: var(--text);
      border: 1px solid var(--border);

      &:hover { background: var(--surface-3); }
    }

    .spin {
      animation: spin 1s linear infinite;
    }

    @keyframes spin {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }
  `]
})
export class TicketCreateComponent implements OnInit {
  form: FormGroup;

  submitting = signal(false);
  error = signal<string | null>(null);
  areas = signal<PortalArea[]>([]);
  topicId = signal('');
  definition = signal<FormDefinition | null>(null);
  answers = signal<Record<string, unknown>>({});
  complete = signal(true);
  submitted = signal(false);
  staged = signal<EvidenceDto[]>([]);
  uploading = signal(false);
  fileError = signal<string | null>(null);
  readonly accept = EVIDENCE_ACCEPT;
  fieldErrors = signal<Record<string, string>>({});

  constructor(
    private fb: FormBuilder,
    private ticketService: TicketService,
    private authService: AuthService,
    private catalog: CatalogService,
    protected evidence: EvidenceService,
    private router: Router
  ) {
    this.form = this.fb.group({
      title: ['', [Validators.required, Validators.maxLength(500)]],
      description: [''],
      ticketType: ['INCIDENT' as TicketType, Validators.required],
      priority: ['MEDIUM' as TicketPriority, Validators.required],
      urgency: ['MEDIUM' as TicketPriority],
      impact: ['MEDIUM' as TicketPriority],
    });
  }

  ngOnInit(): void {
    // O catálogo só ajuda a classificar: sem ele o chamado avulso continua possível.
    this.catalog.portal().subscribe({ next: c => this.areas.set(c.areas.filter(a => a.topics.length > 0)), error: () => this.areas.set([]) });
  }

  chooseTopic(id: string): void {
    this.staged().forEach(f => this.evidence.remove(f.id).subscribe({ error: () => undefined }));
    this.staged.set([]);
    this.topicId.set(id);
    this.definition.set(null);
    this.answers.set({});
    this.fieldErrors.set({});
    this.complete.set(true);
    if (!id) return;
    this.catalog.portalForm(id).subscribe({
      next: def => {
        this.definition.set(def);
        this.complete.set(def.fields.every(f => !f.required));
      },
      error: () => this.error.set('Não foi possível abrir o formulário deste assunto.'),
    });
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
        this.error.set(null);
      },
      error: (err: HttpErrorResponse) => {
        this.fileError.set(typeof err.error?.detail === 'string' ? err.error.detail : 'Não foi possível enviar o arquivo.');
        this.uploading.set(false);
      },
    });
  }

  unstage(f: EvidenceDto): void {
    this.staged.update(list => list.filter(x => x.id !== f.id));
    this.evidence.remove(f.id).subscribe({ error: () => undefined });
  }

  onSubmit(): void {
    this.submitted.set(true);
    if (this.form.invalid) return;
    if (!this.complete()) {
      this.error.set('Preencha os campos obrigatórios do formulário do assunto.');
      return;
    }

    const user = this.authService.user();
    if (!user) {
      this.error.set('Sessão expirada. Faça login novamente.');
      return;
    }

    this.submitting.set(true);
    this.error.set(null);

    const value = this.form.getRawValue();
    this.ticketService.create({
      title: value.title,
      description: value.description || undefined,
      ticketType: value.ticketType,
      priority: value.priority,
      urgency: value.urgency,
      impact: value.impact,
      tenantId: user.tenantId,
      reporterId: user.id,
      topicId: this.topicId() || undefined,
      formAnswers: this.topicId() ? this.answers() : undefined,
      evidenceIds: this.staged().length ? this.staged().map(f => f.id) : undefined,
    }).subscribe({
      next: ticket => {
        this.router.navigate(['/tickets', ticket.id]);
      },
      error: (err: { status?: number; error?: { detail?: string; extensions?: Record<string, string> } }) => {
        this.submitting.set(false);
        const ext = err.error?.extensions;
        if (err.status === 422 && ext && Object.keys(ext).length > 0) {
          this.fieldErrors.set(ext);
          this.error.set('Revise os campos destacados.');
        } else {
          this.error.set(err.status === 422 && err.error?.detail ? err.error.detail : 'Não foi possível criar o ticket. Tente novamente.');
        }
      }
    });
  }

  cancel(): void {
    this.router.navigate(['/tickets']);
  }
}
