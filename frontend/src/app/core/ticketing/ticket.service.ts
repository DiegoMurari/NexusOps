import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'WAITING' | 'ON_HOLD' | 'RESOLVED' | 'CLOSED' | 'REOPENED';
export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type TicketType = 'INCIDENT' | 'PROBLEM' | 'CHANGE';

/**
 * Transições que o analista pode fazer no console. Fechar (aceite) e reabrir (contestação) são do
 * solicitante, no portal; por isso RESOLVED e CLOSED não oferecem ações aqui.
 */
export const VALID_TRANSITIONS: Record<TicketStatus, TicketStatus[]> = {
  OPEN: ['IN_PROGRESS', 'ON_HOLD'],
  IN_PROGRESS: ['WAITING', 'ON_HOLD', 'RESOLVED'],
  WAITING: ['IN_PROGRESS', 'ON_HOLD'],
  ON_HOLD: ['IN_PROGRESS', 'WAITING'],
  RESOLVED: [],
  CLOSED: [],
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
  slaPausedAt: string | null;
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
  queueId: string | null;
}

export type QueueScope = 'MINE' | 'MY_QUEUES' | 'UNASSIGNED' | 'ALL';

export interface ConsoleCounts {
  mine: number;
  myQueues: number;
  unassigned: number;
  all: number;
}

export interface ConsoleContext {
  queueId: string | null;
  queueName: string | null;
  topicName: string | null;
  areaName: string | null;
  reporterName: string | null;
  assigneeName: string | null;
  answers: { label: string; value: string }[];
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
  topicId?: string;
  formAnswers?: Record<string, unknown>;
  evidenceIds?: string[];
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

export interface TicketEventDto {
  id: string;
  seq: number;
  cycleNo: number;
  type: string;
  actorId: string | null;
  actorKind: string;
  visibility: 'PUBLIC' | 'INTERNAL';
  payload: Record<string, unknown> | null;
  occurredAt: string;
}

export interface TicketResolutionDto {
  solutionText: string;
  resolvedBy: string;
  resolvedAt: string;
  outcome: string | null;
  decidedBy: string | null;
  decidedAt: string | null;
  decisionComment: string | null;
}

export interface TicketCycleDto {
  cycleNo: number;
  openedReason: string;
  reopenComment: string | null;
  status: string;
  slaDefinitionId: string | null;
  responseDueAt: string | null;
  resolutionDueAt: string | null;
  openedAt: string;
  firstResponseAt: string | null;
  resolvedAt: string | null;
  validatedAt: string | null;
  pausedSeconds: number;
  timeToFirstResponseSeconds: number | null;
  timeToResolveSeconds: number | null;
  validationSeconds: number | null;
  resolution: TicketResolutionDto | null;
}

export interface TicketCyclesDto {
  summary: {
    cycleCount: number;
    reopenCount: number;
    timeToFirstResolutionSeconds: number | null;
    secondAttendanceSeconds: number | null;
    totalSeconds: number | null;
  };
  cycles: TicketCycleDto[];
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

  queueView(opts: { scope: QueueScope; queueId?: string; status?: TicketStatus; q?: string; page?: number; size?: number }): Observable<PageResponse<TicketDto>> {
    let params = new HttpParams().set('scope', opts.scope).set('page', opts.page ?? 0).set('size', opts.size ?? 20);
    if (opts.queueId) params = params.set('queueId', opts.queueId);
    if (opts.status) params = params.set('status', opts.status);
    if (opts.q) params = params.set('q', opts.q);
    return this.http.get<PageResponse<TicketDto>>(`${this.base}/queue-view`, { params });
  }

  counts(): Observable<ConsoleCounts> {
    return this.http.get<ConsoleCounts>(`${this.base}/counts`);
  }

  context(id: string): Observable<ConsoleContext> {
    return this.http.get<ConsoleContext>(`${this.base}/${id}/context`);
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

  changeQueue(id: string, queueId: string, reason?: string): Observable<TicketDto> {
    return this.http.post<TicketDto>(`${this.base}/${id}/queue`, { queueId, reason });
  }

  addComment(id: string, content: string, publicComment: boolean): Observable<unknown> {
    return this.http.post(`${this.base}/${id}/comments`, { content, publicComment });
  }

  timeline(id: string): Observable<TicketEventDto[]> {
    return this.http.get<TicketEventDto[]>(`${this.base}/${id}/timeline`);
  }

  cycles(id: string): Observable<TicketCyclesDto> {
    return this.http.get<TicketCyclesDto>(`${this.base}/${id}/cycles`);
  }

  stats(): Observable<TicketStatsDto> {
    return this.http.get<TicketStatsDto>(`${this.base}/stats`);
  }
}
