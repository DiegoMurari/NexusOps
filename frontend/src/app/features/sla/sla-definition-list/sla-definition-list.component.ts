import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { SlaService, SlaDefinitionDto } from '../../../core/sla/sla.service';
import {
  ButtonComponent, DataTableComponent, EmptyStateComponent, NxCellDirective, NxColumn, PageHeaderComponent,
  StatusBadgeComponent,
} from '../../../shared/components';

interface DefinitionForm {
  name: string;
  description: string;
  type: string;
  priority: string;
  response: number | null;
  resolution: number | null;
  pauseOnHold: boolean;
  stopOnFirstResponse: boolean;
  active: boolean;
}

const EMPTY_FORM: DefinitionForm = {
  name: '', description: '', type: '', priority: '', response: null, resolution: null,
  pauseOnHold: true, stopOnFirstResponse: true, active: true,
};

@Component({
  selector: 'app-sla-definition-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatIconModule, ButtonComponent, DataTableComponent, EmptyStateComponent,
    NxCellDirective, PageHeaderComponent, StatusBadgeComponent,
  ],
  template: `
    <nx-page-header heading="Definições de SLA">
      <button nxButton variant="primary" (click)="startCreate()">
        <mat-icon>add</mat-icon>
        Nova definição
      </button>
    </nx-page-header>

    @if (formOpen()) {
      <form class="card form" (ngSubmit)="save()" [attr.aria-label]="editingId() ? 'Editar definição' : 'Nova definição'">
        <h2 class="form-title">
          {{ editingId() ? 'Editar definição' : 'Nova definição' }}
          @if (editingVersion()) { <span class="rev">revisão {{ editingVersion() }}</span> }
        </h2>
        <div class="field-row">
          <label class="field">
            <span class="label">Nome *</span>
            <input class="input" name="name" type="text" [(ngModel)]="form.name" required
                   placeholder="Ex.: Incidentes críticos" />
          </label>
          <label class="field">
            <span class="label">Descrição</span>
            <input class="input" name="description" type="text" [(ngModel)]="form.description" />
          </label>
        </div>
        <div class="field-row">
          <label class="field">
            <span class="label">Tipo de chamado</span>
            <select class="input" name="type" [(ngModel)]="form.type">
              <option value="">Qualquer</option>
              <option value="INCIDENT">Incidente</option>
              <option value="PROBLEM">Problema</option>
              <option value="CHANGE">Mudança</option>
            </select>
          </label>
          <label class="field">
            <span class="label">Prioridade</span>
            <select class="input" name="priority" [(ngModel)]="form.priority">
              <option value="">Qualquer</option>
              <option value="LOW">Baixa</option>
              <option value="MEDIUM">Média</option>
              <option value="HIGH">Alta</option>
              <option value="CRITICAL">Crítica</option>
            </select>
          </label>
        </div>
        <div class="field-row">
          <label class="field">
            <span class="label">Tempo de resposta (min)</span>
            <input class="input" name="response" type="number" [(ngModel)]="form.response" min="1" />
          </label>
          <label class="field">
            <span class="label">Tempo de resolução (min)</span>
            <input class="input" name="resolution" type="number" [(ngModel)]="form.resolution" min="1" />
          </label>
        </div>
        <div class="checks">
          <label><input type="checkbox" name="pause" [(ngModel)]="form.pauseOnHold" /> Pausar o relógio em espera / aguardando solicitante</label>
          <label><input type="checkbox" name="stop" [(ngModel)]="form.stopOnFirstResponse" /> Primeira resposta encerra o prazo de resposta</label>
          @if (editingId()) {
            <label><input type="checkbox" name="active" [(ngModel)]="form.active" /> Definição ativa</label>
          }
        </div>
        <p class="hint">
          Critérios em branco valem para qualquer chamado; vence a definição mais específica.
          Alterações valem para chamados novos e reaberturas; prazos de ciclos em andamento não são reescritos.
        </p>
        @if (formError()) {
          <p class="inline-error" role="alert">{{ formError() }}</p>
        }
        <div class="form-actions">
          <button nxButton type="button" (click)="closeForm()">Cancelar</button>
          <button nxButton variant="primary" type="submit" [loading]="saving()" [disabled]="!form.name.trim() || saving()">
            <mat-icon>check</mat-icon>
            {{ editingId() ? 'Salvar' : 'Criar' }}
          </button>
        </div>
      </form>
    }

    @if (error()) {
      <nx-empty-state variant="error" [heading]="error()!" />
    } @else {
      <nx-data-table caption="Definições de SLA" [columns]="columns" [rows]="definitions()" [loading]="loading()"
                     [rowClickable]="true" [selectedRow]="selected()" (rowActivate)="startEdit($any($event))"
                     emptyTitle="Nenhuma definição de SLA cadastrada">
        <ng-template nxCell="appliesToType" let-d>{{ typeLabel(d.appliesToType) }}</ng-template>
        <ng-template nxCell="appliesToPriority" let-d>{{ d.appliesToPriority || 'Qualquer' }}</ng-template>
        <ng-template nxCell="responseTimeMinutes" let-d>{{ d.responseTimeMinutes ? d.responseTimeMinutes + ' min' : '—' }}</ng-template>
        <ng-template nxCell="resolutionTimeMinutes" let-d>{{ d.resolutionTimeMinutes ? d.resolutionTimeMinutes + ' min' : '—' }}</ng-template>
        <ng-template nxCell="version" let-d>r{{ d.version }}</ng-template>
        <ng-template nxCell="active" let-d>
          <nx-status-badge [tone]="d.active ? 'success' : 'neutral'">{{ d.active ? 'Ativa' : 'Inativa' }}</nx-status-badge>
        </ng-template>
      </nx-data-table>
    }
  `,
  styles: [`
    :host { display: block; }
    .form { display: flex; flex-direction: column; gap: var(--sp-5); margin-bottom: var(--sp-7); }
    .form-title { margin: 0; font-size: 1rem; font-weight: 600; display: flex; gap: var(--sp-4); align-items: baseline; }
    .rev { font-family: var(--font-mono); font-size: .75rem; font-weight: 400; opacity: .7; }
    .field-row { display: grid; grid-template-columns: 1fr 1fr; gap: var(--sp-6); }
    .checks { display: flex; flex-direction: column; gap: var(--sp-3); }
    .hint { margin: 0; font-size: .75rem; opacity: .75; }
    .inline-error { margin: 0; }
  `]
})
export class SlaDefinitionListComponent implements OnInit {
  readonly columns: NxColumn[] = [
    { key: 'name', header: 'Nome', rowHeader: true },
    { key: 'appliesToType', header: 'Tipo', muted: true },
    { key: 'appliesToPriority', header: 'Prioridade', muted: true },
    { key: 'responseTimeMinutes', header: 'Resposta', mono: true },
    { key: 'resolutionTimeMinutes', header: 'Resolução', mono: true },
    { key: 'version', header: 'Rev.', mono: true },
    { key: 'active', header: 'Status' },
  ];

  definitions = signal<SlaDefinitionDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  formOpen = signal(false);
  saving = signal(false);
  formError = signal<string | null>(null);
  editingId = signal<string | null>(null);
  editingVersion = signal<number | null>(null);
  selected = signal<SlaDefinitionDto | null>(null);

  form: DefinitionForm = { ...EMPTY_FORM };

  constructor(private slaService: SlaService) {}

  ngOnInit(): void {
    this.load();
  }

  typeLabel(type: string | null): string {
    switch (type) {
      case 'INCIDENT': return 'Incidente';
      case 'PROBLEM': return 'Problema';
      case 'CHANGE': return 'Mudança';
      default: return 'Qualquer';
    }
  }

  startCreate(): void {
    this.editingId.set(null);
    this.editingVersion.set(null);
    this.selected.set(null);
    this.form = { ...EMPTY_FORM };
    this.formError.set(null);
    this.formOpen.set(true);
  }

  startEdit(def: SlaDefinitionDto): void {
    this.editingId.set(def.id);
    this.editingVersion.set(def.version);
    this.selected.set(def);
    this.form = {
      name: def.name,
      description: def.description ?? '',
      type: def.appliesToType ?? '',
      priority: def.appliesToPriority ?? '',
      response: def.responseTimeMinutes,
      resolution: def.resolutionTimeMinutes,
      pauseOnHold: def.pauseOnHold,
      stopOnFirstResponse: def.stopOnFirstResponse,
      active: def.active,
    };
    this.formError.set(null);
    this.formOpen.set(true);
  }

  closeForm(): void {
    this.formOpen.set(false);
    this.editingId.set(null);
    this.selected.set(null);
  }

  save(): void {
    if (!this.form.name.trim() || this.saving()) return;
    this.saving.set(true);
    this.formError.set(null);
    const f = this.form;
    const id = this.editingId();

    // Edição: texto vazio limpa o critério (o servidor trata nulo como "manter").
    const request$ = id
      ? this.slaService.updateDefinition(id, {
          name: f.name.trim(),
          description: f.description,
          appliesToType: f.type,
          appliesToPriority: f.priority,
          responseTimeMinutes: f.response ?? undefined,
          resolutionTimeMinutes: f.resolution ?? undefined,
          pauseOnHold: f.pauseOnHold,
          stopOnFirstResponse: f.stopOnFirstResponse,
          active: f.active,
        })
      : this.slaService.createDefinition({
          name: f.name.trim(),
          description: f.description || undefined,
          appliesToType: f.type || undefined,
          appliesToPriority: f.priority || undefined,
          responseTimeMinutes: f.response ?? undefined,
          resolutionTimeMinutes: f.resolution ?? undefined,
          pauseOnHold: f.pauseOnHold,
          stopOnFirstResponse: f.stopOnFirstResponse,
        });

    request$.subscribe({
      next: def => {
        this.definitions.update(list => id ? list.map(d => d.id === def.id ? def : d) : [def, ...list]);
        this.saving.set(false);
        this.closeForm();
      },
      error: (e: HttpErrorResponse) => {
        this.saving.set(false);
        const detail = e.error?.detail;
        this.formError.set(e.status === 422
          ? (typeof detail === 'string' && detail !== 'Request validation failed'
              ? detail
              : 'Confira os campos: os tempos devem ser positivos e a resolução não pode ser menor que a resposta.')
          : 'Não foi possível salvar a definição de SLA.');
      }
    });
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.slaService.listDefinitions().subscribe({
      next: defs => {
        this.definitions.set(defs);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Não foi possível carregar as definições de SLA.');
        this.loading.set(false);
      }
    });
  }
}
