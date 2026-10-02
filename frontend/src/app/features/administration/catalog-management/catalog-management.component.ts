import { Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { forkJoin } from 'rxjs';
import {
  CatalogAreaDto, CatalogService, CatalogTopicDto, QueueDto,
} from '../../../core/catalog/catalog.service';
import { SlaDefinitionDto, SlaService } from '../../../core/sla/sla.service';
import { FormBuilderComponent } from '../form-builder/form-builder.component';
import {
  ButtonComponent, DataTableComponent, EmptyStateComponent, NxCellDirective, NxColumn, PageHeaderComponent,
  StatusBadgeComponent,
} from '../../../shared/components';

interface AreaForm { name: string; description: string; icon: string; sortOrder: number; active: boolean; }
interface TopicForm {
  name: string; description: string; defaultQueueId: string; defaultPriority: string; slaDefinitionId: string;
  sortOrder: number; active: boolean;
}

const EMPTY_AREA: AreaForm = { name: '', description: '', icon: '', sortOrder: 0, active: true };
const EMPTY_TOPIC: TopicForm = {
  name: '', description: '', defaultQueueId: '', defaultPriority: '', slaDefinitionId: '', sortOrder: 0, active: true,
};

@Component({
  selector: 'app-catalog-management',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatIconModule, ButtonComponent, DataTableComponent, EmptyStateComponent,
    NxCellDirective, PageHeaderComponent, StatusBadgeComponent, FormBuilderComponent,
  ],
  template: `
    <nx-page-header heading="Catálogo de serviços">
      <button nxButton variant="primary" (click)="startAreaCreate()">
        <mat-icon>add</mat-icon>
        Nova área
      </button>
    </nx-page-header>

    <p class="intro">
      A <strong>área</strong> é o agrupamento que o solicitante vê no Portal. O <strong>tópico</strong> é o que ele escolhe;
      fila, prioridade inicial e SLA ficam no tópico e o solicitante nunca os vê — o sistema aplica ao abrir o chamado.
    </p>

    @if (error()) {
      <nx-empty-state variant="error" [heading]="error()!" />
    } @else {
      <div class="layout">
        <section class="areas" aria-label="Áreas do catálogo">
          @if (areaFormOpen()) {
            <form class="card form" (ngSubmit)="saveArea()" [attr.aria-label]="editingAreaId() ? 'Editar área' : 'Nova área'">
              <h2 class="form-title">{{ editingAreaId() ? 'Editar área' : 'Nova área' }}</h2>
              <label class="field">
                <span class="label">Nome *</span>
                <input class="input" name="aname" type="text" [(ngModel)]="areaForm.name" required placeholder="Ex.: Rede e Telecom" />
              </label>
              <label class="field">
                <span class="label">Descrição</span>
                <input class="input" name="adesc" type="text" [(ngModel)]="areaForm.description" />
              </label>
              <div class="field-row">
                <label class="field">
                  <span class="label">Ícone (Material)</span>
                  <input class="input mono" name="aicon" type="text" [(ngModel)]="areaForm.icon" placeholder="wifi" />
                </label>
                <label class="field">
                  <span class="label">Ordem</span>
                  <input class="input" name="aorder" type="number" [(ngModel)]="areaForm.sortOrder" />
                </label>
              </div>
              @if (editingAreaId()) {
                <label class="check"><input type="checkbox" name="aactive" [(ngModel)]="areaForm.active" /> Área ativa (visível no Portal)</label>
              }
              @if (areaError()) { <p class="inline-error" role="alert">{{ areaError() }}</p> }
              <div class="form-actions">
                <button nxButton type="button" (click)="closeAreaForm()">Cancelar</button>
                <button nxButton variant="primary" type="submit" [loading]="saving()" [disabled]="!areaForm.name.trim() || saving()">
                  <mat-icon>check</mat-icon>
                  {{ editingAreaId() ? 'Salvar' : 'Criar' }}
                </button>
              </div>
            </form>
          }

          @if (loading()) {
            <p class="muted">Carregando…</p>
          } @else if (areas().length === 0) {
            <nx-empty-state heading="Nenhuma área cadastrada" description="Crie a primeira área para organizar os tópicos." />
          } @else {
            <ul class="area-list">
              @for (a of areas(); track a.id) {
                <li>
                  <button type="button" class="area" [class.is-selected]="a.id === selectedAreaId()" (click)="selectArea(a)"
                          [attr.aria-pressed]="a.id === selectedAreaId()">
                    <mat-icon class="a-ic" aria-hidden="true">{{ a.icon || 'folder' }}</mat-icon>
                    <span class="a-body">
                      <span class="a-name">{{ a.name }}</span>
                      <span class="a-meta">{{ a.topicCount }} {{ a.topicCount === 1 ? 'tópico' : 'tópicos' }}@if (!a.active) { · inativa }</span>
                    </span>
                  </button>
                </li>
              }
            </ul>
          }
        </section>

        <section class="topics" aria-label="Tópicos da área">
          @if (!selectedArea()) {
            <nx-empty-state heading="Selecione uma área" description="Os tópicos da área aparecem aqui." />
          } @else {
            <div class="topics-head">
              <h2 class="topics-title">{{ selectedArea()!.name }}</h2>
              <button nxButton type="button" (click)="startAreaEdit(selectedArea()!)"><mat-icon>edit</mat-icon>Editar área</button>
              <button nxButton variant="primary" type="button" (click)="startTopicCreate()"><mat-icon>add</mat-icon>Novo tópico</button>
            </div>

            @if (topicFormOpen()) {
              <form class="card form" (ngSubmit)="saveTopic()" [attr.aria-label]="editingTopicId() ? 'Editar tópico' : 'Novo tópico'">
                <h3 class="form-title">{{ editingTopicId() ? 'Editar tópico' : 'Novo tópico' }}</h3>
                <label class="field">
                  <span class="label">Nome (como o solicitante lê) *</span>
                  <input class="input" name="tname" type="text" [(ngModel)]="topicForm.name" required placeholder="Ex.: Falha de conexão" />
                </label>
                <label class="field">
                  <span class="label">Descrição</span>
                  <input class="input" name="tdesc" type="text" [(ngModel)]="topicForm.description" />
                </label>
                <fieldset class="internal">
                  <legend>Padrões internos (o solicitante não vê)</legend>
                  <div class="field-row">
                    <label class="field">
                      <span class="label">Fila padrão</span>
                      <select class="input" name="tqueue" [(ngModel)]="topicForm.defaultQueueId">
                        <option value="">Sem fila (triagem manual)</option>
                        @for (q of activeQueues(); track q.id) { <option [value]="q.id">{{ q.name }} ({{ q.code }})</option> }
                      </select>
                    </label>
                    <label class="field">
                      <span class="label">Prioridade inicial</span>
                      <select class="input" name="tprio" [(ngModel)]="topicForm.defaultPriority">
                        <option value="">Padrão do sistema</option>
                        <option value="LOW">Baixa</option>
                        <option value="MEDIUM">Média</option>
                        <option value="HIGH">Alta</option>
                        <option value="CRITICAL">Crítica</option>
                      </select>
                    </label>
                  </div>
                  <label class="field">
                    <span class="label">SLA</span>
                    <select class="input" name="tsla" [(ngModel)]="topicForm.slaDefinitionId">
                      <option value="">Automático (a definição mais específica)</option>
                      @for (s of slaDefinitions(); track s.id) { <option [value]="s.id">{{ s.name }}</option> }
                    </select>
                  </label>
                </fieldset>
                <div class="field-row">
                  <label class="field">
                    <span class="label">Ordem</span>
                    <input class="input" name="torder" type="number" [(ngModel)]="topicForm.sortOrder" />
                  </label>
                  @if (editingTopicId()) {
                    <label class="check"><input type="checkbox" name="tactive" [(ngModel)]="topicForm.active" /> Tópico ativo (visível no Portal)</label>
                  }
                </div>
                @if (topicError()) { <p class="inline-error" role="alert">{{ topicError() }}</p> }
                <div class="form-actions">
                  @if (editingTopicId()) {
                    <button nxButton type="button" (click)="openForm()"><mat-icon>dynamic_form</mat-icon>Formulário do tópico</button>
                  }
                  <button nxButton type="button" (click)="closeTopicForm()">Cancelar</button>
                  <button nxButton variant="primary" type="submit" [loading]="saving()" [disabled]="!topicForm.name.trim() || saving()">
                    <mat-icon>check</mat-icon>
                    {{ editingTopicId() ? 'Salvar' : 'Criar' }}
                  </button>
                </div>
              </form>
            }

            @if (formTopic(); as ft) {
              <app-form-builder [topicId]="ft.id" [topicName]="ft.name" (closed)="formTopic.set(null)" />
            }

            <nx-data-table caption="Tópicos da área" [columns]="topicColumns" [rows]="areaTopics()"
                           [rowClickable]="true" [selectedRow]="selectedTopic()" (rowActivate)="startTopicEdit($any($event))"
                           emptyTitle="Esta área ainda não tem tópicos">
              <ng-template nxCell="defaultQueueId" let-t>{{ queueName(t.defaultQueueId) }}</ng-template>
              <ng-template nxCell="defaultPriority" let-t>{{ t.defaultPriority || '—' }}</ng-template>
              <ng-template nxCell="slaDefinitionId" let-t>{{ slaName(t.slaDefinitionId) }}</ng-template>
              <ng-template nxCell="active" let-t>
                <nx-status-badge [tone]="t.active ? 'success' : 'neutral'">{{ t.active ? 'Ativo' : 'Inativo' }}</nx-status-badge>
              </ng-template>
            </nx-data-table>
          }
        </section>
      </div>
    }
  `,
  styles: [`
    :host { display: block; }
    .intro { margin: 0 0 var(--sp-6); max-width: 75ch; color: var(--text-muted); font-size: var(--fs-sm); }
    .layout { display: grid; grid-template-columns: minmax(240px, 300px) 1fr; gap: var(--sp-7); align-items: start; }
    @media (max-width: 860px) { .layout { grid-template-columns: 1fr; } }
    .areas, .topics { min-width: 0; display: flex; flex-direction: column; gap: var(--sp-5); }
    .form { display: flex; flex-direction: column; gap: var(--sp-5); }
    .form-title { margin: 0; font-size: 1rem; font-weight: 600; }
    .field-row { display: grid; grid-template-columns: 1fr 1fr; gap: var(--sp-6); }
    .mono { font-family: var(--font-mono); }
    .check { display: flex; gap: var(--sp-3); align-items: center; }
    .inline-error { margin: 0; }
    .muted { margin: 0; color: var(--text-muted); }
    .internal { border: 1px solid var(--border); padding: var(--sp-5); display: flex; flex-direction: column; gap: var(--sp-5); margin: 0; }
    .internal legend { padding: 0 var(--sp-3); font-size: var(--fs-xs); text-transform: uppercase; letter-spacing: .06em; color: var(--text-muted); }
    .area-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; border: 1px solid var(--border); }
    .area-list li + li { border-top: 1px solid var(--border); }
    .area { all: unset; box-sizing: border-box; display: flex; align-items: center; gap: var(--sp-4); width: 100%; padding: var(--sp-4) var(--sp-5); cursor: pointer; }
    .area:hover { background: var(--surface-2); }
    .area:focus-visible { outline: 2px solid var(--action); outline-offset: -2px; }
    .area.is-selected { background: var(--surface-2); box-shadow: inset 3px 0 0 var(--action); }
    .a-ic { color: var(--text-muted); flex: none; }
    .a-body { display: flex; flex-direction: column; min-width: 0; }
    .a-name { font-weight: 500; }
    .a-meta { font-size: var(--fs-xs); color: var(--text-muted); }
    .topics-head { display: flex; align-items: center; gap: var(--sp-4); }
    .topics-title { margin: 0 auto 0 0; font-size: var(--fs-md); font-weight: 600; }
  `]
})
export class CatalogManagementComponent implements OnInit {
  readonly topicColumns: NxColumn[] = [
    { key: 'name', header: 'Tópico', rowHeader: true },
    { key: 'defaultQueueId', header: 'Fila padrão', muted: true },
    { key: 'defaultPriority', header: 'Prioridade', mono: true },
    { key: 'slaDefinitionId', header: 'SLA', muted: true },
    { key: 'active', header: 'Status' },
  ];

  areas = signal<CatalogAreaDto[]>([]);
  topics = signal<CatalogTopicDto[]>([]);
  queues = signal<QueueDto[]>([]);
  slaDefinitions = signal<SlaDefinitionDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  saving = signal(false);

  selectedAreaId = signal<string | null>(null);
  selectedArea = computed(() => this.areas().find(a => a.id === this.selectedAreaId()) ?? null);
  areaTopics = computed(() => this.topics().filter(t => t.areaId === this.selectedAreaId()));
  activeQueues = computed(() => this.queues().filter(q => q.active));

  areaFormOpen = signal(false);
  editingAreaId = signal<string | null>(null);
  areaError = signal<string | null>(null);
  areaForm: AreaForm = { ...EMPTY_AREA };

  formTopic = signal<CatalogTopicDto | null>(null);
  topicFormOpen = signal(false);
  editingTopicId = signal<string | null>(null);
  selectedTopic = signal<CatalogTopicDto | null>(null);
  topicError = signal<string | null>(null);
  topicForm: TopicForm = { ...EMPTY_TOPIC };

  constructor(private catalog: CatalogService, private sla: SlaService) {}

  ngOnInit(): void {
    this.load();
  }

  queueName(id: string | null): string {
    return id ? (this.queues().find(q => q.id === id)?.name ?? 'Fila removida') : 'Triagem manual';
  }

  slaName(id: string | null): string {
    return id ? (this.slaDefinitions().find(s => s.id === id)?.name ?? 'SLA removido') : 'Automático';
  }

  selectArea(a: CatalogAreaDto): void {
    this.selectedAreaId.set(a.id);
    this.closeTopicForm();
    this.formTopic.set(null);
  }

  openForm(): void {
    this.formTopic.set(this.selectedTopic());
  }

  // Áreas
  startAreaCreate(): void {
    this.editingAreaId.set(null);
    this.areaForm = { ...EMPTY_AREA, sortOrder: this.areas().length };
    this.areaError.set(null);
    this.areaFormOpen.set(true);
  }

  startAreaEdit(a: CatalogAreaDto): void {
    this.editingAreaId.set(a.id);
    this.areaForm = { name: a.name, description: a.description ?? '', icon: a.icon ?? '', sortOrder: a.sortOrder, active: a.active };
    this.areaError.set(null);
    this.areaFormOpen.set(true);
  }

  closeAreaForm(): void {
    this.areaFormOpen.set(false);
    this.editingAreaId.set(null);
  }

  saveArea(): void {
    if (this.saving()) return;
    this.saving.set(true);
    this.areaError.set(null);
    const id = this.editingAreaId();
    const f = this.areaForm;
    const body = { name: f.name.trim(), description: f.description, icon: f.icon, sortOrder: f.sortOrder };
    const request$ = id ? this.catalog.updateArea(id, { ...body, active: f.active }) : this.catalog.createArea(body);
    request$.subscribe({
      next: area => {
        this.areas.update(list => (id ? list.map(x => x.id === area.id ? area : x) : [...list, area])
          .sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name)));
        this.saving.set(false);
        this.selectedAreaId.set(area.id);
        this.closeAreaForm();
      },
      error: (e: HttpErrorResponse) => this.fail(e, this.areaError, 'Não foi possível salvar a área.'),
    });
  }

  // Tópicos
  startTopicCreate(): void {
    this.editingTopicId.set(null);
    this.selectedTopic.set(null);
    this.topicForm = { ...EMPTY_TOPIC, sortOrder: this.areaTopics().length };
    this.topicError.set(null);
    this.topicFormOpen.set(true);
  }

  startTopicEdit(t: CatalogTopicDto): void {
    this.editingTopicId.set(t.id);
    this.selectedTopic.set(t);
    this.topicForm = {
      name: t.name, description: t.description ?? '', defaultQueueId: t.defaultQueueId ?? '',
      defaultPriority: t.defaultPriority ?? '', slaDefinitionId: t.slaDefinitionId ?? '', sortOrder: t.sortOrder, active: t.active,
    };
    this.topicError.set(null);
    this.topicFormOpen.set(true);
  }

  closeTopicForm(): void {
    this.topicFormOpen.set(false);
    this.editingTopicId.set(null);
    this.selectedTopic.set(null);
  }

  saveTopic(): void {
    const areaId = this.selectedAreaId();
    if (this.saving() || !areaId) return;
    this.saving.set(true);
    this.topicError.set(null);
    const id = this.editingTopicId();
    const f = this.topicForm;
    // No PATCH, texto vazio limpa o padrão; no POST, vazio vira "sem padrão".
    const body = {
      name: f.name.trim(), description: f.description, defaultQueueId: f.defaultQueueId,
      defaultPriority: f.defaultPriority, slaDefinitionId: f.slaDefinitionId, sortOrder: f.sortOrder,
    };
    const request$ = id
      ? this.catalog.updateTopic(id, { ...body, active: f.active })
      : this.catalog.createTopic({ ...body, areaId });
    request$.subscribe({
      next: topic => {
        this.topics.update(list => id ? list.map(x => x.id === topic.id ? topic : x) : [...list, topic]);
        this.saving.set(false);
        this.closeTopicForm();
        this.refreshAreas();
      },
      error: (e: HttpErrorResponse) => this.fail(e, this.topicError, 'Não foi possível salvar o tópico.'),
    });
  }

  private refreshAreas(): void {
    this.catalog.listAreas().subscribe(list => this.areas.set(list));
  }

  private fail(e: HttpErrorResponse, target: { set(v: string | null): void }, fallback: string): void {
    this.saving.set(false);
    const detail = e.error?.detail;
    target.set(e.status === 422 && typeof detail === 'string' && detail !== 'Request validation failed' ? detail : fallback);
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({
      areas: this.catalog.listAreas(),
      topics: this.catalog.listTopics(),
      queues: this.catalog.listQueues(),
      sla: this.sla.listDefinitions(true),
    }).subscribe({
      next: ({ areas, topics, queues, sla }) => {
        this.areas.set(areas);
        this.topics.set(topics);
        this.queues.set(queues);
        this.slaDefinitions.set(sla);
        if (areas.length > 0) this.selectedAreaId.set(areas[0].id);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Não foi possível carregar o catálogo.');
        this.loading.set(false);
      },
    });
  }
}
