import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type AssetType = 'HARDWARE' | 'SOFTWARE' | 'CLOUD' | 'VIRTUAL' | 'NETWORK' | 'STORAGE' | 'PERIPHERAL';
export type LifecycleStatus = 'PROCURED' | 'DEPLOYED' | 'MAINTENANCE' | 'RETIRED' | 'DISPOSED' | 'LOST' | 'STOLEN';

export interface AssetDto {
  id: string;
  assetTag: string;
  name: string;
  description: string | null;
  type: AssetType;
  lifecycleStatus: LifecycleStatus;
  tenantId: string;
  manufacturer: string | null;
  model: string | null;
  serialNumber: string | null;
  specifications: string | null;
  locationId: string | null;
  assignedToId: string | null;
  purchaseDate: string | null;
  warrantyExpiration: string | null;
  purchaseCost: number | null;
  depreciationMethod: string | null;
  discoverySource: string | null;
  lastDiscoveredAt: string | null;
  createdAt: string;
  updatedAt: string;
  createdBy: string | null;
  updatedBy: string | null;
}

export interface CreateAssetRequest {
  assetTag?: string;
  name: string;
  description?: string;
  type: AssetType;
  lifecycleStatus?: LifecycleStatus;
  manufacturer?: string;
  model?: string;
  serialNumber?: string;
  locationId?: string;
  assignedToId?: string;
  purchaseDate?: string;
  warrantyExpiration?: string;
  purchaseCost?: number;
}

export interface UpdateAssetRequest {
  name?: string;
  description?: string;
  type?: AssetType;
  lifecycleStatus?: LifecycleStatus;
  manufacturer?: string;
  model?: string;
  serialNumber?: string;
  locationId?: string;
  assignedToId?: string;
  purchaseCost?: number;
}

export interface LocationDto {
  id: string;
  name: string;
  description: string | null;
  tenantId: string;
  parentId: string | null;
  type: string;
  address: string | null;
  coordinates: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CIRelationshipDto {
  id: string;
  sourceId: string;
  targetId: string;
  relationshipType: string;
  tenantId: string;
  description: string | null;
  createdAt: string;
  createdBy: string | null;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class AssetService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/assets`;
  private locationsBase = `${environment.apiUrl}/locations`;
  private relationshipsBase = `${environment.apiUrl}/ci-relationships`;

  list(opts: { type?: AssetType; status?: LifecycleStatus; search?: string; page?: number; size?: number } = {}): Observable<PageResponse<AssetDto>> {
    let params = new HttpParams();
    if (opts.type) params = params.set('type', opts.type);
    if (opts.status) params = params.set('status', opts.status);
    if (opts.search) params = params.set('search', opts.search);
    params = params.set('page', opts.page ?? 0).set('size', opts.size ?? 20);
    return this.http.get<PageResponse<AssetDto>>(this.base, { params });
  }

  get(id: string): Observable<AssetDto> {
    return this.http.get<AssetDto>(`${this.base}/${id}`);
  }

  create(request: CreateAssetRequest): Observable<AssetDto> {
    return this.http.post<AssetDto>(this.base, request);
  }

  update(id: string, request: UpdateAssetRequest): Observable<AssetDto> {
    return this.http.patch<AssetDto>(`${this.base}/${id}`, request);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  listLocations(): Observable<LocationDto[]> {
    return this.http.get<LocationDto[]>(this.locationsBase);
  }

  listRelationships(assetId: string): Observable<CIRelationshipDto[]> {
    return this.http.get<CIRelationshipDto[]>(`${this.relationshipsBase}/asset/${assetId}`);
  }
}
