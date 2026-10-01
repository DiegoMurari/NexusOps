import { Routes } from '@angular/router';
import { AuthGuard } from './core/auth/auth.guard';
import { MfaGuard } from './core/auth/mfa.guard';
import { RoleGuard } from './core/auth/role.guard';

export const routes: Routes = [
  {
    path: '',
    redirectTo: '/dashboard',
    pathMatch: 'full'
  },
  {
    path: 'login',
    loadComponent: () => import('./features/administration/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: 'mfa-challenge',
    loadComponent: () => import('./features/administration/mfa-challenge/mfa-challenge.component').then(m => m.MfaChallengeComponent)
  },
  {
    path: 'dashboard',
    loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent),
    canActivate: [AuthGuard, MfaGuard]
  },
  {
    path: 'tickets',
    loadChildren: () => import('./features/ticketing/ticketing.routes').then(m => m.TICKETING_ROUTES),
    canActivate: [AuthGuard, MfaGuard]
  },
  {
    path: 'assets',
    loadChildren: () => import('./features/assets/assets.routes').then(m => m.ASSETS_ROUTES),
    canActivate: [AuthGuard, MfaGuard]
  },
  {
    path: 'knowledge',
    loadChildren: () => import('./features/knowledge/knowledge.routes').then(m => m.KNOWLEDGE_ROUTES),
    canActivate: [AuthGuard, MfaGuard]
  },
  {
    path: 'sla',
    loadChildren: () => import('./features/sla/sla.routes').then(m => m.SLA_ROUTES),
    canActivate: [AuthGuard, MfaGuard]
  },
  {
    path: 'reports',
    loadChildren: () => import('./features/reporting/reporting.routes').then(m => m.REPORTING_ROUTES),
    canActivate: [AuthGuard, MfaGuard]
  },
  {
    path: 'integrations',
    loadChildren: () => import('./features/integrations/integrations.routes').then(m => m.INTEGRATIONS_ROUTES),
    canActivate: [AuthGuard, MfaGuard]
  },
  {
    path: 'admin',
    loadChildren: () => import('./features/administration/administration.routes').then(m => m.ADMINISTRATION_ROUTES),
    canActivate: [AuthGuard, MfaGuard, RoleGuard],
    data: { roles: ['ADMIN', 'SUPER_ADMIN'] }
  },
  {
    path: '**',
    redirectTo: '/dashboard'
  }
];