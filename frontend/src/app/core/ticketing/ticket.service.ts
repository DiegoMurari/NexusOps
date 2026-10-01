import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'WAITING' | 'ON_HOLD' | 'RESOLVED' | 'CLOSED' | 'REOPENED';
export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type TicketType = 'INCIDENT' | 'PROBLEM' | 'CHANGE';

export const VALID_TRANSITIONS: Record<TicketStatus, TicketStatus[]> = {
  OPEN: ['IN_PROGRESS', 'ON_HOLD'],
  IN_PROGRESS: ['WAITING', 'ON_HOLD', 'RESOLVED'],
  WAITING: ['IN_PROGRESS', 'ON_HOLD'],
  ON_HOLD: ['IN_PROGRESS', 'WAITING'],
  RESOLVED: ['CLOSED', 'REOPENED'],
  CLOSED: ['REOPENED'],
  REOPENED: ['IN_PROGRESS', 'ON_HOLD'],
};

export interface TicketDto {
  id: string;
  ticketNumber: string;
  title: string;
  description: string;
  status: TicketStatus;
  priority: TicketPriority;
  urgency: TicketPriority | null;
  impact: TicketPriority | null;
  tenantId: string;
  categoryId: string | null;
  assigneeId: string | null;
  reporterId: string;
  groupId: string | null;
  slaDefinitionId: string | null;
  responseDueAt: string | null;
  resolutionDueAt: string | null;
  firstResponseAt: string | null;
  resolvedAt: string | null;
  closedAt: string | null;
  ciReference: string | null;
  tags: string | null;
  customFields: string | null;
  createdAt: string;
  updatedAt: string;
  ticketType: TicketType;
}

export interface CreateTicketRequest {
  title: string;
  description?: string;
  priority: TicketPriority;
  urgency?: TicketPriority;
  impact?: TicketPriority;
  tenantId: string;
  categoryId?: string;
  assigneeId?: string;
  reporterId: string;
  groupId?: string;
  ciReference?: string;
  tags?: string;
  ticketType: TicketType;
}

export interface UpdateTicketRequest {
  title?: string;
  description?: string;
  priority?: TicketPriority;
  urgency?: TicketPriority;
  impact?: TicketPriority;
  categoryId?: string;
  assigneeId?: string;
  groupId?: string;
  ciReference?: string;
  tags?: string;
}

export interface TransitionRequest {
  targetStatus: TicketStatus;
  comment?: string;
  resolution?: string;
}

export interface AssignRequest {
  assigneeId: string;
  groupId?: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface TicketStatsDto {
  countsByStatus: Record<string, number>;
  assignedToMe: number;
  total: number;
}

@Injectable({ providedIn: 'root' })
export class TicketService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/tickets`;

  list(opts: { status?: TicketStatus; assigneeId?: string; page?: number; size?: number } = {}): Observable<PageResponse<TicketDto>> {
    let params = new HttpParams();
    if (opts.status) params = params.set('status', opts.status);
    if (opts.assigneeId) params = params.set('assigneeId', opts.assigneeId);
    params = params.set('page', opts.page ?? 0).set('size', opts.size ?? 20);
    return this.http.get<PageResponse<TicketDto>>(this.base, { params });
  }

  get(id: string): Observable<TicketDto> {
    return this.http.get<TicketDto>(`${this.base}/${id}`);
  }

  create(request: CreateTicketRequest): Observable<TicketDto> {
    return this.http.post<TicketDto>(this.base, request);
  }

  update(id: string, request: UpdateTicketRequest): Observable<TicketDto> {
    return this.http.patch<TicketDto>(`${this.base}/${id}`, request);
  }

  transition(id: string, request: TransitionRequest): Observable<TicketDto> {
    return this.http.post<TicketDto>(`${this.base}/${id}/transition`, request);
  }

  assign(id: string, request: AssignRequest): Observable<TicketDto> {
    return this.http.post<TicketDto>(`${this.base}/${id}/assign`, request);
  }

  stats(): Observable<TicketStatsDto> {
    return this.http.get<TicketStatsDto>(`${this.base}/stats`);
  }
}
