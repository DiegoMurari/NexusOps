import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { SlaService, SlaBreachDto } from '../../../core/sla/sla.service';

@Component({
  selector: 'app-sla-breach-list',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  template: `
    <h1 class="page-title">Violações de SLA</h1>

    @if (loading()) {
      <div class="card empty-state">
        <mat-icon class="empty-ic">hourglass_empty</mat-icon>
        <p>Carregando violações…</p>
      </div>
    } @else if (error()) {
      <div class="card empty-state error">
        <mat-icon class="empty-ic">error_outline</mat-icon>
        <p>{{ error() }}</p>
      </div>
    } @else if (breaches().length === 0) {
      <div class="card empty-state">
        <mat-icon class="empty-ic">verified</mat-icon>
        <p>Nenhuma violação de SLA registrada</p>
      </div>
    } @else {
      <div class="card table-card">
        <table class="breach-table">
          <thead>
            <tr>
              <th>Ticket</th>
              <th>Tipo</th>
              <th>Detectada em</th>
              <th>Status</th>
              <th>Ações</th>
            </tr>
          </thead>
          <tbody>
            @for (breach of breaches(); track breach.id) {
              <tr>
                <td class="mono">{{ breach.ticketId | slice:0:8 }}</td>
                <td>{{ breach.breachType === 'RESPONSE' ? 'Resposta' : 'Resolução' }}</td>
                <td class="mono muted">{{ breach.breachTime | date:'dd/MM/yyyy HH:mm' }}</td>
                <td>
                  @if (breach.resolved) {
                    <span class="sla-chip ok">Resolvida</span>
                  } @else if (breach.acknowledged) {
                    <span class="sla-chip warn">Reconhecida</span>
                  } @else {
                    <span class="sla-chip crit">Pendente</span>
                  }
                </td>
                <td class="actions-cell">
                  @if (!breach.acknowledged) {
                    <button class="action-btn" [disabled]="acting() === breach.id" (click)="acknowledge(breach)">Reconhecer</button>
                  }
                  @if (!breach.resolved) {
                    <button class="action-btn" [disabled]="acting() === breach.id" (click)="resolve(breach)">Resolver</button>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
  styles: [`
    :host { display: block; }

    .page-title {
      margin: 0 0 20px;
      font-size: 1.5rem;
      font-weight: 600;
      color: var(--text);
    }

    .table-card {
      padding: 0;
      overflow-x: auto;
    }

    .breach-table {
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

    .muted { color: var(--text-faint); }

    .actions-cell {
      display: flex;
      gap: 8px;
    }

    .action-btn {
      height: 28px;
      padding: 0 12px;
      border-radius: var(--radius-s);
      border: 1px solid var(--border);
      background: var(--surface-2);
      color: var(--text);
      font-size: 12px;
      font-weight: 500;
      cursor: pointer;

      &:hover:not(:disabled) { background: var(--accent-soft); border-color: var(--accent); color: var(--accent); }
      &:disabled { opacity: 0.5; cursor: default; }
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
export class SlaBreachListComponent implements OnInit {
  breaches = signal<SlaBreachDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  acting = signal<string | null>(null);

  constructor(private slaService: SlaService) {}

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.slaService.listBreaches().subscribe({
      next: res => {
        this.breaches.set(res.content);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Não foi possível carregar as violações de SLA.');
        this.loading.set(false);
      }
    });
  }

  acknowledge(breach: SlaBreachDto): void {
    this.acting.set(breach.id);
    this.slaService.acknowledgeBreach(breach.id).subscribe({
      next: updated => {
        this.breaches.update(list => list.map(b => b.id === updated.id ? updated : b));
        this.acting.set(null);
      },
      error: () => this.acting.set(null)
    });
  }

  resolve(breach: SlaBreachDto): void {
    this.acting.set(breach.id);
    this.slaService.resolveBreach(breach.id).subscribe({
      next: updated => {
        this.breaches.update(list => list.map(b => b.id === updated.id ? updated : b));
        this.acting.set(null);
      },
      error: () => this.acting.set(null)
    });
  }
}
