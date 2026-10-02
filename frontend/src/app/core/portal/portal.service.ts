import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import type { EvidenceDto } from '../ticketing/evidence.service';

/** Etapa do pedido na visão do solicitante. */
export type PortalStage = 'RECEIVED' | 'IN_PROGRESS' | 'WAITING_YOU' | 'AWAITING_VALIDATION' | 'CLOSED';

export interface PortalTicket {
  id: string;
  ticketNumber: string;
  title: string;
  stage: PortalStage;
  needsAttention: boolean;
  areaName: string | null;
  topicName: string | null;
  assigneeName: string | null;
  createdAt: string;
  updatedAt: string;
}

export type PortalActor = 'REQUESTER' | 'TEAM' | 'SYSTEM';

export interface PortalEvent {
  id: string;
  type: string;
  text: string | null;
  actor: PortalActor;
  cycleNo: number;
  occurredAt: string;
}

export interface PortalAnswer {
  label: string;
  value: string;
}

export interface PortalDetail {
  ticket: PortalTicket;
  description: string | null;
  answers: PortalAnswer[];
  pendingSolution: string | null;
  canReply: boolean;
  timeline: PortalEvent[];
  evidence: EvidenceDto[];
}

export interface PortalCreateRequest {
  topicId: string;
  title: string;
  description?: string;
  locationId?: string;
  formAnswers?: Record<string, unknown>;
  evidenceIds?: string[];
}

export interface PortalLocation {
  id: string;
  name: string;
  code: string | null;
}

export interface PortalProfile {
  phone: string | null;
  jobTitle: string | null;
  department: string | null;
  defaultLocationId: string | null;
}

/** API do Portal do Solicitante: tudo escopado a "pedidos que eu abri" e sem estrutura interna. */
@Injectable({ providedIn: 'root' })
export class PortalService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/portal`;

  tickets(): Observable<PortalTicket[]> {
    return this.http.get<PortalTicket[]>(`${this.base}/tickets`);
  }

  ticket(id: string): Observable<PortalDetail> {
    return this.http.get<PortalDetail>(`${this.base}/tickets/${id}`);
  }

  create(request: PortalCreateRequest): Observable<PortalDetail> {
    return this.http.post<PortalDetail>(`${this.base}/tickets`, request);
  }

  reply(id: string, message: string): Observable<PortalDetail> {
    return this.http.post<PortalDetail>(`${this.base}/tickets/${id}/reply`, { message });
  }

  accept(id: string, comment?: string): Observable<PortalDetail> {
    return this.http.post<PortalDetail>(`${this.base}/tickets/${id}/accept`, { comment: comment || null });
  }

  contest(id: string, comment: string): Observable<PortalDetail> {
    return this.http.post<PortalDetail>(`${this.base}/tickets/${id}/contest`, { comment });
  }

  locations(): Observable<PortalLocation[]> {
    return this.http.get<PortalLocation[]>(`${environment.apiUrl}/locations/options`);
  }

  profile(): Observable<PortalProfile> {
    return this.http.get<PortalProfile>(`${environment.apiUrl}/users/me`);
  }
}

export const STAGE_LABELS: Record<PortalStage, string> = {
  RECEIVED: 'Recebido',
  IN_PROGRESS: 'Em atendimento',
  WAITING_YOU: 'Aguardando você',
  AWAITING_VALIDATION: 'Aguardando sua validação',
  CLOSED: 'Concluído',
};

export const STAGE_TONES: Record<PortalStage, 'neutral' | 'info' | 'success' | 'warning'> = {
  RECEIVED: 'neutral',
  IN_PROGRESS: 'info',
  WAITING_YOU: 'warning',
  AWAITING_VALIDATION: 'warning',
  CLOSED: 'success',
};
