import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { TicketService, TicketPriority, TicketType } from '../../../core/ticketing/ticket.service';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-ticket-create',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatIconModule],
  template: `
    <h1 class="page-title">Criar Ticket</h1>

    <form class="card form" [formGroup]="form" (ngSubmit)="onSubmit()">
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
export class TicketCreateComponent {
  form: FormGroup;

  submitting = signal(false);
  error = signal<string | null>(null);

  constructor(
    private fb: FormBuilder,
    private ticketService: TicketService,
    private authService: AuthService,
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

  onSubmit(): void {
    if (this.form.invalid) return;

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
    }).subscribe({
      next: ticket => {
        this.router.navigate(['/tickets', ticket.id]);
      },
      error: () => {
        this.submitting.set(false);
        this.error.set('Não foi possível criar o ticket. Tente novamente.');
      }
    });
  }

  cancel(): void {
    this.router.navigate(['/tickets']);
  }
}
