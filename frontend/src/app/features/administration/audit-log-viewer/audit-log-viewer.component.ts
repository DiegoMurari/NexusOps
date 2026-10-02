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
      <input class="input" type="search" placeholder="Buscar usuário, evento ou recurso" aria-label="Buscar nos eventos"
             [value]="q()" (change)="setQuery($any($event.target).value)" />
      <input class="input" type="text" placeholder="Recurso (ex.: roles)" aria-label="Filtrar por recurso"
             [value]="resourceType()" (change)="setResource($any($event.target).value)" />
      <input class="input" type="date" aria-label="A partir de" [value]="since()" (change)="setSince($any($event.target).value)" />
      <input class="input" type="date" aria-label="Até" [value]="until()" (change)="setUntil($any($event.target).value)" />
    </nx-page-header>

    @if (error()) {
      <nx-empty-state variant="error" [heading]="error()!" />
    } @else {
      <nx-data-table caption="Eventos de auditoria" [columns]="columns" [rows]="logs()" [loading]="loading()"
                     [rowClickable]="true" (rowActivate)="select($any($event))"
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

      @if (selected(); as ev) {
        <section class="detail" aria-label="Detalhe do evento">
          <header>
            <h2>Detalhe do evento</h2>
            <button nxButton size="sm" (click)="selected.set(null)">Fechar</button>
          </header>
          <dl>
            <dt>Quem</dt><dd class="mono">{{ ev.userId || 'sistema' }}</dd>
            <dt>Quando</dt><dd class="mono">{{ ev.createdAt | date:'dd/MM/yyyy HH:mm:ss' }}</dd>
            <dt>Tenant</dt><dd class="mono">{{ ev.tenantId || '—' }}</dd>
            <dt>O que fez</dt><dd>{{ ev.eventType }} ({{ ev.action || '—' }})</dd>
            <dt>Recurso</dt><dd class="mono">{{ ev.resourceType || '—' }}{{ ev.resourceId ? ' #' + ev.resourceId : '' }}</dd>
            <dt>Resultado</dt><dd>{{ outcome(ev) === 'FAILURE' ? 'Falha' : outcome(ev) === 'SUCCESS' ? 'Sucesso' : '—' }}</dd>
            <dt>Origem</dt><dd class="mono">{{ ev.ipAddress || '—' }}</dd>
            <dt>Navegador</dt><dd class="muted">{{ ev.userAgent || '—' }}</dd>
          </dl>
          @if (ev.payload) { <pre class="mono">{{ pretty(ev.payload) }}</pre> }
        </section>
      }

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
    .detail { margin-top: var(--sp-6); border: 1px solid var(--border); border-radius: var(--radius-md); padding: var(--sp-6); background: var(--surface); }
    .detail header { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--sp-4); }
    .detail h2 { margin: 0; font-size: var(--fs-md); }
    .detail dl { display: grid; grid-template-columns: max-content 1fr; gap: var(--sp-2) var(--sp-6); margin: 0; }
    .detail dt { color: var(--text-muted); }
    .detail dd { margin: 0; min-width: 0; overflow-wrap: anywhere; }
    .detail pre { margin: var(--sp-4) 0 0; padding: var(--sp-4); background: var(--surface-2); border-radius: var(--radius-sm); overflow-x: auto; font-size: var(--fs-sm); }
    .muted { color: var(--text-muted); }
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
  actions = ['CREATE', 'UPDATE', 'DELETE', 'LOGIN', 'LOGOUT'];
  action = signal('');
  resourceType = signal('');
  q = signal('');
  since = signal('');
  until = signal('');
  selected = signal<AuditLogDto | null>(null);

  constructor(private platformService: PlatformService) {}

  setAction(value: string): void {
    this.action.set(value);
    this.changePage(0);
  }

  setResource(value: string): void {
    this.resourceType.set(value.trim());
    this.changePage(0);
  }

  setQuery(value: string): void {
    this.q.set(value.trim());
    this.changePage(0);
  }

  setSince(value: string): void {
    this.since.set(value);
    this.changePage(0);
  }

  setUntil(value: string): void {
    this.until.set(value);
    this.changePage(0);
  }

  select(log: AuditLogDto): void {
    this.selected.set(log);
  }

  pretty(payload: string): string {
    try {
      return JSON.stringify(JSON.parse(payload), null, 2);
    } catch {
      return payload;
    }
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
    this.selected.set(null);
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.platformService.listAuditLogs(this.page(), 50, {
      action: this.action(),
      resourceType: this.resourceType(),
      q: this.q(),
      since: this.since() ? new Date(this.since() + 'T00:00:00').toISOString() : '',
      until: this.until() ? new Date(this.until() + 'T23:59:59.999').toISOString() : '',
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
