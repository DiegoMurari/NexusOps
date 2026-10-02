import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { forkJoin } from 'rxjs';
import { CatalogAreaDto, CatalogService, CatalogTopicDto, QueueDto } from '../../../core/catalog/catalog.service';
import { AssetService, LocationDto } from '../../../core/asset/asset.service';
import { UserAdminService, UserResponse } from '../../../core/iam/user.service';
import {
  AssigneeStrategy, RoutingCondition, RoutingDecision, RoutingRule, RoutingService,
} from '../../../core/routing/routing.service';
import {
  ButtonComponent, DataTableComponent, EmptyStateComponent, NxCellDirective, NxColumn, PageHeaderComponent,
  StatusBadgeComponent,
} from '../../../shared/components';

interface ConditionRow {
  field: string;
  answerKey: string;
  values: string[];
  valuesText: string;
}

const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

@Component({
  selector: 'app-routing-management',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatIconModule, ButtonComponent, DataTableComponent, EmptyStateComponent,
    NxCellDirective, PageHeaderComponent, StatusBadgeComponent,
  ],
  template: `
    <nx-page-header heading="Regras de roteamento">
      <button nxButton variant="primary" (click)="startCreate()">
        <mat-icon>add</mat-icon>
        Nova regra
      </button>
    </nx-page-header>

    <p class="intro">
      O chamado nasce com a fila e a prioridade padrão do tópico. Depois, a primeira regra ativa, na ordem abaixo,
      cujas condições todas casarem, sobrepõe fila, prioridade e responsável. Dentro de uma condição basta um dos valores.
    </p>

    @if (formOpen()) {
      <form class="card form" (ngSubmit)="save()" [attr.aria-label]="editingId() ? 'Editar regra' : 'Nova regra'">
        <h2 class="form-title">{{ editingId() ? 'Editar regra' : 'Nova regra' }}</h2>
        <label class="field">
          <span class="label">Nome *</span>
          <input class="input" name="name" type="text" [(ngModel)]="form.name" required placeholder="Ex.: Acesso urgente vai para Segurança" />
        </label>
        <label class="field">
          <span class="label">Descrição</span>
          <input class="input" name="description" type="text" [(ngModel)]="form.description" />
        </label>

        <fieldset class="block">
          <legend class="legend">Quando (todas as condições)</legend>
          @if (conds.length === 0) {
            <p class="muted">Sem condições: a regra vale para todo chamado que chegar até ela. Use no fim da lista como regra padrão.</p>
          }
          @for (c of conds; track c; let i = $index) {
            <div class="cond">
              <select class="input c-field" [(ngModel)]="c.field" [name]="'cf' + i" (ngModelChange)="c.values = []"
                      aria-label="Campo da condição">
                <option value="TOPIC">Tópico</option>
                <option value="AREA">Área</option>
                <option value="LOCATION">Localidade</option>
                <option value="PRIORITY">Prioridade</option>
                <option value="ANSWER">Resposta do formulário</option>
              </select>
              @if (c.field === 'ANSWER') {
                <input class="input mono c-key" [(ngModel)]="c.answerKey" [name]="'ck' + i" placeholder="chave do campo" aria-label="Chave do campo do formulário" />
                <input class="input c-values" [(ngModel)]="c.valuesText" [name]="'cv' + i" placeholder="valores separados por vírgula" aria-label="Valores aceitos" />
              } @else {
                <select class="input c-values" multiple [(ngModel)]="c.values" [name]="'cm' + i" aria-label="Valores aceitos">
                  @for (o of optionsFor(c.field); track o.value) {
                    <option [value]="o.value">{{ o.label }}</option>
                  }
                </select>
              }
              <button nxButton type="button" (click)="removeCondition(i)" aria-label="Remover condição">
                <mat-icon>close</mat-icon>
              </button>
            </div>
          }
          <button nxButton type="button" (click)="addCondition()"><mat-icon>add</mat-icon>Condição</button>
        </fieldset>

        <fieldset class="block">
          <legend class="legend">Então</legend>
          <div class="field-row">
            <label class="field">
              <span class="label">Fila</span>
              <select class="input" name="queue" [(ngModel)]="form.queueId">
                <option value="">Manter a do tópico</option>
                @for (q of queues(); track q.id) { <option [value]="q.id">{{ q.name }}</option> }
              </select>
            </label>
            <label class="field">
              <span class="label">Prioridade</span>
              <select class="input" name="priority" [(ngModel)]="form.priority">
                <option value="">Manter a do tópico</option>
                @for (p of priorities; track p) { <option [value]="p">{{ priorityLabel(p) }}</option> }
              </select>
            </label>
            <label class="field">
              <span class="label">Responsável</span>
              <select class="input" name="strategy" [(ngModel)]="form.strategy">
                <option value="NONE">Nenhum (só a fila)</option>
                <option value="LEAST_LOADED">Quem tem menos chamados abertos na fila</option>
                <option value="USER">Uma pessoa específica</option>
              </select>
            </label>
          </div>
          @if (form.strategy === 'USER') {
            <label class="field">
              <span class="label">Pessoa *</span>
              <select class="input" name="assignee" [(ngModel)]="form.assigneeId">
                <option value="">Selecione…</option>
                @for (u of activeUsers(); track u.id) {
                  <option [value]="u.id">{{ u.firstName }} {{ u.lastName }} ({{ u.email }})</option>
                }
              </select>
            </label>
          }
          @if (form.strategy === 'LEAST_LOADED' && !form.queueId) {
            <p class="muted">A pessoa é escolhida entre os membros da fila da regra: escolha a fila.</p>
          }
        </fieldset>

        @if (editingId()) {
          <label class="check"><input type="checkbox" name="active" [(ngModel)]="form.active" /> Regra ativa</label>
        }
        @if (formError()) {
          <p class="inline-error" role="alert">{{ formError() }}</p>
        }
        @for (e of fieldErrors(); track e) {
          <p class="inline-error" role="alert">{{ e }}</p>
        }
        <div class="form-actions">
          @if (editingId()) {
            <button nxButton type="button" (click)="remove()"><mat-icon>delete</mat-icon>Excluir</button>
          }
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
      <nx-data-table caption="Regras de roteamento em ordem de avaliação" [columns]="columns" [rows]="rules()" [loading]="loading()"
                     [rowClickable]="true" [selectedRow]="selected()" (rowActivate)="startEdit($any($event))"
                     emptyTitle="Nenhuma regra" emptyDescription="Sem regras, vale só a fila e a prioridade padrão de cada tópico.">
        <ng-template nxCell="order" let-r>
          <span class="order">
            <button nxButton type="button" [disabled]="isFirst(r)" (click)="move(r, -1); $event.stopPropagation()" [attr.aria-label]="'Subir ' + r.name">
              <mat-icon>arrow_upward</mat-icon>
            </button>
            <button nxButton type="button" [disabled]="isLast(r)" (click)="move(r, 1); $event.stopPropagation()" [attr.aria-label]="'Descer ' + r.name">
              <mat-icon>arrow_downward</mat-icon>
            </button>
          </span>
        </ng-template>
        <ng-template nxCell="position" let-r><span class="mono">{{ rules().indexOf(r) + 1 }}</span></ng-template>
        <ng-template nxCell="when" let-r>{{ whenSummary(r) }}</ng-template>
        <ng-template nxCell="then" let-r>{{ thenSummary(r) }}</ng-template>
        <ng-template nxCell="active" let-r>
          <nx-status-badge [tone]="r.active ? 'success' : 'neutral'">{{ r.active ? 'Ativa' : 'Inativa' }}</nx-status-badge>
        </ng-template>
      </nx-data-table>

      <section class="card sim" aria-labelledby="sim-title">
        <h2 id="sim-title" class="form-title">Simulador</h2>
        <p class="muted">Descreva um chamado hipotético e veja para onde ele iria e por quê. Nada é gravado.</p>
        <div class="field-row">
          <label class="field">
            <span class="label">Tópico</span>
            <select class="input" name="simTopic" [(ngModel)]="sim.topicId">
              <option value="">Sem tópico</option>
              @for (t of topics(); track t.id) { <option [value]="t.id">{{ areaName(t.areaId) }} › {{ t.name }}</option> }
            </select>
          </label>
          <label class="field">
            <span class="label">Localidade</span>
            <select class="input" name="simLocation" [(ngModel)]="sim.locationId">
              <option value="">Não informada</option>
              @for (l of locations(); track l.id) { <option [value]="l.id">{{ l.name }}</option> }
            </select>
          </label>
          <label class="field">
            <span class="label">Prioridade informada</span>
            <select class="input" name="simPriority" [(ngModel)]="sim.priority">
              @for (p of priorities; track p) { <option [value]="p">{{ priorityLabel(p) }}</option> }
            </select>
          </label>
        </div>
        <label class="field">
          <span class="label">Respostas do formulário (uma por linha: chave=valor)</span>
          <textarea class="input mono" rows="3" name="simAnswers" [(ngModel)]="sim.answersText" placeholder="urgente=sim"></textarea>
        </label>
        <div class="form-actions">
          <button nxButton variant="primary" type="button" [loading]="simulating()" (click)="simulate()">
            <mat-icon>play_arrow</mat-icon>Simular
          </button>
        </div>

        @if (simError()) { <p class="inline-error" role="alert">{{ simError() }}</p> }
        @if (decision(); as d) {
          <div class="decision" aria-live="polite">
            <dl class="dl">
              <dt>Origem</dt><dd>{{ sourceLabel(d.source) }}@if (d.ruleName) { · {{ d.ruleName }} }</dd>
              <dt>Fila</dt><dd>{{ d.queueName ?? 'Sem fila' }}</dd>
              <dt>Prioridade</dt><dd>{{ d.priority ? priorityLabel(d.priority) : '—' }}</dd>
              <dt>Responsável</dt><dd>{{ d.assigneeName ?? 'Ninguém: fica na fila' }}</dd>
            </dl>
            @for (n of d.notes; track n) { <p class="note">{{ n }}</p> }
            @if (d.trace.length) {
              <h3 class="members-title">Rastro de avaliação</h3>
              <ol class="trace">
                @for (t of d.trace; track t.ruleId) {
                  <li [class.applied]="t.applied">
                    <div class="t-head">
                      <strong>{{ t.ruleName }}</strong>
                      <nx-status-badge [tone]="t.applied ? 'success' : t.matched ? 'info' : 'neutral'">
                        {{ t.applied ? 'Aplicada' : t.matched ? 'Casou, mas outra veio antes' : 'Não casou' }}
                      </nx-status-badge>
                    </div>
                    @for (c of t.conditions; track c.field) {
                      <div class="t-cond" [class.miss]="!c.matched">
                        <mat-icon>{{ c.matched ? 'check' : 'close' }}</mat-icon>
                        <span class="mono">{{ c.field }}</span> quer {{ c.values.join(' ou ') }}; veio {{ c.actual || 'vazio' }}
                      </div>
                    }
                  </li>
                }
              </ol>
            }
          </div>
        }
      </section>
    }
  `,
  styles: [`
    :host { display: block; }
    .intro { margin: 0 0 var(--sp-6); max-width: 70ch; color: var(--text-muted); font-size: var(--fs-sm); }
    .form { display: flex; flex-direction: column; gap: var(--sp-5); margin-bottom: var(--sp-7); }
    .form-title { margin: 0; font-size: 1rem; font-weight: 600; }
    .field-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: var(--sp-6); }
    .mono { font-family: var(--font-mono); }
    .check { display: flex; gap: var(--sp-3); align-items: center; }
    .inline-error { margin: 0; }
    .muted { margin: 0; color: var(--text-muted); font-size: var(--fs-sm); }
    .block { border: 1px solid var(--border); padding: var(--sp-5); display: flex; flex-direction: column; gap: var(--sp-4); min-width: 0; }
    .legend { font-size: var(--fs-sm); font-weight: 600; padding: 0 var(--sp-3); }
    .cond { display: flex; gap: var(--sp-4); align-items: flex-start; flex-wrap: wrap; }
    .c-field { flex: 0 0 200px; width: auto; }
    .c-key { flex: 0 0 160px; width: auto; }
    .c-values { flex: 1 1 260px; min-width: 0; }
    select[multiple].c-values { min-height: 5.5rem; }
    .order { display: inline-flex; gap: var(--sp-2); }
    .members-title { margin: var(--sp-5) 0 0; font-size: var(--fs-sm); font-weight: 600; }
    .sim { display: flex; flex-direction: column; gap: var(--sp-5); margin-top: var(--sp-7); }
    .decision { border-top: 1px solid var(--border); padding-top: var(--sp-5); }
    .dl { display: grid; grid-template-columns: max-content 1fr; gap: var(--sp-2) var(--sp-6); margin: 0; }
    .dl dt { color: var(--text-muted); font-size: var(--fs-sm); }
    .dl dd { margin: 0; }
    .note { margin: var(--sp-3) 0 0; color: var(--text-muted); font-size: var(--fs-sm); }
    .trace { list-style: none; margin: var(--sp-4) 0 0; padding: 0; display: flex; flex-direction: column; gap: var(--sp-4); }
    .trace li { border: 1px solid var(--border); padding: var(--sp-4); display: flex; flex-direction: column; gap: var(--sp-3); }
    .trace li.applied { border-color: var(--signal); }
    .t-head { display: flex; align-items: center; gap: var(--sp-4); justify-content: space-between; }
    .t-cond { display: flex; align-items: center; gap: var(--sp-3); font-size: var(--fs-sm); }
    .t-cond mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .t-cond.miss { color: var(--text-muted); }
  `]
})
export class RoutingManagementComponent implements OnInit {
  readonly priorities = PRIORITIES;
  readonly columns: NxColumn[] = [
    { key: 'position', header: '#' },
    { key: 'name', header: 'Regra', rowHeader: true, maxWidth: '260px' },
    { key: 'when', header: 'Quando', muted: true, maxWidth: '260px' },
    { key: 'then', header: 'Então', maxWidth: '260px' },
    { key: 'active', header: 'Status' },
    { key: 'order', header: 'Ordem' },
  ];

  rules = signal<RoutingRule[]>([]);
  queues = signal<QueueDto[]>([]);
  topics = signal<CatalogTopicDto[]>([]);
  areas = signal<CatalogAreaDto[]>([]);
  locations = signal<LocationDto[]>([]);
  users = signal<UserResponse[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  formOpen = signal(false);
  saving = signal(false);
  formError = signal<string | null>(null);
  fieldErrors = signal<string[]>([]);
  editingId = signal<string | null>(null);
  selected = signal<RoutingRule | null>(null);

  form = this.emptyForm();
  conds: ConditionRow[] = [];

  sim = { topicId: '', locationId: '', priority: 'MEDIUM', answersText: '' };
  decision = signal<RoutingDecision | null>(null);
  simulating = signal(false);
  simError = signal<string | null>(null);

  constructor(
    private routing: RoutingService,
    private catalog: CatalogService,
    private assets: AssetService,
    private userAdmin: UserAdminService,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  activeUsers(): UserResponse[] {
    return this.users().filter(u => u.status === 'ACTIVE');
  }

  areaName(id: string): string {
    return this.areas().find(a => a.id === id)?.name ?? '';
  }

  priorityLabel(p: string): string {
    return ({ LOW: 'Baixa', MEDIUM: 'Média', HIGH: 'Alta', CRITICAL: 'Crítica' } as Record<string, string>)[p] ?? p;
  }

  sourceLabel(s: string): string {
    return ({ TOPIC: 'Padrão do tópico', RULE: 'Regra', MANUAL: 'Fila escolhida manualmente', NONE: 'Nada definiu a fila' } as Record<string, string>)[s] ?? s;
  }

  optionsFor(field: string): { value: string; label: string }[] {
    switch (field) {
      case 'TOPIC': return this.topics().map(t => ({ value: t.id, label: `${this.areaName(t.areaId)} › ${t.name}` }));
      case 'AREA': return this.areas().map(a => ({ value: a.id, label: a.name }));
      case 'LOCATION': return this.locations().map(l => ({ value: l.id, label: l.name }));
      case 'PRIORITY': return PRIORITIES.map(p => ({ value: p, label: this.priorityLabel(p) }));
      default: return [];
    }
  }

  whenSummary(r: RoutingRule): string {
    if (r.conditions.length === 0) return 'Sempre';
    return r.conditions.map(c => `${this.fieldLabel(c.field)} = ${c.values.map(v => this.valueLabel(c.field, v)).join(' | ')}`).join(' e ');
  }

  thenSummary(r: RoutingRule): string {
    const a = r.actions;
    const parts: string[] = [];
    if (a.queueId) parts.push(`fila ${this.queues().find(q => q.id === a.queueId)?.name ?? '?'}`);
    if (a.priority) parts.push(`prioridade ${this.priorityLabel(a.priority)}`);
    if (a.assigneeStrategy === 'LEAST_LOADED') parts.push('responsável: menor carga');
    if (a.assigneeStrategy === 'USER') {
      const u = this.users().find(x => x.id === a.assigneeId);
      parts.push(`responsável ${u ? u.firstName + ' ' + u.lastName : '?'}`);
    }
    return parts.join(', ');
  }

  isFirst(r: RoutingRule): boolean { return this.rules()[0]?.id === r.id; }
  isLast(r: RoutingRule): boolean { const l = this.rules(); return l[l.length - 1]?.id === r.id; }

  move(r: RoutingRule, delta: -1 | 1): void {
    const list = [...this.rules()];
    const i = list.findIndex(x => x.id === r.id);
    const j = i + delta;
    if (i < 0 || j < 0 || j >= list.length) return;
    [list[i], list[j]] = [list[j], list[i]];
    this.rules.set(list); // otimista; volta se o servidor recusar
    this.routing.reorder(list.map(x => x.id)).subscribe({
      next: ordered => this.rules.set(ordered),
      error: () => {
        this.error.set('Não foi possível reordenar as regras.');
        this.reloadRules();
      },
    });
  }

  addCondition(): void {
    this.conds = [...this.conds, { field: 'TOPIC', answerKey: '', values: [], valuesText: '' }];
  }

  removeCondition(i: number): void {
    this.conds = this.conds.filter((_, idx) => idx !== i);
  }

  startCreate(): void {
    this.editingId.set(null);
    this.selected.set(null);
    this.form = this.emptyForm();
    this.conds = [];
    this.formError.set(null);
    this.fieldErrors.set([]);
    this.formOpen.set(true);
  }

  startEdit(r: RoutingRule): void {
    this.editingId.set(r.id);
    this.selected.set(r);
    this.form = {
      name: r.name, description: r.description ?? '', active: r.active,
      queueId: r.actions.queueId ?? '', priority: r.actions.priority ?? '',
      strategy: r.actions.assigneeStrategy, assigneeId: r.actions.assigneeId ?? '',
    };
    this.conds = r.conditions.map(c => c.field.startsWith('ANSWER:')
      ? { field: 'ANSWER', answerKey: c.field.slice(7), values: [], valuesText: c.values.join(', ') }
      : { field: c.field, answerKey: '', values: [...c.values], valuesText: '' });
    this.formError.set(null);
    this.fieldErrors.set([]);
    this.formOpen.set(true);
  }

  closeForm(): void {
    this.formOpen.set(false);
    this.editingId.set(null);
    this.selected.set(null);
  }

  save(): void {
    if (this.saving()) return;
    this.saving.set(true);
    this.formError.set(null);
    this.fieldErrors.set([]);
    const f = this.form;
    const request = {
      name: f.name.trim(),
      description: f.description,
      conditions: this.conds.map((c): RoutingCondition => c.field === 'ANSWER'
        ? { field: 'ANSWER:' + c.answerKey.trim(), values: c.valuesText.split(',').map(v => v.trim()).filter(Boolean) }
        : { field: c.field, values: c.values }),
      actions: {
        queueId: f.queueId || null,
        priority: f.priority || null,
        assigneeStrategy: f.strategy as AssigneeStrategy,
        assigneeId: f.strategy === 'USER' ? (f.assigneeId || null) : null,
      },
    };
    const id = this.editingId();
    const request$ = id ? this.routing.updateRule(id, { ...request, active: f.active }) : this.routing.createRule(request);
    request$.subscribe({
      next: () => {
        this.saving.set(false);
        this.closeForm();
        this.reloadRules();
      },
      error: (e: HttpErrorResponse) => this.fail(e),
    });
  }

  remove(): void {
    const id = this.editingId();
    if (!id) return;
    this.routing.deleteRule(id).subscribe({
      next: () => {
        this.closeForm();
        this.reloadRules();
      },
      error: (e: HttpErrorResponse) => this.fail(e),
    });
  }

  simulate(): void {
    this.simulating.set(true);
    this.simError.set(null);
    const answers: Record<string, unknown> = {};
    for (const line of this.sim.answersText.split('\n')) {
      const at = line.indexOf('=');
      if (at > 0) answers[line.slice(0, at).trim()] = line.slice(at + 1).trim();
    }
    this.routing.simulate({
      topicId: this.sim.topicId || undefined,
      locationId: this.sim.locationId || undefined,
      priority: this.sim.priority,
      answers,
    }).subscribe({
      next: d => {
        this.decision.set(d);
        this.simulating.set(false);
      },
      error: () => {
        this.simError.set('Não foi possível simular.');
        this.simulating.set(false);
      },
    });
  }

  private fieldLabel(field: string): string {
    if (field.startsWith('ANSWER:')) return 'Resposta ' + field.slice(7);
    return ({ TOPIC: 'Tópico', AREA: 'Área', LOCATION: 'Localidade', PRIORITY: 'Prioridade' } as Record<string, string>)[field] ?? field;
  }

  private valueLabel(field: string, value: string): string {
    if (field.startsWith('ANSWER:')) return value;
    return this.optionsFor(field).find(o => o.value === value)?.label ?? value;
  }

  private fail(e: HttpErrorResponse): void {
    this.saving.set(false);
    const extensions = e.error?.extensions;
    if (e.status === 422 && extensions && Object.keys(extensions).length) {
      this.fieldErrors.set(Object.values(extensions).map(String));
      this.formError.set(null);
      return;
    }
    const detail = e.error?.detail;
    this.formError.set(e.status === 422 && typeof detail === 'string' ? detail : 'Não foi possível salvar a regra.');
  }

  private reloadRules(): void {
    this.routing.rules().subscribe({
      next: r => {
        this.rules.set(r);
        this.error.set(null);
      },
      error: () => this.error.set('Não foi possível carregar as regras.'),
    });
  }

  private emptyForm() {
    return { name: '', description: '', active: true, queueId: '', priority: '', strategy: 'NONE' as string, assigneeId: '' };
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({
      rules: this.routing.rules(),
      queues: this.catalog.listQueues(),
      topics: this.catalog.listTopics(),
      areas: this.catalog.listAreas(),
      locations: this.assets.listLocations(),
      users: this.userAdmin.list(),
    }).subscribe({
      next: r => {
        this.rules.set(r.rules);
        this.queues.set(r.queues);
        this.topics.set(r.topics);
        this.areas.set(r.areas);
        this.locations.set(r.locations);
        this.users.set(r.users);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Não foi possível carregar as regras.');
        this.loading.set(false);
      },
    });
  }
}
