import { Component, Input, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { IntegrationService, describeResult } from '../../../core/integrations/integration.service';
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

    <nx-notice tone="info">As configurações são salvas e, havendo uma URL base, você pode verificar se o endereço responde. A sincronização com o serviço externo ainda não está disponível. Não informe senhas nem tokens: credenciais não são armazenadas.</nx-notice>

    @if (checkResult(); as r) {
      <nx-notice [tone]="r.ok ? 'success' : 'critical'">{{ r.text }}</nx-notice>
    }

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
        <ng-template nxCell="enabled" let-c>
          <nx-status-badge [tone]="c.enabled ? 'success' : 'neutral'">{{ c.enabled ? 'Ativa' : 'Desativada' }}</nx-status-badge>
        </ng-template>
        <ng-template nxCell="lastCheck" let-c>
          @if (c.lastCheckStatus) {
            <nx-status-badge [tone]="c.lastCheckStatus === 'SUCCESS' ? 'success' : 'critical'">{{ c.lastCheckStatus === 'SUCCESS' ? 'Responde' : 'Falhou' }}</nx-status-badge>
            <span class="when">{{ c.lastCheckAt | date:'dd/MM HH:mm' }} · {{ describe(c.lastCheckMessage) }}</span>
          } @else {
            <span class="when">Nunca verificada</span>
          }
        </ng-template>
        <ng-template nxCell="actions" let-c>
          @if (canCheck(c)) {
            <button nxButton variant="icon" size="sm" type="button" [attr.aria-label]="'Verificar ' + c.name"
                    title="Verificar se o endereço responde" [loading]="checkingId() === c.id"
                    [disabled]="checkingId() !== null" (click)="check(c)">
              <mat-icon>network_check</mat-icon>
            </button>
          }
          <button nxButton variant="icon" size="sm" type="button" [attr.aria-label]="(c.enabled ? 'Desativar ' : 'Ativar ') + c.name"
                  [title]="c.enabled ? 'Desativar' : 'Ativar'" (click)="toggle(c)">
            <mat-icon>{{ c.enabled ? 'pause' : 'play_arrow' }}</mat-icon>
          </button>
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
    .when { margin-inline-start: var(--sp-3); font-size: var(--fs-sm); color: var(--text-muted); white-space: nowrap; }
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
    { key: 'enabled', header: 'Estado' },
    { key: 'lastCheck', header: 'Verificação' },
    { key: 'actions', header: 'Ações', align: 'end', hideHeader: true },
  ];

  items = signal<ConnectorDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  actionError = signal<string | null>(null);
  checkingId = signal<string | null>(null);
  checkResult = signal<{ ok: boolean; text: string } | null>(null);

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

  describe(message: string | null): string {
    return describeResult(message);
  }

  /** Só dá para verificar uma conexão ativa que tenha URL base configurada. */
  canCheck(c: ConnectorDto): boolean {
    return c.enabled && !!c.configuration?.['baseUrl'];
  }

  check(c: ConnectorDto): void {
    this.actionError.set(null);
    this.checkResult.set(null);
    this.checkingId.set(c.id);
    this.integrationService.checkConnector(c.id).subscribe({
      next: r => {
        this.checkingId.set(null);
        this.checkResult.set({
          ok: r.outcome === 'SUCCESS',
          text: r.outcome === 'SUCCESS'
            ? `“${c.name}” respondeu (${describeResult(r.message)}) em ${r.durationMs} ms.`
            : `“${c.name}” não respondeu: ${describeResult(r.message)}.`,
        });
        this.load();
      },
      error: err => {
        this.checkingId.set(null);
        this.actionError.set(err?.error?.detail ?? err?.error?.message ?? 'Não foi possível verificar a conexão.');
      }
    });
  }

  toggle(c: ConnectorDto): void {
    this.actionError.set(null);
    this.integrationService.updateConnector(c.id, { enabled: !c.enabled }).subscribe({
      next: () => this.load(),
      error: () => this.actionError.set('Não foi possível alterar a conexão.')
    });
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
