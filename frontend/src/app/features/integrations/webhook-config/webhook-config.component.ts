import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { IntegrationService, WebhookDto } from '../../../core/integrations/integration.service';

@Component({
  selector: 'app-webhook-config',
  standalone: true,
  imports: [CommonModule, RouterLink, MatIconModule],
  template: `
    <div class="page-header">
      <h1 class="page-title">Webhooks</h1>
      <div class="header-actions">
        <a class="btn-secondary" routerLink="/integrations">Voltar</a>
        <button class="btn-primary" (click)="openForm()">
          <mat-icon>add</mat-icon>
          <span>Novo webhook</span>
        </button>
      </div>
    </div>

    <div class="notice">
      <mat-icon>info</mat-icon>
      <span>Os webhooks são cadastrados, mas o envio de eventos ainda não está disponível. Somente URLs https públicas são aceitas.</span>
    </div>

    @if (showForm()) {
      <form class="card form-card" (submit)="submit($event)">
        <div class="grid">
          <label class="field">
            <span class="label">Nome *</span>
            <input class="input" type="text" maxlength="255" [value]="name()" (input)="name.set($any($event.target).value)" />
          </label>
          <label class="field">
            <span class="label">URL de destino (https) *</span>
            <input class="input" type="url" maxlength="500" placeholder="https://exemplo.com/webhook"
                   [value]="url()" (input)="url.set($any($event.target).value)" />
          </label>
          <label class="field">
            <span class="label">Eventos (separados por vírgula)</span>
            <input class="input mono" type="text" placeholder="ticket.created, ticket.closed"
                   [value]="events()" (input)="events.set($any($event.target).value)" />
          </label>
          <label class="field">
            <span class="label">Segredo de assinatura {{ editingId() ? '(vazio mantém o atual)' : '' }}</span>
            <input class="input" type="password" maxlength="255" autocomplete="new-password"
                   [value]="secret()" (input)="secret.set($any($event.target).value)" />
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
      <div class="card empty-state"><mat-icon class="empty-ic">hourglass_empty</mat-icon><p>Carregando webhooks…</p></div>
    } @else if (error()) {
      <div class="card empty-state error"><mat-icon class="empty-ic">error_outline</mat-icon><p>{{ error() }}</p></div>
    } @else if (items().length === 0) {
      <div class="card empty-state"><mat-icon class="empty-ic">webhook</mat-icon><p>Nenhum webhook cadastrado</p></div>
    } @else {
      <div class="card table-card">
        <table class="hook-table">
          <thead>
            <tr><th>Nome</th><th>URL</th><th>Eventos</th><th>Assinado</th><th>Status</th><th></th></tr>
          </thead>
          <tbody>
            @for (w of items(); track w.id) {
              <tr>
                <td class="title-cell">{{ w.name }}</td>
                <td class="mono muted url-cell">{{ w.targetUrl }}</td>
                <td class="muted">{{ w.events.length ? w.events.join(', ') : '—' }}</td>
                <td class="muted">{{ w.hasSecret ? 'Sim' : 'Não' }}</td>
                <td><span class="status-tag" [class.on]="w.status === 'ACTIVE'">{{ w.status === 'ACTIVE' ? 'Ativo' : 'Inativo' }}</span></td>
                <td class="actions">
                  <button class="icon-btn" [title]="w.status === 'ACTIVE' ? 'Desativar' : 'Ativar'" (click)="toggle(w)">
                    <mat-icon>{{ w.status === 'ACTIVE' ? 'pause' : 'play_arrow' }}</mat-icon>
                  </button>
                  <button class="icon-btn" title="Editar" (click)="edit(w)"><mat-icon>edit</mat-icon></button>
                  <button class="icon-btn danger" title="Excluir" (click)="remove(w)"><mat-icon>delete_outline</mat-icon></button>
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
    .hook-table {
      width: 100%; border-collapse: collapse; font-size: 13px;
      th { text-align: left; padding: 12px 16px; font-size: 11.5px; font-weight: 600; letter-spacing: 0.03em;
           text-transform: uppercase; color: var(--text-faint); border-bottom: 1px solid var(--border); white-space: nowrap; }
      td { padding: 12px 16px; border-bottom: 1px solid var(--border); color: var(--text); }
      tr:last-child td { border-bottom: none; }
    }
    .title-cell { max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .url-cell { max-width: 280px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
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
export class WebhookConfigComponent implements OnInit {
  items = signal<WebhookDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  actionError = signal<string | null>(null);

  showForm = signal(false);
  saving = signal(false);
  formError = signal<string | null>(null);
  editingId = signal<string | null>(null);
  name = signal('');
  url = signal('');
  events = signal('');
  secret = signal('');

  constructor(private integrationService: IntegrationService) {}

  ngOnInit(): void {
    this.load();
  }

  openForm(): void {
    this.editingId.set(null);
    this.name.set('');
    this.url.set('');
    this.events.set('');
    this.secret.set('');
    this.formError.set(null);
    this.showForm.set(true);
  }

  edit(w: WebhookDto): void {
    this.editingId.set(w.id);
    this.name.set(w.name);
    this.url.set(w.targetUrl);
    this.events.set(w.events.join(', '));
    this.secret.set('');
    this.formError.set(null);
    this.showForm.set(true);
  }

  submit(event: Event): void {
    event.preventDefault();
    if (!this.name().trim() || !this.url().trim()) {
      this.formError.set('Informe nome e URL de destino.');
      return;
    }
    const events = this.events().split(',').map(e => e.trim()).filter(Boolean);
    const body: { name: string; targetUrl: string; events: string[]; secret?: string } = {
      name: this.name().trim(),
      targetUrl: this.url().trim(),
      events,
    };
    // Empty secret on edit keeps the stored one; the API never returns it.
    if (this.secret()) body.secret = this.secret();
    this.saving.set(true);
    this.formError.set(null);
    const id = this.editingId();
    const request$ = id ? this.integrationService.updateWebhook(id, body) : this.integrationService.createWebhook(body);
    request$.subscribe({
      next: () => {
        this.saving.set(false);
        this.showForm.set(false);
        this.secret.set('');
        this.load();
      },
      error: err => {
        this.saving.set(false);
        this.formError.set(err?.error?.detail ?? err?.error?.message ?? 'Não foi possível salvar o webhook.');
      }
    });
  }

  toggle(w: WebhookDto): void {
    this.actionError.set(null);
    this.integrationService.updateWebhook(w.id, { status: w.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE' }).subscribe({
      next: () => this.load(),
      error: () => this.actionError.set('Não foi possível alterar o webhook.')
    });
  }

  remove(w: WebhookDto): void {
    if (!confirm(`Excluir o webhook "${w.name}"?`)) return;
    this.actionError.set(null);
    this.integrationService.deleteWebhook(w.id).subscribe({
      next: () => this.load(),
      error: () => this.actionError.set('Não foi possível excluir o webhook.')
    });
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.integrationService.listWebhooks().subscribe({
      next: list => { this.items.set(list); this.loading.set(false); },
      error: () => { this.error.set('Não foi possível carregar os webhooks.'); this.loading.set(false); }
    });
  }
}
