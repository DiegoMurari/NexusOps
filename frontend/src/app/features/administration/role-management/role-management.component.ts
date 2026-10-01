import { Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { RoleService } from '../../../core/iam/role.service';
import type { PermissionDto, RoleDto } from '../../../core/iam/role.service';

interface PermissionGroup {
  category: string;
  items: PermissionDto[];
}

@Component({
  selector: 'app-role-management',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  template: `
    <div class="page-header">
      <h1 class="page-title">Gerenciamento de Papéis</h1>
      <button class="btn-primary" (click)="openCreate()">
        <mat-icon>add</mat-icon>
        <span>Novo papel</span>
      </button>
    </div>

    <div class="notice">
      <mat-icon>info</mat-icon>
      <span>Papéis do sistema são somente leitura. Em papéis personalizados você só pode conceder permissões que você mesmo possui.</span>
    </div>

    @if (showForm()) {
      <form class="card form-card" (submit)="submit($event)">
        <h2 class="form-title">{{ readOnly() ? 'Papel do sistema' : (editingId() ? 'Editar papel' : 'Novo papel') }}</h2>
        <div class="grid">
          <label class="field">
            <span class="label">Nome *</span>
            <input class="input mono" type="text" maxlength="50" placeholder="EX: LIDER_QA"
                   [disabled]="!!editingId() || readOnly()" [value]="name()" (input)="name.set($any($event.target).value)" />
          </label>
          <label class="field">
            <span class="label">Descrição</span>
            <input class="input" type="text" maxlength="255" [disabled]="readOnly()"
                   [value]="description()" (input)="description.set($any($event.target).value)" />
          </label>
        </div>

        <div class="perm-head">
          <span class="label">Permissões ({{ selected().size }} selecionadas)</span>
        </div>
        <div class="perm-groups">
          @for (g of groups(); track g.category) {
            <fieldset class="perm-group">
              <legend>{{ g.category }}</legend>
              @for (p of g.items; track p.permissionKey) {
                <label class="perm" [title]="p.description || ''">
                  <input type="checkbox" [disabled]="readOnly()" [checked]="selected().has(p.permissionKey)"
                         (change)="toggle(p.permissionKey, $any($event.target).checked)" />
                  <span class="mono">{{ p.permissionKey }}</span>
                </label>
              }
            </fieldset>
          }
        </div>

        @if (formError()) { <p class="inline-error">{{ formError() }}</p> }
        <div class="form-actions">
          <button class="btn-secondary" type="button" (click)="showForm.set(false)">{{ readOnly() ? 'Fechar' : 'Cancelar' }}</button>
          @if (!readOnly()) {
            <button class="btn-primary" type="submit" [disabled]="saving()">{{ saving() ? 'Salvando…' : 'Salvar' }}</button>
          }
        </div>
      </form>
    }

    @if (loading()) {
      <div class="card empty-state"><mat-icon class="empty-ic">hourglass_empty</mat-icon><p>Carregando papéis…</p></div>
    } @else if (error()) {
      <div class="card empty-state error"><mat-icon class="empty-ic">error_outline</mat-icon><p>{{ error() }}</p></div>
    } @else {
      <div class="card table-card">
        <table class="role-table">
          <thead>
            <tr><th>Papel</th><th>Descrição</th><th>Tipo</th><th>Permissões</th><th></th></tr>
          </thead>
          <tbody>
            @for (r of roles(); track r.id) {
              <tr>
                <td class="mono title-cell">{{ r.name }}</td>
                <td class="muted">{{ r.description || '—' }}</td>
                <td><span class="status-tag" [class.on]="!r.isSystem">{{ r.isSystem ? 'Sistema' : 'Personalizado' }}</span></td>
                <td class="muted">{{ r.permissions.length }}</td>
                <td class="actions">
                  <button class="icon-btn" [title]="r.isSystem ? 'Ver' : 'Editar'" (click)="open(r)">
                    <mat-icon>{{ r.isSystem ? 'visibility' : 'edit' }}</mat-icon>
                  </button>
                  @if (!r.isSystem) {
                    <button class="icon-btn danger" title="Excluir" (click)="remove(r)"><mat-icon>delete_outline</mat-icon></button>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
    @if (actionError()) { <p class="inline-error">{{ actionError() }}</p> }
  `,
  styles: [`
    :host { display: block; }
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .page-title { margin: 0; font-size: 1.5rem; font-weight: 600; color: var(--text); }
    .notice {
      display: flex; align-items: center; gap: 8px; padding: 10px 14px; margin-bottom: 16px; border-radius: var(--radius-s);
      background: var(--warning-soft); color: var(--warning); font-size: 12.5px;
      mat-icon { font-size: 18px; width: 18px; height: 18px; flex: none; }
    }
    .btn-primary, .btn-secondary {
      display: inline-flex; align-items: center; gap: 6px; height: 38px; padding: 0 18px;
      border-radius: var(--radius-s); font-weight: 500; font-size: 13px; text-decoration: none; cursor: pointer;
      mat-icon { font-size: 18px; width: 18px; height: 18px; }
    }
    .btn-primary { background: var(--accent); color: #fff; border: none; &:hover:not(:disabled) { filter: brightness(1.08); } &:disabled { opacity: 0.5; cursor: default; } }
    .btn-secondary { background: var(--surface); color: var(--text); border: 1px solid var(--border); &:hover { background: var(--surface-2); } }
    .form-card { padding: 20px; margin-bottom: 16px; display: flex; flex-direction: column; gap: 16px; }
    .form-title { margin: 0; font-size: 15px; font-weight: 600; color: var(--text); }
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 16px; }
    .field { display: flex; flex-direction: column; gap: 6px; }
    .label { font-size: 12.5px; font-weight: 600; color: var(--text-muted); }
    .input {
      min-height: 36px; padding: 6px 12px; border-radius: var(--radius-s); border: 1px solid var(--border);
      background: var(--surface-2); color: var(--text); font-size: 13px; font-family: inherit; outline: none;
      &:focus { border-color: var(--accent); }
      &:disabled { opacity: 0.7; }
    }
    .perm-groups { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 12px; max-height: 340px; overflow-y: auto; }
    .perm-group { border: 1px solid var(--border); border-radius: var(--radius-s); padding: 8px 12px; margin: 0; min-width: 0;
      legend { font-size: 11.5px; font-weight: 600; letter-spacing: 0.03em; text-transform: uppercase; color: var(--text-faint); padding: 0 6px; } }
    .perm { display: flex; align-items: center; gap: 8px; padding: 3px 0; font-size: 12px; color: var(--text); cursor: pointer; }
    .form-actions { display: flex; justify-content: flex-end; gap: 8px; }
    .table-card { padding: 0; overflow-x: auto; }
    .role-table {
      width: 100%; border-collapse: collapse; font-size: 13px;
      th { text-align: left; padding: 12px 16px; font-size: 11.5px; font-weight: 600; letter-spacing: 0.03em;
           text-transform: uppercase; color: var(--text-faint); border-bottom: 1px solid var(--border); white-space: nowrap; }
      td { padding: 12px 16px; border-bottom: 1px solid var(--border); color: var(--text); }
      tr:last-child td { border-bottom: none; }
    }
    .title-cell { white-space: nowrap; }
    .muted { color: var(--text-faint); }
    .actions { text-align: right; white-space: nowrap; }
    .icon-btn {
      width: 30px; height: 30px; border-radius: 8px; border: 1px solid var(--border); background: var(--surface);
      color: var(--text-muted); cursor: pointer; display: inline-flex; align-items: center; justify-content: center; margin-left: 4px;
      mat-icon { font-size: 18px; width: 18px; height: 18px; }
      &:hover { background: var(--surface-2); }
      &.danger:hover { color: var(--critical); background: var(--critical-soft); }
    }
    .status-tag {
      display: inline-flex; align-items: center; height: 22px; padding: 0 10px; border-radius: 999px;
      font-size: 11.5px; font-weight: 600; background: var(--surface-2); color: var(--text-muted);
      &.on { background: var(--success-soft); color: var(--success); }
    }
    .inline-error { color: var(--critical); font-size: 13px; margin: 8px 0 0; }
    .empty-state {
      display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px;
      padding: 48px 16px; color: var(--text-faint); font-size: 13px;
      &.error { color: var(--critical); }
    }
    .empty-ic { font-size: 36px; width: 36px; height: 36px; color: inherit; }
  `]
})
export class RoleManagementComponent implements OnInit {
  roles = signal<RoleDto[]>([]);
  permissions = signal<PermissionDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  actionError = signal<string | null>(null);

  showForm = signal(false);
  saving = signal(false);
  formError = signal<string | null>(null);
  editingId = signal<string | null>(null);
  readOnly = signal(false);
  name = signal('');
  description = signal('');
  selected = signal<Set<string>>(new Set());

  groups = computed<PermissionGroup[]>(() => {
    const byCategory = new Map<string, PermissionDto[]>();
    for (const p of this.permissions()) {
      const key = p.category || 'OUTROS';
      byCategory.set(key, [...(byCategory.get(key) ?? []), p]);
    }
    return [...byCategory.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([category, items]) => ({ category, items: items.sort((x, y) => x.permissionKey.localeCompare(y.permissionKey)) }));
  });

  constructor(private roleService: RoleService) {}

  ngOnInit(): void {
    this.roleService.listPermissions().subscribe({ next: p => this.permissions.set(p), error: () => {} });
    this.load();
  }

  openCreate(): void {
    this.editingId.set(null);
    this.readOnly.set(false);
    this.name.set('');
    this.description.set('');
    this.selected.set(new Set());
    this.formError.set(null);
    this.showForm.set(true);
  }

  open(role: RoleDto): void {
    this.editingId.set(role.id);
    this.readOnly.set(role.isSystem);
    this.name.set(role.name);
    this.description.set(role.description ?? '');
    this.selected.set(new Set(role.permissions));
    this.formError.set(null);
    this.showForm.set(true);
  }

  toggle(key: string, checked: boolean): void {
    this.selected.update(current => {
      const next = new Set(current);
      if (checked) next.add(key); else next.delete(key);
      return next;
    });
  }

  submit(event: Event): void {
    event.preventDefault();
    if (this.readOnly()) return;
    if (!this.editingId() && !this.name().trim()) {
      this.formError.set('Informe o nome do papel.');
      return;
    }
    const body = { description: this.description().trim(), permissions: [...this.selected()] };
    const id = this.editingId();
    this.saving.set(true);
    this.formError.set(null);
    const request$ = id
      ? this.roleService.updateRole(id, body)
      : this.roleService.createRole({ ...body, name: this.name().trim() });
    request$.subscribe({
      next: () => {
        this.saving.set(false);
        this.showForm.set(false);
        this.load();
      },
      error: err => {
        this.saving.set(false);
        this.formError.set(err?.error?.detail ?? err?.error?.message ?? 'Não foi possível salvar o papel.');
      }
    });
  }

  remove(role: RoleDto): void {
    if (!confirm(`Excluir o papel "${role.name}"?`)) return;
    this.actionError.set(null);
    this.roleService.deleteRole(role.id).subscribe({
      next: () => this.load(),
      error: err => this.actionError.set(err?.error?.detail ?? err?.error?.message ?? 'Não foi possível excluir o papel.')
    });
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.roleService.listRoles().subscribe({
      next: list => { this.roles.set(list); this.loading.set(false); },
      error: () => { this.error.set('Não foi possível carregar os papéis.'); this.loading.set(false); }
    });
  }
}
