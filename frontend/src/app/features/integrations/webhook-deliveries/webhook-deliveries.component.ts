import { Component, Input, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import {
  DELIVERY_STATUS_LABELS, DeliveryStatus, IntegrationService, WebhookDeliveryDto, WebhookDto, describeResult,
} from '../../../core/integrations/integration.service';
import {
  ButtonComponent, DataTableComponent, EmptyStateComponent, NoticeComponent, NxCellDirective, NxColumn,
  StatusBadgeComponent, Tone,
} from '../../../shared/components';

const TONES: Record<DeliveryStatus, Tone> = {
  PENDING: 'info', SENDING: 'info', DELIVERED: 'success', FAILED: 'critical', CANCELLED: 'neutral',
};

/** Fila de entregas automáticas dos eventos do sistema aos webhooks, com reenvio das que desistiram. */
@Component({
  selector: 'app-webhook-deliveries',
  standalone: true,
  imports: [
    CommonModule, MatIconModule, ButtonComponent, DataTableComponent, EmptyStateComponent, NoticeComponent,
    NxCellDirective, StatusBadgeComponent,
  ],
  template: `
    <div class="head">
      <h2 class="title">Entregas de eventos</h2>
      <div class="filters">
        <select class="input" aria-label="Filtrar por situação" (change)="setStatus($any($event.target).value)">
          <option value="">Todas as situações</option>
          <option value="PENDING">Na fila</option>
          <option value="DELIVERED">Entregues</option>
          <option value="FAILED">Desistiu</option>
          <option value="CANCELLED">Canceladas</option>
        </select>
        <button nxButton size="sm" type="button" (click)="refresh()" [disabled]="loading()">
          <mat-icon>refresh</mat-icon>
          Atualizar
        </button>
      </div>
    </div>

    @if (actionError()) { <nx-notice tone="critical">{{ actionError() }}</nx-notice> }

    @if (error()) {
      <nx-empty-state variant="error" [heading]="error()!" />
    } @else {
      <nx-data-table caption="Entregas de eventos aos webhooks" [columns]="columns" [rows]="items()" [loading]="loading()"
                     emptyTitle="Nenhuma entrega ainda"
                     emptyDescription="Quando um chamado for aberto, atribuído, resolvido ou encerrado, os webhooks ativos que assinam o evento aparecem aqui.">
        <ng-template nxCell="createdAt" let-d>{{ d.createdAt | date:'dd/MM/yyyy HH:mm:ss' }}</ng-template>
        <ng-template nxCell="webhook" let-d>{{ webhookName(d) }}</ng-template>
        <ng-template nxCell="status" let-d>
          <nx-status-badge [tone]="tone(d)">{{ label(d) }}</nx-status-badge>
        </ng-template>
        <ng-template nxCell="detail" let-d>{{ detail(d) }}</ng-template>
        <ng-template nxCell="actions" let-d>
          @if (d.status === 'FAILED') {
            <button nxButton variant="icon" size="sm" type="button" [attr.aria-label]="'Reenviar ' + d.eventType"
                    title="Reenviar" [loading]="retryingId() === d.id" [disabled]="retryingId() !== null" (click)="retry(d)">
              <mat-icon>replay</mat-icon>
            </button>
          }
        </ng-template>
      </nx-data-table>

      @if (items().length > 0) {
        <nav class="pagination" aria-label="Paginação das entregas">
          <button nxButton size="sm" [disabled]="loading() || page() === 0" (click)="changePage(page() - 1)">Anterior</button>
          <span class="page-info mono" aria-live="polite">{{ page() + 1 }} / {{ totalPages() || 1 }}</span>
          <button nxButton size="sm" [disabled]="loading() || page() + 1 >= totalPages()" (click)="changePage(page() + 1)">Próxima</button>
        </nav>
      }
    }
  `,
  styles: [`
    :host { display: block; margin-top: var(--sp-8); }
    .head { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: var(--sp-5); margin-bottom: var(--sp-5); }
    .title { margin: 0; font-size: var(--fs-lg); line-height: 24px; font-weight: var(--fw-semibold); color: var(--text); }
    .filters { display: flex; flex-wrap: wrap; gap: var(--sp-4); align-items: center; }
    .pagination { display: flex; align-items: center; justify-content: center; gap: var(--sp-6); margin-top: var(--sp-6); }
    .page-info { font-size: var(--fs-sm); color: var(--text-muted); }
  `]
})
export class WebhookDeliveriesComponent implements OnInit {
  /** Webhooks da tela pai, só para mostrar o nome em vez do identificador. */
  @Input() webhooks: WebhookDto[] = [];

  readonly columns: NxColumn[] = [
    { key: 'createdAt', header: 'Quando', mono: true, muted: true },
    { key: 'webhook', header: 'Webhook', rowHeader: true, maxWidth: '220px' },
    { key: 'eventType', header: 'Evento', mono: true },
    { key: 'status', header: 'Situação' },
    { key: 'attempts', header: 'Tentativas', mono: true, align: 'end' },
    { key: 'detail', header: 'Detalhe', muted: true },
    { key: 'actions', header: 'Ações', align: 'end', hideHeader: true },
  ];

  items = signal<WebhookDeliveryDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  actionError = signal<string | null>(null);
  retryingId = signal<string | null>(null);
  page = signal(0);
  totalPages = signal(0);
  private status: DeliveryStatus | '' = '';

  constructor(private integrationService: IntegrationService) {}

  ngOnInit(): void {
    this.load();
  }

  refresh(): void {
    this.page.set(0);
    this.load();
  }

  setStatus(value: string): void {
    this.status = value as DeliveryStatus | '';
    this.refresh();
  }

  changePage(page: number): void {
    this.page.set(page);
    this.load();
  }

  webhookName(d: WebhookDeliveryDto): string {
    return this.webhooks.find(w => w.id === d.webhookId)?.name ?? 'Webhook removido';
  }

  tone(d: WebhookDeliveryDto): Tone {
    return TONES[d.status] ?? 'neutral';
  }

  label(d: WebhookDeliveryDto): string {
    return DELIVERY_STATUS_LABELS[d.status] ?? d.status;
  }

  detail(d: WebhookDeliveryDto): string {
    if (d.status === 'DELIVERED') return d.lastHttpStatus ? `HTTP ${d.lastHttpStatus}` : '—';
    if (d.status === 'PENDING' && d.attempts > 0) {
      const when = new Date(d.nextAttemptAt).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
      return `${this.errorText(d)} · próxima tentativa ${when}`;
    }
    if (d.status === 'PENDING') return 'Aguardando envio';
    return this.errorText(d);
  }

  retry(d: WebhookDeliveryDto): void {
    this.actionError.set(null);
    this.retryingId.set(d.id);
    this.integrationService.retryDelivery(d.id).subscribe({
      next: () => { this.retryingId.set(null); this.load(); },
      error: err => {
        this.retryingId.set(null);
        this.actionError.set(err?.error?.detail ?? err?.error?.message ?? 'Não foi possível reenviar a entrega.');
      }
    });
  }

  private errorText(d: WebhookDeliveryDto): string {
    if (!d.lastError) return '—';
    return describeResult(d.lastError);
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.integrationService.listDeliveries(this.page(), 10, { status: this.status || undefined }).subscribe({
      next: res => {
        this.items.set(res.content);
        this.totalPages.set(res.totalPages);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Não foi possível carregar as entregas.');
        this.loading.set(false);
      }
    });
  }
}
