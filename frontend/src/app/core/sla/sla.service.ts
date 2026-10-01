import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface SlaBreachDto {
  id: string;
  ticketId: string;
  slaDefinitionId: string;
  tenantId: string;
  breachType: 'RESPONSE' | 'RESOLUTION';
  breachTime: string;
  acknowledged: boolean;
  acknowledgedBy: string | null;
  acknowledgedAt: string | null;
  escalated: boolean;
  escalatedAt: string | null;
  resolved: boolean;
  resolvedAt: string | null;
  responseTimeMinutes: number | null;
  resolutionTimeMinutes: number | null;
  breachPercentage: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface SlaDefinitionDto {
  id: string;
  name: string;
  description: string | null;
  tenantId: string;
  active: boolean;
  appliesToType: string | null;
  appliesToCategory: string | null;
  appliesToPriority: string | null;
  appliesToCustomerTier: string | null;
  responseTimeMinutes: number | null;
  resolutionTimeMinutes: number | null;
  businessCalendarId: string | null;
  pauseOnHold: boolean;
  stopOnFirstResponse: boolean;
  breachWarning80: boolean;
  breachWarning90: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateSlaDefinitionRequest {
  name: string;
  description?: string;
  tenantId: string;
  active?: boolean;
  appliesToType?: string;
  appliesToCategory?: string;
  appliesToPriority?: string;
  responseTimeMinutes?: number;
  resolutionTimeMinutes?: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class SlaService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/sla`;

  listBreaches(page = 0, size = 20): Observable<PageResponse<SlaBreachDto>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<SlaBreachDto>>(`${this.base}/breaches`, { params });
  }

  listActiveBreaches(): Observable<SlaBreachDto[]> {
    return this.http.get<SlaBreachDto[]>(`${this.base}/breaches/active`);
  }

  acknowledgeBreach(id: string): Observable<SlaBreachDto> {
    return this.http.post<SlaBreachDto>(`${this.base}/breaches/${id}/acknowledge`, {});
  }

  resolveBreach(id: string): Observable<SlaBreachDto> {
    return this.http.post<SlaBreachDto>(`${this.base}/breaches/${id}/resolve`, {});
  }

  listDefinitions(activeOnly = false): Observable<SlaDefinitionDto[]> {
    const params = activeOnly ? new HttpParams().set('active', 'true') : undefined;
    return this.http.get<SlaDefinitionDto[]>(`${this.base}/definitions`, { params });
  }

  createDefinition(request: CreateSlaDefinitionRequest): Observable<SlaDefinitionDto> {
    return this.http.post<SlaDefinitionDto>(`${this.base}/definitions`, request);
  }
}
