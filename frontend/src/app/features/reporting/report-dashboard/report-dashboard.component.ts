import { Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { catchError, forkJoin, of } from 'rxjs';
import {
  REPORT_TYPE_LABELS,
  ReportDto,
  ReportResultDto,
  ReportType,
  ReportingService,
} from '../../../core/reporting/reporting.service';
import {
  BarItem, BarListComponent, ButtonComponent, DataTableComponent, EmptyStateComponent, NoticeComponent,
  NxCellDirective, NxColumn, PageHeaderComponent, SkeletonComponent, StatusBadgeComponent, TrendChartComponent,
  TrendPoint,
} from '../../../shared/components';

interface Analytics {
  summary: ReportResultDto | null;
  sla: ReportResultDto | null;
  backlog: ReportResultDto | null;
  trend: ReportResultDto | null;
  agents: ReportResultDto | null;
  categories: ReportResultDto | null;
}

const STATUS_LABELS: Record<string, string> = {
  OPEN: 'Aberto', IN_PROGRESS: 'Em andamento', ON_HOLD: 'Em espera', PENDING: 'Pendente', WAITING: 'Aguardando',
  REOPENED: 'Reaberto', RESOLVED: 'Resolvido', CLOSED: 'Fechado', CANCELLED: 'Cancelado',
};
const PRIORITY_LABELS: Record<string, string> = { LOW: 'Baixa', MEDIUM: 'Média', HIGH: 'Alta', CRITICAL: 'Crítica' };
const PRIORITY_TONES: Record<string, BarItem['tone']> = { LOW: 'neutral', MEDIUM: 'accent', HIGH: 'warning', CRITICAL: 'critical' };
const STATUS_TONES: Record<string, BarItem['tone']> = { RESOLVED: 'success', CLOSED: 'neutral', ON_HOLD: 'warning', REOPENED: 'warning' };

const PERIODS = [
  { days: 7, label: '7 dias' },
  { days: 30, label: '30 dias' },
  { days: 90, label: '90 dias' },
];

@Component({
  selector: 'app-report-dashboard',
  standalone: true,
  imports: [
    CommonModule, RouterLink, MatIconModule, BarListComponent, ButtonComponent, DataTableComponent,
    EmptyStateComponent, NoticeComponent, NxCellDirective, PageHeaderComponent, SkeletonComponent,
    StatusBadgeComponent, TrendChartComponent,
  ],
  template: `
    <nx-page-header heading="Relatórios">
      <a nxButton routerLink="scheduled">
        <mat-icon>schedule</mat-icon>
        Agendados
      </a>
      <a nxButton variant="primary" routerLink="builder">
        <mat-icon>add</mat-icon>
        Novo relatório
      </a>
    </nx-page-header>

    <div class="seg period" role="group" aria-label="Período">
      @for (p of periods; track p.days) {
        <button type="button" [attr.aria-pressed]="days() === p.days" (click)="setDays(p.days)">{{ p.label }}</button>
      }
    </div>

    @if (analytics(); as a) {
      @if (a.summary) {
        <div class="kpi-grid">
          <div class="kpi"><div class="num">{{ a.summary.summary['total'] }}</div><div class="lbl">Tickets criados</div></div>
          <div class="kpi"><div class="num">{{ a.summary.summary['open'] }}</div><div class="lbl">Em aberto</div></div>
          <div class="kpi"><div class="num">{{ a.summary.summary['resolved'] }}</div><div class="lbl">Resolvidos</div></div>
          <div class="kpi"><div class="num">{{ a.summary.summary['avgResolutionHours'] }} h</div><div class="lbl">Tempo médio de resolução</div></div>
        </div>
      }

      <div class="panels">
        <section class="panel wide" aria-labelledby="h-trend">
          <h3 id="h-trend">Tickets criados e resolvidos por dia</h3>
          @if (a.trend) { <nx-trend-chart [points]="trendPoints()" /> } @else { <p class="fail">Não foi possível carregar a tendência.</p> }
        </section>

        <section class="panel" aria-labelledby="h-status">
          <h3 id="h-status">Distribuição por situação</h3>
          @if (a.summary) { <nx-bar-list [items]="dimension('Status')" /> } @else { <p class="fail">Indisponível.</p> }
        </section>

        <section class="panel" aria-labelledby="h-prio">
          <h3 id="h-prio">Distribuição por prioridade</h3>
          @if (a.summary) { <nx-bar-list [items]="dimension('Prioridade')" /> } @else { <p class="fail">Indisponível.</p> }
        </section>

        <section class="panel" aria-labelledby="h-sla">
          <h3 id="h-sla">Conformidade de SLA</h3>
          @if (a.sla) {
            <div class="big"><span class="num">{{ a.sla.summary['compliancePercentage'] }}%</span>
              <span class="hint">dos prazos já decididos foram cumpridos</span></div>
            <nx-bar-list [items]="slaItems()" emptyText="Nenhum ticket com SLA no período." />
          } @else { <p class="fail">Indisponível.</p> }
        </section>

        <section class="panel" aria-labelledby="h-backlog">
          <h3 id="h-backlog">Backlog por idade</h3>
          @if (a.backlog) {
            <p class="meta">
              <b>{{ a.backlog.summary['open'] }}</b> em aberto ·
              <b>{{ a.backlog.summary['overdue'] }}</b> vencidos ·
              <b>{{ a.backlog.summary['unassigned'] }}</b> sem responsável ·
              mais antigo: <b>{{ a.backlog.summary['oldestDays'] }}</b> d
            </p>
            <nx-bar-list [items]="backlogItems()" emptyText="Nenhum ticket em aberto." />
          } @else { <p class="fail">Indisponível.</p> }
        </section>

        <section class="panel" aria-labelledby="h-cat">
          <h3 id="h-cat">Por categoria</h3>
          @if (a.categories) { <nx-bar-list [items]="categoryItems()" /> } @else { <p class="fail">Indisponível.</p> }
        </section>

        <section class="panel wide" aria-labelledby="h-agents">
          <h3 id="h-agents">Desempenho operacional por agente</h3>
          @if (a.agents) {
            @if (a.agents.rows.length > 0) {
              <nx-data-table caption="Desempenho por agente" [columns]="agentColumns" [rows]="a.agents.rows" />
            } @else { <p class="fail">Nenhum ticket atribuído no período.</p> }
          } @else { <p class="fail">Indisponível.</p> }
        </section>
      </div>
    } @else if (analyticsLoading()) {
      <div class="kpi-grid">
        <nx-skeleton height="72px" /><nx-skeleton height="72px" /><nx-skeleton height="72px" /><nx-skeleton height="72px" />
      </div>
    } @else {
      <nx-notice tone="critical">Não foi possível carregar os indicadores.</nx-notice>
    }

    <h2 class="section-title">Relatórios salvos</h2>

    @if (actionError()) { <nx-notice tone="critical">{{ actionError() }}</nx-notice> }

    @if (error()) {
      <nx-empty-state variant="error" [heading]="error()!" />
    } @else {
      <nx-data-table caption="Relatórios salvos" [columns]="columns" [rows]="reports()" [loading]="loading()"
                     [selectedRow]="selected() ?? undefined"
                     emptyTitle="Nenhum relatório salvo" emptyDescription="Crie o primeiro em “Novo relatório”.">
        <ng-template nxCell="reportType" let-r>{{ typeLabel(r.reportType) }}</ng-template>
        <ng-template nxCell="publicReport" let-r>
          <nx-status-badge [tone]="r.publicReport ? 'info' : 'neutral'">{{ r.publicReport ? 'Público' : 'Privado' }}</nx-status-badge>
        </ng-template>
        <ng-template nxCell="createdAt" let-r>{{ r.createdAt | date:'dd/MM/yyyy HH:mm' }}</ng-template>
        <ng-template nxCell="actions" let-r>
          <button nxButton variant="icon" size="sm" type="button" [attr.aria-label]="'Executar ' + r.name" title="Executar" (click)="run(r)"><mat-icon>play_arrow</mat-icon></button>
          <button nxButton variant="icon" size="sm" type="button" [attr.aria-label]="'Exportar CSV de ' + r.name" title="Exportar CSV" (click)="exportCsv(r)"><mat-icon>download</mat-icon></button>
          <button nxButton variant="icon-danger" size="sm" type="button" [attr.aria-label]="'Excluir ' + r.name" title="Excluir" (click)="remove(r)"><mat-icon>delete_outline</mat-icon></button>
        </ng-template>
      </nx-data-table>
    }

    @if (selected(); as sel) {
      <h2 class="section-title result-title">Resultado — {{ sel.name }}</h2>
      @if (result(); as res) {
        <div class="card result-meta">
          <div>Período: {{ res.from | date:'dd/MM/yyyy' }} a {{ res.to | date:'dd/MM/yyyy' }} · gerado em {{ res.generatedAt | date:'dd/MM/yyyy HH:mm' }}</div>
          <div class="summary-row">
            @for (e of summaryEntries(); track e[0]) {
              <span class="summary-item"><b>{{ e[1] }}</b> {{ e[0] }}</span>
            }
          </div>
        </div>
      }
      @if (!result() || result()!.rows.length > 0) {
        <nx-data-table caption="Resultado do relatório" [columns]="resultColumns()" [rows]="result()?.rows ?? []" [loading]="running()" />
      } @else {
        <nx-empty-state heading="Sem dados no período" />
      }
    }
  `,
  styles: [`
    :host { display: block; }
    .period { margin-bottom: var(--sp-6); }
    .kpi-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: var(--sp-5); margin-bottom: var(--sp-8); }
    .panels { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(320px, 100%), 1fr)); gap: var(--sp-5); margin-bottom: var(--sp-8); }
    .panel { min-width: 0; padding: var(--sp-6); border: 1px solid var(--border); border-radius: var(--radius-m); background: var(--surface); }
    .panel.wide { grid-column: 1 / -1; }
    .panel h3 { margin: 0 0 var(--sp-5); font-size: var(--fs-base); font-weight: var(--fw-semibold); color: var(--text); }
    .big { display: flex; align-items: baseline; flex-wrap: wrap; gap: var(--sp-4); margin-bottom: var(--sp-5); }
    .big .num { font-family: var(--mono); font-size: 28px; font-weight: var(--fw-semibold); color: var(--text); }
    .hint, .meta { color: var(--text-muted); font-size: var(--fs-sm); }
    .meta { margin: 0 0 var(--sp-5); b { color: var(--text); font-family: var(--mono); } }
    .fail { margin: 0; color: var(--text-faint); font-size: var(--fs-base); }
    .section-title { margin: var(--sp-4) 0 var(--sp-5); font-size: var(--fs-lg); line-height: 24px; font-weight: var(--fw-semibold); color: var(--text); }
    .result-title { margin-top: var(--sp-8); }
    .result-meta { display: flex; flex-direction: column; gap: var(--sp-4); margin-bottom: var(--sp-5); font-size: var(--fs-base); color: var(--text-muted); }
    .summary-row { display: flex; flex-wrap: wrap; gap: var(--sp-4); }
    .summary-item { padding: var(--sp-2) var(--sp-4); border-radius: var(--radius-s); background: var(--surface-2); font-size: var(--fs-sm); b { color: var(--text); } }
  `]
})
export class ReportDashboardComponent implements OnInit {
  readonly columns: NxColumn[] = [
    { key: 'name', header: 'Nome', rowHeader: true, maxWidth: '360px' },
    { key: 'reportType', header: 'Tipo', muted: true },
    { key: 'publicReport', header: 'Visibilidade' },
    { key: 'createdAt', header: 'Criado em', mono: true, muted: true },
    { key: 'actions', header: 'Ações', align: 'end', hideHeader: true },
  ];

  readonly agentColumns: NxColumn[] = [
    { key: 'agent', header: 'Agente', rowHeader: true },
    { key: 'assigned', header: 'Atribuídos', mono: true, align: 'end' },
    { key: 'resolved', header: 'Resolvidos', mono: true, align: 'end' },
    { key: 'avgResolutionHours', header: 'Média (h)', mono: true, align: 'end' },
  ];

  periods = PERIODS;
  days = signal(30);
  analytics = signal<Analytics | null>(null);
  analyticsLoading = signal(true);
  reports = signal<ReportDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  actionError = signal<string | null>(null);
  selected = signal<ReportDto | null>(null);
  result = signal<ReportResultDto | null>(null);
  running = signal(false);

  resultColumns = computed<NxColumn[]>(() =>
    (this.result()?.columns ?? []).map(c => ({ key: c, header: c, mono: true }))
  );

  constructor(private reportingService: ReportingService) {}

  ngOnInit(): void {
    this.loadReports();
    this.loadOverview();
  }

  setDays(days: number): void {
    this.days.set(days);
    this.loadOverview();
    const sel = this.selected();
    if (sel) this.run(sel);
  }

  summaryEntries(): [string, number | string][] {
    return Object.entries(this.result()?.summary ?? {});
  }

  typeLabel(type: ReportType): string {
    return REPORT_TYPE_LABELS[type] ?? type;
  }

  run(report: ReportDto): void {
    this.selected.set(report);
    this.result.set(null);
    this.actionError.set(null);
    this.running.set(true);
    this.reportingService.runReport(report.id, this.days()).subscribe({
      next: res => { this.result.set(res); this.running.set(false); },
      error: err => {
        this.running.set(false);
        this.selected.set(null);
        this.actionError.set(err?.error?.detail ?? err?.error?.message ?? 'Não foi possível executar o relatório.');
      }
    });
  }

  exportCsv(report: ReportDto): void {
    this.actionError.set(null);
    this.reportingService.exportCsv(report.id, this.days()).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `${report.name.replace(/[^\w-]+/g, '_')}.csv`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.actionError.set('Não foi possível exportar o relatório.')
    });
  }

  remove(report: ReportDto): void {
    if (!confirm(`Excluir o relatório "${report.name}"? Agendamentos associados também serão removidos.`)) return;
    this.actionError.set(null);
    this.reportingService.deleteReport(report.id).subscribe({
      next: () => {
        if (this.selected()?.id === report.id) { this.selected.set(null); this.result.set(null); }
        this.loadReports();
      },
      error: () => this.actionError.set('Não foi possível excluir (apenas o dono pode excluir).')
    });
  }

  dimension(name: 'Status' | 'Prioridade'): BarItem[] {
    const labels = name === 'Status' ? STATUS_LABELS : PRIORITY_LABELS;
    const tones = name === 'Status' ? STATUS_TONES : PRIORITY_TONES;
    return (this.analytics()?.summary?.rows ?? [])
      .filter(r => r['dimension'] === name)
      .map(r => ({ label: labels[String(r['value'])] ?? String(r['value']), value: Number(r['count']), tone: tones[String(r['value'])] }));
  }

  slaItems(): BarItem[] {
    const s = this.analytics()?.sla?.summary ?? {};
    return [
      { label: 'Cumpridos', value: Number(s['met'] ?? 0), tone: 'success' as const },
      { label: 'Violados', value: Number(s['breached'] ?? 0), tone: 'critical' as const },
      { label: 'No prazo', value: Number(s['pending'] ?? 0), tone: 'accent' as const },
    ];
  }

  backlogItems(): BarItem[] {
    return (this.analytics()?.backlog?.rows ?? []).map(r => ({
      label: String(r['age']), value: Number(r['count']), display: `${r['count']} · ${r['percentage']}%`,
    }));
  }

  categoryItems(): BarItem[] {
    return (this.analytics()?.categories?.rows ?? []).map(r => ({
      label: String(r['category']), value: Number(r['count']), display: `${r['count']} · ${r['percentage']}%`,
    }));
  }

  trendPoints(): TrendPoint[] {
    return (this.analytics()?.trend?.rows ?? []).map(r => ({
      label: String(r['date']), created: Number(r['created']), resolved: Number(r['resolved']),
    }));
  }

  /** Cada análise falha isoladamente: um cartão indisponível não derruba o painel. */
  private loadOverview(): void {
    const d = this.days();
    const get = (type: ReportType) => this.reportingService.builtin(type, d).pipe(catchError(() => of(null)));
    this.analyticsLoading.set(true);
    forkJoin({
      summary: get('TICKET_SUMMARY'), sla: get('SLA_COMPLIANCE'), backlog: get('BACKLOG'),
      trend: get('TREND_ANALYSIS'), agents: get('AGENT_PERFORMANCE'), categories: get('CATEGORY_DISTRIBUTION'),
    }).subscribe(res => {
      const allFailed = Object.values(res).every(v => v === null);
      this.analytics.set(allFailed ? null : res);
      this.analyticsLoading.set(false);
    });
  }

  private loadReports(): void {
    this.loading.set(true);
    this.error.set(null);
    this.reportingService.listReports().subscribe({
      next: list => { this.reports.set(list); this.loading.set(false); },
      error: () => { this.error.set('Não foi possível carregar os relatórios.'); this.loading.set(false); }
    });
  }
}
