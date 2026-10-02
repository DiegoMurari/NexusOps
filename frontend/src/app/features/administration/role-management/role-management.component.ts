import { Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { RoleService } from '../../../core/iam/role.service';
import type { PermissionDto, RoleDto } from '../../../core/iam/role.service';
import {
  ButtonComponent, DataTableComponent, EmptyStateComponent, NoticeComponent, NxCellDirective, NxColumn,
  PageHeaderComponent, StatusBadgeComponent,
} from '../../../shared/components';

interface PermissionGroup {
  category: string;
  items: PermissionDto[];
}

@Component({
  selector: 'app-role-management',
  standalone: true,
  imports: [
    CommonModule, MatIconModule, ButtonComponent, DataTableComponent, EmptyStateComponent, NoticeComponent,
    NxCellDirective, PageHeaderComponent, StatusBadgeComponent,
  ],
  template: `
    <nx-page-header heading="Gerenciamento de papéis">
      <button nxButton variant="primary" (click)="openCreate()">
        <mat-icon>add</mat-icon>
        Novo papel
      </button>
    </nx-page-header>

    <nx-notice tone="info">Papéis do sistema são somente leitura. Em papéis personalizados você só pode conceder permissões que você mesmo possui.</nx-notice>

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

        @if (formError()) { <p class="inline-error" role="alert">{{ formError() }}</p> }
        <div class="form-actions">
          <button nxButton type="button" (click)="showForm.set(false)">{{ readOnly() ? 'Fechar' : 'Cancelar' }}</button>
          @if (!readOnly()) {
            <button nxButton variant="primary" type="submit" [loading]="saving()" [disabled]="saving()">{{ saving() ? 'Salvando…' : 'Salvar' }}</button>
          }
        </div>
      </form>
    }

    @if (actionError()) { <nx-notice tone="critical">{{ actionError() }}</nx-notice> }

    @if (error()) {
      <nx-empty-state variant="error" [heading]="error()!" />
    } @else {
      <nx-data-table caption="Papéis de acesso" [columns]="columns" [rows]="roles()" [loading]="loading()" emptyTitle="Nenhum papel encontrado">
        <ng-template nxCell="description" let-r>{{ r.description || '—' }}</ng-template>
        <ng-template nxCell="type" let-r>
          <nx-status-badge [tone]="r.isSystem ? 'neutral' : 'info'">{{ r.isSystem ? 'Sistema' : 'Personalizado' }}</nx-status-badge>
        </ng-template>
        <ng-template nxCell="permissions" let-r>{{ r.permissions.length }}</ng-template>
        <ng-template nxCell="actions" let-r>
          <button nxButton variant="icon" size="sm" type="button"
                  [attr.aria-label]="(r.isSystem ? 'Ver ' : 'Editar ') + r.name" [title]="r.isSystem ? 'Ver' : 'Editar'" (click)="open(r)">
            <mat-icon>{{ r.isSystem ? 'visibility' : 'edit' }}</mat-icon>
          </button>
          @if (!r.isSystem) {
            <button nxButton variant="icon-danger" size="sm" type="button" [attr.aria-label]="'Excluir ' + r.name" title="Excluir" (click)="remove(r)">
              <mat-icon>delete_outline</mat-icon>
            </button>
          }
        </ng-template>
      </nx-data-table>
    }
  `,
  styles: [`
    :host { display: block; }
    .form-card { padding: var(--sp-7); margin-bottom: var(--sp-6); display: flex; flex-direction: column; gap: var(--sp-6); }
    .form-title { margin: 0; font-size: var(--fs-lg); line-height: 24px; font-weight: var(--fw-semibold); color: var(--text); }
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: var(--sp-6); }
    .inline-error { margin: 0; }
    .perm-groups { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: var(--sp-5); max-height: 340px; overflow-y: auto; }
    .perm-group { border: 1px solid var(--border); border-radius: var(--radius-s); padding: var(--sp-4) var(--sp-5); margin: 0; min-width: 0;
      legend { font-size: var(--fs-xs); font-weight: var(--fw-semibold); letter-spacing: .04em; text-transform: uppercase; color: var(--text-muted); padding: 0 var(--sp-3); } }
    .perm { display: flex; align-items: center; gap: var(--sp-4); padding: var(--sp-2) 0; font-size: var(--fs-sm); color: var(--text); cursor: pointer; }
  `]
})
export class RoleManagementComponent implements OnInit {
  readonly columns: NxColumn[] = [
    { key: 'name', header: 'Papel', rowHeader: true, mono: true },
    { key: 'description', header: 'Descrição', muted: true },
    { key: 'type', header: 'Tipo' },
    { key: 'permissions', header: 'Permissões', muted: true },
    { key: 'actions', header: 'Ações', align: 'end', hideHeader: true },
  ];

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
