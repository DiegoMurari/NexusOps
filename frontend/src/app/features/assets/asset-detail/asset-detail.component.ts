import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { AssetService, AssetDto, CIRelationshipDto, LifecycleStatus } from '../../../core/asset/asset.service';

const STATUS_LABELS: Record<LifecycleStatus, string> = {
  PROCURED: 'Adquirido',
  DEPLOYED: 'Em uso',
  MAINTENANCE: 'Manutenção',
  RETIRED: 'Desativado',
  DISPOSED: 'Descartado',
  LOST: 'Perdido',
  STOLEN: 'Roubado',
};

const STATUS_OPTIONS: LifecycleStatus[] = ['PROCURED', 'DEPLOYED', 'MAINTENANCE', 'RETIRED', 'DISPOSED', 'LOST', 'STOLEN'];

@Component({
  selector: 'app-asset-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, MatIconModule],
  template: `
    @if (asset(); as a) {
      <div class="detail-header">
        <a class="back-link" routerLink="/assets">
          <mat-icon>arrow_back</mat-icon>
          <span>Voltar</span>
        </a>
        <div class="title-row">
          <span class="mono asset-tag">{{ a.assetTag }}</span>
          <span class="status-tag" [class]="a.lifecycleStatus.toLowerCase()">{{ statusLabel(a.lifecycleStatus) }}</span>
        </div>
        <h1 class="page-title">{{ a.name }}</h1>
      </div>

      <div class="detail-grid">
        <div class="card">
          <h3 class="card-title">Descrição</h3>
          <p class="description">{{ a.description || 'Sem descrição.' }}</p>

          <div class="meta-grid">
            <div class="meta-item">
              <span class="meta-label">Tipo</span>
              <span class="meta-value">{{ a.type }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Fabricante</span>
              <span class="meta-value">{{ a.manufacturer || '—' }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Modelo</span>
              <span class="meta-value">{{ a.model || '—' }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Nº de série</span>
              <span class="meta-value mono">{{ a.serialNumber || '—' }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Criado em</span>
              <span class="meta-value mono">{{ a.createdAt | date:'dd/MM/yyyy HH:mm' }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Atualizado em</span>
              <span class="meta-value mono">{{ a.updatedAt | date:'dd/MM/yyyy HH:mm' }}</span>
            </div>
          </div>

          <h3 class="card-title relationships-title">Relacionamentos (CMDB)</h3>
          @if (relationships().length === 0) {
            <p class="description muted">Nenhum relacionamento cadastrado.</p>
          } @else {
            <ul class="relationship-list">
              @for (rel of relationships(); track rel.id) {
                <li>
                  <span class="rel-type">{{ rel.relationshipType }}</span>
                  <span class="mono rel-target">{{ rel.sourceId === a.id ? rel.targetId : rel.sourceId }}</span>
                </li>
              }
            </ul>
          }
        </div>

        <div class="side-col">
          <div class="card">
            <h3 class="card-title">Status do ciclo de vida</h3>

            @if (actionError()) {
              <div class="form-error">
                <mat-icon>error_outline</mat-icon>
                <span>{{ actionError() }}</span>
              </div>
            }

            <div class="field">
              <select [(ngModel)]="selectedStatus" [disabled]="acting()">
                @for (s of statusOptions; track s) {
                  <option [value]="s">{{ statusLabel(s) }}</option>
                }
              </select>
              <button class="btn-primary" [disabled]="acting() || selectedStatus === a.lifecycleStatus" (click)="updateStatus()">
                Atualizar status
              </button>
            </div>
          </div>

          <div class="card">
            <h3 class="card-title">Detalhes</h3>
            <div class="meta-item">
              <span class="meta-label">Atribuído a</span>
              <span class="meta-value mono">{{ a.assignedToId || '—' }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Localização</span>
              <span class="meta-value mono">{{ a.locationId || '—' }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Custo de aquisição</span>
              <span class="meta-value">{{ a.purchaseCost ? (a.purchaseCost | number:'1.2-2') : '—' }}</span>
            </div>
          </div>
        </div>
      </div>
    } @else if (loading()) {
      <div class="card empty-state">
        <mat-icon class="empty-ic">hourglass_empty</mat-icon>
        <p>Carregando asset…</p>
      </div>
    } @else if (error()) {
      <div class="card empty-state error">
        <mat-icon class="empty-ic">error_outline</mat-icon>
        <p>{{ error() }}</p>
      </div>
    }
  `,
  styles: [`
    :host { display: block; }

    .back-link {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      color: var(--text-muted);
      font-size: 12.5px;
      text-decoration: none;
      margin-bottom: 10px;

      mat-icon { font-size: 16px; width: 16px; height: 16px; }
      &:hover { color: var(--text); }
    }

    .detail-header { margin-bottom: 20px; }

    .title-row {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 6px;
    }

    .asset-tag {
      font-size: 12.5px;
      color: var(--text-faint);
    }

    .page-title {
      margin: 0;
      font-size: 1.35rem;
      font-weight: 600;
      color: var(--text);
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

    .detail-grid {
      display: grid;
      grid-template-columns: 2fr 1fr;
      gap: 20px;
      align-items: start;
    }

    @media (max-width: 900px) {
      .detail-grid { grid-template-columns: 1fr; }
    }

    .side-col {
      display: flex;
      flex-direction: column;
      gap: 20px;
    }

    .card-title {
      margin: 0 0 12px;
      font-size: 14px;
      font-weight: 600;
      color: var(--text);
    }

    .relationships-title { margin-top: 20px; }

    .description {
      font-size: 13px;
      color: var(--text);
      line-height: 1.6;
      white-space: pre-wrap;
      margin: 0 0 16px;
    }

    .description.muted { color: var(--text-faint); margin: 0; }

    .relationship-list {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .relationship-list li {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 8px 12px;
      background: var(--surface-2);
      border-radius: var(--radius-s);
      font-size: 12.5px;
    }

    .rel-type {
      font-weight: 600;
      color: var(--accent);
    }

    .rel-target {
      color: var(--text-faint);
    }

    .meta-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 12px;
    }

    .meta-item {
      display: flex;
      flex-direction: column;
      gap: 2px;
      padding: 8px 0;
      border-bottom: 1px solid var(--border);
    }

    .meta-item:last-child { border-bottom: none; }

    .meta-label {
      font-size: 11px;
      text-transform: uppercase;
      letter-spacing: 0.03em;
      color: var(--text-faint);
    }

    .meta-value {
      font-size: 13px;
      color: var(--text);
    }

    .field {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    select {
      font-family: var(--sans);
      font-size: 13px;
      color: var(--text);
      background: var(--surface-2);
      border: 1px solid var(--border);
      border-radius: var(--radius-s);
      padding: 8px 10px;
      outline: none;

      &:focus { border-color: var(--accent); }
    }

    .btn-primary {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      height: 36px;
      padding: 0 16px;
      border-radius: var(--radius-s);
      background: var(--accent);
      color: #fff;
      border: none;
      cursor: pointer;
      font-size: 12.5px;
      font-weight: 500;

      &:hover:not(:disabled) { filter: brightness(1.08); }
      &:disabled { opacity: 0.5; cursor: default; }
    }

    .form-error {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 10px 12px;
      border-radius: var(--radius-s);
      background: var(--critical-soft);
      color: var(--critical);
      font-size: 12.5px;
      margin-bottom: 12px;

      mat-icon { font-size: 18px; width: 18px; height: 18px; }
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
export class AssetDetailComponent implements OnInit {
  asset = signal<AssetDto | null>(null);
  relationships = signal<CIRelationshipDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  acting = signal(false);
  actionError = signal<string | null>(null);
  selectedStatus: LifecycleStatus = 'PROCURED';

  statusOptions = STATUS_OPTIONS;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private assetService: AssetService
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.error.set('Asset inválido.');
      this.loading.set(false);
      return;
    }
    this.load(id);
  }

  private load(id: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.assetService.get(id).subscribe({
      next: a => {
        this.asset.set(a);
        this.selectedStatus = a.lifecycleStatus;
        this.loading.set(false);
        this.assetService.listRelationships(a.id).subscribe({
          next: rels => this.relationships.set(rels),
          error: () => {}
        });
      },
      error: () => {
        this.error.set('Asset não encontrado.');
        this.loading.set(false);
      }
    });
  }

  updateStatus(): void {
    const a = this.asset();
    if (!a) return;

    this.acting.set(true);
    this.actionError.set(null);
    this.assetService.update(a.id, { lifecycleStatus: this.selectedStatus }).subscribe({
      next: updated => {
        this.asset.set(updated);
        this.acting.set(false);
      },
      error: () => {
        this.acting.set(false);
        this.actionError.set('Não foi possível atualizar o status.');
      }
    });
  }

  statusLabel(status: LifecycleStatus): string {
    return STATUS_LABELS[status] ?? status;
  }
}
