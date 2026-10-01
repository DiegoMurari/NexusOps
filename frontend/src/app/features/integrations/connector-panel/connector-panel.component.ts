import { Component, Input, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { IntegrationService } from '../../../core/integrations/integration.service';
import type { ConnectorDto, ConnectorType } from '../../../core/integrations/integration.service';

export interface ConnectorField {
  key: string;
  label: string;
  placeholder: string;
}

@Component({
  selector: 'app-connector-panel',
  standalone: true,
  imports: [CommonModule, RouterLink, MatIconModule],
  template: `
    <div class="page-header">
      <h1 class="page-title">{{ title }}</h1>
      <div class="header-actions">
        <a class="btn-secondary" routerLink="/integrations">Voltar</a>
        <button class="btn-primary" (click)="openForm()">
          <mat-icon>add</mat-icon>
          <span>Nova conexão</span>
        </button>
      </div>
    </div>

    <div class="notice">
      <mat-icon>info</mat-icon>
      <span>As configurações são salvas, mas a conexão e a sincronização com o serviço externo ainda não estão disponíveis. Não informe senhas nem tokens: credenciais não são armazenadas.</span>
    </div>

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
        @if (formError()) { <p class="inline-error">{{ formError() }}</p> }
        <div class="form-actions">
          <button class="btn-secondary" type="button" (click)="showForm.set(false)">Cancelar</button>
          <button class="btn-primary" type="submit" [disabled]="saving()">{{ saving() ? 'Salvando…' : 'Salvar' }}</button>
        </div>
      </form>
    }

    @if (loading()) {
      <div class="card empty-state"><mat-icon class="empty-ic">hourglass_empty</mat-icon><p>Carregando conexões…</p></div>
    } @else if (error()) {
      <div class="card empty-state error"><mat-icon class="empty-ic">error_outline</mat-icon><p>{{ error() }}</p></div>
    } @else if (items().length === 0) {
      <div class="card empty-state"><mat-icon class="empty-ic">{{ icon }}</mat-icon><p>Nenhuma conexão cadastrada</p></div>
    } @else {
      <div class="card table-card">
        <table class="conn-table">
          <thead>
            <tr><th>Nome</th><th>Configuração</th><th>Sincronização</th><th>Status</th><th></th></tr>
          </thead>
          <tbody>
            @for (c of items(); track c.id) {
              <tr>
                <td class="title-cell">{{ c.name }}</td>
                <td class="muted">{{ summary(c) }}</td>
                <td class="mono muted">{{ c.syncScheduleCron || '—' }}</td>
                <td><span class="status-tag">{{ statusLabel(c.status) }}</span></td>
                <td class="actions">
                  <button class="icon-btn" title="Editar" (click)="edit(c)"><mat-icon>edit</mat-icon></button>
                  <button class="icon-btn danger" title="Excluir" (click)="remove(c)"><mat-icon>delete_outline</mat-icon></button>
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
      mat-icon { font-size: 18px; width: 18px; height: 18px; flex: none; }
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
    .form-actions { display: flex; justify-content: flex-end; gap: 8px; }
    .table-card { padding: 0; overflow-x: auto; }
    .conn-table {
      width: 100%; border-collapse: collapse; font-size: 13px;
      th { text-align: left; padding: 12px 16px; font-size: 11.5px; font-weight: 600; letter-spacing: 0.03em;
           text-transform: uppercase; color: var(--text-faint); border-bottom: 1px solid var(--border); white-space: nowrap; }
      td { padding: 12px 16px; border-bottom: 1px solid var(--border); color: var(--text); }
      tr:last-child td { border-bottom: none; }
    }
    .title-cell { max-width: 260px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
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
export class ConnectorPanelComponent implements OnInit {
  @Input({ required: true }) type!: ConnectorType;
  @Input({ required: true }) title!: string;
  @Input() icon = 'link';
  @Input() fields: ConnectorField[] = [];

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
