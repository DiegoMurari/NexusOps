import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { FormDefinition, TicketForm, TopicFormVersion } from './form.models';

export interface QueueDto {
  id: string;
  name: string;
  code: string;
  description: string | null;
  active: boolean;
  memberCount: number;
  createdAt: string;
  updatedAt: string;
}

/** Criar exige nome e código. No PATCH, ausente mantém e texto vazio limpa a descrição. */
export interface QueueRequest {
  name?: string;
  code?: string;
  description?: string;
  active?: boolean;
}

export type QueueMemberRole = 'MEMBER' | 'LEAD';

export interface QueueMemberDto {
  userId: string;
  name: string;
  email: string | null;
  userActive: boolean;
  role: QueueMemberRole;
}

export interface CatalogAreaDto {
  id: string;
  name: string;
  description: string | null;
  icon: string | null;
  sortOrder: number;
  active: boolean;
  topicCount: number;
}

export interface CatalogAreaRequest {
  name?: string;
  description?: string;
  icon?: string;
  sortOrder?: number;
  active?: boolean;
}

export interface CatalogTopicDto {
  id: string;
  areaId: string;
  name: string;
  description: string | null;
  defaultQueueId: string | null;
  defaultPriority: string | null;
  slaDefinitionId: string | null;
  categoryId: string | null;
  sortOrder: number;
  active: boolean;
}

/** No PATCH, texto vazio limpa fila, prioridade, SLA e categoria. */
export interface CatalogTopicRequest {
  areaId?: string;
  name?: string;
  description?: string;
  defaultQueueId?: string;
  defaultPriority?: string;
  slaDefinitionId?: string;
  categoryId?: string;
  sortOrder?: number;
  active?: boolean;
}

export interface PortalTopic {
  id: string;
  name: string;
  description: string | null;
}

export interface PortalArea {
  id: string;
  name: string;
  description: string | null;
  icon: string | null;
  topics: PortalTopic[];
}

export interface PortalCatalogDto {
  areas: PortalArea[];
}

@Injectable({ providedIn: 'root' })
export class CatalogService {
  private http = inject(HttpClient);
  private queuesBase = `${environment.apiUrl}/queues`;
  private catalogBase = `${environment.apiUrl}/catalog`;

  // Filas
  listQueues(includeInactive = true): Observable<QueueDto[]> {
    return this.http.get<QueueDto[]>(this.queuesBase, { params: new HttpParams().set('includeInactive', includeInactive) });
  }

  myQueues(): Observable<QueueDto[]> {
    return this.http.get<QueueDto[]>(`${this.queuesBase}/mine`);
  }

  createQueue(request: QueueRequest): Observable<QueueDto> {
    return this.http.post<QueueDto>(this.queuesBase, request);
  }

  updateQueue(id: string, request: QueueRequest): Observable<QueueDto> {
    return this.http.patch<QueueDto>(`${this.queuesBase}/${id}`, request);
  }

  deleteQueue(id: string): Observable<void> {
    return this.http.delete<void>(`${this.queuesBase}/${id}`);
  }

  queueMembers(id: string): Observable<QueueMemberDto[]> {
    return this.http.get<QueueMemberDto[]>(`${this.queuesBase}/${id}/members`);
  }

  putQueueMember(id: string, userId: string, role: QueueMemberRole): Observable<QueueMemberDto> {
    return this.http.put<QueueMemberDto>(`${this.queuesBase}/${id}/members`, { userId, role });
  }

  removeQueueMember(id: string, userId: string): Observable<void> {
    return this.http.delete<void>(`${this.queuesBase}/${id}/members/${userId}`);
  }

  // Catálogo (administração)
  listAreas(): Observable<CatalogAreaDto[]> {
    return this.http.get<CatalogAreaDto[]>(`${this.catalogBase}/areas`);
  }

  createArea(request: CatalogAreaRequest): Observable<CatalogAreaDto> {
    return this.http.post<CatalogAreaDto>(`${this.catalogBase}/areas`, request);
  }

  updateArea(id: string, request: CatalogAreaRequest): Observable<CatalogAreaDto> {
    return this.http.patch<CatalogAreaDto>(`${this.catalogBase}/areas/${id}`, request);
  }

  deleteArea(id: string): Observable<void> {
    return this.http.delete<void>(`${this.catalogBase}/areas/${id}`);
  }

  listTopics(areaId?: string): Observable<CatalogTopicDto[]> {
    const params = areaId ? new HttpParams().set('areaId', areaId) : undefined;
    return this.http.get<CatalogTopicDto[]>(`${this.catalogBase}/topics`, { params });
  }

  createTopic(request: CatalogTopicRequest): Observable<CatalogTopicDto> {
    return this.http.post<CatalogTopicDto>(`${this.catalogBase}/topics`, request);
  }

  updateTopic(id: string, request: CatalogTopicRequest): Observable<CatalogTopicDto> {
    return this.http.patch<CatalogTopicDto>(`${this.catalogBase}/topics/${id}`, request);
  }

  deleteTopic(id: string): Observable<void> {
    return this.http.delete<void>(`${this.catalogBase}/topics/${id}`);
  }

  // Catálogo como o solicitante o vê (Portal)
  portal(): Observable<PortalCatalogDto> {
    return this.http.get<PortalCatalogDto>(`${this.catalogBase}/portal`);
  }

  portalForm(topicId: string): Observable<FormDefinition> {
    return this.http.get<FormDefinition>(`${this.catalogBase}/portal/topics/${topicId}/form`);
  }

  // Formulários versionados por tópico
  formVersions(topicId: string): Observable<TopicFormVersion[]> {
    return this.http.get<TopicFormVersion[]>(`${this.catalogBase}/topics/${topicId}/forms`);
  }

  /** Abre um rascunho a partir da definição enviada ou, sem corpo, da versão publicada. */
  createFormDraft(topicId: string, definition?: FormDefinition): Observable<TopicFormVersion> {
    return this.http.post<TopicFormVersion>(`${this.catalogBase}/topics/${topicId}/forms`, definition ?? null);
  }

  saveFormDraft(versionId: string, definition: FormDefinition): Observable<TopicFormVersion> {
    return this.http.put<TopicFormVersion>(`${this.catalogBase}/forms/${versionId}`, definition);
  }

  publishForm(versionId: string): Observable<TopicFormVersion> {
    return this.http.post<TopicFormVersion>(`${this.catalogBase}/forms/${versionId}/publish`, {});
  }

  discardFormDraft(versionId: string): Observable<void> {
    return this.http.delete<void>(`${this.catalogBase}/forms/${versionId}`);
  }

  /** O formulário com que o chamado foi aberto; nulo quando ele não tem (HTTP 204). */
  ticketForm(ticketId: string): Observable<TicketForm | null> {
    return this.http.get<TicketForm | null>(`${environment.apiUrl}/tickets/${ticketId}/form`);
  }
}
