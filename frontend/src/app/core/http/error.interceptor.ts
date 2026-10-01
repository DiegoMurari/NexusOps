import { HttpInterceptorFn, HttpRequest, HttpHandlerFn, HttpEvent, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, throwError, catchError, switchMap } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { Router } from '@angular/router';

export const errorInterceptor: HttpInterceptorFn = (req: HttpRequest<unknown>, next: HttpHandlerFn): Observable<HttpEvent<unknown>> => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        // Any /auth/* endpoint (refresh, logout, login, mfa) failing with 401 means the
        // session is already unusable - clear it locally and stop. Attempting a refresh
        // here would call another /auth/* endpoint, which on failure would loop back into
        // this same branch forever (observed live: /auth/logout 401 -> refresh -> refresh
        // fails -> logout -> 401 -> refresh -> ... hammering the backend continuously).
        if (req.url.includes('/auth/')) {
          authService.forceLogout();
          return throwError(() => error);
        }

        return authService.refreshToken().pipe(
          switchMap(() => {
            const token = authService.getAccessToken();
            const retryReq = req.clone({
              setHeaders: { Authorization: `Bearer ${token}` }
            });
            return next(retryReq);
          }),
          catchError(() => {
            authService.forceLogout();
            return throwError(() => error);
          })
        );
      }

      if (error.status === 403) {
        router.navigate(['/dashboard']);
      }

      return throwError(() => error);
    })
  );
};