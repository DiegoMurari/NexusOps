import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { ExportFormat, ReportDto, ReportingService, ScheduledReportDto } from '../../../core/reporting/reporting.service';
import {
  ButtonComponent, DataTableComponent, EmptyStateComponent, NoticeComponent, NxCellDirective, NxColumn,
  PageHeaderComponent, StatusBadgeComponent,
} from '../../../shared/components';

@Component({
  selector: 'app-scheduled-reports',
  standalone: true,
  imports: [
    CommonModule, RouterLink, MatIconModule, ButtonComponent, DataTableComponent, EmptyStateComponent,
    NoticeComponent, NxCellDirective, PageHeaderComponent, StatusBadgeComponent,
  ],
  template: `
    <nx-page-header heading="Relatórios agendados">
      <a nxButton routerLink="/reports">Voltar</a>
      <button nxButton variant="primary" (click)="showForm.set(!showForm())" [disabled]="reports().length === 0">
        <mat-icon>add</mat-icon>
        Novo agendamento
      </button>
    </nx-page-header>

    <nx-notice tone="warning">Os agendamentos são registrados e a próxima execução é calculada, mas o envio automático ainda não está disponível.</nx-notice>

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
        @if (formError()) { <p class="inline-error" role="alert">{{ formError() }}</p> }
        <div class="form-actions">
          <button nxButton variant="primary" type="submit" [loading]="saving()" [disabled]="saving()">{{ saving() ? 'Salvando…' : 'Salvar agendamento' }}</button>
        </div>
      </form>
    }

    @if (actionError()) { <nx-notice tone="critical">{{ actionError() }}</nx-notice> }

    @if (error()) {
      <nx-empty-state variant="error" [heading]="error()!" />
    } @else {
      <nx-data-table caption="Agendamentos de relatórios" [columns]="columns" [rows]="items()" [loading]="loading()"
                     emptyTitle="Nenhum agendamento cadastrado">
        <ng-template nxCell="reportId" let-s>{{ reportName(s.reportId) }}</ng-template>
        <ng-template nxCell="scheduleCron" let-s>{{ s.scheduleCron }} <span class="tz">({{ s.timezone }})</span></ng-template>
        <ng-template nxCell="nextRunAt" let-s>{{ s.nextRunAt ? (s.nextRunAt | date:'dd/MM/yyyy HH:mm') : '—' }}</ng-template>
        <ng-template nxCell="active" let-s>
          <nx-status-badge [tone]="s.active ? 'success' : 'neutral'">{{ s.active ? 'Ativo' : 'Pausado' }}</nx-status-badge>
        </ng-template>
        <ng-template nxCell="actions" let-s>
          <button nxButton variant="icon" size="sm" type="button" [attr.aria-label]="(s.active ? 'Pausar ' : 'Ativar ') + s.name"
                  [title]="s.active ? 'Pausar' : 'Ativar'" (click)="toggle(s)">
            <mat-icon>{{ s.active ? 'pause' : 'play_arrow' }}</mat-icon>
          </button>
          <button nxButton variant="icon-danger" size="sm" type="button" [attr.aria-label]="'Excluir ' + s.name" title="Excluir" (click)="remove(s)">
            <mat-icon>delete_outline</mat-icon>
          </button>
        </ng-template>
      </nx-data-table>
    }
  `,
  styles: [`
    :host { display: block; }
    .form-card { padding: var(--sp-7); margin-bottom: var(--sp-6); display: flex; flex-direction: column; gap: var(--sp-6); }
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: var(--sp-6); }
    .inline-error { margin: 0; }
    .tz { font-size: var(--fs-xs); }
  `]
})
export class ScheduledReportsComponent implements OnInit {
  readonly columns: NxColumn[] = [
    { key: 'name', header: 'Nome', rowHeader: true, maxWidth: '260px' },
    { key: 'reportId', header: 'Relatório', muted: true },
    { key: 'scheduleCron', header: 'Cron', mono: true, muted: true },
    { key: 'format', header: 'Formato', muted: true },
    { key: 'nextRunAt', header: 'Próxima execução', mono: true, muted: true },
    { key: 'active', header: 'Status' },
    { key: 'actions', header: 'Ações', align: 'end', hideHeader: true },
  ];

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
