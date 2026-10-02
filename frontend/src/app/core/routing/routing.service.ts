import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type AssigneeStrategy = 'NONE' | 'USER' | 'LEAST_LOADED';

export interface RoutingCondition {
  /** TOPIC, AREA, LOCATION, PRIORITY ou ANSWER:chave. */
  field: string;
  values: string[];
}

export interface RoutingActions {
  queueId: string | null;
  priority: string | null;
  assigneeStrategy: AssigneeStrategy;
  assigneeId: string | null;
}

export interface RoutingRule {
  id: string;
  name: string;
  description: string | null;
  position: number;
  active: boolean;
  conditions: RoutingCondition[];
  actions: RoutingActions;
}

/** Criar exige nome e ao menos uma ação. No PATCH, ausente mantém; condições e ações substituem por inteiro. */
export interface RoutingRuleRequest {
  name?: string;
  description?: string;
  active?: boolean;
  conditions?: RoutingCondition[];
  actions?: RoutingActions;
}

export interface RoutingSimulationRequest {
  topicId?: string;
  locationId?: string;
  priority?: string;
  answers?: Record<string, unknown>;
}

export interface ConditionTrace {
  field: string;
  values: string[];
  actual: string;
  matched: boolean;
}

export interface RuleTrace {
  ruleId: string;
  ruleName: string;
  matched: boolean;
  applied: boolean;
  conditions: ConditionTrace[];
}

export interface RoutingDecision {
  /** TOPIC, RULE, MANUAL ou NONE. */
  source: string;
  queueId: string | null;
  queueName: string | null;
  priority: string | null;
  assigneeId: string | null;
  assigneeName: string | null;
  ruleId: string | null;
  ruleName: string | null;
  trace: RuleTrace[];
  notes: string[];
}

@Injectable({ providedIn: 'root' })
export class RoutingService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/routing`;

  rules(): Observable<RoutingRule[]> {
    return this.http.get<RoutingRule[]>(`${this.base}/rules`);
  }

  createRule(request: RoutingRuleRequest): Observable<RoutingRule> {
    return this.http.post<RoutingRule>(`${this.base}/rules`, request);
  }

  updateRule(id: string, request: RoutingRuleRequest): Observable<RoutingRule> {
    return this.http.patch<RoutingRule>(`${this.base}/rules/${id}`, request);
  }

  deleteRule(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/rules/${id}`);
  }

  reorder(ids: string[]): Observable<RoutingRule[]> {
    return this.http.put<RoutingRule[]>(`${this.base}/rules/order`, { ids });
  }

  simulate(request: RoutingSimulationRequest): Observable<RoutingDecision> {
    return this.http.post<RoutingDecision>(`${this.base}/simulate`, request);
  }
}
