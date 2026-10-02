import { Routes } from '@angular/router';

export const PORTAL_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./portal-shell.component').then(m => m.PortalShellComponent),
    children: [
      { path: '', loadComponent: () => import('./portal-home.component').then(m => m.PortalHomeComponent) },
      { path: 'novo', loadComponent: () => import('./portal-new.component').then(m => m.PortalNewComponent) },
      { path: ':id', loadComponent: () => import('./portal-ticket.component').then(m => m.PortalTicketComponent) },
    ],
  },
];
