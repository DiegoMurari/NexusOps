import { Routes } from '@angular/router';

export const ASSETS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./asset-grid/asset-grid.component').then(m => m.AssetGridComponent)
  },
  {
    path: 'new',
    loadComponent: () => import('./asset-create/asset-create.component').then(m => m.AssetCreateComponent)
  },
  {
    path: ':id',
    loadComponent: () => import('./asset-detail/asset-detail.component').then(m => m.AssetDetailComponent)
  }
];