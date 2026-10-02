import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type ArticleStatus = 'DRAFT' | 'REVIEW' | 'PUBLISHED' | 'ARCHIVED';

export interface LinkedTicketDto {
  ticketId: string;
  ticketNumber: string;
  title: string;
  status: string;
  linkedBy: string | null;
  linkedAt: string;
}

export interface LinkedArticleDto {
  articleId: string;
  title: string;
  status: string;
  linkedBy: string | null;
  linkedAt: string;
}

export interface ArticleDto {
  id: string;
  title: string;
  slug: string;
  content: string | null;
  contentHtml: string | null;
  excerpt: string | null;
  status: ArticleStatus;
  tenantId: string;
  categoryId: string | null;
  authorId: string;
  version: number;
  publishedAt: string | null;
  publishedBy: string | null;
  seoTitle: string | null;
  seoDescription: string | null;
  seoKeywords: string | null;
  viewCount: number;
  helpfulCount: number;
  notHelpfulCount: number;
  helpfulPercentage: number;
  featured: boolean;
  allowComments: boolean;
  tags: string[];
  createdAt: string;
  updatedAt: string;
  createdBy: string | null;
  updatedBy: string | null;
}

export interface CreateArticleRequest {
  title: string;
  slug?: string;
  content?: string;
  excerpt?: string;
  categoryId?: string;
  featured?: boolean;
  allowComments?: boolean;
  tags?: string[];
}

export interface UpdateArticleRequest {
  title?: string;
  content?: string;
  excerpt?: string;
  categoryId?: string;
  featured?: boolean;
  allowComments?: boolean;
  tags?: string[];
}

export interface CategoryDto {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  parentId: string | null;
  icon: string | null;
  color: string | null;
  sortOrder: number;
  active: boolean;
  articleCount: number;
}

export interface CreateCategoryRequest {
  name: string;
  description?: string;
  parentId?: string;
  icon?: string;
  color?: string;
}

export interface TagDto {
  id: string;
  name: string;
  description: string | null;
  usageCount: number;
}

export interface ArticleFeedbackDto {
  id: string;
  articleId: string;
  userId: string;
  helpful: boolean;
  comment: string | null;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class KnowledgeService {
  private http = inject(HttpClient);
  private articlesBase = `${environment.apiUrl}/articles`;
  private categoriesBase = `${environment.apiUrl}/knowledge-categories`;
  private tagsBase = `${environment.apiUrl}/knowledge-tags`;

  listArticles(opts: { status?: ArticleStatus; categoryId?: string; search?: string; page?: number; size?: number } = {}): Observable<PageResponse<ArticleDto>> {
    let params = new HttpParams();
    if (opts.status) params = params.set('status', opts.status);
    if (opts.categoryId) params = params.set('categoryId', opts.categoryId);
    if (opts.search) params = params.set('search', opts.search);
    params = params.set('page', opts.page ?? 0).set('size', opts.size ?? 20);
    return this.http.get<PageResponse<ArticleDto>>(this.articlesBase, { params });
  }

  getArticle(id: string): Observable<ArticleDto> {
    return this.http.get<ArticleDto>(`${this.articlesBase}/${id}`);
  }

  createArticle(request: CreateArticleRequest): Observable<ArticleDto> {
    return this.http.post<ArticleDto>(this.articlesBase, request);
  }

  updateArticle(id: string, request: UpdateArticleRequest): Observable<ArticleDto> {
    return this.http.patch<ArticleDto>(`${this.articlesBase}/${id}`, request);
  }

  publishArticle(id: string): Observable<ArticleDto> {
    return this.http.post<ArticleDto>(`${this.articlesBase}/${id}/publish`, {});
  }

  archiveArticle(id: string): Observable<ArticleDto> {
    return this.http.post<ArticleDto>(`${this.articlesBase}/${id}/archive`, {});
  }

  deleteArticle(id: string): Observable<void> {
    return this.http.delete<void>(`${this.articlesBase}/${id}`);
  }

  linkedTickets(articleId: string): Observable<LinkedTicketDto[]> {
    return this.http.get<LinkedTicketDto[]>(`${this.articlesBase}/${articleId}/tickets`);
  }

  linkTicket(articleId: string, ticketId: string): Observable<LinkedTicketDto> {
    return this.http.put<LinkedTicketDto>(`${this.articlesBase}/${articleId}/tickets/${ticketId}`, null);
  }

  unlinkTicket(articleId: string, ticketId: string): Observable<void> {
    return this.http.delete<void>(`${this.articlesBase}/${articleId}/tickets/${ticketId}`);
  }

  articlesOfTicket(ticketId: string): Observable<LinkedArticleDto[]> {
    return this.http.get<LinkedArticleDto[]>(`${this.articlesBase}/by-ticket/${ticketId}`);
  }

  submitFeedback(articleId: string, helpful: boolean, comment?: string): Observable<ArticleFeedbackDto> {
    return this.http.post<ArticleFeedbackDto>(`${this.articlesBase}/${articleId}/feedback`, { helpful, comment });
  }

  listCategories(): Observable<CategoryDto[]> {
    return this.http.get<CategoryDto[]>(this.categoriesBase);
  }

  createCategory(request: CreateCategoryRequest): Observable<CategoryDto> {
    return this.http.post<CategoryDto>(this.categoriesBase, request);
  }

  listTags(): Observable<TagDto[]> {
    return this.http.get<TagDto[]>(this.tagsBase);
  }
}
