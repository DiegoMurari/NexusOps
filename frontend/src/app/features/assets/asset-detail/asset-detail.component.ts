import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import {
  AssetService, AssetDto, AssetHistoryDto, AssigneeDto, CIRelationshipDto, LifecycleStatus, LinkedTicketDto, LocationDto,
  UpdateAssetRequest,
} from '../../../core/asset/asset.service';
import { AuthService } from '../../../core/auth/auth.service';
import { TicketDto, TicketService } from '../../../core/ticketing/ticket.service';
import { StatusBadgeComponent } from '../../../shared/components';

type Tone = 'neutral' | 'info' | 'success' | 'warning' | 'critical';

const FIELD_LABELS: Record<string, string> = {
  name: 'Nome', description: 'Descrição', type: 'Tipo', lifecycleStatus: 'Situação', manufacturer: 'Fabricante',
  model: 'Modelo', serialNumber: 'Nº de série', location: 'Localização', assignedTo: 'Responsável',
  purchaseDate: 'Data de compra', warrantyExpiration: 'Fim da garantia', purchaseCost: 'Custo de aquisição',
  depreciationMethod: 'Depreciação',
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

const STATUS_OPTIONS: LifecycleStatus[] = ['PROCURED', 'DEPLOYED', 'MAINTENANCE', 'RETIRED', 'DISPOSED', 'LOST', 'STOLEN'];

@Component({
  selector: 'app-asset-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, MatIconModule, StatusBadgeComponent],
  template: `
    @if (asset(); as a) {
      <div class="detail-header">
        <a class="back-link" routerLink="/assets">
          <mat-icon>arrow_back</mat-icon>
          <span>Voltar</span>
        </a>
        <div class="title-row">
          <span class="mono asset-tag">{{ a.assetTag }}</span>
          <nx-status-badge [tone]="statusTone(a.lifecycleStatus)">{{ statusLabel(a.lifecycleStatus) }}</nx-status-badge>
        </div>
        <h1 class="page-title">{{ a.name }}</h1>
        @if (canEdit && !editing()) {
          <button type="button" class="btn-secondary edit-btn" (click)="startEdit(a)">Editar</button>
        }
      </div>

      @if (editing()) {
        <form class="card edit-card" (ngSubmit)="saveEdit()" aria-label="Editar ativo">
          <h3 class="card-title">Editar ativo</h3>
          @if (editError()) {
            <div class="form-error"><mat-icon>error_outline</mat-icon><span>{{ editError() }}</span></div>
          }
          <div class="edit-grid">
            <label>Nome
              <input class="input" name="name" [(ngModel)]="form.name" required maxlength="255" />
            </label>
            <label>Fabricante
              <input class="input" name="manufacturer" [(ngModel)]="form.manufacturer" maxlength="100" />
            </label>
            <label>Modelo
              <input class="input" name="model" [(ngModel)]="form.model" maxlength="100" />
            </label>
            <label>Nº de série
              <input class="input" name="serial" [(ngModel)]="form.serialNumber" maxlength="100" />
            </label>
            <label>Localização
              <select class="input" name="location" [(ngModel)]="form.locationId">
                <option value="">Sem localização</option>
                @for (l of locations(); track l.id) { <option [value]="l.id">{{ l.name }}</option> }
              </select>
            </label>
            <label>Responsável
              @if (form.assignedToId) {
                <span class="chosen">
                  {{ form.assignedToName }}
                  <button type="button" class="link-remove" (click)="clearAssignee()">Remover</button>
                </span>
              } @else {
                <input class="input" type="search" name="assignee" autocomplete="off" placeholder="Buscar por nome ou e-mail"
                       [ngModel]="assigneeQuery()" (ngModelChange)="searchAssignees($event)" />
                @if (assigneeResults().length > 0) {
                  <ul class="results" role="listbox" aria-label="Pessoas encontradas">
                    @for (u of assigneeResults(); track u.id) {
                      <li><button type="button" (click)="chooseAssignee(u)"><span>{{ u.name }}</span><span class="result-title">{{ u.email }}</span></button></li>
                    }
                  </ul>
                }
              }
            </label>
            <label class="wide">Descrição
              <textarea class="input" name="description" rows="3" [(ngModel)]="form.description"></textarea>
            </label>
          </div>
          <div class="edit-actions">
            <button type="submit" class="btn-primary" [disabled]="acting() || !form.name.trim()">Salvar</button>
            <button type="button" class="btn-secondary" (click)="editing.set(false)" [disabled]="acting()">Cancelar</button>
          </div>
        </form>
      }

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
                  <a class="rel-target" [routerLink]="['/assets', rel.sourceId === a.id ? rel.targetId : rel.sourceId]">
                    {{ relatedName(rel.sourceId === a.id ? rel.targetId : rel.sourceId) }}
                  </a>
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
              <span class="meta-label">Responsável</span>
              <span class="meta-value">{{ a.assignedToName || (a.assignedToId ? 'Usuário removido' : '—') }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Localização</span>
              <span class="meta-value">{{ a.locationName || '—' }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Custo de aquisição</span>
              <span class="meta-value">{{ a.purchaseCost ? (a.purchaseCost | number:'1.2-2') : '—' }}</span>
            </div>
          </div>

          <div class="card">
            <h3 class="card-title">Tickets vinculados</h3>
            @if (ticketError()) {
              <div class="form-error"><mat-icon>error_outline</mat-icon><span>{{ ticketError() }}</span></div>
            }
            @if (canEdit) {
              <div class="link-box">
                <input class="input" type="search" placeholder="Buscar ticket por número ou título" aria-label="Buscar ticket para vincular"
                       [ngModel]="ticketQuery()" (ngModelChange)="searchTickets($event)" />
                @if (ticketResults().length > 0) {
                  <ul class="results" role="listbox" aria-label="Tickets encontrados">
                    @for (t of ticketResults(); track t.id) {
                      <li>
                        <button type="button" (click)="linkTicket(t.id)" [disabled]="acting()">
                          <span class="mono">{{ t.ticketNumber }}</span>
                          <span class="result-title">{{ t.title }}</span>
                        </button>
                      </li>
                    }
                  </ul>
                }
              </div>
            }
            @if (linkedTickets().length === 0) {
              <p class="description muted">Nenhum ticket vinculado a este ativo.</p>
            } @else {
              <ul class="linked-list">
                @for (t of linkedTickets(); track t.ticketId) {
                  <li>
                    <a [routerLink]="['/tickets', t.ticketId]" class="linked-main">
                      <span class="mono">{{ t.ticketNumber }}</span>
                      <span class="linked-title">{{ t.title }}</span>
                    </a>
                    @if (canEdit) {
                      <button type="button" class="link-remove" (click)="unlinkTicket(t.ticketId)" [disabled]="acting()"
                              [attr.aria-label]="'Desvincular ' + t.ticketNumber">Remover</button>
                    }
                  </li>
                }
              </ul>
            }
          </div>
        </div>
      </div>

      <div class="card history-card">
        <h3 class="card-title">Histórico</h3>
        @if (historyError()) {
          <p class="description muted">{{ historyError() }}</p>
        } @else if (history().length === 0) {
          <p class="description muted">Nenhum evento registrado.</p>
        } @else {
          <ol class="timeline">
            @for (h of history(); track h.id) {
              <li>
                <span class="when mono">{{ h.createdAt | date:'dd/MM/yyyy HH:mm' }}</span>
                <span class="what">{{ describe(h) }}</span>
                <span class="who">{{ h.actor || 'sistema' }}</span>
              </li>
            }
          </ol>
          @if (historyPage() + 1 < historyPages()) {
            <button type="button" class="btn-secondary" (click)="moreHistory()">Ver mais</button>
          }
        }
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

    .edit-btn { margin-top: var(--sp-4); }
    .edit-card { margin-bottom: var(--sp-6); }
    .edit-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--sp-5) var(--sp-6); }
    .edit-grid label { display: grid; gap: var(--sp-2); font-size: var(--fs-sm); color: var(--text-muted); position: relative; }
    .edit-grid .wide { grid-column: 1 / -1; }
    .edit-grid .input { width: 100%; box-sizing: border-box; }
    .chosen { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-4); min-height: var(--control-h-md); color: var(--text); }
    .edit-actions { display: flex; gap: var(--sp-4); margin-top: var(--sp-6); }
    @media (max-width: 700px) { .edit-grid { grid-template-columns: 1fr; } }
    .rel-target { color: var(--accent); text-decoration: none; }
    .rel-target:hover { text-decoration: underline; }

    .link-box { position: relative; margin-bottom: var(--sp-5); }
    .link-box input { width: 100%; box-sizing: border-box; }
    .results {
      list-style: none; margin: var(--sp-2) 0 0; padding: 0;
      border: 1px solid var(--border); border-radius: var(--radius-s); background: var(--surface);
      max-height: 220px; overflow-y: auto;
    }
    .results button {
      display: flex; gap: var(--sp-4); align-items: baseline; width: 100%; text-align: left;
      padding: var(--sp-3) var(--sp-4); border: 0; background: transparent; color: var(--text); cursor: pointer;
    }
    .results button:hover:not(:disabled), .results button:focus-visible { background: var(--surface-2); }
    .result-title { color: var(--text-muted); min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

    .linked-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--sp-3); }
    .linked-list li { display: flex; align-items: baseline; justify-content: space-between; gap: var(--sp-4); }
    .linked-main { display: flex; gap: var(--sp-4); align-items: baseline; min-width: 0; color: var(--text); text-decoration: none; }
    .linked-main:hover .linked-title { text-decoration: underline; }
    .linked-title { color: var(--text-muted); min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .link-remove { border: 0; background: transparent; color: var(--text-muted); cursor: pointer; font-size: 12px; }
    .link-remove:hover:not(:disabled) { color: var(--critical); }

    .history-card { margin-top: var(--sp-6); }
    .timeline { list-style: none; margin: 0 0 var(--sp-5); padding: 0; display: flex; flex-direction: column; gap: var(--sp-3); }
    .timeline li { display: grid; grid-template-columns: max-content 1fr max-content; gap: var(--sp-6); align-items: baseline; }
    .timeline .when { color: var(--text-faint); font-size: 12.5px; }
    .timeline .who { color: var(--text-muted); font-size: 12.5px; }
    .timeline .what { min-width: 0; overflow-wrap: anywhere; }
    @media (max-width: 700px) {
      .timeline li { grid-template-columns: 1fr; gap: var(--sp-1); }
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

  history = signal<AssetHistoryDto[]>([]);
  historyPage = signal(0);
  historyPages = signal(0);
  historyError = signal<string | null>(null);
  linkedTickets = signal<LinkedTicketDto[]>([]);
  ticketError = signal<string | null>(null);
  ticketQuery = signal('');
  ticketResults = signal<TicketDto[]>([]);
  relatedNames = signal<Record<string, string>>({});
  readonly canEdit: boolean;
  private searchTimer: ReturnType<typeof setTimeout> | null = null;
  private assigneeTimer: ReturnType<typeof setTimeout> | null = null;

  editing = signal(false);
  editError = signal<string | null>(null);
  locations = signal<LocationDto[]>([]);
  assigneeQuery = signal('');
  assigneeResults = signal<AssigneeDto[]>([]);
  form = { name: '', description: '', manufacturer: '', model: '', serialNumber: '', locationId: '', assignedToId: '', assignedToName: '' };

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private assetService: AssetService,
    private ticketService: TicketService,
    auth: AuthService
  ) {
    this.canEdit = auth.can('ASSET', 'UPDATE');
  }

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
          next: rels => {
            this.relationships.set(rels);
            this.resolveRelated(a.id, rels);
          },
          error: () => {}
        });
        this.loadHistory(a.id, 0);
        this.loadTickets(a.id);
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
      next: () => {
        this.acting.set(false);
        this.load(a.id);
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

  startEdit(a: AssetDto): void {
    this.form = {
      name: a.name,
      description: a.description ?? '',
      manufacturer: a.manufacturer ?? '',
      model: a.model ?? '',
      serialNumber: a.serialNumber ?? '',
      locationId: a.locationId ?? '',
      assignedToId: a.assignedToId ?? '',
      assignedToName: a.assignedToName ?? '',
    };
    this.assigneeQuery.set('');
    this.assigneeResults.set([]);
    this.editError.set(null);
    if (this.locations().length === 0) {
      this.assetService.listLocations().subscribe({ next: l => this.locations.set(l), error: () => {} });
    }
    this.editing.set(true);
  }

  searchAssignees(query: string): void {
    this.assigneeQuery.set(query);
    if (this.assigneeTimer) clearTimeout(this.assigneeTimer);
    if (query.trim().length < 2) {
      this.assigneeResults.set([]);
      return;
    }
    this.assigneeTimer = setTimeout(() => {
      this.assetService.assignees(query.trim()).subscribe({
        next: list => this.assigneeResults.set(list),
        error: () => this.assigneeResults.set([])
      });
    }, 300);
  }

  chooseAssignee(user: AssigneeDto): void {
    this.form.assignedToId = user.id;
    this.form.assignedToName = user.name;
    this.assigneeQuery.set('');
    this.assigneeResults.set([]);
  }

  clearAssignee(): void {
    this.form.assignedToId = '';
    this.form.assignedToName = '';
  }

  saveEdit(): void {
    const a = this.asset();
    if (!a || !this.form.name.trim()) return;
    this.acting.set(true);
    this.editError.set(null);
    // String vazia limpa o campo no servidor; campos iguais ao atual nem são enviados.
    const f = this.form;
    const request: UpdateAssetRequest = {};
    if (f.name.trim() !== a.name) request.name = f.name.trim();
    if (f.description !== (a.description ?? '')) request.description = f.description;
    if (f.manufacturer !== (a.manufacturer ?? '')) request.manufacturer = f.manufacturer;
    if (f.model !== (a.model ?? '')) request.model = f.model;
    if (f.serialNumber !== (a.serialNumber ?? '')) request.serialNumber = f.serialNumber;
    if (f.locationId !== (a.locationId ?? '')) request.locationId = f.locationId;
    if (f.assignedToId !== (a.assignedToId ?? '')) request.assignedToId = f.assignedToId;
    if (Object.keys(request).length === 0) {
      this.acting.set(false);
      this.editing.set(false);
      return;
    }
    this.assetService.update(a.id, request).subscribe({
      next: () => {
        this.acting.set(false);
        this.editing.set(false);
        this.load(a.id);
      },
      error: (err) => {
        this.acting.set(false);
        this.editError.set(err?.error?.detail ?? 'Não foi possível salvar as alterações.');
      }
    });
  }

  statusTone(status: LifecycleStatus): Tone {
    switch (status) {
      case 'DEPLOYED': return 'success';
      case 'PROCURED': return 'info';
      case 'MAINTENANCE': return 'warning';
      case 'RETIRED': case 'DISPOSED': case 'LOST': case 'STOLEN': return 'critical';
      default: return 'neutral';
    }
  }

  relatedName(id: string): string {
    return this.relatedNames()[id] ?? 'Ativo relacionado';
  }

  /** Mostra o nome do ativo relacionado, não o identificador. */
  private resolveRelated(selfId: string, rels: CIRelationshipDto[]): void {
    const ids = new Set(rels.map(r => (r.sourceId === selfId ? r.targetId : r.sourceId)));
    ids.forEach(id => this.assetService.get(id).subscribe({
      next: other => this.relatedNames.update(m => ({ ...m, [id]: `${other.assetTag} · ${other.name}` })),
      error: () => {}
    }));
  }

  private loadHistory(id: string, page: number): void {
    this.assetService.history(id, page).subscribe({
      next: res => {
        this.history.update(list => (page === 0 ? res.content : [...list, ...res.content]));
        this.historyPage.set(res.number);
        this.historyPages.set(res.totalPages);
        this.historyError.set(null);
      },
      error: () => this.historyError.set('Não foi possível carregar o histórico.')
    });
  }

  moreHistory(): void {
    const a = this.asset();
    if (a) this.loadHistory(a.id, this.historyPage() + 1);
  }

  /** Frase curta para uma linha do histórico. */
  describe(h: AssetHistoryDto): string {
    switch (h.eventType) {
      case 'CREATED': return `Ativo cadastrado: ${h.newValue ?? ''}`.trim();
      case 'TICKET_LINKED': return `Vinculado ao ticket ${h.newValue ?? ''}`.trim();
      case 'TICKET_UNLINKED': return `Desvinculado do ticket ${h.oldValue ?? ''}`.trim();
      default: {
        const label = FIELD_LABELS[h.fieldName ?? ''] ?? h.fieldName ?? 'Campo';
        const shown = (v?: string | null) => {
          if (!v) return 'vazio';
          const text = h.fieldName === 'lifecycleStatus' ? (STATUS_LABELS[v as LifecycleStatus] ?? v) : v;
          return `“${text}”`;
        };
        const before = shown(h.oldValue);
        const after = shown(h.newValue);
        return `${label}: ${before} → ${after}`;
      }
    }
  }

  private loadTickets(id: string): void {
    this.assetService.linkedTickets(id).subscribe({
      next: list => this.linkedTickets.set(list),
      error: () => this.ticketError.set('Não foi possível carregar os tickets vinculados.')
    });
  }

  searchTickets(query: string): void {
    this.ticketQuery.set(query);
    if (this.searchTimer) clearTimeout(this.searchTimer);
    const q = query.trim();
    if (q.length < 2) {
      this.ticketResults.set([]);
      return;
    }
    this.searchTimer = setTimeout(() => {
      this.ticketService.queueView({ scope: 'ALL', q, size: 6 }).subscribe({
        next: res => {
          const linked = new Set(this.linkedTickets().map(t => t.ticketId));
          this.ticketResults.set(res.content.filter(t => !linked.has(t.id)));
        },
        error: () => this.ticketResults.set([])
      });
    }, 300);
  }

  linkTicket(ticketId: string): void {
    const a = this.asset();
    if (!a) return;
    this.acting.set(true);
    this.ticketError.set(null);
    this.assetService.linkTicket(a.id, ticketId).subscribe({
      next: () => {
        this.acting.set(false);
        this.ticketQuery.set('');
        this.ticketResults.set([]);
        this.loadTickets(a.id);
        this.loadHistory(a.id, 0);
      },
      error: () => {
        this.acting.set(false);
        this.ticketError.set('Não foi possível vincular o ticket.');
      }
    });
  }

  unlinkTicket(ticketId: string): void {
    const a = this.asset();
    if (!a) return;
    this.acting.set(true);
    this.ticketError.set(null);
    this.assetService.unlinkTicket(a.id, ticketId).subscribe({
      next: () => {
        this.acting.set(false);
        this.loadTickets(a.id);
        this.loadHistory(a.id, 0);
      },
      error: () => {
        this.acting.set(false);
        this.ticketError.set('Não foi possível desvincular o ticket.');
      }
    });
  }
}
