import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { forkJoin } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { TicketService } from '../../core/ticketing/ticket.service';
import { SlaService } from '../../core/sla/sla.service';
import { PlatformService } from '../../core/platform/platform.service';

interface DashboardStat {
  label: string;
  value: string | number;
  icon: string;
  accent: 'k-info' | 'k-critical' | 'k-success' | 'k-accent';
}

interface ActivityItem {
  id: string;
  title: string;
  icon: string;
  time: string;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, MatIconModule],
  template: `
    <div class="dash-header">
      <div>
        <h1 class="page-title">Dashboard</h1>
        <p class="welcome-text">Bem-vindo, {{ authService.user()?.firstName || 'Usuário' }}!</p>
      </div>
      <a class="btn-primary" routerLink="/tickets/new">
        <mat-icon>add</mat-icon>
        <span>Novo Ticket</span>
      </a>
    </div>

    <div class="stats-grid">
      @for (stat of stats(); track stat.label) {
        <div class="kpi" [class]="stat.accent">
          <mat-icon class="kpi-ic">{{ stat.icon }}</mat-icon>
          <div class="num">{{ stat.value }}</div>
          <div class="lbl">{{ stat.label }}</div>
        </div>
      }
    </div>

    <div class="dashboard-sections">
      <div class="card">
        <h3 class="card-title">Ações Rápidas</h3>
        <div class="actions-grid">
          <a class="action-btn" routerLink="/tickets/new">
            <mat-icon>add_task</mat-icon>
            <span>Novo Ticket</span>
          </a>
          <a class="action-btn" routerLink="/tickets">
            <mat-icon>list_alt</mat-icon>
            <span>Ver Tickets</span>
          </a>
          <a class="action-btn" routerLink="/assets/new">
            <mat-icon>add_circle</mat-icon>
            <span>Novo Asset</span>
          </a>
          <a class="action-btn" routerLink="/knowledge/new">
            <mat-icon>article</mat-icon>
            <span>Novo Artigo</span>
          </a>
        </div>
      </div>

      <div class="card">
        <h3 class="card-title">Atividade Recente</h3>
        <p class="card-subtitle">Últimas ações no sistema</p>
        <div class="activity-list">
          @for (activity of recentActivity(); track activity.id) {
            <div class="activity-item">
              <mat-icon class="activity-ic">{{ activity.icon }}</mat-icon>
              <div class="activity-content">
                <span class="activity-title">{{ activity.title }}</span>
                <span class="activity-time">{{ activity.time }}</span>
              </div>
            </div>
          } @empty {
            <p class="no-activity">Nenhuma atividade recente</p>
          }
        </div>
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
    }

    .dash-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 16px;
      flex-wrap: wrap;
      margin-bottom: 24px;
    }

    .page-title {
      margin: 0 0 4px 0;
      font-size: 1.5rem;
      font-weight: 600;
      color: var(--text);
    }

    .welcome-text {
      margin: 0;
      color: var(--text-muted);
      font-size: 0.9rem;
    }

    .btn-primary {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      height: 38px;
      padding: 0 18px;
      border-radius: var(--radius-s);
      background: var(--accent);
      color: #fff;
      font-weight: 500;
      font-size: 13px;
      text-decoration: none;

      mat-icon { font-size: 18px; width: 18px; height: 18px; }

      &:hover { filter: brightness(1.08); }
    }

    .stats-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 14px;
      margin-bottom: 24px;
    }

    .kpi-ic {
      color: var(--text-faint);
      font-size: 20px;
      width: 20px;
      height: 20px;
      margin-bottom: 8px;
    }

    .dashboard-sections {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 20px;
    }

    @media (max-width: 960px) {
      .dashboard-sections {
        grid-template-columns: 1fr;
      }
    }

    .card-title {
      margin: 0;
      font-size: 14px;
      font-weight: 600;
      color: var(--text);
    }

    .card-subtitle {
      margin: 2px 0 14px;
      font-size: 12.5px;
      color: var(--text-muted);
    }

    .actions-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 10px;
    }

    .action-btn {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 8px;
      padding: 16px;
      min-height: 84px;
      text-align: center;
      border: 1px solid var(--border);
      border-radius: var(--radius-m);
      background: var(--surface-2);
      color: var(--text);
      font-size: 12.5px;
      font-weight: 500;
      text-decoration: none;

      mat-icon { font-size: 24px; width: 24px; height: 24px; color: var(--accent); }

      &:hover { background: var(--surface-3); border-color: var(--border-strong); }
    }

    .activity-list {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .activity-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 10px 12px;
      border-radius: var(--radius-s);
      border: 1px solid var(--border);

      &:hover { background: var(--surface-2); }
    }

    .activity-ic {
      flex: none;
      color: var(--text-faint);
      font-size: 18px;
      width: 18px;
      height: 18px;
    }

    .activity-content {
      display: flex;
      flex-direction: column;
      gap: 2px;
      min-width: 0;
    }

    .activity-title {
      font-weight: 500;
      color: var(--text);
      font-size: 13px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .activity-time {
      font-size: 11.5px;
      color: var(--text-faint);
    }

    .no-activity {
      text-align: center;
      color: var(--text-faint);
      padding: 32px;
      margin: 0;
      font-size: 13px;
    }
  `]
})
export class DashboardComponent implements OnInit {
  stats = signal<DashboardStat[]>([]);
  recentActivity = signal<ActivityItem[]>([]);

  constructor(
    public authService: AuthService,
    private ticketService: TicketService,
    private slaService: SlaService,
    private platformService: PlatformService
  ) {}

  ngOnInit(): void {
    forkJoin({
      ticketStats: this.ticketService.stats(),
      activeBreaches: this.slaService.listActiveBreaches(),
      auditLogs: this.platformService.listAuditLogs(0, 5)
    }).subscribe({
      next: ({ ticketStats, activeBreaches, auditLogs }) => {
        this.stats.set([
          { label: 'Tickets Abertos', value: ticketStats.countsByStatus['OPEN'] ?? 0, icon: 'assignment', accent: 'k-info' },
          { label: 'Em Andamento', value: ticketStats.countsByStatus['IN_PROGRESS'] ?? 0, icon: 'sync', accent: 'k-accent' },
          { label: 'Atribuídos a Mim', value: ticketStats.assignedToMe, icon: 'person', accent: 'k-success' },
          { label: 'SLA em Risco', value: activeBreaches.length, icon: 'warning', accent: 'k-critical' }
        ]);

        this.recentActivity.set(
          auditLogs.content.map(log => ({
            id: log.id,
            title: this.describeAuditLog(log),
            icon: 'history',
            time: new Date(log.createdAt).toLocaleString('pt-BR')
          }))
        );
      },
      error: () => {
        this.stats.set([]);
        this.recentActivity.set([]);
      }
    });
  }

  private describeAuditLog(log: { eventType: string; action: string | null; resourceType: string | null; resourceId: string | null }): string {
    const parts = [log.action ?? log.eventType];
    if (log.resourceType) parts.push(log.resourceType);
    if (log.resourceId) parts.push(`#${log.resourceId.slice(0, 8)}`);
    return parts.join(' ');
  }
}
