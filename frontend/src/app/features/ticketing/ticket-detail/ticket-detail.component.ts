import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { TicketService, TicketDto, TicketStatus, VALID_TRANSITIONS } from '../../../core/ticketing/ticket.service';

const STATUS_LABELS: Record<TicketStatus, string> = {
  OPEN: 'Aberto',
  IN_PROGRESS: 'Em andamento',
  WAITING: 'Aguardando',
  ON_HOLD: 'Em espera',
  RESOLVED: 'Resolvido',
  CLOSED: 'Fechado',
  REOPENED: 'Reaberto',
};

const PRIORITY_LABELS: Record<string, string> = {
  LOW: 'Baixa',
  MEDIUM: 'Média',
  HIGH: 'Alta',
  CRITICAL: 'Crítica',
};

@Component({
  selector: 'app-ticket-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, MatIconModule],
  template: `
    @if (ticket(); as t) {
      <div class="detail-header">
        <div>
          <a class="back-link" routerLink="/tickets">
            <mat-icon>arrow_back</mat-icon>
            <span>Voltar</span>
          </a>
          <div class="title-row">
            <span class="mono ticket-number">{{ t.ticketNumber }}</span>
            <span class="status-tag" [class]="t.status.toLowerCase()">{{ statusLabel(t.status) }}</span>
            <span class="prio" [class]="t.priority.toLowerCase()"><span class="bar"></span>{{ priorityLabel(t.priority) }}</span>
          </div>
          <h1 class="page-title">{{ t.title }}</h1>
        </div>
      </div>

      <div class="detail-grid">
        <div class="card">
          <h3 class="card-title">Descrição</h3>
          <p class="description">{{ t.description || 'Sem descrição.' }}</p>

          <div class="meta-grid">
            <div class="meta-item">
              <span class="meta-label">Tipo</span>
              <span class="meta-value">{{ t.ticketType }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Urgência</span>
              <span class="meta-value">{{ t.urgency ? priorityLabel(t.urgency) : '—' }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Impacto</span>
              <span class="meta-value">{{ t.impact ? priorityLabel(t.impact) : '—' }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Criado em</span>
              <span class="meta-value mono">{{ t.createdAt | date:'dd/MM/yyyy HH:mm' }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Atualizado em</span>
              <span class="meta-value mono">{{ t.updatedAt | date:'dd/MM/yyyy HH:mm' }}</span>
            </div>
            @if (t.resolvedAt) {
              <div class="meta-item">
                <span class="meta-label">Resolvido em</span>
                <span class="meta-value mono">{{ t.resolvedAt | date:'dd/MM/yyyy HH:mm' }}</span>
              </div>
            }
          </div>
        </div>

        <div class="side-col">
          <div class="card">
            <h3 class="card-title">Ações</h3>

            @if (actionError()) {
              <div class="form-error">
                <mat-icon>error_outline</mat-icon>
                <span>{{ actionError() }}</span>
              </div>
            }

            @if (availableTransitions().length > 0) {
              <div class="field">
                <span class="field-label">Mudar status</span>
                <div class="transition-buttons">
                  @for (next of availableTransitions(); track next) {
                    <button class="transition-btn" [disabled]="acting()" (click)="transition(next)">
                      {{ statusLabel(next) }}
                    </button>
                  }
                </div>
              </div>
            }

            <div class="field assign-field">
              <span class="field-label">Atribuir para (ID do usuário)</span>
              <div class="assign-row">
                <input type="text" [(ngModel)]="assigneeInput" placeholder="ID do responsável" />
                <button class="btn-primary" [disabled]="acting() || !assigneeInput" (click)="assign()">
                  <mat-icon>person_add</mat-icon>
                </button>
              </div>
              @if (t.assigneeId) {
                <span class="assigned-hint mono">Atual: {{ t.assigneeId }}</span>
              }
            </div>
          </div>

          <div class="card">
            <h3 class="card-title">Detalhes</h3>
            <div class="meta-item">
              <span class="meta-label">Reportado por</span>
              <span class="meta-value mono">{{ t.reporterId }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Responsável</span>
              <span class="meta-value mono">{{ t.assigneeId || '—' }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Grupo</span>
              <span class="meta-value mono">{{ t.groupId || '—' }}</span>
            </div>
          </div>
        </div>
      </div>
    } @else if (loading()) {
      <div class="card empty-state">
        <mat-icon class="empty-ic">hourglass_empty</mat-icon>
        <p>Carregando ticket…</p>
      </div>
    } @else if (error()) {
      <div class="card empty-state error">
        <mat-icon class="empty-ic">error_outline</mat-icon>
        <p>{{ error() }}</p>
      </div>
    }
  `,
  styles: [`
    :host { display: block; }

    .back-link {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      color: var(--text-muted);
      font-size: 12.5px;
      text-decoration: none;
      margin-bottom: 10px;

      mat-icon { font-size: 16px; width: 16px; height: 16px; }
      &:hover { color: var(--text); }
    }

    .detail-header {
      margin-bottom: 20px;
    }

    .title-row {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 6px;
    }

    .ticket-number {
      font-size: 12.5px;
      color: var(--text-faint);
    }

    .page-title {
      margin: 0;
      font-size: 1.35rem;
      font-weight: 600;
      color: var(--text);
    }

    .detail-grid {
      display: grid;
      grid-template-columns: 2fr 1fr;
      gap: 20px;
      align-items: start;
    }

    @media (max-width: 900px) {
      .detail-grid { grid-template-columns: 1fr; }
    }

    .side-col {
      display: flex;
      flex-direction: column;
      gap: 20px;
    }

    .card-title {
      margin: 0 0 12px;
      font-size: 14px;
      font-weight: 600;
      color: var(--text);
    }

    .description {
      font-size: 13px;
      color: var(--text);
      line-height: 1.6;
      white-space: pre-wrap;
      margin: 0 0 16px;
    }

    .meta-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 12px;
    }

    .meta-item {
      display: flex;
      flex-direction: column;
      gap: 2px;
      padding: 8px 0;
      border-bottom: 1px solid var(--border);
    }

    .meta-item:last-child { border-bottom: none; }

    .meta-label {
      font-size: 11px;
      text-transform: uppercase;
      letter-spacing: 0.03em;
      color: var(--text-faint);
    }

    .meta-value {
      font-size: 13px;
      color: var(--text);
    }

    .field {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .assign-field { margin-top: 16px; }

    .field-label {
      font-size: 12.5px;
      font-weight: 600;
      color: var(--text-muted);
    }

    .transition-buttons {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .transition-btn {
      height: 32px;
      padding: 0 14px;
      border-radius: var(--radius-s);
      border: 1px solid var(--border);
      background: var(--surface-2);
      color: var(--text);
      font-size: 12.5px;
      font-weight: 500;
      cursor: pointer;

      &:hover:not(:disabled) { background: var(--accent-soft); border-color: var(--accent); color: var(--accent); }
      &:disabled { opacity: 0.5; cursor: default; }
    }

    .assign-row {
      display: flex;
      gap: 8px;
    }

    input {
      flex: 1;
      font-family: var(--sans);
      font-size: 13px;
      color: var(--text);
      background: var(--surface-2);
      border: 1px solid var(--border);
      border-radius: var(--radius-s);
      padding: 8px 10px;
      outline: none;

      &:focus { border-color: var(--accent); }
      &::placeholder { color: var(--text-faint); }
    }

    .assigned-hint {
      font-size: 11.5px;
      color: var(--text-faint);
    }

    .btn-primary {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 38px;
      height: 38px;
      border-radius: var(--radius-s);
      background: var(--accent);
      color: #fff;
      border: none;
      cursor: pointer;
      flex: none;

      mat-icon { font-size: 18px; width: 18px; height: 18px; }
      &:hover:not(:disabled) { filter: brightness(1.08); }
      &:disabled { opacity: 0.5; cursor: default; }
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
      margin-bottom: 12px;

      mat-icon { font-size: 18px; width: 18px; height: 18px; }
    }

    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 10px;
      padding: 56px 16px;
      color: var(--text-faint);
      font-size: 13px;

      &.error { color: var(--critical); }
    }

    .empty-ic {
      font-size: 36px;
      width: 36px;
      height: 36px;
      color: inherit;
    }
  `]
})
export class TicketDetailComponent implements OnInit {
  ticket = signal<TicketDto | null>(null);
  loading = signal(true);
  error = signal<string | null>(null);
  acting = signal(false);
  actionError = signal<string | null>(null);
  assigneeInput = '';

  availableTransitions = computed<TicketStatus[]>(() => {
    const t = this.ticket();
    return t ? VALID_TRANSITIONS[t.status] : [];
  });

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private ticketService: TicketService
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.error.set('Ticket inválido.');
      this.loading.set(false);
      return;
    }
    this.load(id);
  }

  private load(id: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.ticketService.get(id).subscribe({
      next: t => {
        this.ticket.set(t);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Ticket não encontrado.');
        this.loading.set(false);
      }
    });
  }

  transition(target: TicketStatus): void {
    const t = this.ticket();
    if (!t) return;

    this.acting.set(true);
    this.actionError.set(null);
    this.ticketService.transition(t.id, { targetStatus: target }).subscribe({
      next: updated => {
        this.ticket.set(updated);
        this.acting.set(false);
      },
      error: () => {
        this.acting.set(false);
        this.actionError.set('Não foi possível alterar o status.');
      }
    });
  }

  assign(): void {
    const t = this.ticket();
    if (!t || !this.assigneeInput.trim()) return;

    this.acting.set(true);
    this.actionError.set(null);
    this.ticketService.assign(t.id, { assigneeId: this.assigneeInput.trim() }).subscribe({
      next: updated => {
        this.ticket.set(updated);
        this.assigneeInput = '';
        this.acting.set(false);
      },
      error: () => {
        this.acting.set(false);
        this.actionError.set('Não foi possível atribuir o ticket.');
      }
    });
  }

  statusLabel(status: TicketStatus): string {
    return STATUS_LABELS[status];
  }

  priorityLabel(priority: string): string {
    return PRIORITY_LABELS[priority] ?? priority;
  }
}
