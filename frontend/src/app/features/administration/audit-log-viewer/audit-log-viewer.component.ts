import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PlatformService, AuditLogDto } from '../../../core/platform/platform.service';
import {
  ButtonComponent, DataTableComponent, EmptyStateComponent, NxCellDirective, NxColumn, PageHeaderComponent,
  StatusBadgeComponent,
} from '../../../shared/components';

@Component({
  selector: 'app-audit-log-viewer',
  standalone: true,
  imports: [
    CommonModule, ButtonComponent, DataTableComponent, EmptyStateComponent, NxCellDirective, PageHeaderComponent,
    StatusBadgeComponent,
  ],
  template: `
    <nx-page-header heading="Log de auditoria">
      <select class="input" aria-label="Filtrar por ação" (change)="setAction($any($event.target).value)">
        <option value="">Todas as ações</option>
        @for (a of actions; track a) { <option [value]="a">{{ a }}</option> }
      </select>
      <input class="input" type="text" placeholder="Recurso (ex.: roles)" aria-label="Filtrar por recurso"
             [value]="resourceType()" (change)="setResource($any($event.target).value)" />
    </nx-page-header>

    @if (error()) {
      <nx-empty-state variant="error" [heading]="error()!" />
    } @else {
      <nx-data-table caption="Eventos de auditoria" [columns]="columns" [rows]="logs()" [loading]="loading()"
                     emptyTitle="Nenhum evento registrado">
        <ng-template nxCell="createdAt" let-log>{{ log.createdAt | date:'dd/MM/yyyy HH:mm:ss' }}</ng-template>
        <ng-template nxCell="action" let-log>
          @if (log.action) { <nx-status-badge>{{ log.action }}</nx-status-badge> } @else { — }
        </ng-template>
        <ng-template nxCell="resource" let-log>{{ log.resourceType || '—' }}{{ log.resourceId ? ' #' + log.resourceId : '' }}</ng-template>
        <ng-template nxCell="outcome" let-log>
          @if (outcome(log); as o) {
            <nx-status-badge [tone]="o === 'FAILURE' ? 'critical' : 'success'">{{ o === 'SUCCESS' ? 'Sucesso' : 'Falha' }}</nx-status-badge>
          } @else { — }
        </ng-template>
        <ng-template nxCell="userId" let-log>{{ log.userId || 'sistema' }}</ng-template>
      </nx-data-table>

      @if (logs().length > 0) {
        <nav class="pagination" aria-label="Paginação do log de auditoria">
          <button nxButton size="sm" [disabled]="loading() || page() === 0" (click)="changePage(page() - 1)">Anterior</button>
          <span class="page-info mono" aria-live="polite">{{ page() + 1 }} / {{ totalPages() || 1 }}</span>
          <button nxButton size="sm" [disabled]="loading() || page() + 1 >= totalPages()" (click)="changePage(page() + 1)">Próxima</button>
        </nav>
      }
    }
  `,
  styles: [`
    :host { display: block; }
    .pagination { display: flex; align-items: center; justify-content: center; gap: var(--sp-6); margin-top: var(--sp-6); }
    .page-info { font-size: var(--fs-sm); color: var(--text-muted); }
  `]
})
export class AuditLogViewerComponent implements OnInit {
  readonly columns: NxColumn[] = [
    { key: 'createdAt', header: 'Data', mono: true, muted: true },
    { key: 'eventType', header: 'Evento' },
    { key: 'action', header: 'Ação' },
    { key: 'resource', header: 'Recurso', mono: true },
    { key: 'outcome', header: 'Resultado' },
    { key: 'userId', header: 'Usuário', mono: true, muted: true },
    { key: 'ipAddress', header: 'IP', mono: true, muted: true },
  ];

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
