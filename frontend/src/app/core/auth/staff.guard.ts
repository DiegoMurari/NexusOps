import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from './auth.service';

/** O console é da equipe: quem só solicita pedidos cai no Portal. */
export const StaffGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isAgent() ? true : inject(Router).createUrlTree(['/portal']);
};

/** Raiz do sistema: cada pessoa começa na sua experiência (equipe no console, solicitante no Portal). */
export const HomeGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) return router.createUrlTree(['/login']);
  return router.createUrlTree([auth.isAgent() ? '/dashboard' : '/portal']);
};
