import { Injectable, signal, computed, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap, catchError, throwError, BehaviorSubject, finalize } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface User {
  id: string;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  roles: string[];
  permissions: string[];
  mfaEnabled: boolean;
  tenantId: string;
}

export interface LoginRequest {
  username: string;
  password: string;
  mfaCode?: string;
  rememberMe?: boolean;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  tokenType: string;
  user: User;
  mfaRequired?: boolean;
}

export interface MfaSetupResponse {
  secret: string;
  qrCodeUrl: string;
  recoveryCodes: string[];
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

  private readonly TOKEN_KEY = 'nexusops_access_token';
  private readonly REFRESH_TOKEN_KEY = 'nexusops_refresh_token';
  private readonly USER_KEY = 'nexusops_user';

  private _user = signal<User | null>(null);
  private _isAuthenticated = signal(false);
  private _isMfaRequired = signal(false);
  private _loading = signal(false);
  private pendingCredentials: { username: string; password: string; rememberMe?: boolean } | null = null;

  user = computed(() => this._user());
  isAuthenticated = computed(() => this._isAuthenticated());
  isMfaRequired = computed(() => this._isMfaRequired());
  isAdmin = computed(() => this._user()?.roles.some(r => r === 'ADMIN' || r === 'SUPER_ADMIN') ?? false);
  isAgent = computed(() => this._user()?.roles.some(r => ['AGENT', 'TEAM_LEAD', 'MANAGER', 'ADMIN', 'SUPER_ADMIN'].includes(r)) ?? false);
  loading = computed(() => this._loading());

  constructor() {
    this.loadFromStorage();
  }

  private loadFromStorage(): void {
    const token = localStorage.getItem(this.TOKEN_KEY);
    const userStr = localStorage.getItem(this.USER_KEY);
    if (token && userStr) {
      if (!this.isTokenValid(token)) {
        this.clearStorage();
        return;
      }
      try {
        const user = JSON.parse(userStr);
        this._user.set(user);
        this._isAuthenticated.set(true);
      } catch {
        this.clearStorage();
      }
    }
  }

  /** A JWT with a passed `exp` claim is unusable even though it's still in storage -
   *  without this check, a stale token from a prior session (access tokens expire in
   *  15 min) makes isAuthenticated() report true forever, rendering the authenticated
   *  shell around routes like /login. */
  private isTokenValid(token: string): boolean {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return typeof payload.exp === 'number' && payload.exp * 1000 > Date.now();
    } catch {
      return false;
    }
  }

  login(credentials: LoginRequest): Observable<LoginResponse> {
    this._loading.set(true);
    return this.http.post<LoginResponse>(`${environment.apiUrl}/auth/login`, credentials).pipe(
      tap(response => this.handleAuthSuccess(response)),
      catchError(err => {
        // O servidor pede o código do autenticador: guarda as credenciais só na memória, para a segunda etapa.
        if (typeof err.error?.detail === 'string' && err.error.detail.includes('MFA_REQUIRED')) {
          this.pendingCredentials = { username: credentials.username, password: credentials.password, rememberMe: credentials.rememberMe };
          this._isMfaRequired.set(true);
        }
        return throwError(() => err);
      }),
      finalize(() => this._loading.set(false))
    );
  }

  /** Segunda etapa do login: repete o login com o código de 6 dígitos do autenticador. */
  verifyMfa(code: string): Observable<LoginResponse> {
    const credentials = this.pendingCredentials;
    if (!credentials) {
      return throwError(() => ({ error: { detail: 'Sessão de login expirada. Entre novamente.' } }));
    }
    return this.login({ ...credentials, mfaCode: code }).pipe(
      tap(() => {
        this.pendingCredentials = null;
        this._isMfaRequired.set(false);
      })
    );
  }

  /** Há um login esperando o código do autenticador. */
  hasPendingMfa(): boolean {
    return this.pendingCredentials !== null;
  }

  private handleAuthSuccess(response: LoginResponse): void {
    localStorage.setItem(this.TOKEN_KEY, response.accessToken);
    localStorage.setItem(this.REFRESH_TOKEN_KEY, response.refreshToken);
    localStorage.setItem(this.USER_KEY, JSON.stringify(response.user));
    this._user.set(response.user);
    this._isAuthenticated.set(true);
    this._isMfaRequired.set(false);
  }

  refreshToken(): Observable<LoginResponse> {
    this._loading.set(true);
    const refreshToken = localStorage.getItem(this.REFRESH_TOKEN_KEY);
    return this.http.post<LoginResponse>(`${environment.apiUrl}/auth/refresh`, { refreshToken }).pipe(
      tap(response => this.handleAuthSuccess(response)),
      finalize(() => this._loading.set(false))
    );
  }

  logout(): void {
    this._loading.set(true);
    const refreshToken = localStorage.getItem(this.REFRESH_TOKEN_KEY);
    this.http.post(`${environment.apiUrl}/auth/logout`, { refreshToken }).subscribe({
      complete: () => {
        this.clearAuth();
        this._loading.set(false);
      },
      error: () => {
        // Server call failed (e.g. token already expired) - still clear local state
        // so the user isn't stuck in a logged-in-looking but broken session.
        this.clearAuth();
        this._loading.set(false);
      }
    });
  }

  /** Local-only session teardown, no HTTP call. Used by the error interceptor so a
   *  401 on an /auth/* request can't recursively trigger another /auth/* request. */
  forceLogout(): void {
    this.clearAuth();
  }

  logoutAll(): Observable<void> {
    this._loading.set(true);
    return this.http.post<void>(`${environment.apiUrl}/auth/logout-all`, {}).pipe(
      tap(() => this.clearAuth()),
      finalize(() => this._loading.set(false))
    );
  }

  private clearAuth(): void {
    this.clearStorage();
    this._user.set(null);
    this._isAuthenticated.set(false);
    this._isMfaRequired.set(false);
    this.router.navigate(['/login']);
  }

  private clearStorage(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.REFRESH_TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
  }

  getAccessToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(this.REFRESH_TOKEN_KEY);
  }

  hasPermission(permission: string): boolean {
    return this._user()?.permissions.includes(permission) ?? false;
  }

  /** Tem a permissão em qualquer escopo? Ex.: can('SLA', 'READ') cobre SLA:READ:TENANT. */
  can(resource: string, action: string): boolean {
    const prefix = `${resource}:${action}:`;
    return this._user()?.permissions.some(p => p.startsWith(prefix)) ?? false;
  }

  hasRole(role: string): boolean {
    return this._user()?.roles.includes(role) ?? false;
  }

  hasAnyRole(roles: string[]): boolean {
    return roles.some(r => this.hasRole(r));
  }

  /** Reflete no usuário guardado (sinal e navegador) que a verificação em duas etapas mudou. */
  markMfaEnabled(enabled: boolean): void {
    const user = this._user();
    if (!user) return;
    const updated = { ...user, mfaEnabled: enabled };
    this._user.set(updated);
    localStorage.setItem(this.USER_KEY, JSON.stringify(updated));
  }

  setupMfa(): Observable<MfaSetupResponse> {
    return this.http.post<MfaSetupResponse>(`${environment.apiUrl}/auth/mfa/setup`, {});
  }

  enableMfa(code: string): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/auth/mfa/enable`, { code });
  }

  disableMfa(password: string): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/auth/mfa/disable`, { password });
  }

  getRecoveryCodes(): Observable<string[]> {
    return this.http.get<string[]>(`${environment.apiUrl}/auth/mfa/recovery-codes`);
  }

  regenerateRecoveryCodes(): Observable<string[]> {
    return this.http.post<string[]>(`${environment.apiUrl}/auth/mfa/recovery-codes/regenerate`, {});
  }
}