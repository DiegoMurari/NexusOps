import { Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import {
  REPORT_TYPE_LABELS,
  ReportDto,
  ReportResultDto,
  ReportType,
  ReportingService,
} from '../../../core/reporting/reporting.service';
import {
  ButtonComponent, DataTableComponent, EmptyStateComponent, NoticeComponent, NxCellDirective, NxColumn,
  PageHeaderComponent, StatusBadgeComponent,
} from '../../../shared/components';

const PERIODS = [
  { days: 7, label: '7 dias' },
  { days: 30, label: '30 dias' },
  { days: 90, label: '90 dias' },
];

@Component({
  selector: 'app-report-dashboard',
  standalone: true,
  imports: [
    CommonModule, RouterLink, MatIconModule, ButtonComponent, DataTableComponent, EmptyStateComponent,
    NoticeComponent, NxCellDirective, PageHeaderComponent, StatusBadgeComponent,
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

    @if (overview(); as ov) {
      <div class="kpi-grid">
        <div class="kpi"><div class="num">{{ ov.summary['total'] }}</div><div class="lbl">Tickets criados</div></div>
        <div class="kpi"><div class="num">{{ ov.summary['open'] }}</div><div class="lbl">Em aberto</div></div>
        <div class="kpi"><div class="num">{{ ov.summary['resolved'] }}</div><div class="lbl">Resolvidos</div></div>
        <div class="kpi"><div class="num">{{ ov.summary['avgResolutionHours'] }} h</div><div class="lbl">Tempo médio de resolução</div></div>
      </div>
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

  periods = PERIODS;
  days = signal(30);
  overview = signal<ReportResultDto | null>(null);
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

  private loadOverview(): void {
    this.reportingService.overview(this.days()).subscribe({
      next: res => this.overview.set(res),
      error: () => this.overview.set(null)
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
