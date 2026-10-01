import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { SlaService, SlaDefinitionDto } from '../../../core/sla/sla.service';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-sla-definition-list',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule],
  template: `
    <div class="page-header">
      <h1 class="page-title">Definições de SLA</h1>
      <button class="btn-primary" (click)="showForm.set(!showForm())">
        <mat-icon>add</mat-icon>
        <span>Nova Definição</span>
      </button>
    </div>

    @if (showForm()) {
      <div class="card form">
        <div class="field-row">
          <label class="field">
            <span class="field-label">Nome*</span>
            <input type="text" [(ngModel)]="newName" placeholder="Ex.: SLA Padrão - Incidentes Críticos" />
          </label>
          <label class="field">
            <span class="field-label">Aplica-se a prioridade</span>
            <select [(ngModel)]="newPriority">
              <option value="">Todas</option>
              <option value="LOW">Baixa</option>
              <option value="MEDIUM">Média</option>
              <option value="HIGH">Alta</option>
              <option value="CRITICAL">Crítica</option>
            </select>
          </label>
        </div>
        <div class="field-row">
          <label class="field">
            <span class="field-label">Tempo de resposta (min)</span>
            <input type="number" [(ngModel)]="newResponseMinutes" min="1" />
          </label>
          <label class="field">
            <span class="field-label">Tempo de resolução (min)</span>
            <input type="number" [(ngModel)]="newResolutionMinutes" min="1" />
          </label>
        </div>
        @if (createError()) {
          <div class="form-error">
            <mat-icon>error_outline</mat-icon>
            <span>{{ createError() }}</span>
          </div>
        }
        <div class="form-actions">
          <button class="btn-secondary" (click)="showForm.set(false)">Cancelar</button>
          <button class="btn-primary" [disabled]="!newName || creating()" (click)="create()">
            <mat-icon>check</mat-icon>
            <span>Criar</span>
          </button>
        </div>
      </div>
    }

    @if (loading()) {
      <div class="card empty-state">
        <mat-icon class="empty-ic">hourglass_empty</mat-icon>
        <p>Carregando definições…</p>
      </div>
    } @else if (error()) {
      <div class="card empty-state error">
        <mat-icon class="empty-ic">error_outline</mat-icon>
        <p>{{ error() }}</p>
      </div>
    } @else if (definitions().length === 0) {
      <div class="card empty-state">
        <mat-icon class="empty-ic">rule</mat-icon>
        <p>Nenhuma definição de SLA cadastrada</p>
      </div>
    } @else {
      <div class="card table-card">
        <table class="def-table">
          <thead>
            <tr>
              <th>Nome</th>
              <th>Prioridade</th>
              <th>Resposta</th>
              <th>Resolução</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            @for (def of definitions(); track def.id) {
              <tr>
                <td>{{ def.name }}</td>
                <td class="muted">{{ def.appliesToPriority || 'Todas' }}</td>
                <td class="mono">{{ def.responseTimeMinutes ? def.responseTimeMinutes + ' min' : '—' }}</td>
                <td class="mono">{{ def.resolutionTimeMinutes ? def.resolutionTimeMinutes + ' min' : '—' }}</td>
                <td>
                  <span class="status-tag" [class]="def.active ? 'resolved' : 'closed'">{{ def.active ? 'Ativa' : 'Inativa' }}</span>
                </td>
              </tr>
            }
          </tbody>
        </table>
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

    .btn-primary, .btn-secondary {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      height: 38px;
      padding: 0 18px;
      border-radius: var(--radius-s);
      font-weight: 500;
      font-size: 13px;
      border: none;
      cursor: pointer;

      mat-icon { font-size: 18px; width: 18px; height: 18px; }
    }

    .btn-primary {
      background: var(--accent);
      color: #fff;

      &:hover:not(:disabled) { filter: brightness(1.08); }
      &:disabled { opacity: 0.5; cursor: default; }
    }

    .btn-secondary {
      background: var(--surface-2);
      color: var(--text);
      border: 1px solid var(--border);

      &:hover { background: var(--surface-3); }
    }

    .form {
      display: flex;
      flex-direction: column;
      gap: 14px;
      margin-bottom: 20px;
    }

    .field-row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 16px;
    }

    .field {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .field-label {
      font-size: 12.5px;
      font-weight: 600;
      color: var(--text-muted);
    }

    input, select {
      font-family: var(--sans);
      font-size: 13px;
      color: var(--text);
      background: var(--surface-2);
      border: 1px solid var(--border);
      border-radius: var(--radius-s);
      padding: 9px 12px;
      outline: none;

      &:focus { border-color: var(--accent); }
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

      mat-icon { font-size: 18px; width: 18px; height: 18px; }
    }

    .form-actions {
      display: flex;
      justify-content: flex-end;
      gap: 10px;
    }

    .table-card {
      padding: 0;
      overflow-x: auto;
    }

    .def-table {
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

    .muted { color: var(--text-faint); }

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
export class SlaDefinitionListComponent implements OnInit {
  definitions = signal<SlaDefinitionDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  showForm = signal(false);
  creating = signal(false);
  createError = signal<string | null>(null);

  newName = '';
  newPriority = '';
  newResponseMinutes: number | null = null;
  newResolutionMinutes: number | null = null;

  constructor(private slaService: SlaService, private authService: AuthService) {}

  ngOnInit(): void {
    this.load();
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

  create(): void {
    const user = this.authService.user();
    if (!user || !this.newName) return;

    this.creating.set(true);
    this.createError.set(null);
    this.slaService.createDefinition({
      name: this.newName,
      tenantId: user.tenantId,
      appliesToPriority: this.newPriority || undefined,
      responseTimeMinutes: this.newResponseMinutes ?? undefined,
      resolutionTimeMinutes: this.newResolutionMinutes ?? undefined,
    }).subscribe({
      next: def => {
        this.definitions.update(list => [def, ...list]);
        this.creating.set(false);
        this.showForm.set(false);
        this.newName = '';
        this.newPriority = '';
        this.newResponseMinutes = null;
        this.newResolutionMinutes = null;
      },
      error: () => {
        this.creating.set(false);
        this.createError.set('Não foi possível criar a definição de SLA.');
      }
    });
  }
}
