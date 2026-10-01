import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { PlatformService, AuditLogDto } from '../../../core/platform/platform.service';

@Component({
  selector: 'app-audit-log-viewer',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  template: `
    <div class="page-header">
      <h1 class="page-title">Log de Auditoria</h1>
      <div class="filters">
        <select class="filter" aria-label="Filtrar por ação" (change)="setAction($any($event.target).value)">
          <option value="">Todas as ações</option>
          @for (a of actions; track a) { <option [value]="a">{{ a }}</option> }
        </select>
        <input class="filter" type="text" placeholder="Recurso (ex.: roles)" aria-label="Filtrar por recurso"
               [value]="resourceType()" (change)="setResource($any($event.target).value)" />
      </div>
    </div>

    @if (loading()) {
      <div class="card empty-state">
        <mat-icon class="empty-ic">hourglass_empty</mat-icon>
        <p class="empty-text">Carregando log de auditoria…</p>
      </div>
    } @else if (error()) {
      <div class="card empty-state error">
        <mat-icon class="empty-ic">error_outline</mat-icon>
        <p class="empty-text">{{ error() }}</p>
      </div>
    } @else if (logs().length === 0) {
      <div class="card empty-state">
        <mat-icon class="empty-ic">history</mat-icon>
        <p class="empty-text">Nenhum evento registrado</p>
      </div>
    } @else {
      <div class="card table-card">
        <table class="audit-table">
          <thead>
            <tr>
              <th>Data</th>
              <th>Evento</th>
              <th>Ação</th>
              <th>Recurso</th>
              <th>Resultado</th>
              <th>Usuário</th>
              <th>IP</th>
            </tr>
          </thead>
          <tbody>
            @for (log of logs(); track log.id) {
              <tr>
                <td class="mono muted">{{ log.createdAt | date:'dd/MM/yyyy HH:mm:ss' }}</td>
                <td>{{ log.eventType }}</td>
                <td>
                  @if (log.action) {
                    <span class="action-chip">{{ log.action }}</span>
                  } @else {
                    <span class="muted">—</span>
                  }
                </td>
                <td class="mono">{{ log.resourceType || '—' }}{{ log.resourceId ? ' #' + log.resourceId : '' }}</td>
                <td>
                  @if (outcome(log); as o) {
                    <span class="outcome" [class.fail]="o === 'FAILURE'">{{ o === 'SUCCESS' ? 'Sucesso' : 'Falha' }}</span>
                  } @else {
                    <span class="muted">—</span>
                  }
                </td>
                <td class="mono muted">{{ log.userId || 'sistema' }}</td>
                <td class="mono muted">{{ log.ipAddress || '—' }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>

      <div class="pagination">
        <button class="page-btn" [disabled]="page() === 0" (click)="changePage(page() - 1)">Anterior</button>
        <span class="page-info mono">{{ page() + 1 }} / {{ totalPages() || 1 }}</span>
        <button class="page-btn" [disabled]="page() + 1 >= totalPages()" (click)="changePage(page() + 1)">Próxima</button>
      </div>
    }
  `,
  styles: [`
    :host { display: block; }

    .page-header { margin-bottom: 20px; display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; }

    .filters { display: flex; gap: 8px; }

    .filter {
      min-height: 34px;
      padding: 4px 10px;
      border-radius: var(--radius-s);
      border: 1px solid var(--border);
      background: var(--surface-2);
      color: var(--text);
      font-size: 13px;
      font-family: inherit;
      outline: none;

      &:focus { border-color: var(--accent); }
    }

    .outcome {
      display: inline-block;
      font-size: 11px;
      font-weight: 600;
      padding: 2px 8px;
      border-radius: 6px;
      background: var(--success-soft);
      color: var(--success);

      &.fail { background: var(--critical-soft); color: var(--critical); }
    }

    .page-title {
      margin: 0;
      font-size: 1.5rem;
      font-weight: 600;
      color: var(--text);
    }

    .table-card {
      padding: 0;
      overflow-x: auto;
    }

    .audit-table {
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
        white-space: nowrap;
      }

      tr:last-child td { border-bottom: none; }
    }

    .muted { color: var(--text-faint); }

    .action-chip {
      display: inline-block;
      font-size: 11px;
      font-weight: 600;
      padding: 2px 8px;
      border-radius: 6px;
      background: var(--accent-soft);
      color: var(--accent);
    }

    .pagination {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 16px;
      margin-top: 16px;
    }

    .page-btn {
      padding: 6px 14px;
      border-radius: 8px;
      border: 1px solid var(--border);
      background: var(--surface);
      color: var(--text);
      font-size: 12.5px;
      cursor: pointer;

      &:disabled { opacity: 0.5; cursor: default; }
    }

    .page-info {
      font-size: 12.5px;
      color: var(--text-faint);
    }

    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 12px;
      padding: 64px 24px;
      text-align: center;

      &.error { color: var(--critical); }
    }

    .empty-ic {
      font-size: 40px;
      width: 40px;
      height: 40px;
      color: inherit;
    }

    .empty-text {
      margin: 0;
      color: var(--text-muted);
      font-size: 13.5px;
    }
  `]
})
export class AuditLogViewerComponent implements OnInit {
  logs = signal<AuditLogDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  page = signal(0);
  totalPages = signal(0);
  actions = ['CREATE', 'UPDATE', 'DELETE', 'LOGIN'];
  action = signal('');
  resourceType = signal('');

  constructor(private platformService: PlatformService) {}

  setAction(value: string): void {
    this.action.set(value);
    this.changePage(0);
  }

  setResource(value: string): void {
    this.resourceType.set(value.trim());
    this.changePage(0);
  }

  /** SUCCESS/FAILURE recorded by the backend in the payload, or null for events without one. */
  outcome(log: AuditLogDto): string | null {
    if (!log.payload) return null;
    try {
      const value = JSON.parse(log.payload)?.outcome;
      return typeof value === 'string' ? value : null;
    } catch {
      return null;
    }
  }

  ngOnInit(): void {
    this.load();
  }

  changePage(page: number): void {
    this.page.set(page);
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.platformService.listAuditLogs(this.page(), 50, {
      action: this.action(),
      resourceType: this.resourceType(),
    }).subscribe({
      next: res => {
        this.logs.set(res.content);
        this.totalPages.set(res.totalPages);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Não foi possível carregar o log de auditoria.');
        this.loading.set(false);
      }
    });
  }
}
