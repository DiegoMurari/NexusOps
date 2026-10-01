import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { PlatformService, SystemSettingDto } from '../../../core/platform/platform.service';

@Component({
  selector: 'app-system-settings',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  template: `
    <div class="page-header">
      <h1 class="page-title">Configurações do Sistema</h1>
    </div>

    @if (loading()) {
      <div class="card empty-state">
        <mat-icon class="empty-ic">hourglass_empty</mat-icon>
        <p class="empty-text">Carregando configurações…</p>
      </div>
    } @else if (error()) {
      <div class="card empty-state error">
        <mat-icon class="empty-ic">error_outline</mat-icon>
        <p class="empty-text">{{ error() }}</p>
      </div>
    } @else if (settings().length === 0) {
      <div class="card empty-state">
        <mat-icon class="empty-ic">settings</mat-icon>
        <p class="empty-text">Nenhuma configuração cadastrada</p>
      </div>
    } @else {
      <div class="card table-card">
        <table class="settings-table">
          <thead>
            <tr>
              <th>Chave</th>
              <th>Valor</th>
              <th>Tipo</th>
              <th>Categoria</th>
              <th>Visibilidade</th>
              <th>Atualizado em</th>
            </tr>
          </thead>
          <tbody>
            @for (s of settings(); track s.id) {
              <tr>
                <td class="mono">{{ s.settingKey }}</td>
                <td class="mono value-cell">{{ s.value }}</td>
                <td>{{ s.valueType }}</td>
                <td>{{ s.category || '—' }}</td>
                <td>
                  <span class="status-tag" [class]="s.public ? 'resolved' : 'closed'">
                    {{ s.public ? 'Pública' : 'Privada' }}
                  </span>
                </td>
                <td class="mono muted">{{ s.updatedAt | date:'dd/MM/yyyy HH:mm' }}</td>
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

    .settings-table {
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

    .value-cell {
      max-width: 320px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
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
export class SystemSettingsComponent implements OnInit {
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
