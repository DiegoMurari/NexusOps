import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { IntegrationOverview, IntegrationService } from '../../../core/integrations/integration.service';
import { NoticeComponent, PageHeaderComponent } from '../../../shared/components';
import { IntegrationActivityComponent } from '../integration-activity/integration-activity.component';

@Component({
  selector: 'app-integration-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, MatIconModule, NoticeComponent, PageHeaderComponent, IntegrationActivityComponent],
  template: `
    <nx-page-header heading="Integrações" />

    <nx-notice tone="info">
      Os webhooks recebem automaticamente os eventos de chamados que assinam, com nova tentativa em caso de falha.
      O teste de envio e a verificação de alcance das conexões ficam registrados abaixo.
      A sincronização com Jira e Slack ainda não está disponível.
    </nx-notice>

    @if (error()) {
      <nx-notice tone="critical">{{ error() }}</nx-notice>
    }

    <div class="cards">
      <a class="card nav-card" routerLink="/integrations/webhooks">
        <mat-icon class="ic" aria-hidden="true">webhook</mat-icon>
        <div class="body">
          <h2 class="card-title">Webhooks</h2>
          <p class="card-text">Endpoints HTTPS que receberão eventos do NexusOps.</p>
          <span class="count">{{ overview()?.webhooks ?? '—' }} cadastrados · {{ overview()?.activeWebhooks ?? '—' }} ativos</span>
          @if (overview()?.failingWebhooks) {
            <span class="alert">{{ overview()!.failingWebhooks }} com falha no último envio</span>
          }
          @if (overview()?.failedDeliveries) {
            <span class="alert">{{ overview()!.failedDeliveries }} entregas desistiram — reenvie em Webhooks</span>
          }
          @if (overview()?.pendingDeliveries) {
            <span class="count">{{ overview()!.pendingDeliveries }} na fila de envio</span>
          }
        </div>
      </a>
      <a class="card nav-card" routerLink="/integrations/jira">
        <mat-icon class="ic" aria-hidden="true">bug_report</mat-icon>
        <div class="body">
          <h2 class="card-title">Jira</h2>
          <p class="card-text">Configuração da conexão com projetos Jira.</p>
          <span class="count">{{ connections('JIRA') }}</span>
        </div>
      </a>
      <a class="card nav-card" routerLink="/integrations/slack">
        <mat-icon class="ic" aria-hidden="true">forum</mat-icon>
        <div class="body">
          <h2 class="card-title">Slack</h2>
          <p class="card-text">Configuração da conexão com canais do Slack.</p>
          <span class="count">{{ connections('SLACK') }}</span>
          @if (overview()?.disabledConnectors) {
            <span class="count">{{ overview()!.disabledConnectors }} desativadas no total</span>
          }
          @if (overview()?.failingConnectors) {
            <span class="alert">{{ overview()!.failingConnectors }} com falha na última verificação</span>
          }
        </div>
      </a>
    </div>

    <app-integration-activity class="activity" />
  `,
  styles: [`
    :host { display: block; }
    .cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: var(--sp-6); margin-bottom: var(--sp-8); }
    .nav-card {
      display: flex; gap: var(--sp-5); padding: var(--sp-7); text-decoration: none; color: inherit;
      transition: background-color var(--dur-fast) var(--ease);
      &:hover { background: var(--surface-2); }
    }
    .ic { width: 24px; height: 24px; font-size: 24px; color: var(--text-muted); flex: none; }
    .body { display: flex; flex-direction: column; min-width: 0; }
    .card-title { margin: 0 0 var(--sp-2); font-size: var(--fs-lg); line-height: 24px; font-weight: var(--fw-semibold); color: var(--text); }
    .card-text { margin: 0 0 var(--sp-4); font-size: var(--fs-base); color: var(--text-muted); }
    .count { font-size: var(--fs-sm); color: var(--text-muted); }
    .alert { margin-top: var(--sp-2); font-size: var(--fs-sm); font-weight: var(--fw-semibold); color: var(--critical); }
    .activity { display: block; }
  `]
})
export class IntegrationDashboardComponent implements OnInit {
  overview = signal<IntegrationOverview | null>(null);
  error = signal<string | null>(null);

  constructor(private integrationService: IntegrationService) {}

  connections(type: string): string {
    const n = this.overview()?.connectorsByType?.[type] ?? 0;
    return `${n} ${n === 1 ? 'conexão' : 'conexões'}`;
  }

  ngOnInit(): void {
    this.integrationService.overview().subscribe({
      next: o => this.overview.set(o),
      error: () => this.error.set('Não foi possível carregar o resumo das integrações.')
    });
  }
}
