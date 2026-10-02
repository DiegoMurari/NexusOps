import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface TenantDto {
  id: string;
  name: string;
  domain: string;
  status: 'ACTIVE' | 'SUSPENDED' | 'CANCELLED';
  subscriptionTier: string;
  settings: string;
  maxUsers: number;
  maxAssets: number;
  createdAt: string;
  updatedAt: string;
}

export interface FeatureFlagDto {
  id: string;
  key: string;
  name: string;
  description: string | null;
  enabled: boolean;
  rolloutPercentage: number;
  targetingRules: string | null;
  variants: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface UpdateFeatureFlagRequest {
  name?: string;
  description?: string;
  enabled?: boolean;
  rolloutPercentage?: number;
}

export interface SystemSettingDto {
  id: string;
  settingKey: string;
  value: string;
  valueType: string;
  description: string | null;
  public: boolean;
  category: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AuditLogDto {
  id: string;
  eventType: string;
  aggregateId: string | null;
  aggregateType: string | null;
  tenantId: string;
  userId: string | null;
  resourceType: string | null;
  resourceId: string | null;
  action: string | null;
  ipAddress: string | null;
  userAgent: string | null;
  payload: string | null;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class PlatformService {
  private http = inject(HttpClient);
  private base = environment.apiUrl;

  listTenants(): Observable<TenantDto[]> {
    return this.http.get<TenantDto[]>(`${this.base}/tenants`);
  }

  listFeatureFlags(): Observable<FeatureFlagDto[]> {
    return this.http.get<FeatureFlagDto[]>(`${this.base}/feature-flags`);
  }

  updateFeatureFlag(id: string, request: UpdateFeatureFlagRequest): Observable<FeatureFlagDto> {
    return this.http.patch<FeatureFlagDto>(`${this.base}/feature-flags/${id}`, request);
  }

  listSettings(): Observable<SystemSettingDto[]> {
    return this.http.get<SystemSettingDto[]>(`${this.base}/settings`);
  }

  listAuditLogs(page = 0, size = 50, filters: { action?: string; resourceType?: string; q?: string; since?: string; until?: string } = {}): Observable<PageResponse<AuditLogDto>> {
    const params: Record<string, string> = { page: String(page), size: String(size) };
    if (filters.action) params['action'] = filters.action;
    if (filters.resourceType) params['resourceType'] = filters.resourceType;
    if (filters.q) params['q'] = filters.q;
    if (filters.since) params['since'] = filters.since;
    if (filters.until) params['until'] = filters.until;
    return this.http.get<PageResponse<AuditLogDto>>(`${this.base}/audit-logs`, { params });
  }
}
