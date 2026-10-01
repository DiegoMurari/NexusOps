import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface UserResponse {
  id: string;
  version: number;
  email: string;
  firstName: string;
  lastName: string;
  status: 'ACTIVE' | 'INACTIVE' | 'LOCKED';
  tenantId: string;
  roles: string[];
  permissions: string[];
  mfaEnabled: boolean;
  lastLoginAt: string | null;
  createdAt: string;
  updatedAt: string;
}

@Injectable({ providedIn: 'root' })
export class UserAdminService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/users`;

  list(tenantId?: string): Observable<UserResponse[]> {
    const url = tenantId ? `${this.base}?tenantId=${tenantId}` : this.base;
    return this.http.get<UserResponse[]>(url);
  }
}
