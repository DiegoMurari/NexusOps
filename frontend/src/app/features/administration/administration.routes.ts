import { Routes } from '@angular/router';

export const ADMINISTRATION_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./admin-dashboard/admin-dashboard.component').then(m => m.AdminDashboardComponent)
  },
  {
    path: 'users',
    loadComponent: () => import('./user-management/user-management.component').then(m => m.UserManagementComponent)
  },
  {
    path: 'queues',
    loadComponent: () => import('./queue-management/queue-management.component').then(m => m.QueueManagementComponent)
  },
  {
    path: 'catalog',
    loadComponent: () => import('./catalog-management/catalog-management.component').then(m => m.CatalogManagementComponent)
  },
  {
    path: 'routing',
    loadComponent: () => import('./routing-management/routing-management.component').then(m => m.RoutingManagementComponent)
  },
  {
    path: 'tenants',
    loadComponent: () => import('./tenant-management/tenant-management.component').then(m => m.TenantManagementComponent)
  },
  {
    path: 'roles',
    loadComponent: () => import('./role-management/role-management.component').then(m => m.RoleManagementComponent)
  },
  {
    path: 'feature-flags',
    loadComponent: () => import('./feature-flag-management/feature-flag-management.component').then(m => m.FeatureFlagManagementComponent)
  },
  {
    path: 'settings',
    loadComponent: () => import('./system-settings/system-settings.component').then(m => m.SystemSettingsComponent)
  },
  {
    path: 'audit-logs',
    loadComponent: () => import('./audit-log-viewer/audit-log-viewer.component').then(m => m.AuditLogViewerComponent)
  }
];