import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { REPORT_TYPE_LABELS, ReportType, ReportingService } from '../../../core/reporting/reporting.service';

const TYPE_DESCRIPTIONS: Record<ReportType, string> = {
  CUSTOM: 'Reservado para consultas personalizadas (ainda sem execução).',
  TICKET_SUMMARY: 'Totais de tickets por status e prioridade, além do tempo médio de resolução.',
  SLA_COMPLIANCE: 'Percentual de tickets resolvidos dentro do prazo de SLA, por prioridade.',
  AGENT_PERFORMANCE: 'Tickets atribuídos e resolvidos por agente.',
  CATEGORY_DISTRIBUTION: 'Distribuição dos tickets por categoria.',
  TREND_ANALYSIS: 'Tickets criados e resolvidos por dia.',
};

@Component({
  selector: 'app-report-builder',
  standalone: true,
  imports: [CommonModule, RouterLink, MatIconModule],
  template: `
    <div class="page-header">
      <h1 class="page-title">Novo Relatório</h1>
      <a class="btn-secondary" routerLink="/reports">Cancelar</a>
    </div>

    <form class="card form-card" (submit)="submit($event)">
      <label class="field">
        <span class="label">Nome *</span>
        <input class="input" type="text" maxlength="255" [value]="name()" (input)="name.set($any($event.target).value)" />
      </label>

      <label class="field">
        <span class="label">Descrição</span>
        <textarea class="input area" rows="3" maxlength="1000" [value]="description()"
                  (input)="description.set($any($event.target).value)"></textarea>
      </label>

      <label class="field">
        <span class="label">Tipo *</span>
        <select class="input" [value]="reportType()" (change)="reportType.set($any($event.target).value)">
          @for (t of types; track t.value) {
            <option [value]="t.value" [selected]="t.value === reportType()" [disabled]="t.value === 'CUSTOM'">{{ t.label }}</option>
          }
        </select>
        <span class="hint">{{ description_(reportType()) }}</span>
      </label>

      <label class="check">
        <input type="checkbox" [checked]="publicReport()" (change)="publicReport.set($any($event.target).checked)" />
        <span>Visível para todos os usuários do tenant</span>
      </label>

      @if (error()) {
        <p class="inline-error">{{ error() }}</p>
      }

      <div class="form-actions">
        <button class="btn-primary" type="submit" [disabled]="saving()">
          <mat-icon>save</mat-icon>
          <span>{{ saving() ? 'Salvando…' : 'Salvar relatório' }}</span>
        </button>
      </div>
    </form>
  `,
  styles: [`
    :host { display: block; }
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .page-title { margin: 0; font-size: 1.5rem; font-weight: 600; color: var(--text); }
    .form-card { display: flex; flex-direction: column; gap: 16px; padding: 20px; max-width: 640px; }
    .field { display: flex; flex-direction: column; gap: 6px; }
    .label { font-size: 12.5px; font-weight: 600; color: var(--text-muted); }
    .hint { font-size: 12px; color: var(--text-faint); }
    .input {
      min-height: 36px; padding: 6px 12px; border-radius: var(--radius-s); border: 1px solid var(--border);
      background: var(--surface-2); color: var(--text); font-size: 13px; font-family: inherit; outline: none;
      &:focus { border-color: var(--accent); }
    }
    .area { resize: vertical; }
    .check { display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--text); }
    .form-actions { display: flex; justify-content: flex-end; }
    .btn-primary, .btn-secondary {
      display: inline-flex; align-items: center; gap: 6px; height: 38px; padding: 0 18px;
      border-radius: var(--radius-s); font-weight: 500; font-size: 13px; text-decoration: none; cursor: pointer;
      mat-icon { font-size: 18px; width: 18px; height: 18px; }
    }
    .btn-primary { background: var(--accent); color: #fff; border: none; &:hover:not(:disabled) { filter: brightness(1.08); } &:disabled { opacity: 0.6; cursor: default; } }
    .btn-secondary { background: var(--surface); color: var(--text); border: 1px solid var(--border); &:hover { background: var(--surface-2); } }
    .inline-error { margin: 0; color: var(--critical); font-size: 13px; }
  `]
})
export class ReportBuilderComponent {
  types = (Object.keys(REPORT_TYPE_LABELS) as ReportType[]).map(value => ({ value, label: REPORT_TYPE_LABELS[value] }));
  name = signal('');
  description = signal('');
  reportType = signal<ReportType>('TICKET_SUMMARY');
  publicReport = signal(false);
  saving = signal(false);
  error = signal<string | null>(null);

  constructor(private reportingService: ReportingService, private router: Router) {}

  description_(type: ReportType): string {
    return TYPE_DESCRIPTIONS[type];
  }

  submit(event: Event): void {
    event.preventDefault();
    if (!this.name().trim()) {
      this.error.set('Informe o nome do relatório.');
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.reportingService.createReport({
      name: this.name().trim(),
      description: this.description().trim() || undefined,
      reportType: this.reportType(),
      publicReport: this.publicReport(),
    }).subscribe({
      next: () => this.router.navigate(['/reports']),
      error: err => {
        this.saving.set(false);
        this.error.set(err?.error?.detail ?? err?.error?.message ?? 'Não foi possível salvar o relatório.');
      }
    });
  }
}
