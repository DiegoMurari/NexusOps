import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface RoleDto {
  id: string;
  name: string;
  description: string | null;
  tenantId: string;
  permissions: string[];
  isSystem: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface PermissionDto {
  id: string;
  permissionKey: string;
  resource: string;
  action: string;
  scope: string;
  description: string | null;
  category: string | null;
}

export interface RoleRequest {
  name?: string;
  description?: string;
  permissions?: string[];
}

@Injectable({ providedIn: 'root' })
export class RoleService {
  private http = inject(HttpClient);
  private rolesBase = `${environment.apiUrl}/roles`;

  listRoles(): Observable<RoleDto[]> {
    return this.http.get<RoleDto[]>(this.rolesBase);
  }

  createRole(request: RoleRequest): Observable<RoleDto> {
    return this.http.post<RoleDto>(this.rolesBase, request);
  }

  updateRole(id: string, request: RoleRequest): Observable<RoleDto> {
    return this.http.patch<RoleDto>(`${this.rolesBase}/${id}`, request);
  }

  deleteRole(id: string): Observable<void> {
    return this.http.delete<void>(`${this.rolesBase}/${id}`);
  }

  listPermissions(): Observable<PermissionDto[]> {
    return this.http.get<PermissionDto[]>(`${environment.apiUrl}/permissions`);
  }
}
