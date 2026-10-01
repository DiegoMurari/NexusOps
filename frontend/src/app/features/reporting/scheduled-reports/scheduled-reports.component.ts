import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { ExportFormat, ReportDto, ReportingService, ScheduledReportDto } from '../../../core/reporting/reporting.service';

@Component({
  selector: 'app-scheduled-reports',
  standalone: true,
  imports: [CommonModule, RouterLink, MatIconModule],
  template: `
    <div class="page-header">
      <h1 class="page-title">Relatórios Agendados</h1>
      <div class="header-actions">
        <a class="btn-secondary" routerLink="/reports">Voltar</a>
        <button class="btn-primary" (click)="showForm.set(!showForm())" [disabled]="reports().length === 0">
          <mat-icon>add</mat-icon>
          <span>Novo Agendamento</span>
        </button>
      </div>
    </div>

    <div class="notice">
      <mat-icon>info</mat-icon>
      <span>Os agendamentos são registrados e a próxima execução é calculada, mas o envio automático ainda não está disponível.</span>
    </div>

    @if (showForm()) {
      <form class="card form-card" (submit)="submit($event)">
        <div class="grid">
          <label class="field">
            <span class="label">Relatório *</span>
            <select class="input" (change)="reportId.set($any($event.target).value)">
              <option value="">Selecione…</option>
              @for (r of reports(); track r.id) { <option [value]="r.id">{{ r.name }}</option> }
            </select>
          </label>
          <label class="field">
            <span class="label">Nome *</span>
            <input class="input" type="text" maxlength="255" [value]="name()" (input)="name.set($any($event.target).value)" />
          </label>
          <label class="field">
            <span class="label">Cron (seg min hora dia mês dia-semana) *</span>
            <input class="input mono" type="text" [value]="cron()" (input)="cron.set($any($event.target).value)" />
          </label>
          <label class="field">
            <span class="label">Fuso horário</span>
            <input class="input" type="text" [value]="timezone()" (input)="timezone.set($any($event.target).value)" />
          </label>
          <label class="field">
            <span class="label">Formato</span>
            <select class="input" (change)="format.set($any($event.target).value)">
              @for (f of formats; track f) { <option [value]="f" [selected]="f === format()">{{ f }}</option> }
            </select>
          </label>
          <label class="field">
            <span class="label">Destinatários (separados por vírgula) *</span>
            <input class="input" type="text" [value]="recipients()" (input)="recipients.set($any($event.target).value)" />
          </label>
        </div>
        @if (formError()) { <p class="inline-error">{{ formError() }}</p> }
        <div class="form-actions">
          <button class="btn-primary" type="submit" [disabled]="saving()">{{ saving() ? 'Salvando…' : 'Salvar agendamento' }}</button>
        </div>
      </form>
    }

    @if (loading()) {
      <div class="card empty-state"><mat-icon class="empty-ic">hourglass_empty</mat-icon><p>Carregando agendamentos…</p></div>
    } @else if (error()) {
      <div class="card empty-state error"><mat-icon class="empty-ic">error_outline</mat-icon><p>{{ error() }}</p></div>
    } @else if (items().length === 0) {
      <div class="card empty-state"><mat-icon class="empty-ic">schedule</mat-icon><p>Nenhum agendamento cadastrado</p></div>
    } @else {
      <div class="card table-card">
        <table class="sched-table">
          <thead>
            <tr><th>Nome</th><th>Relatório</th><th>Cron</th><th>Formato</th><th>Próxima execução</th><th>Status</th><th></th></tr>
          </thead>
          <tbody>
            @for (s of items(); track s.id) {
              <tr>
                <td class="title-cell">{{ s.name }}</td>
                <td class="muted">{{ reportName(s.reportId) }}</td>
                <td class="mono muted">{{ s.scheduleCron }} <span class="tz">({{ s.timezone }})</span></td>
                <td class="muted">{{ s.format }}</td>
                <td class="mono muted">{{ s.nextRunAt ? (s.nextRunAt | date:'dd/MM/yyyy HH:mm') : '—' }}</td>
                <td><span class="status-tag" [class.on]="s.active">{{ s.active ? 'Ativo' : 'Pausado' }}</span></td>
                <td class="actions">
                  <button class="icon-btn" [title]="s.active ? 'Pausar' : 'Ativar'" (click)="toggle(s)">
                    <mat-icon>{{ s.active ? 'pause' : 'play_arrow' }}</mat-icon>
                  </button>
                  <button class="icon-btn danger" title="Excluir" (click)="remove(s)"><mat-icon>delete_outline</mat-icon></button>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
    @if (actionError()) { <p class="inline-error">{{ actionError() }}</p> }
  `,
  styles: [`
    :host { display: block; }
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .page-title { margin: 0; font-size: 1.5rem; font-weight: 600; color: var(--text); }
    .header-actions { display: flex; gap: 8px; }
    .notice {
      display: flex; align-items: center; gap: 8px; padding: 10px 14px; margin-bottom: 16px; border-radius: var(--radius-s);
      background: var(--warning-soft); color: var(--warning); font-size: 12.5px;
      mat-icon { font-size: 18px; width: 18px; height: 18px; }
    }
    .btn-primary, .btn-secondary {
      display: inline-flex; align-items: center; gap: 6px; height: 38px; padding: 0 18px;
      border-radius: var(--radius-s); font-weight: 500; font-size: 13px; text-decoration: none; cursor: pointer;
      mat-icon { font-size: 18px; width: 18px; height: 18px; }
    }
    .btn-primary { background: var(--accent); color: #fff; border: none; &:hover:not(:disabled) { filter: brightness(1.08); } &:disabled { opacity: 0.5; cursor: default; } }
    .btn-secondary { background: var(--surface); color: var(--text); border: 1px solid var(--border); &:hover { background: var(--surface-2); } }
    .form-card { padding: 20px; margin-bottom: 16px; display: flex; flex-direction: column; gap: 16px; }
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 16px; }
    .field { display: flex; flex-direction: column; gap: 6px; }
    .label { font-size: 12.5px; font-weight: 600; color: var(--text-muted); }
    .input {
      min-height: 36px; padding: 6px 12px; border-radius: var(--radius-s); border: 1px solid var(--border);
      background: var(--surface-2); color: var(--text); font-size: 13px; font-family: inherit; outline: none;
      &:focus { border-color: var(--accent); }
    }
    .form-actions { display: flex; justify-content: flex-end; }
    .table-card { padding: 0; overflow-x: auto; }
    .sched-table {
      width: 100%; border-collapse: collapse; font-size: 13px;
      th { text-align: left; padding: 12px 16px; font-size: 11.5px; font-weight: 600; letter-spacing: 0.03em;
           text-transform: uppercase; color: var(--text-faint); border-bottom: 1px solid var(--border); white-space: nowrap; }
      td { padding: 12px 16px; border-bottom: 1px solid var(--border); color: var(--text); }
      tr:last-child td { border-bottom: none; }
    }
    .title-cell { max-width: 260px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .muted { color: var(--text-faint); }
    .tz { font-size: 11px; }
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
      &.on { background: var(--success-soft); color: var(--success); }
    }
    .inline-error { color: var(--critical); font-size: 13px; margin: 8px 0 0; }
    .empty-state {
      display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px;
      padding: 48px 16px; color: var(--text-faint); font-size: 13px;
      &.error { color: var(--critical); }
    }
    .empty-ic { font-size: 36px; width: 36px; height: 36px; color: inherit; }
  `]
})
export class ScheduledReportsComponent implements OnInit {
  formats: ExportFormat[] = ['CSV', 'PDF', 'EXCEL'];
  items = signal<ScheduledReportDto[]>([]);
  reports = signal<ReportDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  actionError = signal<string | null>(null);

  showForm = signal(false);
  saving = signal(false);
  formError = signal<string | null>(null);
  reportId = signal('');
  name = signal('');
  cron = signal('0 0 8 * * MON');
  timezone = signal('America/Sao_Paulo');
  format = signal<ExportFormat>('CSV');
  recipients = signal('');

  constructor(private reportingService: ReportingService) {}

  ngOnInit(): void {
    this.reportingService.listReports().subscribe({ next: r => this.reports.set(r), error: () => {} });
    this.load();
  }

  reportName(id: string): string {
    return this.reports().find(r => r.id === id)?.name ?? '—';
  }

  submit(event: Event): void {
    event.preventDefault();
    const recipients = this.recipients().split(',').map(r => r.trim()).filter(Boolean);
    if (!this.reportId() || !this.name().trim() || !this.cron().trim() || recipients.length === 0) {
      this.formError.set('Preencha relatório, nome, cron e ao menos um destinatário.');
      return;
    }
    this.saving.set(true);
    this.formError.set(null);
    this.reportingService.createScheduled({
      reportId: this.reportId(),
      name: this.name().trim(),
      scheduleCron: this.cron().trim(),
      timezone: this.timezone().trim() || undefined,
      format: this.format(),
      recipients,
    }).subscribe({
      next: () => {
        this.saving.set(false);
        this.showForm.set(false);
        this.name.set('');
        this.recipients.set('');
        this.load();
      },
      error: err => {
        this.saving.set(false);
        this.formError.set(err?.error?.detail ?? err?.error?.message ?? 'Não foi possível salvar o agendamento.');
      }
    });
  }

  toggle(item: ScheduledReportDto): void {
    this.actionError.set(null);
    this.reportingService.setScheduledActive(item.id, !item.active).subscribe({
      next: () => this.load(),
      error: () => this.actionError.set('Não foi possível alterar o agendamento.')
    });
  }

  remove(item: ScheduledReportDto): void {
    if (!confirm(`Excluir o agendamento "${item.name}"?`)) return;
    this.actionError.set(null);
    this.reportingService.deleteScheduled(item.id).subscribe({
      next: () => this.load(),
      error: () => this.actionError.set('Não foi possível excluir o agendamento.')
    });
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.reportingService.listScheduled().subscribe({
      next: list => { this.items.set(list); this.loading.set(false); },
      error: () => { this.error.set('Não foi possível carregar os agendamentos.'); this.loading.set(false); }
    });
  }
}
