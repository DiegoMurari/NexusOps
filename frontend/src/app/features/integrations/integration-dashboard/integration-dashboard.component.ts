import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { IntegrationOverview, IntegrationService } from '../../../core/integrations/integration.service';

@Component({
  selector: 'app-integration-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, MatIconModule],
  template: `
    <div class="page-header">
      <h1 class="page-title">Integrações</h1>
    </div>

    <div class="notice">
      <mat-icon>info</mat-icon>
      <span>Webhooks e conexões são cadastrados e validados, mas o envio de eventos e a sincronização com serviços externos ainda não estão disponíveis.</span>
    </div>

    @if (error()) {
      <p class="inline-error">{{ error() }}</p>
    }

    <div class="cards">
      <a class="card nav-card" routerLink="/integrations/webhooks">
        <mat-icon class="ic">webhook</mat-icon>
        <div class="body">
          <h2 class="card-title">Webhooks</h2>
          <p class="card-text">Endpoints HTTPS que receberão eventos do NexusOps.</p>
          <span class="count">{{ overview()?.webhooks ?? '—' }} cadastrados · {{ overview()?.activeWebhooks ?? '—' }} ativos</span>
        </div>
      </a>
      <a class="card nav-card" routerLink="/integrations/jira">
        <mat-icon class="ic">bug_report</mat-icon>
        <div class="body">
          <h2 class="card-title">Jira</h2>
          <p class="card-text">Configuração da conexão com projetos Jira.</p>
          <span class="count">{{ overview()?.connectorsByType?.['JIRA'] ?? 0 }} conexões</span>
        </div>
      </a>
      <a class="card nav-card" routerLink="/integrations/slack">
        <mat-icon class="ic">forum</mat-icon>
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
    .page-header { margin-bottom: 16px; }
    .page-title { margin: 0; font-size: 1.5rem; font-weight: 600; color: var(--text); }
    .notice {
      display: flex; align-items: center; gap: 8px; padding: 10px 14px; margin-bottom: 16px; border-radius: var(--radius-s);
      background: var(--warning-soft); color: var(--warning); font-size: 12.5px;
      mat-icon { font-size: 18px; width: 18px; height: 18px; flex: none; }
    }
    .cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 16px; }
    .nav-card {
      display: flex; gap: 14px; padding: 20px; text-decoration: none; color: inherit;
      &:hover { background: var(--surface-2); }
    }
    .ic { font-size: 28px; width: 28px; height: 28px; color: var(--accent); flex: none; }
    .card-title { margin: 0 0 4px; font-size: 15px; font-weight: 600; color: var(--text); }
    .card-text { margin: 0 0 8px; font-size: 13px; color: var(--text-muted); }
    .count { font-size: 12px; color: var(--text-faint); }
    .inline-error { color: var(--critical); font-size: 13px; margin: 0 0 12px; }
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
