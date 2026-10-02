import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { forkJoin } from 'rxjs';
import { CatalogService, QueueDto, QueueMemberDto, QueueMemberRole } from '../../../core/catalog/catalog.service';
import { UserAdminService, UserResponse } from '../../../core/iam/user.service';
import {
  ButtonComponent, DataTableComponent, EmptyStateComponent, NxCellDirective, NxColumn, PageHeaderComponent,
  StatusBadgeComponent,
} from '../../../shared/components';

@Component({
  selector: 'app-queue-management',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatIconModule, ButtonComponent, DataTableComponent, EmptyStateComponent,
    NxCellDirective, PageHeaderComponent, StatusBadgeComponent,
  ],
  template: `
    <nx-page-header heading="Filas de atendimento">
      <button nxButton variant="primary" (click)="startCreate()">
        <mat-icon>add</mat-icon>
        Nova fila
      </button>
    </nx-page-header>

    <p class="intro">
      Fila é a equipe que atende um tipo de demanda. O responsável de um chamado é uma pessoa e não depende da fila:
      mover o chamado de fila não troca quem o assumiu.
    </p>

    @if (formOpen()) {
      <form class="card form" (ngSubmit)="save()" [attr.aria-label]="editingId() ? 'Editar fila' : 'Nova fila'">
        <h2 class="form-title">{{ editingId() ? 'Editar fila' : 'Nova fila' }}</h2>
        <div class="field-row">
          <label class="field">
            <span class="label">Nome *</span>
            <input class="input" name="name" type="text" [(ngModel)]="form.name" required placeholder="Ex.: Rede e Telecom" />
          </label>
          <label class="field">
            <span class="label">Código *</span>
            <input class="input mono" name="code" type="text" [(ngModel)]="form.code" required placeholder="REDE" />
          </label>
        </div>
        <label class="field">
          <span class="label">Descrição</span>
          <input class="input" name="description" type="text" [(ngModel)]="form.description" />
        </label>
        @if (editingId()) {
          <label class="check"><input type="checkbox" name="active" [(ngModel)]="form.active" /> Fila ativa</label>
        }
        @if (formError()) {
          <p class="inline-error" role="alert">{{ formError() }}</p>
        }
        <div class="form-actions">
          <button nxButton type="button" (click)="closeForm()">Cancelar</button>
          <button nxButton variant="primary" type="submit" [loading]="saving()"
                  [disabled]="!form.name.trim() || !form.code.trim() || saving()">
            <mat-icon>check</mat-icon>
            {{ editingId() ? 'Salvar' : 'Criar' }}
          </button>
        </div>

        @if (editingId()) {
          <section class="members" aria-labelledby="members-title">
            <h3 id="members-title" class="members-title">Membros da fila</h3>
            @if (members().length === 0) {
              <p class="muted">Nenhum membro ainda. Quem não é membro ainda pode assumir chamados desta fila.</p>
            } @else {
              <ul class="member-list">
                @for (m of members(); track m.userId) {
                  <li class="member">
                    <span class="m-name">{{ m.name }}</span>
                    <span class="m-mail mono">{{ m.email }}</span>
                    @if (!m.userActive) { <nx-status-badge tone="neutral">Inativo</nx-status-badge> }
                    <select class="input m-role" [ngModel]="m.role" [ngModelOptions]="{standalone: true}"
                            (ngModelChange)="changeRole(m, $event)" [attr.aria-label]="'Papel de ' + m.name">
                      <option value="MEMBER">Membro</option>
                      <option value="LEAD">Líder</option>
                    </select>
                    <button nxButton type="button" (click)="removeMember(m)" [attr.aria-label]="'Remover ' + m.name">
                      <mat-icon>close</mat-icon>
                    </button>
                  </li>
                }
              </ul>
            }
            <div class="add-member">
              <select class="input" [(ngModel)]="newMemberId" [ngModelOptions]="{standalone: true}" aria-label="Usuário a adicionar">
                <option value="">Adicionar usuário…</option>
                @for (u of candidates(); track u.id) {
                  <option [value]="u.id">{{ u.firstName }} {{ u.lastName }} ({{ u.email }})</option>
                }
              </select>
              <select class="input" [(ngModel)]="newMemberRole" [ngModelOptions]="{standalone: true}" aria-label="Papel">
                <option value="MEMBER">Membro</option>
                <option value="LEAD">Líder</option>
              </select>
              <button nxButton type="button" [disabled]="!newMemberId" (click)="addMember()">
                <mat-icon>person_add</mat-icon>
                Adicionar
              </button>
            </div>
          </section>
        }
      </form>
    }

    @if (error()) {
      <nx-empty-state variant="error" [heading]="error()!" />
    } @else {
      <nx-data-table caption="Filas de atendimento" [columns]="columns" [rows]="queues()" [loading]="loading()"
                     [rowClickable]="true" [selectedRow]="selected()" (rowActivate)="startEdit($any($event))"
                     emptyTitle="Nenhuma fila cadastrada" emptyDescription="Crie a primeira fila para rotear chamados.">
        <ng-template nxCell="active" let-q>
          <nx-status-badge [tone]="q.active ? 'success' : 'neutral'">{{ q.active ? 'Ativa' : 'Inativa' }}</nx-status-badge>
        </ng-template>
      </nx-data-table>
    }
  `,
  styles: [`
    :host { display: block; }
    .intro { margin: 0 0 var(--sp-6); max-width: 70ch; color: var(--text-muted); font-size: var(--fs-sm); }
    .form { display: flex; flex-direction: column; gap: var(--sp-5); margin-bottom: var(--sp-7); }
    .form-title { margin: 0; font-size: 1rem; font-weight: 600; }
    .field-row { display: grid; grid-template-columns: 2fr 1fr; gap: var(--sp-6); }
    .mono { font-family: var(--font-mono); }
    .check { display: flex; gap: var(--sp-3); align-items: center; }
    .inline-error { margin: 0; }
    .members { border-top: 1px solid var(--border); padding-top: var(--sp-5); display: flex; flex-direction: column; gap: var(--sp-4); }
    .members-title { margin: 0; font-size: var(--fs-sm); font-weight: 600; }
    .muted { margin: 0; color: var(--text-muted); font-size: var(--fs-sm); }
    .member-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
    .member { display: flex; align-items: center; gap: var(--sp-4); padding: var(--sp-3) 0; border-bottom: 1px solid var(--border); }
    .m-name { font-weight: 500; }
    .m-mail { color: var(--text-muted); font-size: var(--fs-sm); }
    .m-role { margin-left: auto; width: auto; }
    .add-member { display: flex; gap: var(--sp-4); flex-wrap: wrap; }
    .add-member select:first-child { flex: 1 1 260px; }
  `]
})
export class QueueManagementComponent implements OnInit {
  readonly columns: NxColumn[] = [
    { key: 'name', header: 'Nome', rowHeader: true },
    { key: 'code', header: 'Código', mono: true },
    { key: 'description', header: 'Descrição', muted: true },
    { key: 'memberCount', header: 'Membros', mono: true },
    { key: 'active', header: 'Status' },
  ];

  queues = signal<QueueDto[]>([]);
  members = signal<QueueMemberDto[]>([]);
  users = signal<UserResponse[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  formOpen = signal(false);
  saving = signal(false);
  formError = signal<string | null>(null);
  editingId = signal<string | null>(null);
  selected = signal<QueueDto | null>(null);

  form = { name: '', code: '', description: '', active: true };
  newMemberId = '';
  newMemberRole: QueueMemberRole = 'MEMBER';

  constructor(private catalog: CatalogService, private userAdmin: UserAdminService) {}

  ngOnInit(): void {
    this.load();
  }

  /** Usuários ativos que ainda não são membros da fila aberta. */
  candidates(): UserResponse[] {
    const taken = new Set(this.members().map(m => m.userId));
    return this.users().filter(u => u.status === 'ACTIVE' && !taken.has(u.id));
  }

  startCreate(): void {
    this.editingId.set(null);
    this.selected.set(null);
    this.members.set([]);
    this.form = { name: '', code: '', description: '', active: true };
    this.formError.set(null);
    this.formOpen.set(true);
  }

  startEdit(q: QueueDto): void {
    this.editingId.set(q.id);
    this.selected.set(q);
    this.form = { name: q.name, code: q.code, description: q.description ?? '', active: q.active };
    this.formError.set(null);
    this.formOpen.set(true);
    this.loadMembers(q.id);
  }

  closeForm(): void {
    this.formOpen.set(false);
    this.editingId.set(null);
    this.selected.set(null);
  }

  save(): void {
    if (this.saving()) return;
    this.saving.set(true);
    this.formError.set(null);
    const id = this.editingId();
    const f = this.form;
    const request$ = id
      ? this.catalog.updateQueue(id, { name: f.name.trim(), code: f.code.trim(), description: f.description, active: f.active })
      : this.catalog.createQueue({ name: f.name.trim(), code: f.code.trim(), description: f.description || undefined });
    request$.subscribe({
      next: q => {
        this.queues.update(list => id ? list.map(x => x.id === q.id ? q : x) : [...list, q].sort((a, b) => a.name.localeCompare(b.name)));
        this.saving.set(false);
        if (id) {
          this.selected.set(q);
        } else {
          // Depois de criar, abre a fila para já adicionar os membros.
          this.startEdit(q);
        }
      },
      error: (e: HttpErrorResponse) => this.fail(e, 'Não foi possível salvar a fila.'),
    });
  }

  addMember(): void {
    const id = this.editingId();
    if (!id || !this.newMemberId) return;
    this.catalog.putQueueMember(id, this.newMemberId, this.newMemberRole).subscribe({
      next: () => {
        this.newMemberId = '';
        this.newMemberRole = 'MEMBER';
        this.refreshAfterMemberChange(id);
      },
      error: (e: HttpErrorResponse) => this.fail(e, 'Não foi possível adicionar o membro.'),
    });
  }

  changeRole(m: QueueMemberDto, role: QueueMemberRole): void {
    const id = this.editingId();
    if (!id || role === m.role) return;
    this.catalog.putQueueMember(id, m.userId, role).subscribe({
      next: () => this.refreshAfterMemberChange(id),
      error: (e: HttpErrorResponse) => this.fail(e, 'Não foi possível mudar o papel.'),
    });
  }

  removeMember(m: QueueMemberDto): void {
    const id = this.editingId();
    if (!id) return;
    this.catalog.removeQueueMember(id, m.userId).subscribe({
      next: () => this.refreshAfterMemberChange(id),
      error: (e: HttpErrorResponse) => this.fail(e, 'Não foi possível remover o membro.'),
    });
  }

  private refreshAfterMemberChange(queueId: string): void {
    this.formError.set(null);
    this.loadMembers(queueId);
    this.catalog.listQueues().subscribe(list => this.queues.set(list));
  }

  private loadMembers(queueId: string): void {
    this.catalog.queueMembers(queueId).subscribe({
      next: m => this.members.set(m),
      error: () => this.formError.set('Não foi possível carregar os membros.'),
    });
  }

  private fail(e: HttpErrorResponse, fallback: string): void {
    this.saving.set(false);
    const detail = e.error?.detail;
    this.formError.set(e.status === 422 && typeof detail === 'string' && detail !== 'Request validation failed' ? detail : fallback);
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({ queues: this.catalog.listQueues(), users: this.userAdmin.list() }).subscribe({
      next: ({ queues, users }) => {
        this.queues.set(queues);
        this.users.set(users);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Não foi possível carregar as filas.');
        this.loading.set(false);
      },
    });
  }
}
