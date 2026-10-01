import { Routes } from '@angular/router';

export const REPORTING_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./report-dashboard/report-dashboard.component').then(m => m.ReportDashboardComponent)
  },
  {
    path: 'builder',
    loadComponent: () => import('./report-builder/report-builder.component').then(m => m.ReportBuilderComponent)
  },
  {
    path: 'scheduled',
    loadComponent: () => import('./scheduled-reports/scheduled-reports.component').then(m => m.ScheduledReportsComponent)
  }
];