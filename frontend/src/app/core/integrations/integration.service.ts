import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type ConnectorType = 'JIRA' | 'SLACK' | 'TEAMS' | 'SERVICENOW' | 'ZENDESK' | 'CUSTOM';
export type ConnectorStatus = 'CONNECTED' | 'DISCONNECTED' | 'ERROR' | 'SYNCING' | 'AUTH_EXPIRED';
export type WebhookStatus = 'ACTIVE' | 'INACTIVE' | 'FAILED' | 'DISABLED';

export interface WebhookDto {
  id: string;
  name: string;
  targetUrl: string;
  events: string[];
  status: WebhookStatus;
  timeoutSeconds: number;
  hasSecret: boolean;
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
  syncScheduleCron: string | null;
  lastSyncAt: string | null;
  lastSyncStatus: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ConnectorRequest {
  name?: string;
  type?: ConnectorType;
  configuration?: Record<string, string>;
  syncScheduleCron?: string;
}

export interface IntegrationOverview {
  webhooks: number;
  activeWebhooks: number;
  connectors: number;
  connectorsByType: Record<string, number>;
  deliveryAvailable: boolean;
}

@Injectable({ providedIn: 'root' })
export class IntegrationService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/integrations`;

  overview(): Observable<IntegrationOverview> {
    return this.http.get<IntegrationOverview>(`${this.base}/overview`);
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

  deleteConnector(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/connectors/${id}`);
  }
}
