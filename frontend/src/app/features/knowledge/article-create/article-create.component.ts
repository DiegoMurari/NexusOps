import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { CategoryDto, KnowledgeService } from '../../../core/knowledge/knowledge.service';

@Component({
  selector: 'app-article-create',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatIconModule],
  template: `
    <h1 class="page-title">Criar Artigo</h1>

    <form class="card form" [formGroup]="form" (ngSubmit)="onSubmit()">
      <label class="field">
        <span class="field-label">Título*</span>
        <input type="text" formControlName="title" placeholder="Ex: Como resetar sua senha de VPN" />
        @if (form.controls['title'].touched && form.controls['title'].invalid) {
          <span class="field-error">Informe um título (até 500 caracteres)</span>
        }
      </label>

      <label class="field">
        <span class="field-label">Resumo</span>
        <textarea formControlName="excerpt" rows="2" placeholder="Resumo curto exibido na listagem…"></textarea>
      </label>

      <label class="field">
        <span class="field-label">Conteúdo</span>
        <textarea formControlName="content" rows="10" placeholder="Conteúdo completo do artigo…"></textarea>
      </label>

      <div class="field-row">
        <label class="field">
          <span class="field-label">Categoria</span>
          <select formControlName="categoryId">
            <option value="">— Nenhuma —</option>
            @for (cat of categories(); track cat.id) {
              <option [value]="cat.id">{{ cat.name }}</option>
            }
          </select>
        </label>

        <label class="field">
          <span class="field-label">Tags (separadas por vírgula)</span>
          <input type="text" formControlName="tagsInput" placeholder="vpn, senha, acesso" />
        </label>
      </div>

      @if (error()) {
        <div class="form-error">
          <mat-icon>error_outline</mat-icon>
          <span>{{ error() }}</span>
        </div>
      }

      <div class="form-actions">
        <button type="button" class="btn-secondary" (click)="cancel()">Cancelar</button>
        <button type="submit" class="btn-primary" [disabled]="form.invalid || submitting()">
          @if (submitting()) {
            <mat-icon class="spin">progress_activity</mat-icon>
          } @else {
            <mat-icon>check</mat-icon>
          }
          <span>Criar Artigo</span>
        </button>
      </div>
    </form>
  `,
  styles: [`
    :host { display: block; }

    .page-title {
      margin: 0 0 20px;
      font-size: 1.5rem;
      font-weight: 600;
      color: var(--text);
    }

    .form {
      display: flex;
      flex-direction: column;
      gap: 16px;
      max-width: 760px;
    }

    .field {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .field-row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 16px;
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
      &::placeholder { color: var(--text-faint); }
    }

    textarea { resize: vertical; font-family: var(--sans); }

    .field-error {
      font-size: 11.5px;
      color: var(--critical);
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

      mat-icon { font-size: 18px; width: 18px; height: 18px; }
    }

    .form-actions {
      display: flex;
      justify-content: flex-end;
      gap: 10px;
      margin-top: 4px;
    }

    .btn-primary, .btn-secondary {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      height: 38px;
      padding: 0 18px;
      border-radius: var(--radius-s);
      font-weight: 500;
      font-size: 13px;
      border: none;
      cursor: pointer;

      mat-icon { font-size: 18px; width: 18px; height: 18px; }
    }

    .btn-primary {
      background: var(--accent);
      color: #fff;

      &:hover:not(:disabled) { filter: brightness(1.08); }
      &:disabled { opacity: 0.5; cursor: default; }
    }

    .btn-secondary {
      background: var(--surface-2);
      color: var(--text);
      border: 1px solid var(--border);

      &:hover { background: var(--surface-3); }
    }

    .spin {
      animation: spin 1s linear infinite;
    }

    @keyframes spin {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }
  `]
})
export class ArticleCreateComponent implements OnInit {
  form: FormGroup;

  submitting = signal(false);
  error = signal<string | null>(null);
  categories = signal<CategoryDto[]>([]);

  constructor(
    private fb: FormBuilder,
    private knowledgeService: KnowledgeService,
    private router: Router
  ) {
    this.form = this.fb.group({
      title: ['', [Validators.required, Validators.maxLength(500)]],
      excerpt: [''],
      content: [''],
      categoryId: [''],
      tagsInput: [''],
    });
  }

  ngOnInit(): void {
    this.knowledgeService.listCategories().subscribe({
      next: cats => this.categories.set(cats),
      error: () => {}
    });
  }

  onSubmit(): void {
    if (this.form.invalid) return;

    this.submitting.set(true);
    this.error.set(null);

    const value = this.form.getRawValue();
    const tags = (value.tagsInput as string)
      .split(',')
      .map(t => t.trim())
      .filter(t => t.length > 0);

    this.knowledgeService.createArticle({
      title: value.title,
      excerpt: value.excerpt || undefined,
      content: value.content || undefined,
      categoryId: value.categoryId || undefined,
      tags: tags.length > 0 ? tags : undefined,
    }).subscribe({
      next: article => {
        this.router.navigate(['/knowledge', article.id]);
      },
      error: err => {
        this.submitting.set(false);
        this.error.set(err?.error?.detail ?? 'Não foi possível criar o artigo. Tente novamente.');
      }
    });
  }

  cancel(): void {
    this.router.navigate(['/knowledge']);
  }
}
