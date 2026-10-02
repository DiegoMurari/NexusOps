import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router, RouterLink, ActivatedRoute } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { AssetService, AssetDto, AssetType, LifecycleStatus } from '../../../core/asset/asset.service';
import {
  DataTableComponent, NxCellDirective, NxColumn, StatusBadgeComponent, StatusGlyph, Tone,
} from '../../../shared/components';

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

const STATUS_TONE: Record<LifecycleStatus, Tone> = {
  PROCURED: 'info',
  DEPLOYED: 'success',
  MAINTENANCE: 'warning',
  RETIRED: 'neutral',
  DISPOSED: 'neutral',
  LOST: 'critical',
  STOLEN: 'critical',
};

const STATUS_GLYPH: Record<LifecycleStatus, StatusGlyph> = {
  PROCURED: 'open',
  DEPLOYED: 'done',
  MAINTENANCE: 'hold',
  RETIRED: 'closed',
  DISPOSED: 'closed',
  LOST: 'reopened',
  STOLEN: 'reopened',
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
  imports: [DatePipe, RouterLink, MatIconModule, DataTableComponent, NxCellDirective, StatusBadgeComponent],
  template: `
    <header class="head">
      <h1>Ativos</h1>
      <a class="nx-verb primary" routerLink="new"><mat-icon aria-hidden="true">add</mat-icon>Novo ativo</a>
    </header>

    <div class="toolbar">
      <div class="seg" role="group" aria-label="Filtrar por ciclo de vida">
        @for (f of statusFilters; track f.label) {
          <button type="button" [attr.aria-pressed]="activeStatus() === f.value" (click)="setStatus(f.value)">{{ f.label }}</button>
        }
      </div>
      <label class="sr-only" for="asset-search">Buscar ativos</label>
      <input id="asset-search" class="search" type="search" placeholder="Buscar por nome, tag ou nº de série…"
        [value]="searchTerm()" (input)="onSearchInput($event)" />
    </div>

    @if (error()) {
      <p class="inline-error" role="alert">{{ error() }}</p>
    }

    <nx-data-table
      caption="Ativos"
      [columns]="columns"
      [rows]="assets()"
      [loading]="loading()"
      [rowClickable]="true"
      [rowRail]="rail"
      emptyTitle="Nenhum ativo encontrado"
      (rowActivate)="open($any($event))"
    >
      <ng-template nxCell="assetTag" let-a>
        <a class="tag" [routerLink]="[a.id]">{{ a.assetTag }}</a>
      </ng-template>
      <ng-template nxCell="type" let-a>{{ typeLabel(a.type) }}</ng-template>
      <ng-template nxCell="lifecycleStatus" let-a>
        <nx-status-badge [tone]="tone(a.lifecycleStatus)" [glyph]="glyph(a.lifecycleStatus)">{{ statusLabel(a.lifecycleStatus) }}</nx-status-badge>
      </ng-template>
      <ng-template nxCell="createdAt" let-a>{{ a.createdAt | date:'dd/MM/yyyy HH:mm' }}</ng-template>
    </nx-data-table>

    @if (totalPages() > 1) {
      <nav class="pagination" aria-label="Paginação">
        <button type="button" class="page-btn" [disabled]="page() === 0" (click)="prevPage()" aria-label="Página anterior">
          <mat-icon aria-hidden="true">chevron_left</mat-icon>
        </button>
        <span class="page-info">Página {{ page() + 1 }} de {{ totalPages() }}</span>
        <button type="button" class="page-btn" [disabled]="page() + 1 >= totalPages()" (click)="nextPage()" aria-label="Próxima página">
          <mat-icon aria-hidden="true">chevron_right</mat-icon>
        </button>
      </nav>
    }
  `,
  styles: [`
    :host { display: grid; gap: var(--sp-6); }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
    .head { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-6); }
    h1 { margin: 0; font-size: var(--fs-xl); line-height: 28px; font-weight: var(--fw-semibold); }
    .nx-verb mat-icon { width: 16px; height: 16px; font-size: 16px; }

    .toolbar { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--sp-5); }
    .search {
      height: var(--control-h-md);
      min-width: 280px;
      padding: 0 var(--sp-5);
      font: var(--fw-regular) var(--fs-base) var(--sans);
      color: var(--text);
      background: var(--surface);
      border: 1px solid var(--border-strong);
      border-radius: var(--radius-s);
    }
    .search::placeholder { color: var(--text-faint); }
    .search:focus-visible { outline: var(--focus-ring); outline-offset: 1px; border-color: var(--accent); }

    .tag { font-family: var(--mono); font-size: var(--fs-sm); font-weight: var(--fw-medium); color: var(--accent); text-decoration: none; }
    .tag:hover { text-decoration: underline; }
    .inline-error { margin: 0; color: var(--critical); font-size: var(--fs-sm); }

    .pagination { display: flex; align-items: center; justify-content: center; gap: var(--sp-5); }
    .page-btn {
      display: grid; place-items: center;
      width: var(--control-h-md); height: var(--control-h-md);
      border: 1px solid var(--border-strong); border-radius: var(--radius-s);
      background: var(--surface); color: var(--text-muted); cursor: pointer;
    }
    .page-btn:hover:not(:disabled) { background: var(--surface-2); }
    .page-btn:disabled { opacity: var(--disabled-opacity); cursor: default; }
    .page-info { font-family: var(--mono); font-size: var(--fs-sm); color: var(--text-muted); }
  `]
})
export class AssetGridComponent implements OnInit {
  private assetService = inject(AssetService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  assets = signal<AssetDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  activeStatus = signal<LifecycleStatus | null>(null);
  searchTerm = signal('');
  page = signal(0);
  totalPages = signal(0);

  statusFilters = STATUS_FILTERS;
  private searchDebounce: ReturnType<typeof setTimeout> | null = null;

  columns: NxColumn[] = [
    { key: 'assetTag', header: 'Tag', rowHeader: true },
    { key: 'name', header: 'Nome', maxWidth: '320px' },
    { key: 'type', header: 'Tipo' },
    { key: 'lifecycleStatus', header: 'Ciclo de vida' },
    { key: 'manufacturer', header: 'Fabricante', muted: true },
    { key: 'serialNumber', header: 'Nº de série', mono: true, muted: true },
    { key: 'createdAt', header: 'Criado em', mono: true, muted: true },
  ];

  /** Nexus Rail: só o que exige atenção marca a linha (manutenção = âmbar; perdido/roubado = vermelho). */
  rail = (a: AssetDto): 'warn' | 'crit' | null => {
    if (a.lifecycleStatus === 'LOST' || a.lifecycleStatus === 'STOLEN') return 'crit';
    if (a.lifecycleStatus === 'MAINTENANCE') return 'warn';
    return null;
  };

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

  open(asset: AssetDto): void {
    this.router.navigate([asset.id], { relativeTo: this.route });
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
        this.error.set('Não foi possível carregar os ativos.');
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

  tone(status: LifecycleStatus): Tone {
    return STATUS_TONE[status] ?? 'neutral';
  }

  glyph(status: LifecycleStatus): StatusGlyph {
    return STATUS_GLYPH[status] ?? 'open';
  }
}
