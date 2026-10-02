import { Component, Input, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { IntegrationService } from '../../../core/integrations/integration.service';
import type { ConnectorDto, ConnectorType } from '../../../core/integrations/integration.service';
import {
  ButtonComponent, DataTableComponent, EmptyStateComponent, NoticeComponent, NxCellDirective, NxColumn,
  PageHeaderComponent, StatusBadgeComponent,
} from '../../../shared/components';

export interface ConnectorField {
  key: string;
  label: string;
  placeholder: string;
}

@Component({
  selector: 'app-connector-panel',
  standalone: true,
  imports: [
    CommonModule, RouterLink, MatIconModule, ButtonComponent, DataTableComponent, EmptyStateComponent,
    NoticeComponent, NxCellDirective, PageHeaderComponent, StatusBadgeComponent,
  ],
  template: `
    <nx-page-header [heading]="title">
      <a nxButton routerLink="/integrations">Voltar</a>
      <button nxButton variant="primary" (click)="openForm()">
        <mat-icon>add</mat-icon>
        Nova conexão
      </button>
    </nx-page-header>

    <nx-notice tone="warning">As configurações são salvas, mas a conexão e a sincronização com o serviço externo ainda não estão disponíveis. Não informe senhas nem tokens: credenciais não são armazenadas.</nx-notice>

    @if (showForm()) {
      <form class="card form-card" (submit)="submit($event)">
        <div class="grid">
          <label class="field">
            <span class="label">Nome *</span>
            <input class="input" type="text" maxlength="255" [value]="name()" (input)="name.set($any($event.target).value)" />
          </label>
          @for (f of fields; track f.key) {
            <label class="field">
              <span class="label">{{ f.label }}</span>
              <input class="input" type="text" maxlength="500" [placeholder]="f.placeholder"
                     [value]="fieldValue(f.key)" (input)="setValue(f.key, $any($event.target).value)" />
            </label>
          }
          <label class="field">
            <span class="label">Cron de sincronização (seg min hora dia mês dia-semana)</span>
            <input class="input mono" type="text" placeholder="0 0 * * * *" [value]="cron()" (input)="cron.set($any($event.target).value)" />
          </label>
        </div>
        @if (formError()) { <p class="inline-error" role="alert">{{ formError() }}</p> }
        <div class="form-actions">
          <button nxButton type="button" (click)="showForm.set(false)">Cancelar</button>
          <button nxButton variant="primary" type="submit" [loading]="saving()" [disabled]="saving()">{{ saving() ? 'Salvando…' : 'Salvar' }}</button>
        </div>
      </form>
    }

    @if (actionError()) { <nx-notice tone="critical">{{ actionError() }}</nx-notice> }

    @if (error()) {
      <nx-empty-state variant="error" [heading]="error()!" />
    } @else {
      <nx-data-table [caption]="'Conexões ' + title" [columns]="columns" [rows]="items()" [loading]="loading()" emptyTitle="Nenhuma conexão cadastrada">
        <ng-template nxCell="summary" let-c>{{ summary(c) }}</ng-template>
        <ng-template nxCell="syncScheduleCron" let-c>{{ c.syncScheduleCron || '—' }}</ng-template>
        <ng-template nxCell="status" let-c>
          <nx-status-badge [tone]="c.status === 'CONNECTED' ? 'success' : 'neutral'">{{ statusLabel(c.status) }}</nx-status-badge>
        </ng-template>
        <ng-template nxCell="actions" let-c>
          <button nxButton variant="icon" size="sm" type="button" [attr.aria-label]="'Editar ' + c.name" title="Editar" (click)="edit(c)">
            <mat-icon>edit</mat-icon>
          </button>
          <button nxButton variant="icon-danger" size="sm" type="button" [attr.aria-label]="'Excluir ' + c.name" title="Excluir" (click)="remove(c)">
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
  `]
})
export class ConnectorPanelComponent implements OnInit {
  @Input({ required: true }) type!: ConnectorType;
  @Input({ required: true }) title!: string;
  @Input() icon = 'link';
  @Input() fields: ConnectorField[] = [];

  readonly columns: NxColumn[] = [
    { key: 'name', header: 'Nome', rowHeader: true, maxWidth: '260px' },
    { key: 'summary', header: 'Configuração', muted: true },
    { key: 'syncScheduleCron', header: 'Sincronização', mono: true, muted: true },
    { key: 'status', header: 'Status' },
    { key: 'actions', header: 'Ações', align: 'end', hideHeader: true },
  ];

  items = signal<ConnectorDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  actionError = signal<string | null>(null);

  showForm = signal(false);
  saving = signal(false);
  formError = signal<string | null>(null);
  editingId = signal<string | null>(null);
  name = signal('');
  cron = signal('');
  values = signal<Record<string, string>>({});

  constructor(private integrationService: IntegrationService) {}

  ngOnInit(): void {
    this.load();
  }

  statusLabel(status: string): string {
    return status === 'CONNECTED' ? 'Conectado' : status === 'DISCONNECTED' ? 'Desconectado' : status;
  }

  summary(c: ConnectorDto): string {
    const parts = this.fields.map(f => c.configuration?.[f.key]).filter(Boolean);
    return parts.length ? parts.join(' · ') : '—';
  }

  openForm(): void {
    this.editingId.set(null);
    this.name.set('');
    this.cron.set('');
    this.values.set({});
    this.formError.set(null);
    this.showForm.set(true);
  }

  edit(c: ConnectorDto): void {
    this.editingId.set(c.id);
    this.name.set(c.name);
    this.cron.set(c.syncScheduleCron ?? '');
    this.values.set({ ...(c.configuration ?? {}) });
    this.formError.set(null);
    this.showForm.set(true);
  }

  fieldValue(key: string): string {
    return this.values()[key] || '';
  }

  setValue(key: string, value: string): void {
    this.values.update(v => ({ ...v, [key]: value }));
  }

  submit(event: Event): void {
    event.preventDefault();
    if (!this.name().trim()) {
      this.formError.set('Informe um nome.');
      return;
    }
    const configuration: Record<string, string> = {};
    for (const f of this.fields) {
      const v = (this.values()[f.key] ?? '').trim();
      if (v) configuration[f.key] = v;
    }
    this.saving.set(true);
    this.formError.set(null);
    const body = { name: this.name().trim(), configuration, syncScheduleCron: this.cron().trim() };
    const id = this.editingId();
    const request$ = id
      ? this.integrationService.updateConnector(id, body)
      : this.integrationService.createConnector({ ...body, type: this.type });
    request$.subscribe({
      next: () => {
        this.saving.set(false);
        this.showForm.set(false);
        this.load();
      },
      error: err => {
        this.saving.set(false);
        this.formError.set(err?.error?.detail ?? err?.error?.message ?? 'Não foi possível salvar a conexão.');
      }
    });
  }

  remove(c: ConnectorDto): void {
    if (!confirm(`Excluir a conexão "${c.name}"?`)) return;
    this.actionError.set(null);
    this.integrationService.deleteConnector(c.id).subscribe({
      next: () => this.load(),
      error: () => this.actionError.set('Não foi possível excluir a conexão.')
    });
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.integrationService.listConnectors(this.type).subscribe({
      next: list => { this.items.set(list); this.loading.set(false); },
      error: () => { this.error.set('Não foi possível carregar as conexões.'); this.loading.set(false); }
    });
  }
}
