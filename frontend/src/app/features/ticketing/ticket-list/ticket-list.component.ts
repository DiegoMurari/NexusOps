import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TicketService, TicketDto, TicketStatus } from '../../../core/ticketing/ticket.service';

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

const STATUS_FILTERS: { value: TicketStatus | null; label: string }[] = [
  { value: null, label: 'Todos' },
  { value: 'OPEN', label: 'Aberto' },
  { value: 'IN_PROGRESS', label: 'Em andamento' },
  { value: 'WAITING', label: 'Aguardando' },
  { value: 'ON_HOLD', label: 'Em espera' },
  { value: 'RESOLVED', label: 'Resolvido' },
  { value: 'CLOSED', label: 'Fechado' },
];

@Component({
  selector: 'app-ticket-list',
  standalone: true,
  imports: [CommonModule, RouterLink, MatIconModule],
  template: `
    <div class="page-header">
      <h1 class="page-title">Tickets</h1>
      <a class="btn-primary" routerLink="new">
        <mat-icon>add</mat-icon>
        <span>Novo Ticket</span>
      </a>
    </div>

    <div class="filters">
      @for (f of statusFilters; track f.label) {
        <button
          class="filter-chip"
          [class.active]="activeStatus() === f.value"
          (click)="setStatus(f.value)"
        >{{ f.label }}</button>
      }
    </div>

    @if (loading()) {
      <div class="card empty-state">
        <mat-icon class="empty-ic">hourglass_empty</mat-icon>
        <p>Carregando tickets…</p>
      </div>
    } @else if (error()) {
      <div class="card empty-state error">
        <mat-icon class="empty-ic">error_outline</mat-icon>
        <p>{{ error() }}</p>
      </div>
    } @else if (tickets().length === 0) {
      <div class="card empty-state">
        <mat-icon class="empty-ic">assignment</mat-icon>
        <p>Nenhum ticket encontrado</p>
      </div>
    } @else {
      <div class="card table-card">
        <table class="ticket-table">
          <thead>
            <tr>
              <th>Número</th>
              <th>Título</th>
              <th>Status</th>
              <th>Prioridade</th>
              <th>Criado em</th>
            </tr>
          </thead>
          <tbody>
            @for (ticket of tickets(); track ticket.id) {
              <tr [routerLink]="[ticket.id]" class="ticket-row">
                <td class="mono">{{ ticket.ticketNumber }}</td>
                <td class="title-cell">{{ ticket.title }}</td>
                <td>
                  <span class="status-tag" [class]="statusClass(ticket.status)">{{ statusLabel(ticket.status) }}</span>
                </td>
                <td>
                  <span class="prio" [class]="ticket.priority.toLowerCase()">
                    <span class="bar"></span>{{ priorityLabel(ticket.priority) }}
                  </span>
                </td>
                <td class="mono muted">{{ ticket.createdAt | date:'dd/MM/yyyy HH:mm' }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>

      <div class="pagination">
        <button class="page-btn" [disabled]="page() === 0" (click)="prevPage()">
          <mat-icon>chevron_left</mat-icon>
        </button>
        <span class="page-info">Página {{ page() + 1 }} de {{ totalPages() || 1 }}</span>
        <button class="page-btn" [disabled]="page() + 1 >= totalPages()" (click)="nextPage()">
          <mat-icon>chevron_right</mat-icon>
        </button>
      </div>
    }
  `,
  styles: [`
    :host { display: block; }

    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
    }

    .page-title {
      margin: 0;
      font-size: 1.5rem;
      font-weight: 600;
      color: var(--text);
    }

    .btn-primary {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      height: 38px;
      padding: 0 18px;
      border-radius: var(--radius-s);
      background: var(--accent);
      color: #fff;
      font-weight: 500;
      font-size: 13px;
      text-decoration: none;

      mat-icon { font-size: 18px; width: 18px; height: 18px; }

      &:hover { filter: brightness(1.08); }
    }

    .filters {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      margin-bottom: 16px;
    }

    .filter-chip {
      height: 30px;
      padding: 0 14px;
      border-radius: 999px;
      border: 1px solid var(--border);
      background: var(--surface);
      color: var(--text-muted);
      font-size: 12.5px;
      font-weight: 500;
      cursor: pointer;

      &:hover { background: var(--surface-2); }

      &.active {
        background: var(--accent-soft);
        border-color: var(--accent);
        color: var(--accent);
      }
    }

    .table-card {
      padding: 0;
      overflow-x: auto;
    }

    .ticket-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 13px;

      th {
        text-align: left;
        padding: 12px 16px;
        font-size: 11.5px;
        font-weight: 600;
        letter-spacing: 0.03em;
        text-transform: uppercase;
        color: var(--text-faint);
        border-bottom: 1px solid var(--border);
        white-space: nowrap;
      }

      td {
        padding: 12px 16px;
        border-bottom: 1px solid var(--border);
        color: var(--text);
      }

      tr:last-child td { border-bottom: none; }
    }

    .ticket-row {
      cursor: pointer;

      &:hover { background: var(--surface-2); }
    }

    .title-cell {
      max-width: 360px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .muted { color: var(--text-faint); }

    .pagination {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 12px;
      margin-top: 16px;
    }

    .page-btn {
      width: 32px;
      height: 32px;
      border-radius: 8px;
      border: 1px solid var(--border);
      background: var(--surface);
      color: var(--text-muted);
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;

      &:hover:not(:disabled) { background: var(--surface-2); }
      &:disabled { opacity: 0.4; cursor: default; }
    }

    .page-info {
      font-size: 12.5px;
      color: var(--text-muted);
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
export class TicketListComponent implements OnInit {
  tickets = signal<TicketDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  activeStatus = signal<TicketStatus | null>(null);
  page = signal(0);
  totalPages = signal(0);

  statusFilters = STATUS_FILTERS;

  constructor(private ticketService: TicketService) {}

  ngOnInit(): void {
    this.load();
  }

  setStatus(status: TicketStatus | null): void {
    this.activeStatus.set(status);
    this.page.set(0);
    this.load();
  }

  prevPage(): void {
    if (this.page() > 0) {
      this.page.update(p => p - 1);
      this.load();
    }
  }

  nextPage(): void {
    if (this.page() + 1 < this.totalPages()) {
      this.page.update(p => p + 1);
      this.load();
    }
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.ticketService.list({ status: this.activeStatus() ?? undefined, page: this.page() }).subscribe({
      next: res => {
        this.tickets.set(res.content);
        this.totalPages.set(res.totalPages);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Não foi possível carregar os tickets.');
        this.loading.set(false);
      }
    });
  }

  statusClass(status: TicketStatus): string {
    return status.toLowerCase();
  }

  statusLabel(status: TicketStatus): string {
    return STATUS_LABELS[status];
  }

  priorityLabel(priority: string): string {
    return PRIORITY_LABELS[priority] ?? priority;
  }
}
