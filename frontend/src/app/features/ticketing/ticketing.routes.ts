import { Routes } from '@angular/router';

export const TICKETING_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./ticket-list/ticket-list.component').then(m => m.TicketListComponent)
  },
  {
    path: 'new',
    loadComponent: () => import('./ticket-create/ticket-create.component').then(m => m.TicketCreateComponent)
  },
  {
    path: ':id',
    loadComponent: () => import('./ticket-detail/ticket-detail.component').then(m => m.TicketDetailComponent)
  }
];