import { Routes } from '@angular/router';

export const INTEGRATIONS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./integration-dashboard/integration-dashboard.component').then(m => m.IntegrationDashboardComponent)
  },
  {
    path: 'webhooks',
    loadComponent: () => import('./webhook-config/webhook-config.component').then(m => m.WebhookConfigComponent)
  },
  {
    path: 'jira',
    loadComponent: () => import('./jira-config/jira-config.component').then(m => m.JiraConfigComponent)
  },
  {
    path: 'slack',
    loadComponent: () => import('./slack-config/slack-config.component').then(m => m.SlackConfigComponent)
  }
];