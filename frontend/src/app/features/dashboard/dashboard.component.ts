import { Component, OnInit, computed, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { catchError, forkJoin, of } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { TicketService, TicketDto, TicketStatus } from '../../core/ticketing/ticket.service';
import { SlaService } from '../../core/sla/sla.service';
import { PlatformService } from '../../core/platform/platform.service';
import { AssetService } from '../../core/asset/asset.service';
import { IntegrationService } from '../../core/integrations/integration.service';
import {
  ActionIconComponent,
  ChainNode,
  ContextChainComponent,
  OperationsPulseComponent,
  PriorityLevel,
  PriorityMarkComponent,
  PulseReading,
  SlaRulerComponent,
  SlaReading,
  slaFromTicket,
} from '../../shared/components';

const OPEN_STATUSES: TicketStatus[] = ['OPEN', 'IN_PROGRESS', 'WAITING', 'ON_HOLD', 'REOPENED'];

const STATUS_LABELS: Record<TicketStatus, string> = {
  OPEN: 'Aberto',
  IN_PROGRESS: 'Em andamento',
  WAITING: 'Aguardando',
  ON_HOLD: 'Em espera',
  RESOLVED: 'Resolvido',
  CLOSED: 'Fechado',
  REOPENED: 'Reaberto',
};

const PRIORITY_RANK: Record<string, number> = { CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1 };

interface AtRiskItem {
  ticket: TicketDto;
  sla: SlaReading;
}

interface ActivityItem {
  id: string;
  who: string;
  what: string;
  time: string;
}

function formatAge(ms: number): string {
  const min = Math.max(0, Math.floor(ms / 60000));
  const days = Math.floor(min / 1440);
  const hours = Math.floor((min % 1440) / 60);
  const pad = (n: number) => String(n).padStart(2, '0');
  return days > 0 ? `${days}d ${pad(hours)}h` : `${pad(hours)}:${pad(min % 60)}`;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    DatePipe,
    RouterLink,
    MatIconModule,
    ActionIconComponent,
    ContextChainComponent,
    OperationsPulseComponent,
    PriorityMarkComponent,
    SlaRulerComponent,
  ],
  template: `
    <header class="head">
      <div>
        <h1>Visão geral</h1>
        <div class="sub">atualizado {{ updatedAt() | date:'HH:mm:ss' }}</div>
      </div>
      <a class="nx-verb primary" routerLink="/tickets/new">
        <mat-icon aria-hidden="true">add</mat-icon>Novo ticket
      </a>
    </header>

    <nx-operations-pulse [readings]="readings()" [loading]="loading()" />

    @if (focus(); as f) {
      <section class="panel" aria-labelledby="focus-h">
        <div class="ph">
          <h2 id="focus-h">Incidente em foco</h2>
          <span class="meta"><span class="node" aria-hidden="true"></span>{{ f.ticket.ticketNumber }}</span>
        </div>
        <div class="pad">
          <div class="focus-title">{{ f.ticket.title }}</div>
          <nx-context-chain [nodes]="f.chain" label="Contexto do incidente em foco" />
          <div class="verbs">
            @if (f.canTake) {
              <button type="button" class="nx-verb primary" [disabled]="acting()" (click)="take(f.ticket)">
                <nx-action-icon name="take" />Assumir
              </button>
              <a class="nx-verb" [routerLink]="['/tickets', f.ticket.id]">Abrir chamado</a>
            } @else {
              <a class="nx-verb primary" [routerLink]="['/tickets', f.ticket.id]">Abrir chamado</a>
            }
          </div>
        </div>
      </section>
    }

    <div class="cols">
      <section class="panel" aria-labelledby="risk-h">
        <div class="ph">
          <h2 id="risk-h">Prazos em risco</h2>
          <span class="meta">{{ atRisk().length }}</span>
        </div>
        @if (atRisk().length > 0) {
          <ul class="risk">
            @for (r of atRisk(); track r.ticket.id) {
              <li>
                <a class="it" [class.c]="r.sla.state === 'crit'" [class.w]="r.sla.state === 'warn'"
                   [routerLink]="['/tickets', r.ticket.id]">
                  <span class="id">{{ r.ticket.ticketNumber }}</span>
                  <nx-sla-ruler [pct]="r.sla.pct" [state]="r.sla.state" [label]="r.sla.label" />
                  <span class="tt">{{ r.ticket.title }}</span>
                  <nx-priority-mark [level]="level(r.ticket.priority)" />
                </a>
              </li>
            }
          </ul>
        } @else {
          <p class="calm">Nenhum chamado com o prazo em risco.</p>
        }
      </section>

      <section class="panel" aria-labelledby="act-h">
        <div class="ph">
          <h2 id="act-h">Atividade</h2>
          <span class="meta">últimas 5 ações</span>
        </div>
        @if (activity().length > 0) {
          <ul class="log">
            @for (a of activity(); track a.id) {
              <li><span class="ts">{{ a.time }}</span><span class="who">{{ a.who }}</span><span>{{ a.what }}</span></li>
            }
          </ul>
        } @else {
          <p class="calm">Nenhuma atividade recente.</p>
        }
      </section>
    </div>
  `,
  styles: [`
    :host { display: grid; gap: var(--sp-6); }

    .head { display: flex; align-items: flex-end; justify-content: space-between; gap: var(--sp-6); flex-wrap: wrap; }
    h1 { margin: 0; font-size: var(--fs-xl); line-height: 28px; font-weight: var(--fw-semibold); }
    .sub { font-family: var(--mono); font-size: var(--fs-xs); color: var(--text-muted); }
    .nx-verb mat-icon { width: 16px; height: 16px; font-size: 16px; }

    .cols { display: grid; grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr); gap: var(--sp-6); }
    @media (max-width: 960px) { .cols { grid-template-columns: 1fr; } }

    .panel { min-width: 0; overflow: hidden; background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-m); }
    .ph {
      position: relative;
      display: flex;
      align-items: baseline;
      gap: var(--sp-5);
      padding: var(--sp-5) var(--sp-6) var(--sp-4);
      border-bottom: 1px solid var(--border);
    }
    /* Nexus Rail: marca azul de 28px sob o título da seção. */
    .ph::after { content: ''; position: absolute; left: var(--sp-6); bottom: -1px; width: 28px; height: 2px; background: var(--accent); }
    .ph h2 { margin: 0; font-size: var(--fs-md); font-weight: var(--fw-semibold); }
    .meta {
      display: flex;
      align-items: center;
      gap: var(--sp-3);
      margin-left: auto;
      font-family: var(--mono);
      font-size: var(--fs-xs);
      color: var(--text-muted);
    }
    .node { width: 8px; height: 8px; border-radius: 2px; background: var(--accent-2); }

    .pad { display: grid; gap: var(--sp-5); padding: var(--sp-5) var(--sp-6); }
    .focus-title { font-size: var(--fs-md); font-weight: var(--fw-medium); }
    .verbs { display: flex; flex-wrap: wrap; gap: var(--sp-4); }

    .risk, .log { margin: 0; padding: 0; list-style: none; }
    .it {
      position: relative;
      display: grid;
      grid-template-columns: auto auto minmax(0, 1fr) auto;
      align-items: center;
      gap: var(--sp-5);
      padding: var(--sp-4) var(--sp-6);
      border-bottom: 1px solid var(--border);
      color: inherit;
      text-decoration: none;
    }
    li:last-child > .it { border-bottom: 0; }
    .it:hover { background: var(--surface-2); }
    .it.c::before, .it.w::before { content: ''; position: absolute; inset: 0 auto 0 0; width: 2px; }
    .it.c::before { background: var(--critical); }
    .it.w::before { background: var(--warning); }
    .id { font-family: var(--mono); font-size: var(--fs-xs); font-weight: var(--fw-medium); color: var(--text-muted); }
    .tt { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: var(--fs-base); }

    .log li {
      display: grid;
      grid-template-columns: 112px 84px minmax(0, 1fr);
      gap: var(--sp-5);
      padding: var(--sp-3) var(--sp-6);
      border-bottom: 1px solid var(--border);
      font-family: var(--mono);
      font-size: var(--fs-sm);
      color: var(--text-muted);
    }
    .log li:last-child { border-bottom: 0; }
    .log .ts { color: var(--text-faint); }
    .log .who { color: var(--text); overflow: hidden; text-overflow: ellipsis; }
    .calm { margin: 0; padding: var(--sp-7) var(--sp-6); font-size: var(--fs-base); color: var(--text-faint); }
  `]
})
export class DashboardComponent implements OnInit {
  loading = signal(true);
  updatedAt = signal(new Date());
  acting = signal(false);

  private tickets = signal<TicketDto[]>([]);
  private breaches = signal(0);
  private counts = signal<{ unassigned: number } | null>(null);
  private assets = signal<{ total: number; maintenance: number } | null>(null);
  private automations = signal<{ active: number; connectors: number; delivery: boolean } | null>(null);
  activity = signal<ActivityItem[]>([]);

  private open = computed(() => this.tickets().filter(t => OPEN_STATUSES.includes(t.status)));

  /** Só chamados com SLA de verdade (definição aplicada): prazo solto, sem definição, não é meta a cumprir. */
  private tracked = computed(() => this.open().filter(t => t.slaDefinitionId && !t.slaPausedAt));

  atRisk = computed<AtRiskItem[]>(() =>
    this.tracked()
      .map(ticket => ({ ticket, sla: slaFromTicket(ticket) }))
      .filter((r): r is AtRiskItem => r.sla !== null && r.sla.state !== 'ok' && r.sla.state !== 'none')
      .sort((a, b) => b.sla.pct - a.sla.pct)
      .slice(0, 6)
  );

  focus = computed(() => {
    const top = [...this.open()]
      .sort((a, b) => (PRIORITY_RANK[b.priority] - PRIORITY_RANK[a.priority])
        || (new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()))[0];
    if (!top || (top.priority !== 'CRITICAL' && top.priority !== 'HIGH')) return null;

    const sla = top.slaDefinitionId ? slaFromTicket(top) : null;
    const chain: ChainNode[] = [
      { kind: 'Ticket', value: top.ticketNumber, sub: STATUS_LABELS[top.status].toLowerCase(), tone: 'focus' },
      { kind: 'Solicitante', value: top.reporterId },
    ];
    if (top.ciReference) chain.push({ kind: 'Ativo', value: top.ciReference });
    if (sla) {
      chain.push({
        kind: 'SLA',
        value: 'Resolução',
        sub: sla.state === 'crit' ? `estourado ${sla.label}` : `restam ${sla.label}`,
        tone: sla.state === 'crit' ? 'crit' : undefined,
      });
    }
    return { ticket: top, chain, canTake: !top.assigneeId && (top.status === 'OPEN' || top.status === 'REOPENED') };
  });

  readings = computed<PulseReading[]>(() => {
    const open = this.open();
    const crit = open.filter(t => t.priority === 'CRITICAL').length;
    const high = open.filter(t => t.priority === 'HIGH').length;
    const incidents = open.filter(t => t.ticketType === 'INCIDENT');

    const unassigned = open.filter(t => !t.assigneeId);
    const oldest = unassigned.length
      ? Math.max(...unassigned.map(t => Date.now() - new Date(t.createdAt).getTime()))
      : 0;

    const unassignedTotal = this.counts()?.unassigned ?? unassigned.length;
    const atRisk = this.atRisk().filter(r => r.sla.state === 'warn').length;
    // Violados = violações registradas ainda ativas + chamados em atendimento já fora do prazo, sem contar duas vezes.
    const overdue = this.tracked().filter(t => slaFromTicket(t)?.state === 'crit').length;
    const breached = Math.max(this.breaches(), overdue);

    const assets = this.assets();
    const auto = this.automations();

    return [
      {
        key: 'incidents', label: 'Incidents', value: incidents.length,
        detail: incidents.length ? `${crit} crítico · ${high} alta` : 'nenhum aberto',
        state: crit > 0 ? 'crit' : high > 0 ? 'warn' : 'calm', link: '/tickets',
      },
      {
        key: 'sla', label: 'SLA', value: breached,
        detail: breached || atRisk ? `${breached} violado · ${atRisk} em risco` : 'tudo no prazo',
        state: breached > 0 ? 'crit' : atRisk > 0 ? 'warn' : 'calm', link: '/sla',
      },
      {
        key: 'unassigned', label: 'Unassigned', value: unassignedTotal,
        detail: unassignedTotal ? (unassigned.length ? `mais antigo ${formatAge(oldest)}` : 'aguardando atribuição') : 'fila atribuída',
        state: oldest > 4 * 3600000 ? 'crit' : unassignedTotal > 0 ? 'warn' : 'calm',
        link: '/tickets', queryParams: { scope: 'UNASSIGNED' },
      },
      {
        key: 'assets', label: 'Assets', value: assets ? assets.total : '—',
        detail: assets ? (assets.maintenance ? `${assets.maintenance} em manutenção` : 'todos operacionais') : 'indisponível',
        state: assets && assets.maintenance > 0 ? 'warn' : 'calm', link: '/assets',
      },
      {
        key: 'automations', label: 'Automations', value: auto ? auto.active : '—',
        detail: auto ? (auto.delivery ? `${auto.connectors} conectores` : 'entrega indisponível') : 'indisponível',
        state: auto && !auto.delivery ? 'warn' : 'calm', link: '/integrations',
      },
    ];
  });

  constructor(
    private ticketService: TicketService,
    private slaService: SlaService,
    private platformService: PlatformService,
    private assetService: AssetService,
    private integrationService: IntegrationService,
    private auth: AuthService
  ) {}

  /** Só consulta o que a pessoa pode ler: o resto vira "indisponível" em vez de um 403 no console. */
  private can(resource: string): boolean {
    return this.auth.can(resource, 'READ');
  }

  ngOnInit(): void {
    this.ticketService.counts().subscribe({ next: c => this.counts.set(c), error: () => this.counts.set(null) });
    this.load();
  }

  level(priority: string): PriorityLevel {
    return priority.toLowerCase() as PriorityLevel;
  }

  take(ticket: TicketDto): void {
    this.acting.set(true);
    this.ticketService.transition(ticket.id, { targetStatus: 'IN_PROGRESS' }).subscribe({
      next: () => { this.acting.set(false); this.load(); },
      error: () => this.acting.set(false),
    });
  }

  private load(): void {
    forkJoin({
      tickets: this.ticketService.queueView({ scope: 'ALL', size: 100 }).pipe(catchError(() => of(null))),
      breaches: this.can('SLA') ? this.slaService.listActiveBreaches().pipe(catchError(() => of(null))) : of(null),
      assets: this.can('ASSET') ? this.assetService.list({ size: 1 }).pipe(catchError(() => of(null))) : of(null),
      maintenance: this.can('ASSET') ? this.assetService.list({ status: 'MAINTENANCE', size: 1 }).pipe(catchError(() => of(null))) : of(null),
      automations: this.can('INTEGRATION') ? this.integrationService.overview().pipe(catchError(() => of(null))) : of(null),
      audit: this.can('AUDIT') ? this.platformService.listAuditLogs(0, 5).pipe(catchError(() => of(null))) : of(null),
    }).subscribe(r => {
      this.tickets.set(r.tickets?.content ?? []);
      this.breaches.set(r.breaches?.length ?? 0);
      this.assets.set(r.assets ? { total: r.assets.totalElements, maintenance: r.maintenance?.totalElements ?? 0 } : null);
      this.automations.set(r.automations
        ? { active: r.automations.activeWebhooks, connectors: r.automations.connectors, delivery: r.automations.deliveryAvailable }
        : null);
      this.activity.set((r.audit?.content ?? []).map(log => ({
        id: log.id,
        who: log.userId?.split('@')[0] ?? 'sistema',
        what: this.describeAuditLog(log),
        time: new Date(log.createdAt)
          .toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false })
          .replace(',', ''),
      })));
      this.updatedAt.set(new Date());
      this.loading.set(false);
    });
  }

  private describeAuditLog(log: { eventType: string; action: string | null; resourceType: string | null; resourceId: string | null }): string {
    const parts = [log.action ?? log.eventType];
    if (log.resourceType) parts.push(log.resourceType);
    if (log.resourceId) parts.push(`#${log.resourceId.slice(0, 8)}`);
    return parts.join(' ');
  }
}
