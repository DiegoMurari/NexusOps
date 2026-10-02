import { Routes } from '@angular/router';
import { AuthGuard } from './core/auth/auth.guard';
import { MfaGuard } from './core/auth/mfa.guard';
import { RoleGuard } from './core/auth/role.guard';
import { HomeGuard, StaffGuard } from './core/auth/staff.guard';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    canActivate: [HomeGuard],
    children: []
  },
  {
    path: 'portal',
    loadChildren: () => import('./features/portal/portal.routes').then(m => m.PORTAL_ROUTES),
    canActivate: [AuthGuard, MfaGuard]
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
    path: 'account/security',
    loadComponent: () => import('./features/account/account-security.component').then(m => m.AccountSecurityComponent),
    canActivate: [AuthGuard, MfaGuard]
  },
  {
    path: 'dashboard',
    loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent),
    canActivate: [AuthGuard, MfaGuard, StaffGuard]
  },
  {
    path: 'tickets',
    loadChildren: () => import('./features/ticketing/ticketing.routes').then(m => m.TICKETING_ROUTES),
    canActivate: [AuthGuard, MfaGuard, StaffGuard]
  },
  {
    path: 'assets',
    loadChildren: () => import('./features/assets/assets.routes').then(m => m.ASSETS_ROUTES),
    canActivate: [AuthGuard, MfaGuard, StaffGuard]
  },
  {
    path: 'knowledge',
    loadChildren: () => import('./features/knowledge/knowledge.routes').then(m => m.KNOWLEDGE_ROUTES),
    canActivate: [AuthGuard, MfaGuard, StaffGuard]
  },
  {
    path: 'sla',
    loadChildren: () => import('./features/sla/sla.routes').then(m => m.SLA_ROUTES),
    canActivate: [AuthGuard, MfaGuard, StaffGuard]
  },
  {
    path: 'reports',
    loadChildren: () => import('./features/reporting/reporting.routes').then(m => m.REPORTING_ROUTES),
    canActivate: [AuthGuard, MfaGuard, StaffGuard]
  },
  {
    path: 'integrations',
    loadChildren: () => import('./features/integrations/integrations.routes').then(m => m.INTEGRATIONS_ROUTES),
    canActivate: [AuthGuard, MfaGuard, StaffGuard]
  },
  {
    path: 'admin',
    loadChildren: () => import('./features/administration/administration.routes').then(m => m.ADMINISTRATION_ROUTES),
    canActivate: [AuthGuard, MfaGuard, RoleGuard],
    data: { roles: ['ADMIN', 'SUPER_ADMIN'] }
  },
  {
    path: '**',
    canActivate: [HomeGuard],
    children: []
  }
];