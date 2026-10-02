import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { IntegrationService, WebhookDto } from '../../../core/integrations/integration.service';
import {
  ButtonComponent, DataTableComponent, EmptyStateComponent, NoticeComponent, NxCellDirective, NxColumn,
  PageHeaderComponent, StatusBadgeComponent,
} from '../../../shared/components';

@Component({
  selector: 'app-webhook-config',
  standalone: true,
  imports: [
    CommonModule, RouterLink, MatIconModule, ButtonComponent, DataTableComponent, EmptyStateComponent,
    NoticeComponent, NxCellDirective, PageHeaderComponent, StatusBadgeComponent,
  ],
  template: `
    <nx-page-header heading="Webhooks">
      <a nxButton routerLink="/integrations">Voltar</a>
      <button nxButton variant="primary" (click)="openForm()">
        <mat-icon>add</mat-icon>
        Novo webhook
      </button>
    </nx-page-header>

    <nx-notice tone="warning">Os webhooks são cadastrados, mas o envio de eventos ainda não está disponível. Somente URLs https públicas são aceitas.</nx-notice>

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
      <nx-data-table caption="Webhooks cadastrados" [columns]="columns" [rows]="items()" [loading]="loading()" emptyTitle="Nenhum webhook cadastrado">
        <ng-template nxCell="events" let-w>{{ w.events.length ? w.events.join(', ') : '—' }}</ng-template>
        <ng-template nxCell="hasSecret" let-w>{{ w.hasSecret ? 'Sim' : 'Não' }}</ng-template>
        <ng-template nxCell="status" let-w>
          <nx-status-badge [tone]="w.status === 'ACTIVE' ? 'success' : 'neutral'">{{ w.status === 'ACTIVE' ? 'Ativo' : 'Inativo' }}</nx-status-badge>
        </ng-template>
        <ng-template nxCell="actions" let-w>
          <button nxButton variant="icon" size="sm" type="button" [attr.aria-label]="(w.status === 'ACTIVE' ? 'Desativar ' : 'Ativar ') + w.name"
                  [title]="w.status === 'ACTIVE' ? 'Desativar' : 'Ativar'" (click)="toggle(w)">
            <mat-icon>{{ w.status === 'ACTIVE' ? 'pause' : 'play_arrow' }}</mat-icon>
          </button>
          <button nxButton variant="icon" size="sm" type="button" [attr.aria-label]="'Editar ' + w.name" title="Editar" (click)="edit(w)">
            <mat-icon>edit</mat-icon>
          </button>
          <button nxButton variant="icon-danger" size="sm" type="button" [attr.aria-label]="'Excluir ' + w.name" title="Excluir" (click)="remove(w)">
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
export class WebhookConfigComponent implements OnInit {
  readonly columns: NxColumn[] = [
    { key: 'name', header: 'Nome', rowHeader: true, maxWidth: '220px' },
    { key: 'targetUrl', header: 'URL', mono: true, muted: true, maxWidth: '280px' },
    { key: 'events', header: 'Eventos', muted: true },
    { key: 'hasSecret', header: 'Assinado', muted: true },
    { key: 'status', header: 'Status' },
    { key: 'actions', header: 'Ações', align: 'end', hideHeader: true },
  ];

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
