import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { UserAdminService, UserResponse } from '../../../core/iam/user.service';

@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  template: `
    <div class="page-header">
      <h1 class="page-title">Gerenciamento de Usuários</h1>
    </div>

    @if (loading()) {
      <div class="card empty-state">
        <mat-icon class="empty-ic">hourglass_empty</mat-icon>
        <p class="empty-text">Carregando usuários…</p>
      </div>
    } @else if (error()) {
      <div class="card empty-state error">
        <mat-icon class="empty-ic">error_outline</mat-icon>
        <p class="empty-text">{{ error() }}</p>
      </div>
    } @else if (users().length === 0) {
      <div class="card empty-state">
        <mat-icon class="empty-ic">group</mat-icon>
        <p class="empty-text">Nenhum usuário cadastrado</p>
      </div>
    } @else {
      <div class="card table-card">
        <table class="user-table">
          <thead>
            <tr>
              <th>Nome</th>
              <th>Email</th>
              <th>Funções</th>
              <th>MFA</th>
              <th>Último login</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            @for (u of users(); track u.id) {
              <tr>
                <td>{{ u.firstName }} {{ u.lastName }}</td>
                <td class="mono muted">{{ u.email }}</td>
                <td>
                  @for (role of u.roles; track role) {
                    <span class="role-chip">{{ role }}</span>
                  }
                </td>
                <td>
                  <mat-icon class="mfa-ic" [class.on]="u.mfaEnabled">{{ u.mfaEnabled ? 'lock' : 'lock_open' }}</mat-icon>
                </td>
                <td class="mono muted">{{ u.lastLoginAt ? (u.lastLoginAt | date:'dd/MM/yyyy HH:mm') : 'Nunca' }}</td>
                <td>
                  <span class="status-tag" [class]="u.status === 'ACTIVE' ? 'resolved' : u.status === 'LOCKED' ? 'reopened' : 'closed'">
                    {{ u.status === 'ACTIVE' ? 'Ativo' : u.status === 'LOCKED' ? 'Bloqueado' : 'Inativo' }}
                  </span>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
  styles: [`
    :host { display: block; }

    .page-header { margin-bottom: 20px; }

    .page-title {
      margin: 0;
      font-size: 1.5rem;
      font-weight: 600;
      color: var(--text);
    }

    .table-card {
      padding: 0;
      overflow-x: auto;
    }

    .user-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 13px;

      th {
        text-align: left;
        padding: 12px 16px;
        font-size: 11.5px;
        font-weight: 600;
        letter-spacing: 0.03em;
        text-transform: uppercase;
        color: var(--text-faint);
        border-bottom: 1px solid var(--border);
        white-space: nowrap;
      }

      td {
        padding: 12px 16px;
        border-bottom: 1px solid var(--border);
        color: var(--text);
      }

      tr:last-child td { border-bottom: none; }
    }

    .muted { color: var(--text-faint); }

    .role-chip {
      display: inline-block;
      font-size: 11px;
      font-weight: 600;
      padding: 2px 8px;
      border-radius: 6px;
      background: var(--accent-soft);
      color: var(--accent);
      margin-right: 4px;
    }

    .mfa-ic {
      font-size: 18px;
      width: 18px;
      height: 18px;
      color: var(--text-faint);

      &.on { color: var(--success); }
    }

    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 12px;
      padding: 64px 24px;
      text-align: center;

      &.error { color: var(--critical); }
    }

    .empty-ic {
      font-size: 40px;
      width: 40px;
      height: 40px;
      color: inherit;
    }

    .empty-text {
      margin: 0;
      color: var(--text-muted);
      font-size: 13.5px;
    }
  `]
})
export class UserManagementComponent implements OnInit {
  users = signal<UserResponse[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  constructor(private userAdminService: UserAdminService) {}

  ngOnInit(): void {
    this.userAdminService.list().subscribe({
      next: users => {
        this.users.set(users);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Não foi possível carregar os usuários.');
        this.loading.set(false);
      }
    });
  }
}
