import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { map } from 'rxjs';
import { TicketService, TicketDto, TicketStatus, QueueScope, ConsoleCounts } from '../../../core/ticketing/ticket.service';
import { CatalogService, QueueDto } from '../../../core/catalog/catalog.service';
import {
  DataTableComponent,
  NxCellDirective,
  NxColumn,
  PriorityLevel,
  PriorityMarkComponent,
  SlaRulerComponent,
  StatusBadgeComponent,
  slaFromTicket,
  ticketStatusGlyph,
  ticketStatusTone,
} from '../../../shared/components';

const STATUS_LABELS: Record<TicketStatus, string> = {
  OPEN: 'Aberto',
  IN_PROGRESS: 'Em andamento',
  WAITING: 'Aguardando',
  ON_HOLD: 'Em espera',
  RESOLVED: 'Resolvido',
  CLOSED: 'Fechado',
  REOPENED: 'Reaberto',
};

const SCOPES: { value: QueueScope; label: string; count: keyof ConsoleCounts }[] = [
  { value: 'MY_QUEUES', label: 'Minhas filas', count: 'myQueues' },
  { value: 'MINE', label: 'Atribuídos a mim', count: 'mine' },
  { value: 'UNASSIGNED', label: 'Sem responsável', count: 'unassigned' },
  { value: 'ALL', label: 'Toda a operação', count: 'all' },
];

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
  imports: [
    DatePipe,
    RouterLink,
    MatIconModule,
    DataTableComponent,
    NxCellDirective,
    PriorityMarkComponent,
    SlaRulerComponent,
    StatusBadgeComponent,
  ],
  template: `
    <header class="head">
      <h1>Tickets</h1>
      <a class="nx-verb primary" routerLink="new"><mat-icon aria-hidden="true">add</mat-icon>Novo ticket</a>
    </header>

    <div class="filters">
      <div class="seg" role="group" aria-label="Recorte da lista">
        @for (s of scopes; track s.value) {
          <button type="button" [attr.aria-pressed]="scope() === s.value" (click)="setScope(s.value)">
            {{ s.label }}@if (counts(); as c) { <span class="cnt">{{ c[s.count] }}</span> }
          </button>
        }
      </div>
      <select class="queue-sel" aria-label="Filtrar por fila" [value]="queueId() ?? ''" (change)="setQueue($any($event.target).value)">
        <option value="">Todas as filas</option>
        @for (q of queues(); track q.id) {
          <option [value]="q.id">{{ q.name }}</option>
        }
      </select>
    </div>

    <div class="filters">
      <div class="seg" role="group" aria-label="Filtrar por status">
        @for (f of statusFilters; track f.label) {
          <button type="button" [attr.aria-pressed]="activeStatus() === f.value" (click)="setStatus(f.value)">{{ f.label }}</button>
        }
      </div>
      @if (query(); as q) {
        <span class="q">
          busca <b>{{ q }}</b>
          <button type="button" class="clear" (click)="clearQuery()" aria-label="Limpar busca"><mat-icon aria-hidden="true">close</mat-icon></button>
        </span>
      }
    </div>

    @if (error()) {
      <p class="inline-error" role="alert">{{ error() }}</p>
    }

    <nx-data-table
      caption="Tickets"
      [columns]="columns"
      [rows]="visible()"
      [loading]="loading()"
      [rowClickable]="true"
      [rowRail]="rail"
      emptyTitle="Nenhum ticket encontrado"
      (rowActivate)="open($any($event))"
    >
      <ng-template nxCell="ticketNumber" let-t>
        <a class="num" [routerLink]="[t.id]">{{ t.ticketNumber }}</a>
      </ng-template>
      <ng-template nxCell="queue" let-t>
        @if (queueName(t.queueId); as n) { {{ n }} } @else { <span class="none">—</span> }
      </ng-template>
      <ng-template nxCell="assignee" let-t>
        @if (t.assigneeId) { <span [title]="t.assigneeId">{{ person(t.assigneeId) }}</span> } @else { <span class="unassigned">Sem responsável</span> }
      </ng-template>
      <ng-template nxCell="status" let-t>
        <nx-status-badge [tone]="tone(t.status)" [glyph]="glyph(t.status)">{{ label(t.status) }}</nx-status-badge>
      </ng-template>
      <ng-template nxCell="priority" let-t>
        <nx-priority-mark [level]="level(t.priority)" />
      </ng-template>
      <ng-template nxCell="sla" let-t>
        @if (sla(t); as s) {
          <nx-sla-ruler [pct]="s.pct" [state]="s.state" [label]="s.label" />
        } @else {
          <span class="none">—</span>
        }
      </ng-template>
      <ng-template nxCell="createdAt" let-t>{{ t.createdAt | date:'dd/MM/yyyy HH:mm' }}</ng-template>
    </nx-data-table>

    @if (totalPages() > 1) {
      <nav class="pagination" aria-label="Paginação">
        <button type="button" class="page-btn" [disabled]="page() === 0" (click)="prevPage()" aria-label="Página anterior">
          <mat-icon aria-hidden="true">chevron_left</mat-icon>
        </button>
        <span class="page-info">Página {{ page() + 1 }} de {{ totalPages() }}</span>
        <button type="button" class="page-btn" [disabled]="page() + 1 >= totalPages()" (click)="nextPage()" aria-label="Próxima página">
          <mat-icon aria-hidden="true">chevron_right</mat-icon>
        </button>
      </nav>
    }
  `,
  styles: [`
    :host { display: grid; grid-template-columns: minmax(0, 1fr); gap: var(--sp-6); }
    .head { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-6); }
    h1 { margin: 0; font-size: var(--fs-xl); line-height: 28px; font-weight: var(--fw-semibold); }
    .nx-verb mat-icon { width: 16px; height: 16px; font-size: 16px; }

    .filters { display: flex; flex-wrap: wrap; align-items: center; gap: var(--sp-5); }
    .q {
      display: inline-flex;
      align-items: center;
      gap: var(--sp-3);
      height: var(--control-h-sm);
      padding: 0 var(--sp-4);
      border: 1px solid var(--border-strong);
      border-radius: var(--radius-s);
      background: var(--surface);
      font: var(--fw-regular) var(--fs-sm) var(--mono);
      color: var(--text-muted);
    }
    .q b { color: var(--text); font-weight: var(--fw-medium); }
    .clear { display: grid; place-items: center; padding: 0; border: 0; background: transparent; color: var(--text-muted); cursor: pointer; }
    .clear mat-icon { width: 14px; height: 14px; font-size: 14px; }

    .queue-sel { height: var(--control-h-sm); padding: 0 var(--sp-4); border: 1px solid var(--border-strong); border-radius: var(--radius-s); background: var(--surface); color: var(--text); font: var(--fw-regular) var(--fs-sm) var(--sans, inherit); }
    .cnt { margin-left: var(--sp-3); font-family: var(--mono); color: var(--text-muted); }
    .unassigned { color: var(--warn, var(--text-muted)); font-size: var(--fs-sm); }

    .num { font-family: var(--mono); font-size: var(--fs-sm); font-weight: var(--fw-medium); color: var(--accent); text-decoration: none; }
    .num:hover { text-decoration: underline; }
    .none { color: var(--text-faint); font-family: var(--mono); }
    .inline-error { margin: 0; color: var(--critical); font-size: var(--fs-sm); }

    .pagination { display: flex; align-items: center; justify-content: center; gap: var(--sp-5); }
    .page-btn {
      display: grid;
      place-items: center;
      width: var(--control-h-md);
      height: var(--control-h-md);
      border: 1px solid var(--border-strong);
      border-radius: var(--radius-s);
      background: var(--surface);
      color: var(--text-muted);
      cursor: pointer;
    }
    .page-btn:hover:not(:disabled) { background: var(--surface-2); }
    .page-btn:disabled { opacity: var(--disabled-opacity); cursor: default; }
    .page-info { font-family: var(--mono); font-size: var(--fs-sm); color: var(--text-muted); }
  `]
})
export class TicketListComponent implements OnInit {
  private ticketService = inject(TicketService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private catalog = inject(CatalogService);

  private nowMs = Date.now();

  tickets = signal<TicketDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  activeStatus = signal<TicketStatus | null>(null);
  scope = signal<QueueScope>('MY_QUEUES');
  queueId = signal<string | null>(null);
  counts = signal<ConsoleCounts | null>(null);
  queues = signal<QueueDto[]>([]);
  scopes = SCOPES;
  page = signal(0);
  totalPages = signal(0);

  /** Busca vinda do campo de comando do Shell (?q=…): filtra a página carregada. */
  query = toSignal(this.route.queryParamMap.pipe(map(p => p.get('q')?.trim() || null)), { initialValue: null });

  visible = computed(() => this.tickets());

  statusFilters = STATUS_FILTERS;

  columns: NxColumn[] = [
    { key: 'ticketNumber', header: 'Número', rowHeader: true },
    { key: 'title', header: 'Título', maxWidth: '250px' },
    { key: 'queue', header: 'Fila' },
    { key: 'assignee', header: 'Responsável' },
    { key: 'status', header: 'Status' },
    { key: 'priority', header: 'Prioridade' },
    { key: 'sla', header: 'SLA' },
    { key: 'createdAt', header: 'Criado em', mono: true, muted: true },
  ];

  /** Nexus Rail: só urgência marca a linha. Crítico/estourado = vermelho; alta/em atenção = âmbar. */
  rail = (t: TicketDto): 'warn' | 'crit' | null => {
    if (t.status === 'RESOLVED' || t.status === 'CLOSED') return null;
    const s = slaFromTicket(t, this.nowMs);
    if (t.priority === 'CRITICAL' || s?.state === 'crit') return 'crit';
    if (t.priority === 'HIGH' || s?.state === 'warn') return 'warn';
    return null;
  };

  ngOnInit(): void {
    this.catalog.listQueues(false).subscribe({ next: q => this.queues.set(q), error: () => {} });
    this.route.queryParamMap.subscribe(p => {
      const sc = p.get('scope');
      if (sc && SCOPES.some(x => x.value === sc)) this.scope.set(sc as QueueScope);
      this.page.set(0);
      this.load();
    });
  }

  setScope(scope: QueueScope): void {
    this.scope.set(scope);
    this.page.set(0);
    this.load();
  }

  setQueue(id: string): void {
    this.queueId.set(id || null);
    this.page.set(0);
    this.load();
  }

  /** O responsável é guardado pelo e-mail; na lista basta a parte antes do @, com o e-mail completo no tooltip. */
  person(email: string): string {
    return email.split('@')[0];
  }

  queueName(id: string | null): string | null {
    return id ? this.queues().find(q => q.id === id)?.name ?? null : null;
  }

  setStatus(status: TicketStatus | null): void {
    this.activeStatus.set(status);
    this.page.set(0);
    this.load();
  }

  clearQuery(): void {
    this.router.navigate([], { queryParams: { q: null }, queryParamsHandling: 'merge' });
  }

  open(ticket: TicketDto): void {
    this.router.navigate([ticket.id], { relativeTo: this.route });
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

  tone = ticketStatusTone;
  glyph = ticketStatusGlyph;

  label(status: TicketStatus): string {
    return STATUS_LABELS[status];
  }

  level(priority: string): PriorityLevel {
    return priority.toLowerCase() as PriorityLevel;
  }

  sla(t: TicketDto) {
    return slaFromTicket(t, this.nowMs);
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.ticketService.counts().subscribe({ next: c => this.counts.set(c), error: () => {} });
    this.ticketService.queueView({
      scope: this.scope(), queueId: this.queueId() ?? undefined,
      status: this.activeStatus() ?? undefined, q: this.query() ?? undefined, page: this.page(),
    }).subscribe({
      next: res => {
        // Um único "agora" por carga: o SLA não pode mudar entre as passagens de detecção de mudanças.
        this.nowMs = Date.now();
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
}
