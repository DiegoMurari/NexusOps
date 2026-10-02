import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { ArticleDto, ArticleStatus, CategoryDto, KnowledgeService, LinkedTicketDto } from '../../../core/knowledge/knowledge.service';
import { AuthService } from '../../../core/auth/auth.service';
import { TicketDto, TicketService } from '../../../core/ticketing/ticket.service';
import { StatusBadgeComponent } from '../../../shared/components';

type Tone = 'neutral' | 'info' | 'success' | 'warning' | 'critical';

const STATUS_LABELS: Record<ArticleStatus, string> = {
  DRAFT: 'Rascunho',
  REVIEW: 'Em revisão',
  PUBLISHED: 'Publicado',
  ARCHIVED: 'Arquivado',
};

@Component({
  selector: 'app-article-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, ReactiveFormsModule, MatIconModule, StatusBadgeComponent],
  template: `
    @if (article(); as a) {
      <div class="detail-header">
        <a class="back-link" routerLink="/knowledge">
          <mat-icon>arrow_back</mat-icon>
          <span>Voltar</span>
        </a>
        <div class="title-row">
          <nx-status-badge [tone]="statusTone(a.status)">{{ statusLabel(a.status) }}</nx-status-badge>
          @if (a.featured) { <nx-status-badge tone="info">Destaque</nx-status-badge> }
        </div>
        @if (!editing()) {
          <h1 class="page-title">{{ a.title }}</h1>
        }
      </div>

      <div class="detail-grid">
        <div class="card">
          @if (editing()) {
            <form [formGroup]="editForm" (ngSubmit)="saveEdit()">
              <label class="field">
                <span class="field-label">Título</span>
                <input type="text" formControlName="title" />
              </label>
              <label class="field">
                <span class="field-label">Resumo</span>
                <textarea formControlName="excerpt" rows="2"></textarea>
              </label>
              <label class="field">
                <span class="field-label">Conteúdo</span>
                <textarea formControlName="content" rows="12"></textarea>
              </label>
              <label class="field">
                <span class="field-label">Categoria</span>
                <select formControlName="categoryId">
                  <option value="">— Nenhuma —</option>
                  @for (cat of categories(); track cat.id) {
                    <option [value]="cat.id">{{ cat.name }}</option>
                  }
                </select>
              </label>
              <div class="edit-actions">
                <button type="button" class="btn-secondary" (click)="cancelEdit()">Cancelar</button>
                <button type="submit" class="btn-primary" [disabled]="editForm.invalid || saving()">Salvar</button>
              </div>
            </form>
          } @else {
            <div class="content-header">
              <h3 class="card-title">Conteúdo</h3>
              @if (canManage) {
                <button class="btn-link" (click)="startEdit()">
                  <mat-icon>edit</mat-icon>
                  <span>Editar</span>
                </button>
              }
            </div>
            @if (a.excerpt) {
              <p class="excerpt">{{ a.excerpt }}</p>
            }
            <p class="content">{{ a.content || 'Sem conteúdo.' }}</p>

            @if (a.tags.length > 0) {
              <div class="tags-row">
                @for (tag of a.tags; track tag) {
                  <span class="tag-chip">{{ tag }}</span>
                }
              </div>
            }
          }
        </div>

        <div class="side-col">
          @if (canManage) {
            <div class="card">
              <h3 class="card-title">Ações</h3>

              @if (actionError()) {
                <div class="form-error">
                  <mat-icon>error_outline</mat-icon>
                  <span>{{ actionError() }}</span>
                </div>
              }

              <div class="actions-list">
                @if (a.status !== 'PUBLISHED') {
                  <button class="btn-primary" [disabled]="acting()" (click)="publish()">Publicar</button>
                }
                @if (a.status === 'PUBLISHED') {
                  <button class="btn-secondary" [disabled]="acting()" (click)="archive()">Arquivar</button>
                }
                <button class="btn-danger" [disabled]="acting()" (click)="deleteArticle()">Excluir</button>
              </div>
            </div>
          }

          <div class="card">
            <h3 class="card-title">Tickets relacionados</h3>
            @if (ticketError()) {
              <div class="form-error"><mat-icon>error_outline</mat-icon><span>{{ ticketError() }}</span></div>
            }
            @if (canManage && a.status === 'PUBLISHED') {
              <div class="link-box">
                <input class="input" type="search" placeholder="Buscar ticket por número ou título" aria-label="Buscar ticket para vincular"
                       [ngModel]="ticketQuery()" (ngModelChange)="searchTickets($event)" [ngModelOptions]="{standalone: true}" />
                @if (ticketResults().length > 0) {
                  <ul class="results" role="listbox" aria-label="Tickets encontrados">
                    @for (t of ticketResults(); track t.id) {
                      <li>
                        <button type="button" (click)="linkTicket(t.id)" [disabled]="acting()">
                          <span class="mono">{{ t.ticketNumber }}</span>
                          <span class="result-title">{{ t.title }}</span>
                        </button>
                      </li>
                    }
                  </ul>
                }
              </div>
            }
            @if (linkedTickets().length === 0) {
              <p class="description muted">Nenhum ticket usou este artigo ainda.</p>
            } @else {
              <ul class="linked-list">
                @for (t of linkedTickets(); track t.ticketId) {
                  <li>
                    <a [routerLink]="['/tickets', t.ticketId]" class="linked-main">
                      <span class="mono">{{ t.ticketNumber }}</span>
                      <span class="linked-title">{{ t.title }}</span>
                    </a>
                    @if (canManage) {
                      <button type="button" class="link-remove" (click)="unlinkTicket(t.ticketId)" [disabled]="acting()"
                              [attr.aria-label]="'Desvincular ' + t.ticketNumber">Remover</button>
                    }
                  </li>
                }
              </ul>
            }
          </div>

          <div class="card">
            <h3 class="card-title">Detalhes</h3>
            <div class="meta-item">
              <span class="meta-label">Categoria</span>
              <span class="meta-value">{{ categoryName(a.categoryId) }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Visualizações</span>
              <span class="meta-value mono">{{ a.viewCount }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Útil</span>
              <span class="meta-value mono">{{ a.helpfulPercentage | number:'1.0-0' }}% ({{ a.helpfulCount }}/{{ a.helpfulCount + a.notHelpfulCount }})</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Publicado em</span>
              <span class="meta-value mono">{{ a.publishedAt ? (a.publishedAt | date:'dd/MM/yyyy HH:mm') : '—' }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Atualizado em</span>
              <span class="meta-value mono">{{ a.updatedAt | date:'dd/MM/yyyy HH:mm' }}</span>
            </div>
          </div>

          <div class="card">
            <h3 class="card-title">Este artigo foi útil?</h3>
            @if (feedbackSent()) {
              <p class="feedback-thanks">Obrigado pelo seu feedback!</p>
            } @else {
              <div class="feedback-buttons">
                <button class="btn-secondary" [disabled]="acting()" (click)="sendFeedback(true)">
                  <mat-icon>thumb_up</mat-icon>
                  <span>Sim</span>
                </button>
                <button class="btn-secondary" [disabled]="acting()" (click)="sendFeedback(false)">
                  <mat-icon>thumb_down</mat-icon>
                  <span>Não</span>
                </button>
              </div>
            }
          </div>
        </div>
      </div>
    } @else if (loading()) {
      <div class="card empty-state">
        <mat-icon class="empty-ic">hourglass_empty</mat-icon>
        <p>Carregando artigo…</p>
      </div>
    } @else if (error()) {
      <div class="card empty-state error">
        <mat-icon class="empty-ic">error_outline</mat-icon>
        <p>{{ error() }}</p>
      </div>
    }
  `,
  styles: [`
    :host { display: block; }

    .back-link {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      color: var(--text-muted);
      font-size: 12.5px;
      text-decoration: none;
      margin-bottom: 10px;

      mat-icon { font-size: 16px; width: 16px; height: 16px; }
      &:hover { color: var(--text); }
    }

    .detail-header { margin-bottom: 20px; }

    .title-row {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 6px;
    }

    .page-title {
      margin: 0;
      font-size: 1.35rem;
      font-weight: 600;
      color: var(--text);
    }

    .link-box { position: relative; margin-bottom: var(--sp-5); }
    .link-box input { width: 100%; box-sizing: border-box; }
    .results {
      list-style: none; margin: var(--sp-2) 0 0; padding: 0;
      border: 1px solid var(--border); border-radius: var(--radius-s); background: var(--surface);
      max-height: 220px; overflow-y: auto;
    }
    .results button {
      display: flex; gap: var(--sp-4); align-items: baseline; width: 100%; text-align: left;
      padding: var(--sp-3) var(--sp-4); border: 0; background: transparent; color: var(--text); cursor: pointer;
    }
    .results button:hover:not(:disabled), .results button:focus-visible { background: var(--surface-2); }
    .result-title { color: var(--text-muted); min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .linked-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--sp-3); }
    .linked-list li { display: flex; align-items: baseline; justify-content: space-between; gap: var(--sp-4); }
    .linked-main { display: flex; gap: var(--sp-4); align-items: baseline; min-width: 0; color: var(--text); text-decoration: none; }
    .linked-main:hover .linked-title { text-decoration: underline; }
    .linked-title { color: var(--text-muted); min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .link-remove { border: 0; background: transparent; color: var(--text-muted); cursor: pointer; font-size: 12px; }
    .link-remove:hover:not(:disabled) { color: var(--critical); }

    .detail-grid {
      display: grid;
      grid-template-columns: 2fr 1fr;
      gap: 20px;
      align-items: start;
    }

    @media (max-width: 900px) {
      .detail-grid { grid-template-columns: 1fr; }
    }

    .side-col {
      display: flex;
      flex-direction: column;
      gap: 20px;
    }

    .content-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;
    }

    .card-title {
      margin: 0;
      font-size: 14px;
      font-weight: 600;
      color: var(--text);
    }

    .btn-link {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      background: none;
      border: none;
      color: var(--accent);
      font-size: 12.5px;
      cursor: pointer;
      padding: 0;

      mat-icon { font-size: 16px; width: 16px; height: 16px; }
      &:hover { text-decoration: underline; }
    }

    .excerpt {
      font-size: 13px;
      color: var(--text-muted);
      font-style: italic;
      line-height: 1.6;
      margin: 0 0 12px;
    }

    .content {
      font-size: 13px;
      color: var(--text);
      line-height: 1.6;
      white-space: pre-wrap;
      margin: 0 0 16px;
    }

    .tags-row {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
    }

    .tag-chip {
      display: inline-flex;
      align-items: center;
      height: 22px;
      padding: 0 10px;
      border-radius: 999px;
      background: var(--surface-2);
      color: var(--text-muted);
      font-size: 11.5px;
    }

    .field {
      display: flex;
      flex-direction: column;
      gap: 6px;
      margin-bottom: 14px;
    }

    .field-label {
      font-size: 12.5px;
      font-weight: 600;
      color: var(--text-muted);
    }

    input, textarea, select {
      font-family: var(--sans);
      font-size: 13px;
      color: var(--text);
      background: var(--surface-2);
      border: 1px solid var(--border);
      border-radius: var(--radius-s);
      padding: 9px 12px;
      outline: none;

      &:focus { border-color: var(--accent); }
    }

    textarea { resize: vertical; font-family: var(--sans); }

    .edit-actions, .actions-list, .feedback-buttons {
      display: flex;
      gap: 10px;
    }

    .actions-list, .feedback-buttons { flex-direction: column; }

    .feedback-buttons { flex-direction: row; }

    .edit-actions { justify-content: flex-end; }

    .feedback-thanks {
      font-size: 13px;
      color: var(--success);
      margin: 0;
    }

    .btn-primary, .btn-secondary, .btn-danger {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
      height: 36px;
      padding: 0 16px;
      border-radius: var(--radius-s);
      font-weight: 500;
      font-size: 12.5px;
      border: none;
      cursor: pointer;

      mat-icon { font-size: 18px; width: 18px; height: 18px; }

      &:disabled { opacity: 0.5; cursor: default; }
    }

    .btn-primary {
      background: var(--accent);
      color: #fff;
      &:hover:not(:disabled) { filter: brightness(1.08); }
    }

    .btn-secondary {
      background: var(--surface-2);
      color: var(--text);
      border: 1px solid var(--border);
      &:hover:not(:disabled) { background: var(--surface-3); }
    }

    .btn-danger {
      background: var(--critical-soft);
      color: var(--critical);
      &:hover:not(:disabled) { filter: brightness(0.95); }
    }

    .meta-item {
      display: flex;
      flex-direction: column;
      gap: 2px;
      padding: 8px 0;
      border-bottom: 1px solid var(--border);
    }

    .meta-item:last-child { border-bottom: none; }

    .meta-label {
      font-size: 11px;
      text-transform: uppercase;
      letter-spacing: 0.03em;
      color: var(--text-faint);
    }

    .meta-value {
      font-size: 13px;
      color: var(--text);
    }

    .form-error {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 10px 12px;
      border-radius: var(--radius-s);
      background: var(--critical-soft);
      color: var(--critical);
      font-size: 12.5px;
      margin-bottom: 12px;

      mat-icon { font-size: 18px; width: 18px; height: 18px; }
    }

    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 10px;
      padding: 56px 16px;
      color: var(--text-faint);
      font-size: 13px;

      &.error { color: var(--critical); }
    }

    .empty-ic {
      font-size: 36px;
      width: 36px;
      height: 36px;
      color: inherit;
    }
  `]
})
export class ArticleDetailComponent implements OnInit {
  article = signal<ArticleDto | null>(null);
  categories = signal<CategoryDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  acting = signal(false);
  actionError = signal<string | null>(null);
  editing = signal(false);
  saving = signal(false);
  feedbackSent = signal(false);
  linkedTickets = signal<LinkedTicketDto[]>([]);
  ticketError = signal<string | null>(null);
  ticketQuery = signal('');
  ticketResults = signal<TicketDto[]>([]);
  /** Quem pode editar a base também publica, arquiva, exclui e vincula tickets. */
  readonly canManage: boolean;
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  editForm: FormGroup;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private fb: FormBuilder,
    private knowledgeService: KnowledgeService,
    private ticketService: TicketService,
    auth: AuthService
  ) {
    this.canManage = auth.can('KNOWLEDGE', 'UPDATE');
    this.editForm = this.fb.group({
      title: ['', [Validators.required, Validators.maxLength(500)]],
      excerpt: [''],
      content: [''],
      categoryId: [''],
    });
  }

  ngOnInit(): void {
    this.knowledgeService.listCategories().subscribe({
      next: cats => this.categories.set(cats),
      error: () => {}
    });

    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.error.set('Artigo inválido.');
      this.loading.set(false);
      return;
    }
    this.load(id);
  }

  private load(id: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.knowledgeService.getArticle(id).subscribe({
      next: a => {
        this.article.set(a);
        this.loading.set(false);
        this.loadTickets(a.id);
      },
      error: () => {
        this.error.set('Artigo não encontrado.');
        this.loading.set(false);
      }
    });
  }

  statusTone(status: ArticleStatus): Tone {
    switch (status) {
      case 'PUBLISHED': return 'success';
      case 'REVIEW': return 'warning';
      case 'ARCHIVED': return 'neutral';
      default: return 'info';
    }
  }

  private loadTickets(id: string): void {
    this.knowledgeService.linkedTickets(id).subscribe({
      next: list => this.linkedTickets.set(list),
      error: () => this.ticketError.set('Não foi possível carregar os tickets relacionados.')
    });
  }

  searchTickets(query: string): void {
    this.ticketQuery.set(query);
    if (this.searchTimer) clearTimeout(this.searchTimer);
    const q = query.trim();
    if (q.length < 2) {
      this.ticketResults.set([]);
      return;
    }
    this.searchTimer = setTimeout(() => {
      this.ticketService.queueView({ scope: 'ALL', q, size: 6 }).subscribe({
        next: res => {
          const linked = new Set(this.linkedTickets().map(t => t.ticketId));
          this.ticketResults.set(res.content.filter(t => !linked.has(t.id)));
        },
        error: () => this.ticketResults.set([])
      });
    }, 300);
  }

  linkTicket(ticketId: string): void {
    const a = this.article();
    if (!a) return;
    this.acting.set(true);
    this.ticketError.set(null);
    this.knowledgeService.linkTicket(a.id, ticketId).subscribe({
      next: () => {
        this.acting.set(false);
        this.ticketQuery.set('');
        this.ticketResults.set([]);
        this.loadTickets(a.id);
      },
      error: () => {
        this.acting.set(false);
        this.ticketError.set('Não foi possível vincular o ticket.');
      }
    });
  }

  unlinkTicket(ticketId: string): void {
    const a = this.article();
    if (!a) return;
    this.acting.set(true);
    this.ticketError.set(null);
    this.knowledgeService.unlinkTicket(a.id, ticketId).subscribe({
      next: () => {
        this.acting.set(false);
        this.loadTickets(a.id);
      },
      error: () => {
        this.acting.set(false);
        this.ticketError.set('Não foi possível desvincular o ticket.');
      }
    });
  }

  startEdit(): void {
    const a = this.article();
    if (!a) return;
    this.editForm.setValue({
      title: a.title,
      excerpt: a.excerpt ?? '',
      content: a.content ?? '',
      categoryId: a.categoryId ?? '',
    });
    this.editing.set(true);
  }

  cancelEdit(): void {
    this.editing.set(false);
  }

  saveEdit(): void {
    const a = this.article();
    if (!a || this.editForm.invalid) return;

    this.saving.set(true);
    const value = this.editForm.getRawValue();
    this.knowledgeService.updateArticle(a.id, {
      title: value.title,
      excerpt: value.excerpt || undefined,
      content: value.content || undefined,
      categoryId: value.categoryId || undefined,
    }).subscribe({
      next: updated => {
        this.article.set(updated);
        this.saving.set(false);
        this.editing.set(false);
      },
      error: () => {
        this.saving.set(false);
      }
    });
  }

  publish(): void {
    const a = this.article();
    if (!a) return;
    this.acting.set(true);
    this.actionError.set(null);
    this.knowledgeService.publishArticle(a.id).subscribe({
      next: updated => { this.article.set(updated); this.acting.set(false); },
      error: () => { this.acting.set(false); this.actionError.set('Não foi possível publicar o artigo.'); }
    });
  }

  archive(): void {
    const a = this.article();
    if (!a) return;
    this.acting.set(true);
    this.actionError.set(null);
    this.knowledgeService.archiveArticle(a.id).subscribe({
      next: updated => { this.article.set(updated); this.acting.set(false); },
      error: () => { this.acting.set(false); this.actionError.set('Não foi possível arquivar o artigo.'); }
    });
  }

  deleteArticle(): void {
    const a = this.article();
    if (!a) return;
    this.acting.set(true);
    this.actionError.set(null);
    this.knowledgeService.deleteArticle(a.id).subscribe({
      next: () => this.router.navigate(['/knowledge']),
      error: () => { this.acting.set(false); this.actionError.set('Não foi possível excluir o artigo.'); }
    });
  }

  sendFeedback(helpful: boolean): void {
    const a = this.article();
    if (!a) return;
    this.acting.set(true);
    this.knowledgeService.submitFeedback(a.id, helpful).subscribe({
      next: () => { this.acting.set(false); this.feedbackSent.set(true); },
      error: () => { this.acting.set(false); }
    });
  }

  categoryName(categoryId: string | null): string {
    if (!categoryId) return '—';
    return this.categories().find(c => c.id === categoryId)?.name ?? '—';
  }

  statusLabel(status: ArticleStatus): string {
    return STATUS_LABELS[status] ?? status;
  }
}
