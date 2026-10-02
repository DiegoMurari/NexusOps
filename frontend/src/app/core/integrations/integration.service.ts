import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type ConnectorType = 'JIRA' | 'SLACK' | 'TEAMS' | 'SERVICENOW' | 'ZENDESK' | 'CUSTOM';
export type ConnectorStatus = 'CONNECTED' | 'DISCONNECTED' | 'ERROR' | 'SYNCING' | 'AUTH_EXPIRED';
export type WebhookStatus = 'ACTIVE' | 'INACTIVE' | 'FAILED' | 'DISABLED';
export type IntegrationOutcome = 'SUCCESS' | 'FAILURE';
export type IntegrationKind = 'WEBHOOK' | 'CONNECTOR';
export type IntegrationEvent = 'CREATED' | 'UPDATED' | 'ENABLED' | 'DISABLED' | 'DELETED' | 'TEST' | 'CHECK' | 'DELIVERY';
export type DeliveryStatus = 'PENDING' | 'SENDING' | 'DELIVERED' | 'FAILED' | 'CANCELLED';

export interface WebhookEventInfo {
  name: string;
  description: string;
}

export interface WebhookDeliveryDto {
  id: string;
  webhookId: string;
  eventType: string;
  status: DeliveryStatus;
  attempts: number;
  nextAttemptAt: string;
  lastHttpStatus: number | null;
  lastError: string | null;
  createdAt: string;
  deliveredAt: string | null;
}

export interface WebhookDeliveryPage {
  content: WebhookDeliveryDto[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export const DELIVERY_STATUS_LABELS: Record<DeliveryStatus, string> = {
  PENDING: 'Na fila', SENDING: 'Enviando', DELIVERED: 'Entregue', FAILED: 'Desistiu', CANCELLED: 'Cancelada',
};

export interface WebhookDto {
  id: string;
  name: string;
  targetUrl: string;
  events: string[];
  status: WebhookStatus;
  timeoutSeconds: number;
  hasSecret: boolean;
  lastDeliveryAt: string | null;
  lastDeliveryStatus: IntegrationOutcome | null;
  lastDeliveryHttpStatus: number | null;
  lastError: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface WebhookRequest {
  name?: string;
  targetUrl?: string;
  secret?: string;
  events?: string[];
  status?: WebhookStatus;
  timeoutSeconds?: number;
}

export interface ConnectorDto {
  id: string;
  name: string;
  type: ConnectorType;
  configuration: Record<string, string>;
  status: ConnectorStatus;
  enabled: boolean;
  syncScheduleCron: string | null;
  lastSyncAt: string | null;
  lastSyncStatus: string | null;
  lastCheckAt: string | null;
  lastCheckStatus: IntegrationOutcome | null;
  lastCheckMessage: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ConnectorRequest {
  name?: string;
  type?: ConnectorType;
  configuration?: Record<string, string>;
  syncScheduleCron?: string;
  enabled?: boolean;
}

export interface IntegrationOverview {
  webhooks: number;
  activeWebhooks: number;
  failingWebhooks: number;
  pendingDeliveries: number;
  failedDeliveries: number;
  connectors: number;
  disabledConnectors: number;
  failingConnectors: number;
  connectorsByType: Record<string, number>;
  deliveryAvailable: boolean;
}

export interface IntegrationTestResult {
  outcome: IntegrationOutcome;
  httpStatus: number | null;
  durationMs: number;
  message: string;
  checkedAt: string;
}

export interface IntegrationLogDto {
  id: string;
  integrationKind: IntegrationKind;
  integrationId: string;
  integrationName: string;
  event: IntegrationEvent;
  outcome: IntegrationOutcome;
  httpStatus: number | null;
  durationMs: number | null;
  message: string | null;
  actor: string | null;
  createdAt: string;
}

export interface IntegrationLogPage {
  content: IntegrationLogDto[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface IntegrationLogFilter {
  kind?: IntegrationKind;
  outcome?: IntegrationOutcome;
  integrationId?: string;
}

export const EVENT_LABELS: Record<IntegrationEvent, string> = {
  CREATED: 'Criado', UPDATED: 'Alterado', ENABLED: 'Ativado', DISABLED: 'Desativado',
  DELETED: 'Excluído', TEST: 'Teste de envio', CHECK: 'Verificação', DELIVERY: 'Entrega de evento',
};

/** Categorias de falha devolvidas pela API, em linguagem de gente. */
export const FAILURE_LABELS: Record<string, string> = {
  TIMEOUT: 'Tempo esgotado', DNS_FAILURE: 'Endereço não encontrado', BLOCKED_ADDRESS: 'Endereço interno bloqueado',
  CONNECTION_FAILED: 'Conexão recusada', TLS_ERROR: 'Falha de certificado (TLS)', INVALID_URL: 'Endereço inválido',
  IO_ERROR: 'Erro de comunicação', INTERRUPTED: 'Interrompido',
};

/**
 * "HTTP 503" e as categorias acima viram texto legível; qualquer outra coisa passa como veio. Mensagens
 * compostas ("ticket.created · tentativa 2 · TIMEOUT") são traduzidas por trecho.
 */
export function describeResult(message: string | null | undefined): string {
  if (!message) return '—';
  return message.split(' · ').map(part => FAILURE_LABELS[part] ?? part.replace(/^HTTP_(\d{3})$/, 'HTTP $1')).join(' · ');
}

@Injectable({ providedIn: 'root' })
export class IntegrationService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/integrations`;

  overview(): Observable<IntegrationOverview> {
    return this.http.get<IntegrationOverview>(`${this.base}/overview`);
  }

  listLogs(page: number, size: number, filter: IntegrationLogFilter = {}): Observable<IntegrationLogPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filter.kind) params = params.set('kind', filter.kind);
    if (filter.outcome) params = params.set('outcome', filter.outcome);
    if (filter.integrationId) params = params.set('integrationId', filter.integrationId);
    return this.http.get<IntegrationLogPage>(`${this.base}/logs`, { params });
  }

  events(): Observable<WebhookEventInfo[]> {
    return this.http.get<WebhookEventInfo[]>(`${this.base}/events`);
  }

  listDeliveries(page: number, size: number, filter: { webhookId?: string; status?: DeliveryStatus } = {}): Observable<WebhookDeliveryPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filter.webhookId) params = params.set('webhookId', filter.webhookId);
    if (filter.status) params = params.set('status', filter.status);
    return this.http.get<WebhookDeliveryPage>(`${this.base}/deliveries`, { params });
  }

  retryDelivery(id: string): Observable<WebhookDeliveryDto> {
    return this.http.post<WebhookDeliveryDto>(`${this.base}/deliveries/${id}/retry`, {});
  }

  listWebhooks(): Observable<WebhookDto[]> {
    return this.http.get<WebhookDto[]>(`${this.base}/webhooks`);
  }

  createWebhook(request: WebhookRequest): Observable<WebhookDto> {
    return this.http.post<WebhookDto>(`${this.base}/webhooks`, request);
  }

  updateWebhook(id: string, request: WebhookRequest): Observable<WebhookDto> {
    return this.http.patch<WebhookDto>(`${this.base}/webhooks/${id}`, request);
  }

  testWebhook(id: string): Observable<IntegrationTestResult> {
    return this.http.post<IntegrationTestResult>(`${this.base}/webhooks/${id}/test`, {});
  }

  deleteWebhook(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/webhooks/${id}`);
  }

  listConnectors(type?: ConnectorType): Observable<ConnectorDto[]> {
    let params = new HttpParams();
    if (type) params = params.set('type', type);
    return this.http.get<ConnectorDto[]>(`${this.base}/connectors`, { params });
  }

  createConnector(request: ConnectorRequest): Observable<ConnectorDto> {
    return this.http.post<ConnectorDto>(`${this.base}/connectors`, request);
  }

  updateConnector(id: string, request: ConnectorRequest): Observable<ConnectorDto> {
    return this.http.patch<ConnectorDto>(`${this.base}/connectors/${id}`, request);
  }

  checkConnector(id: string): Observable<IntegrationTestResult> {
    return this.http.post<IntegrationTestResult>(`${this.base}/connectors/${id}/check`, {});
  }

  deleteConnector(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/connectors/${id}`);
  }
}
