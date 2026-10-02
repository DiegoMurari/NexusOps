import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { IntegrationOverview, IntegrationService } from '../../../core/integrations/integration.service';
import { NoticeComponent, PageHeaderComponent } from '../../../shared/components';

@Component({
  selector: 'app-integration-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, MatIconModule, NoticeComponent, PageHeaderComponent],
  template: `
    <nx-page-header heading="Integrações" />

    <nx-notice tone="warning">Webhooks e conexões são cadastrados e validados, mas o envio de eventos e a sincronização com serviços externos ainda não estão disponíveis.</nx-notice>

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
        </div>
      </a>
      <a class="card nav-card" routerLink="/integrations/jira">
        <mat-icon class="ic" aria-hidden="true">bug_report</mat-icon>
        <div class="body">
          <h2 class="card-title">Jira</h2>
          <p class="card-text">Configuração da conexão com projetos Jira.</p>
          <span class="count">{{ overview()?.connectorsByType?.['JIRA'] ?? 0 }} conexões</span>
        </div>
      </a>
      <a class="card nav-card" routerLink="/integrations/slack">
        <mat-icon class="ic" aria-hidden="true">forum</mat-icon>
        <div class="body">
          <h2 class="card-title">Slack</h2>
          <p class="card-text">Configuração da conexão com canais do Slack.</p>
          <span class="count">{{ overview()?.connectorsByType?.['SLACK'] ?? 0 }} conexões</span>
        </div>
      </a>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: var(--sp-6); }
    .nav-card {
      display: flex; gap: var(--sp-5); padding: var(--sp-7); text-decoration: none; color: inherit;
      transition: background-color var(--dur-fast) var(--ease);
      &:hover { background: var(--surface-2); }
    }
    .ic { width: 24px; height: 24px; font-size: 24px; color: var(--text-muted); flex: none; }
    .card-title { margin: 0 0 var(--sp-2); font-size: var(--fs-lg); line-height: 24px; font-weight: var(--fw-semibold); color: var(--text); }
    .card-text { margin: 0 0 var(--sp-4); font-size: var(--fs-base); color: var(--text-muted); }
    .count { font-size: var(--fs-sm); color: var(--text-muted); }
  `]
})
export class IntegrationDashboardComponent implements OnInit {
  overview = signal<IntegrationOverview | null>(null);
  error = signal<string | null>(null);

  constructor(private integrationService: IntegrationService) {}

  ngOnInit(): void {
    this.integrationService.overview().subscribe({
      next: o => this.overview.set(o),
      error: () => this.error.set('Não foi possível carregar o resumo das integrações.')
    });
  }
}
