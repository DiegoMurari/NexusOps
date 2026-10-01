import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { AssetService, AssetDto, AssetType, LifecycleStatus } from '../../../core/asset/asset.service';

const TYPE_LABELS: Record<AssetType, string> = {
  HARDWARE: 'Hardware',
  SOFTWARE: 'Software',
  CLOUD: 'Nuvem',
  VIRTUAL: 'Virtual',
  NETWORK: 'Rede',
  STORAGE: 'Armazenamento',
  PERIPHERAL: 'Periférico',
};

const STATUS_LABELS: Record<LifecycleStatus, string> = {
  PROCURED: 'Adquirido',
  DEPLOYED: 'Em uso',
  MAINTENANCE: 'Manutenção',
  RETIRED: 'Desativado',
  DISPOSED: 'Descartado',
  LOST: 'Perdido',
  STOLEN: 'Roubado',
};

const STATUS_FILTERS: { value: LifecycleStatus | null; label: string }[] = [
  { value: null, label: 'Todos' },
  { value: 'PROCURED', label: 'Adquirido' },
  { value: 'DEPLOYED', label: 'Em uso' },
  { value: 'MAINTENANCE', label: 'Manutenção' },
  { value: 'RETIRED', label: 'Desativado' },
  { value: 'DISPOSED', label: 'Descartado' },
];

@Component({
  selector: 'app-asset-grid',
  standalone: true,
  imports: [CommonModule, RouterLink, MatIconModule],
  template: `
    <div class="page-header">
      <h1 class="page-title">Assets</h1>
      <a class="btn-primary" routerLink="new">
        <mat-icon>add</mat-icon>
        <span>Novo Asset</span>
      </a>
    </div>

    <div class="toolbar">
      <div class="filters">
        @for (f of statusFilters; track f.label) {
          <button
            class="filter-chip"
            [class.active]="activeStatus() === f.value"
            (click)="setStatus(f.value)"
          >{{ f.label }}</button>
        }
      </div>
      <input
        class="search-input"
        type="text"
        placeholder="Buscar por nome, tag ou nº de série…"
        [value]="searchTerm()"
        (input)="onSearchInput($event)"
      />
    </div>

    @if (loading()) {
      <div class="card empty-state">
        <mat-icon class="empty-ic">hourglass_empty</mat-icon>
        <p>Carregando assets…</p>
      </div>
    } @else if (error()) {
      <div class="card empty-state error">
        <mat-icon class="empty-ic">error_outline</mat-icon>
        <p>{{ error() }}</p>
      </div>
    } @else if (assets().length === 0) {
      <div class="card empty-state">
        <mat-icon class="empty-ic">dns</mat-icon>
        <p>Nenhum asset encontrado</p>
      </div>
    } @else {
      <div class="card table-card">
        <table class="asset-table">
          <thead>
            <tr>
              <th>Tag</th>
              <th>Nome</th>
              <th>Tipo</th>
              <th>Status</th>
              <th>Fabricante</th>
              <th>Nº de série</th>
              <th>Criado em</th>
            </tr>
          </thead>
          <tbody>
            @for (asset of assets(); track asset.id) {
              <tr [routerLink]="[asset.id]" class="asset-row">
                <td class="mono">{{ asset.assetTag }}</td>
                <td class="title-cell">{{ asset.name }}</td>
                <td>{{ typeLabel(asset.type) }}</td>
                <td>
                  <span class="status-tag" [class]="asset.lifecycleStatus.toLowerCase()">{{ statusLabel(asset.lifecycleStatus) }}</span>
                </td>
                <td class="muted">{{ asset.manufacturer || '—' }}</td>
                <td class="mono muted">{{ asset.serialNumber || '—' }}</td>
                <td class="mono muted">{{ asset.createdAt | date:'dd/MM/yyyy HH:mm' }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>

      <div class="pagination">
        <button class="page-btn" [disabled]="page() === 0" (click)="prevPage()">
          <mat-icon>chevron_left</mat-icon>
        </button>
        <span class="page-info">Página {{ page() + 1 }} de {{ totalPages() || 1 }}</span>
        <button class="page-btn" [disabled]="page() + 1 >= totalPages()" (click)="nextPage()">
          <mat-icon>chevron_right</mat-icon>
        </button>
      </div>
    }
  `,
  styles: [`
    :host { display: block; }

    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
    }

    .page-title {
      margin: 0;
      font-size: 1.5rem;
      font-weight: 600;
      color: var(--text);
    }

    .btn-primary {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      height: 38px;
      padding: 0 18px;
      border-radius: var(--radius-s);
      background: var(--accent);
      color: #fff;
      font-weight: 500;
      font-size: 13px;
      text-decoration: none;

      mat-icon { font-size: 18px; width: 18px; height: 18px; }

      &:hover { filter: brightness(1.08); }
    }

    .toolbar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-wrap: wrap;
      gap: 12px;
      margin-bottom: 16px;
    }

    .filters {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .filter-chip {
      height: 30px;
      padding: 0 14px;
      border-radius: 999px;
      border: 1px solid var(--border);
      background: var(--surface);
      color: var(--text-muted);
      font-size: 12.5px;
      font-weight: 500;
      cursor: pointer;

      &:hover { background: var(--surface-2); }

      &.active {
        background: var(--accent-soft);
        border-color: var(--accent);
        color: var(--accent);
      }
    }

    .search-input {
      height: 34px;
      min-width: 260px;
      padding: 0 12px;
      border-radius: var(--radius-s);
      border: 1px solid var(--border);
      background: var(--surface-2);
      color: var(--text);
      font-size: 13px;
      outline: none;

      &:focus { border-color: var(--accent); }
      &::placeholder { color: var(--text-faint); }
    }

    .table-card {
      padding: 0;
      overflow-x: auto;
    }

    .asset-table {
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

    .asset-row {
      cursor: pointer;

      &:hover { background: var(--surface-2); }
    }

    .title-cell {
      max-width: 320px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .status-tag {
      display: inline-flex;
      align-items: center;
      height: 22px;
      padding: 0 10px;
      border-radius: 999px;
      font-size: 11.5px;
      font-weight: 600;
      background: var(--surface-2);
      color: var(--text-muted);

      &.deployed { background: var(--success-soft); color: var(--success); }
      &.procured { background: var(--accent-soft); color: var(--accent); }
      &.maintenance { background: var(--warning-soft); color: var(--warning); }
      &.retired, &.disposed, &.lost, &.stolen { background: var(--critical-soft); color: var(--critical); }
    }

    .muted { color: var(--text-faint); }

    .pagination {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 12px;
      margin-top: 16px;
    }

    .page-btn {
      width: 32px;
      height: 32px;
      border-radius: 8px;
      border: 1px solid var(--border);
      background: var(--surface);
      color: var(--text-muted);
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;

      &:hover:not(:disabled) { background: var(--surface-2); }
      &:disabled { opacity: 0.4; cursor: default; }
    }

    .page-info {
      font-size: 12.5px;
      color: var(--text-muted);
    }

    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 10px;
      padding: 56px 16px;
      color: var(--text-faint);
      font-size: 13px;

      &.error { color: var(--critical); }
    }

    .empty-ic {
      font-size: 36px;
      width: 36px;
      height: 36px;
      color: inherit;
    }
  `]
})
export class AssetGridComponent implements OnInit {
  assets = signal<AssetDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  activeStatus = signal<LifecycleStatus | null>(null);
  searchTerm = signal('');
  page = signal(0);
  totalPages = signal(0);

  statusFilters = STATUS_FILTERS;
  private searchDebounce: ReturnType<typeof setTimeout> | null = null;

  constructor(private assetService: AssetService) {}

  ngOnInit(): void {
    this.load();
  }

  setStatus(status: LifecycleStatus | null): void {
    this.activeStatus.set(status);
    this.page.set(0);
    this.load();
  }

  onSearchInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.searchTerm.set(value);
    if (this.searchDebounce) clearTimeout(this.searchDebounce);
    this.searchDebounce = setTimeout(() => {
      this.page.set(0);
      this.load();
    }, 350);
  }

  prevPage(): void {
    if (this.page() > 0) {
      this.page.update(p => p - 1);
      this.load();
    }
  }

  nextPage(): void {
    if (this.page() + 1 < this.totalPages()) {
      this.page.update(p => p + 1);
      this.load();
    }
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.assetService.list({
      status: this.activeStatus() ?? undefined,
      search: this.searchTerm() || undefined,
      page: this.page(),
    }).subscribe({
      next: res => {
        this.assets.set(res.content);
        this.totalPages.set(res.totalPages);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Não foi possível carregar os assets.');
        this.loading.set(false);
      }
    });
  }

  typeLabel(type: AssetType): string {
    return TYPE_LABELS[type] ?? type;
  }

  statusLabel(status: LifecycleStatus): string {
    return STATUS_LABELS[status] ?? status;
  }
}
