import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { UserAdminService, UserResponse } from '../../../core/iam/user.service';
import {
  DataTableComponent, EmptyStateComponent, NxCellDirective, NxColumn, PageHeaderComponent, StatusBadgeComponent,
  Tone,
} from '../../../shared/components';

@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [
    CommonModule, MatIconModule, DataTableComponent, EmptyStateComponent, NxCellDirective, PageHeaderComponent,
    StatusBadgeComponent,
  ],
  template: `
    <nx-page-header heading="Gerenciamento de usuários" />

    @if (error()) {
      <nx-empty-state variant="error" [heading]="error()!" />
    } @else {
      <nx-data-table caption="Usuários cadastrados" [columns]="columns" [rows]="users()" [loading]="loading()"
                     emptyTitle="Nenhum usuário cadastrado">
        <ng-template nxCell="name" let-u>{{ u.firstName }} {{ u.lastName }}</ng-template>
        <ng-template nxCell="roles" let-u>
          <span class="roles">
            @for (role of u.roles; track role) { <nx-status-badge>{{ role }}</nx-status-badge> }
          </span>
        </ng-template>
        <ng-template nxCell="mfaEnabled" let-u>
          <span class="mfa">
            <mat-icon aria-hidden="true">{{ u.mfaEnabled ? 'lock' : 'lock_open' }}</mat-icon>
            {{ u.mfaEnabled ? 'Ativo' : 'Desativado' }}
          </span>
        </ng-template>
        <ng-template nxCell="lastLoginAt" let-u>{{ u.lastLoginAt ? (u.lastLoginAt | date:'dd/MM/yyyy HH:mm') : 'Nunca' }}</ng-template>
        <ng-template nxCell="status" let-u>
          <nx-status-badge [tone]="statusTone(u.status)">{{ statusLabel(u.status) }}</nx-status-badge>
        </ng-template>
      </nx-data-table>
    }
  `,
  styles: [`
    :host { display: block; }
    .roles { display: inline-flex; flex-wrap: wrap; gap: var(--sp-2); }
    .mfa { display: inline-flex; align-items: center; gap: var(--sp-3); color: var(--text-muted); }
    .mfa mat-icon { width: 16px; height: 16px; font-size: 16px; }
  `]
})
export class UserManagementComponent implements OnInit {
  readonly columns: NxColumn[] = [
    { key: 'name', header: 'Nome', rowHeader: true },
    { key: 'email', header: 'Email', mono: true, muted: true },
    { key: 'roles', header: 'Funções' },
    { key: 'mfaEnabled', header: 'MFA' },
    { key: 'lastLoginAt', header: 'Último login', mono: true, muted: true },
    { key: 'status', header: 'Status' },
  ];

  users = signal<UserResponse[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  constructor(private userAdminService: UserAdminService) {}

  statusTone(status: string): Tone {
    return status === 'ACTIVE' ? 'success' : status === 'LOCKED' ? 'critical' : 'neutral';
  }

  statusLabel(status: string): string {
    return status === 'ACTIVE' ? 'Ativo' : status === 'LOCKED' ? 'Bloqueado' : 'Inativo';
  }

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
