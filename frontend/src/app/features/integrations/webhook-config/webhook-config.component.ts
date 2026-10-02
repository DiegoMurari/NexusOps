import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import {
  IntegrationService, WebhookDto, WebhookEventInfo, describeResult,
} from '../../../core/integrations/integration.service';
import {
  ButtonComponent, DataTableComponent, EmptyStateComponent, NoticeComponent, NxCellDirective, NxColumn,
  PageHeaderComponent, StatusBadgeComponent,
} from '../../../shared/components';
import { WebhookDeliveriesComponent } from '../webhook-deliveries/webhook-deliveries.component';

@Component({
  selector: 'app-webhook-config',
  standalone: true,
  imports: [
    CommonModule, RouterLink, MatIconModule, ButtonComponent, DataTableComponent, EmptyStateComponent,
    NoticeComponent, NxCellDirective, PageHeaderComponent, StatusBadgeComponent, WebhookDeliveriesComponent,
  ],
  template: `
    <nx-page-header heading="Webhooks">
      <a nxButton routerLink="/integrations">Voltar</a>
      <button nxButton variant="primary" (click)="openForm()">
        <mat-icon>add</mat-icon>
        Novo webhook
      </button>
    </nx-page-header>

    <nx-notice tone="info">Use “Enviar teste” para mandar um evento assinado ao endereço e ver se ele responde. Os eventos assinados são enviados automaticamente, com nova tentativa em caso de falha; o corpo traz só identificadores e metadados do chamado, nunca o texto de comentários ou da solução. Somente URLs https públicas são aceitas.</nx-notice>

    @if (testResult(); as t) {
      <nx-notice [tone]="t.ok ? 'success' : 'critical'">{{ t.text }}</nx-notice>
    }

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
            @if (catalog().length) {
              <span class="picker" role="group" aria-label="Eventos disponíveis">
                @for (e of catalog(); track e.name) {
                  <button nxButton size="sm" type="button" [variant]="isSelected(e.name) ? 'primary' : 'secondary'"
                          [attr.aria-pressed]="isSelected(e.name)" [title]="e.description" (click)="toggleEvent(e.name)">{{ e.name }}</button>
                }
              </span>
            }
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
        <ng-template nxCell="lastDelivery" let-w>
          @if (w.lastDeliveryStatus) {
            <nx-status-badge [tone]="w.lastDeliveryStatus === 'SUCCESS' ? 'success' : 'critical'">{{ w.lastDeliveryStatus === 'SUCCESS' ? 'Entregue' : 'Falhou' }}</nx-status-badge>
            <span class="when">{{ w.lastDeliveryAt | date:'dd/MM HH:mm' }}{{ deliveryDetail(w) }}</span>
          } @else {
            <span class="when">Sem envios</span>
          }
        </ng-template>
        <ng-template nxCell="actions" let-w>
          <button nxButton variant="icon" size="sm" type="button" [attr.aria-label]="'Enviar teste para ' + w.name"
                  title="Enviar teste" [loading]="testingId() === w.id" [disabled]="testingId() !== null" (click)="test(w)">
            <mat-icon>send</mat-icon>
          </button>
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

    @if (!loading() && !error()) {
      <app-webhook-deliveries [webhooks]="items()" />
    }
  `,
  styles: [`
    :host { display: block; }
    .picker { display: flex; flex-wrap: wrap; gap: var(--sp-3); margin-top: var(--sp-3); }
    .form-card { padding: var(--sp-7); margin-bottom: var(--sp-6); display: flex; flex-direction: column; gap: var(--sp-6); }
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: var(--sp-6); }
    .inline-error { margin: 0; }
    .when { margin-inline-start: var(--sp-3); font-size: var(--fs-sm); color: var(--text-muted); white-space: nowrap; }
  `]
})
export class WebhookConfigComponent implements OnInit {
  readonly columns: NxColumn[] = [
    { key: 'name', header: 'Nome', rowHeader: true, maxWidth: '220px' },
    { key: 'targetUrl', header: 'URL', mono: true, muted: true, maxWidth: '280px' },
    { key: 'events', header: 'Eventos', muted: true },
    { key: 'hasSecret', header: 'Assinado', muted: true },
    { key: 'status', header: 'Status' },
    { key: 'lastDelivery', header: 'Último envio' },
    { key: 'actions', header: 'Ações', align: 'end', hideHeader: true },
  ];

  items = signal<WebhookDto[]>([]);
  catalog = signal<WebhookEventInfo[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  actionError = signal<string | null>(null);
  testingId = signal<string | null>(null);
  testResult = signal<{ ok: boolean; text: string } | null>(null);

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
    // O catálogo é só conveniência: sem ele o campo de texto continua funcionando.
    this.integrationService.events().subscribe({ next: list => this.catalog.set(list), error: () => undefined });
  }

  private selectedEvents(): string[] {
    return this.events().split(',').map(e => e.trim()).filter(Boolean);
  }

  isSelected(name: string): boolean {
    return this.selectedEvents().includes(name);
  }

  toggleEvent(name: string): void {
    const current = this.selectedEvents();
    this.events.set((current.includes(name) ? current.filter(e => e !== name) : [...current, name]).join(', '));
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

  deliveryDetail(w: WebhookDto): string {
    if (w.lastDeliveryStatus === 'SUCCESS') return w.lastDeliveryHttpStatus ? ` · HTTP ${w.lastDeliveryHttpStatus}` : '';
    return w.lastError ? ` · ${w.lastError.startsWith('HTTP_') ? w.lastError.replace('_', ' ') : describeResult(w.lastError)}` : '';
  }

  /** Manda um evento "ping" assinado e mostra o resultado; o backend também registra na atividade. */
  test(w: WebhookDto): void {
    this.actionError.set(null);
    this.testResult.set(null);
    this.testingId.set(w.id);
    this.integrationService.testWebhook(w.id).subscribe({
      next: r => {
        this.testingId.set(null);
        this.testResult.set({
          ok: r.outcome === 'SUCCESS',
          text: r.outcome === 'SUCCESS'
            ? `“${w.name}” respondeu (${describeResult(r.message)}) em ${r.durationMs} ms.`
            : `“${w.name}” não recebeu o teste: ${describeResult(r.message)}.`,
        });
        this.load();
      },
      error: err => {
        this.testingId.set(null);
        this.actionError.set(err?.error?.detail ?? err?.error?.message ?? 'Não foi possível enviar o teste.');
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
