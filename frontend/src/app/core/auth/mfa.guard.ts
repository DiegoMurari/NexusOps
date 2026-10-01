import { Injectable, inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const MfaGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isMfaRequired()) {
    router.navigate(['/mfa-challenge'], { queryParams: { returnUrl: state.url } });
    return false;
  }

  return true;
};