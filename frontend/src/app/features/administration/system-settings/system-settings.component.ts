import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PlatformService, SystemSettingDto } from '../../../core/platform/platform.service';
import {
  DataTableComponent, EmptyStateComponent, NxCellDirective, NxColumn, PageHeaderComponent, StatusBadgeComponent,
} from '../../../shared/components';

@Component({
  selector: 'app-system-settings',
  standalone: true,
  imports: [CommonModule, DataTableComponent, EmptyStateComponent, NxCellDirective, PageHeaderComponent, StatusBadgeComponent],
  template: `
    <nx-page-header heading="Configurações do sistema" />

    @if (error()) {
      <nx-empty-state variant="error" [heading]="error()!" />
    } @else {
      <nx-data-table caption="Configurações do sistema" [columns]="columns" [rows]="settings()" [loading]="loading()"
                     emptyTitle="Nenhuma configuração cadastrada">
        <ng-template nxCell="category" let-s>{{ s.category || '—' }}</ng-template>
        <ng-template nxCell="public" let-s>
          <nx-status-badge [tone]="s.public ? 'info' : 'neutral'">{{ s.public ? 'Pública' : 'Privada' }}</nx-status-badge>
        </ng-template>
        <ng-template nxCell="updatedAt" let-s>{{ s.updatedAt | date:'dd/MM/yyyy HH:mm' }}</ng-template>
      </nx-data-table>
    }
  `,
  styles: [`:host { display: block; }`]
})
export class SystemSettingsComponent implements OnInit {
  readonly columns: NxColumn[] = [
    { key: 'settingKey', header: 'Chave', rowHeader: true, mono: true },
    { key: 'value', header: 'Valor', mono: true, maxWidth: '320px' },
    { key: 'valueType', header: 'Tipo' },
    { key: 'category', header: 'Categoria' },
    { key: 'public', header: 'Visibilidade' },
    { key: 'updatedAt', header: 'Atualizado em', mono: true, muted: true },
  ];

  settings = signal<SystemSettingDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  constructor(private platformService: PlatformService) {}

  ngOnInit(): void {
    this.platformService.listSettings().subscribe({
      next: settings => {
        this.settings.set(settings);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Não foi possível carregar as configurações.');
        this.loading.set(false);
      }
    });
  }
}
