import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { ArticleDto, ArticleStatus, KnowledgeService } from '../../../core/knowledge/knowledge.service';
import {
  ButtonComponent, DataTableComponent, EmptyStateComponent, NxCellDirective, NxColumn, PageHeaderComponent,
  StatusBadgeComponent, Tone,
} from '../../../shared/components';

const STATUS_LABELS: Record<ArticleStatus, string> = {
  DRAFT: 'Rascunho',
  REVIEW: 'Em revisão',
  PUBLISHED: 'Publicado',
  ARCHIVED: 'Arquivado',
};

const STATUS_TONES: Record<ArticleStatus, Tone> = {
  DRAFT: 'neutral',
  REVIEW: 'warning',
  PUBLISHED: 'success',
  ARCHIVED: 'neutral',
};

const STATUS_FILTERS: { value: ArticleStatus | null; label: string }[] = [
  { value: null, label: 'Todos' },
  { value: 'DRAFT', label: 'Rascunho' },
  { value: 'REVIEW', label: 'Em revisão' },
  { value: 'PUBLISHED', label: 'Publicado' },
  { value: 'ARCHIVED', label: 'Arquivado' },
];

@Component({
  selector: 'app-article-list',
  standalone: true,
  imports: [
    CommonModule, RouterLink, MatIconModule, ButtonComponent, DataTableComponent, EmptyStateComponent,
    NxCellDirective, PageHeaderComponent, StatusBadgeComponent,
  ],
  template: `
    <nx-page-header heading="Base de conhecimento">
      <a nxButton variant="primary" routerLink="new">
        <mat-icon>add</mat-icon>
        Novo artigo
      </a>
    </nx-page-header>

    <div class="toolbar">
      <div class="seg" role="group" aria-label="Filtrar por status">
        @for (f of statusFilters; track f.label) {
          <button type="button" [attr.aria-pressed]="activeStatus() === f.value" (click)="setStatus(f.value)">{{ f.label }}</button>
        }
      </div>
      <input
        class="input search-input"
        type="search"
        placeholder="Buscar por título ou conteúdo…"
        aria-label="Buscar artigos"
        [value]="searchTerm()"
        (input)="onSearchInput($event)"
      />
    </div>

    @if (error()) {
      <nx-empty-state variant="error" [heading]="error()!" />
    } @else {
      <nx-data-table caption="Artigos da base de conhecimento" [columns]="columns" [rows]="articles()" [loading]="loading()"
                     [rowClickable]="true" (rowActivate)="open($event)" emptyTitle="Nenhum artigo encontrado">
        <ng-template nxCell="title" let-a><a class="row-link" [routerLink]="[a.id]">{{ a.title }}</a></ng-template>
        <ng-template nxCell="status" let-a>
          <nx-status-badge [tone]="statusTone(a.status)">{{ statusLabel(a.status) }}</nx-status-badge>
        </ng-template>
        <ng-template nxCell="categoryId" let-a>{{ categoryName(a.categoryId) }}</ng-template>
        <ng-template nxCell="helpfulPercentage" let-a>{{ a.helpfulPercentage | number:'1.0-0' }}%</ng-template>
        <ng-template nxCell="updatedAt" let-a>{{ a.updatedAt | date:'dd/MM/yyyy HH:mm' }}</ng-template>
      </nx-data-table>

      @if (articles().length > 0) {
        <nav class="pagination" aria-label="Paginação de artigos">
          <button nxButton variant="icon" size="sm" aria-label="Página anterior" [disabled]="loading() || page() === 0" (click)="prevPage()">
            <mat-icon>chevron_left</mat-icon>
          </button>
          <span class="page-info" aria-live="polite">Página {{ page() + 1 }} de {{ totalPages() || 1 }}</span>
          <button nxButton variant="icon" size="sm" aria-label="Próxima página" [disabled]="loading() || page() + 1 >= totalPages()" (click)="nextPage()">
            <mat-icon>chevron_right</mat-icon>
          </button>
        </nav>
      }
    }
  `,
  styles: [`
    :host { display: block; }
    .toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: var(--sp-5); margin-bottom: var(--sp-6); }
    .search-input { min-width: 260px; }
    .row-link { color: var(--text); font-weight: var(--fw-medium); text-decoration: none; }
    .row-link:hover { color: var(--accent); text-decoration: underline; }
    .pagination { display: flex; align-items: center; justify-content: center; gap: var(--sp-5); margin-top: var(--sp-6); }
    .page-info { font-size: var(--fs-sm); color: var(--text-muted); }
  `]
})
export class ArticleListComponent implements OnInit {
  readonly columns: NxColumn[] = [
    { key: 'title', header: 'Título', rowHeader: true, maxWidth: '420px' },
    { key: 'status', header: 'Status' },
    { key: 'categoryId', header: 'Categoria', muted: true },
    { key: 'viewCount', header: 'Visualizações', mono: true, muted: true },
    { key: 'helpfulPercentage', header: 'Útil', mono: true, muted: true },
    { key: 'updatedAt', header: 'Atualizado em', mono: true, muted: true },
  ];

  articles = signal<ArticleDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  activeStatus = signal<ArticleStatus | null>(null);
  searchTerm = signal('');
  page = signal(0);
  totalPages = signal(0);
  categories = signal<Map<string, string>>(new Map());

  statusFilters = STATUS_FILTERS;
  private searchDebounce: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private knowledgeService: KnowledgeService,
    private router: Router,
    private route: ActivatedRoute,
  ) {}

  ngOnInit(): void {
    this.knowledgeService.listCategories().subscribe({
      next: cats => this.categories.set(new Map(cats.map(c => [c.id, c.name]))),
      error: () => {}
    });
    this.load();
  }

  open(article: unknown): void {
    this.router.navigate([(article as ArticleDto).id], { relativeTo: this.route });
  }

  setStatus(status: ArticleStatus | null): void {
    this.activeStatus.set(status);
    this.page.set(0);
    this.load();
  }

  onSearchInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.searchTerm.set(value);
    if (this.searchDebounce) clearTimeout(this.searchDebounce);
    this.searchDebounce = setTimeout(() => {
      this.page.set(0);
      this.load();
    }, 350);
  }

  prevPage(): void {
    if (this.page() > 0) {
      this.page.update(p => p - 1);
      this.load();
    }
  }

  nextPage(): void {
    if (this.page() + 1 < this.totalPages()) {
      this.page.update(p => p + 1);
      this.load();
    }
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.knowledgeService.listArticles({
      status: this.activeStatus() ?? undefined,
      search: this.searchTerm() || undefined,
      page: this.page(),
    }).subscribe({
      next: res => {
        this.articles.set(res.content);
        this.totalPages.set(res.totalPages);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Não foi possível carregar os artigos.');
        this.loading.set(false);
      }
    });
  }

  categoryName(categoryId: string | null): string {
    if (!categoryId) return '—';
    return this.categories().get(categoryId) ?? '—';
  }

  statusLabel(status: ArticleStatus): string {
    return STATUS_LABELS[status] ?? status;
  }

  statusTone(status: ArticleStatus): Tone {
    return STATUS_TONES[status] ?? 'neutral';
  }
}
