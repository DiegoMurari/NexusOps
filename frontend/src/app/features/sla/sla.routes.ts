import { Routes } from '@angular/router';

export const SLA_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./sla-dashboard/sla-dashboard.component').then(m => m.SlaDashboardComponent)
  },
  {
    path: 'definitions',
    loadComponent: () => import('./sla-definition-list/sla-definition-list.component').then(m => m.SlaDefinitionListComponent)
  },
  {
    path: 'breaches',
    loadComponent: () => import('./sla-breach-list/sla-breach-list.component').then(m => m.SlaBreachListComponent)
  }
];