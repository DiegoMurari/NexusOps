import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PlatformService, TenantDto } from '../../../core/platform/platform.service';
import {
  DataTableComponent, EmptyStateComponent, NxCellDirective, NxColumn, PageHeaderComponent, StatusBadgeComponent,
  Tone,
} from '../../../shared/components';

@Component({
  selector: 'app-tenant-management',
  standalone: true,
  imports: [CommonModule, DataTableComponent, EmptyStateComponent, NxCellDirective, PageHeaderComponent, StatusBadgeComponent],
  template: `
    <nx-page-header heading="Gerenciamento de tenants" />

    @if (error()) {
      <nx-empty-state variant="error" [heading]="error()!" />
    } @else {
      <nx-data-table caption="Tenants cadastrados" [columns]="columns" [rows]="tenants()" [loading]="loading()"
                     emptyTitle="Nenhum tenant cadastrado">
        <ng-template nxCell="status" let-t>
          <nx-status-badge [tone]="statusTone(t.status)">{{ statusLabel(t.status) }}</nx-status-badge>
        </ng-template>
      </nx-data-table>
    }
  `,
  styles: [`:host { display: block; }`]
})
export class TenantManagementComponent implements OnInit {
  readonly columns: NxColumn[] = [
    { key: 'name', header: 'Nome', rowHeader: true },
    { key: 'domain', header: 'Domínio', mono: true, muted: true },
    { key: 'subscriptionTier', header: 'Plano' },
    { key: 'maxUsers', header: 'Usuários', mono: true },
    { key: 'maxAssets', header: 'Assets', mono: true },
    { key: 'status', header: 'Status' },
  ];

  tenants = signal<TenantDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  constructor(private platformService: PlatformService) {}

  statusTone(status: string): Tone {
    return status === 'ACTIVE' ? 'success' : status === 'SUSPENDED' ? 'warning' : 'critical';
  }

  statusLabel(status: string): string {
    return status === 'ACTIVE' ? 'Ativo' : status === 'SUSPENDED' ? 'Suspenso' : 'Cancelado';
  }

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
