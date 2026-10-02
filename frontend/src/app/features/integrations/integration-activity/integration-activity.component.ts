import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  EVENT_LABELS, IntegrationKind, IntegrationLogDto, IntegrationOutcome, IntegrationService, describeResult,
} from '../../../core/integrations/integration.service';
import {
  ButtonComponent, DataTableComponent, EmptyStateComponent, NxCellDirective, NxColumn, StatusBadgeComponent,
} from '../../../shared/components';

/** Histórico de atividade das integrações: quem fez o quê, quando e com que resultado. */
@Component({
  selector: 'app-integration-activity',
  standalone: true,
  imports: [CommonModule, ButtonComponent, DataTableComponent, EmptyStateComponent, NxCellDirective, StatusBadgeComponent],
  template: `
    <div class="head">
      <h2 class="title">Atividade recente</h2>
      <div class="filters">
        <select class="input" aria-label="Filtrar por tipo" (change)="setKind($any($event.target).value)">
          <option value="">Todos os tipos</option>
          <option value="WEBHOOK">Webhooks</option>
          <option value="CONNECTOR">Conexões</option>
        </select>
        <select class="input" aria-label="Filtrar por resultado" (change)="setOutcome($any($event.target).value)">
          <option value="">Todos os resultados</option>
          <option value="SUCCESS">Sucesso</option>
          <option value="FAILURE">Falha</option>
        </select>
      </div>
    </div>

    @if (error()) {
      <nx-empty-state variant="error" [heading]="error()!" />
    } @else {
      <nx-data-table caption="Atividade das integrações" [columns]="columns" [rows]="logs()" [loading]="loading()"
                     emptyTitle="Nenhuma atividade registrada"
                     emptyDescription="Criar, alterar, ativar, testar ou verificar uma integração aparece aqui.">
        <ng-template nxCell="createdAt" let-l>{{ l.createdAt | date:'dd/MM/yyyy HH:mm:ss' }}</ng-template>
        <ng-template nxCell="integrationName" let-l>
          {{ l.integrationName }} <span class="kind">{{ l.integrationKind === 'WEBHOOK' ? 'webhook' : 'conexão' }}</span>
        </ng-template>
        <ng-template nxCell="event" let-l>{{ eventLabel(l) }}</ng-template>
        <ng-template nxCell="outcome" let-l>
          <nx-status-badge [tone]="l.outcome === 'SUCCESS' ? 'success' : 'critical'">{{ l.outcome === 'SUCCESS' ? 'Sucesso' : 'Falha' }}</nx-status-badge>
        </ng-template>
        <ng-template nxCell="message" let-l>{{ detail(l) }}</ng-template>
        <ng-template nxCell="actor" let-l>{{ l.actor || '—' }}</ng-template>
      </nx-data-table>

      @if (logs().length > 0) {
        <nav class="pagination" aria-label="Paginação da atividade">
          <button nxButton size="sm" [disabled]="loading() || page() === 0" (click)="changePage(page() - 1)">Anterior</button>
          <span class="page-info mono" aria-live="polite">{{ page() + 1 }} / {{ totalPages() || 1 }}</span>
          <button nxButton size="sm" [disabled]="loading() || page() + 1 >= totalPages()" (click)="changePage(page() + 1)">Próxima</button>
        </nav>
      }
    }
  `,
  styles: [`
    :host { display: block; }
    .head { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: var(--sp-5); margin-bottom: var(--sp-5); }
    .title { margin: 0; font-size: var(--fs-lg); line-height: 24px; font-weight: var(--fw-semibold); color: var(--text); }
    .filters { display: flex; flex-wrap: wrap; gap: var(--sp-4); }
    .kind { margin-inline-start: var(--sp-3); font-size: var(--fs-sm); color: var(--text-faint); }
    .pagination { display: flex; align-items: center; justify-content: center; gap: var(--sp-6); margin-top: var(--sp-6); }
    .page-info { font-size: var(--fs-sm); color: var(--text-muted); }
  `]
})
export class IntegrationActivityComponent implements OnInit {
  readonly columns: NxColumn[] = [
    { key: 'createdAt', header: 'Quando', mono: true, muted: true },
    { key: 'integrationName', header: 'Integração', rowHeader: true, maxWidth: '260px' },
    { key: 'event', header: 'Evento' },
    { key: 'outcome', header: 'Resultado' },
    { key: 'message', header: 'Detalhe', muted: true },
    { key: 'actor', header: 'Por', muted: true, maxWidth: '200px' },
  ];

  logs = signal<IntegrationLogDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  page = signal(0);
  totalPages = signal(0);
  private kind: IntegrationKind | '' = '';
  private outcome: IntegrationOutcome | '' = '';

  constructor(private integrationService: IntegrationService) {}

  ngOnInit(): void {
    this.load();
  }

  /** Recarrega a primeira página; a tela pai chama depois de testar/verificar algo. */
  refresh(): void {
    this.page.set(0);
    this.load();
  }

  setKind(value: string): void {
    this.kind = value as IntegrationKind | '';
    this.refresh();
  }

  setOutcome(value: string): void {
    this.outcome = value as IntegrationOutcome | '';
    this.refresh();
  }

  changePage(page: number): void {
    this.page.set(page);
    this.load();
  }

  eventLabel(l: IntegrationLogDto): string {
    return EVENT_LABELS[l.event] ?? l.event;
  }

  detail(l: IntegrationLogDto): string {
    const text = describeResult(l.message);
    return l.durationMs != null && l.message ? `${text} · ${l.durationMs} ms` : text;
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.integrationService.listLogs(this.page(), 15, {
      kind: this.kind || undefined,
      outcome: this.outcome || undefined,
    }).subscribe({
      next: res => {
        this.logs.set(res.content);
        this.totalPages.set(res.totalPages);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Não foi possível carregar a atividade das integrações.');
        this.loading.set(false);
      }
    });
  }
}
