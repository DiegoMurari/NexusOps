import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { PlatformService, TenantDto } from '../../../core/platform/platform.service';

@Component({
  selector: 'app-tenant-management',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  template: `
    <div class="page-header">
      <h1 class="page-title">Gerenciamento de Tenants</h1>
    </div>

    @if (loading()) {
      <div class="card empty-state">
        <mat-icon class="empty-ic">hourglass_empty</mat-icon>
        <p class="empty-text">Carregando tenants…</p>
      </div>
    } @else if (error()) {
      <div class="card empty-state error">
        <mat-icon class="empty-ic">error_outline</mat-icon>
        <p class="empty-text">{{ error() }}</p>
      </div>
    } @else if (tenants().length === 0) {
      <div class="card empty-state">
        <mat-icon class="empty-ic">apartment</mat-icon>
        <p class="empty-text">Nenhum tenant cadastrado</p>
      </div>
    } @else {
      <div class="card table-card">
        <table class="tenant-table">
          <thead>
            <tr>
              <th>Nome</th>
              <th>Domínio</th>
              <th>Plano</th>
              <th>Usuários</th>
              <th>Assets</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            @for (tenant of tenants(); track tenant.id) {
              <tr>
                <td>{{ tenant.name }}</td>
                <td class="mono muted">{{ tenant.domain }}</td>
                <td>{{ tenant.subscriptionTier }}</td>
                <td class="mono">{{ tenant.maxUsers }}</td>
                <td class="mono">{{ tenant.maxAssets }}</td>
                <td>
                  <span class="status-tag" [class]="tenant.status === 'ACTIVE' ? 'resolved' : 'reopened'">
                    {{ tenant.status === 'ACTIVE' ? 'Ativo' : tenant.status === 'SUSPENDED' ? 'Suspenso' : 'Cancelado' }}
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

    .tenant-table {
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
export class TenantManagementComponent implements OnInit {
  tenants = signal<TenantDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  constructor(private platformService: PlatformService) {}

  ngOnInit(): void {
    this.platformService.listTenants().subscribe({
      next: tenants => {
        this.tenants.set(tenants);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Não foi possível carregar os tenants.');
        this.loading.set(false);
      }
    });
  }
}
