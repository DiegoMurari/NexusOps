import { Routes } from '@angular/router';

export const KNOWLEDGE_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./article-list/article-list.component').then(m => m.ArticleListComponent)
  },
  {
    path: 'new',
    loadComponent: () => import('./article-create/article-create.component').then(m => m.ArticleCreateComponent)
  },
  {
    path: ':id',
    loadComponent: () => import('./article-detail/article-detail.component').then(m => m.ArticleDetailComponent)
  }
];