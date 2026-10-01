import { Component, OnInit, signal } from '@angular/core';
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

const PERIODS = [
  { days: 7, label: '7 dias' },
  { days: 30, label: '30 dias' },
  { days: 90, label: '90 dias' },
];

@Component({
  selector: 'app-report-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, MatIconModule],
  template: `
    <div class="page-header">
      <h1 class="page-title">Relatórios</h1>
      <div class="header-actions">
        <a class="btn-secondary" routerLink="scheduled">
          <mat-icon>schedule</mat-icon>
          <span>Agendados</span>
        </a>
        <a class="btn-primary" routerLink="builder">
          <mat-icon>add</mat-icon>
          <span>Novo Relatório</span>
        </a>
      </div>
    </div>

    <div class="filters">
      @for (p of periods; track p.days) {
        <button class="filter-chip" [class.active]="days() === p.days" (click)="setDays(p.days)">{{ p.label }}</button>
      }
    </div>

    @if (overview(); as ov) {
      <div class="kpi-grid">
        <div class="card kpi"><span class="kpi-label">Tickets criados</span><span class="kpi-value">{{ ov.summary['total'] }}</span></div>
        <div class="card kpi"><span class="kpi-label">Em aberto</span><span class="kpi-value">{{ ov.summary['open'] }}</span></div>
        <div class="card kpi"><span class="kpi-label">Resolvidos</span><span class="kpi-value">{{ ov.summary['resolved'] }}</span></div>
        <div class="card kpi"><span class="kpi-label">Tempo médio de resolução</span><span class="kpi-value">{{ ov.summary['avgResolutionHours'] }} h</span></div>
      </div>
    }

    <h2 class="card-title">Relatórios salvos</h2>

    @if (loading()) {
      <div class="card empty-state"><mat-icon class="empty-ic">hourglass_empty</mat-icon><p>Carregando relatórios…</p></div>
    } @else if (error()) {
      <div class="card empty-state error"><mat-icon class="empty-ic">error_outline</mat-icon><p>{{ error() }}</p></div>
    } @else if (reports().length === 0) {
      <div class="card empty-state"><mat-icon class="empty-ic">analytics</mat-icon><p>Nenhum relatório salvo. Crie o primeiro em “Novo Relatório”.</p></div>
    } @else {
      <div class="card table-card">
        <table class="report-table">
          <thead>
            <tr><th>Nome</th><th>Tipo</th><th>Visibilidade</th><th>Criado em</th><th></th></tr>
          </thead>
          <tbody>
            @for (r of reports(); track r.id) {
              <tr [class.selected]="selected()?.id === r.id">
                <td class="title-cell">{{ r.name }}</td>
                <td class="muted">{{ typeLabel(r.reportType) }}</td>
                <td><span class="status-tag" [class.public]="r.publicReport">{{ r.publicReport ? 'Público' : 'Privado' }}</span></td>
                <td class="mono muted">{{ r.createdAt | date:'dd/MM/yyyy HH:mm' }}</td>
                <td class="actions">
                  <button class="icon-btn" title="Executar" (click)="run(r)"><mat-icon>play_arrow</mat-icon></button>
                  <button class="icon-btn" title="Exportar CSV" (click)="exportCsv(r)"><mat-icon>download</mat-icon></button>
                  <button class="icon-btn danger" title="Excluir" (click)="remove(r)"><mat-icon>delete_outline</mat-icon></button>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }

    @if (actionError()) {
      <p class="inline-error">{{ actionError() }}</p>
    }

    @if (selected(); as sel) {
      <h2 class="card-title">Resultado — {{ sel.name }}</h2>
      @if (running()) {
        <div class="card empty-state"><mat-icon class="empty-ic">hourglass_empty</mat-icon><p>Executando…</p></div>
      } @else if (result()) {
        <div class="card table-card">
          <div class="result-meta">
            Período: {{ result()!.from | date:'dd/MM/yyyy' }} a {{ result()!.to | date:'dd/MM/yyyy' }} · gerado em {{ result()!.generatedAt | date:'dd/MM/yyyy HH:mm' }}
          </div>
          <div class="summary-row">
            @for (e of summaryEntries(); track e[0]) {
              <span class="summary-chip"><b>{{ e[1] }}</b> {{ e[0] }}</span>
            }
          </div>
          @if (result()!.rows.length === 0) {
            <div class="empty-state"><p>Sem dados no período.</p></div>
          } @else {
            <table class="report-table">
              <thead><tr>@for (c of result()!.columns; track c) { <th>{{ c }}</th> }</tr></thead>
              <tbody>
                @for (row of result()!.rows; track $index) {
                  <tr>@for (c of result()!.columns; track c) { <td class="mono">{{ row[c] }}</td> }</tr>
                }
              </tbody>
            </table>
          }
        </div>
      }
    }
  `,
  styles: [`
    :host { display: block; }
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .page-title { margin: 0; font-size: 1.5rem; font-weight: 600; color: var(--text); }
    .header-actions { display: flex; gap: 8px; }
    .btn-primary, .btn-secondary {
      display: inline-flex; align-items: center; gap: 6px; height: 38px; padding: 0 18px;
      border-radius: var(--radius-s); font-weight: 500; font-size: 13px; text-decoration: none; cursor: pointer;
      mat-icon { font-size: 18px; width: 18px; height: 18px; }
    }
    .btn-primary { background: var(--accent); color: #fff; border: none; &:hover { filter: brightness(1.08); } }
    .btn-secondary { background: var(--surface); color: var(--text); border: 1px solid var(--border); &:hover { background: var(--surface-2); } }
    .filters { display: flex; gap: 8px; margin-bottom: 16px; }
    .filter-chip {
      height: 30px; padding: 0 14px; border-radius: 999px; border: 1px solid var(--border);
      background: var(--surface); color: var(--text-muted); font-size: 12.5px; font-weight: 500; cursor: pointer;
      &:hover { background: var(--surface-2); }
      &.active { background: var(--accent-soft); border-color: var(--accent); color: var(--accent); }
    }
    .kpi-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; margin-bottom: 24px; }
    .kpi { display: flex; flex-direction: column; gap: 6px; padding: 16px; }
    .kpi-label { font-size: 11.5px; font-weight: 600; letter-spacing: 0.03em; text-transform: uppercase; color: var(--text-faint); }
    .kpi-value { font-size: 1.6rem; font-weight: 600; color: var(--text); }
    .card-title { margin: 8px 0 12px; font-size: 1rem; font-weight: 600; color: var(--text); }
    .table-card { padding: 0; overflow-x: auto; margin-bottom: 16px; }
    .report-table {
      width: 100%; border-collapse: collapse; font-size: 13px;
      th { text-align: left; padding: 12px 16px; font-size: 11.5px; font-weight: 600; letter-spacing: 0.03em;
           text-transform: uppercase; color: var(--text-faint); border-bottom: 1px solid var(--border); white-space: nowrap; }
      td { padding: 12px 16px; border-bottom: 1px solid var(--border); color: var(--text); }
      tr:last-child td { border-bottom: none; }
      tr.selected { background: var(--accent-soft); }
    }
    .title-cell { max-width: 360px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .muted { color: var(--text-faint); }
    .actions { text-align: right; white-space: nowrap; }
    .icon-btn {
      width: 30px; height: 30px; border-radius: 8px; border: 1px solid var(--border); background: var(--surface);
      color: var(--text-muted); cursor: pointer; display: inline-flex; align-items: center; justify-content: center; margin-left: 4px;
      mat-icon { font-size: 18px; width: 18px; height: 18px; }
      &:hover { background: var(--surface-2); }
      &.danger:hover { color: var(--critical); background: var(--critical-soft); }
    }
    .status-tag {
      display: inline-flex; align-items: center; height: 22px; padding: 0 10px; border-radius: 999px;
      font-size: 11.5px; font-weight: 600; background: var(--surface-2); color: var(--text-muted);
      &.public { background: var(--success-soft); color: var(--success); }
    }
    .result-meta { padding: 12px 16px; font-size: 12.5px; color: var(--text-muted); border-bottom: 1px solid var(--border); }
    .summary-row { display: flex; flex-wrap: wrap; gap: 8px; padding: 12px 16px; border-bottom: 1px solid var(--border); }
    .summary-chip { padding: 4px 10px; border-radius: 999px; background: var(--surface-2); color: var(--text-muted); font-size: 12px; b { color: var(--text); } }
    .inline-error { color: var(--critical); font-size: 13px; margin: 0 0 12px; }
    .empty-state {
      display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px;
      padding: 40px 16px; color: var(--text-faint); font-size: 13px;
      &.error { color: var(--critical); }
    }
    .empty-ic { font-size: 36px; width: 36px; height: 36px; color: inherit; }
  `]
})
export class ReportDashboardComponent implements OnInit {
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
