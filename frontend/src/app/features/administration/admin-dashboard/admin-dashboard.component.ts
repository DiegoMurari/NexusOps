import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';

interface AdminSection {
  label: string;
  desc: string;
  icon: string;
  route: string;
}

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, MatIconModule],
  template: `
    <div class="page-header">
      <h1 class="page-title">Painel de Administração</h1>
    </div>

    <div class="nav-cards">
      @for (section of sections; track section.route) {
        <a class="card nav-card" [routerLink]="section.route">
          <mat-icon class="nav-ic">{{ section.icon }}</mat-icon>
          <div>
            <div class="nav-title">{{ section.label }}</div>
            <div class="nav-desc">{{ section.desc }}</div>
          </div>
          <mat-icon class="nav-arrow">chevron_right</mat-icon>
        </a>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }

    .page-header { margin-bottom: 20px; }

    .page-title {
      margin: 0;
      font-size: 1.5rem;
      font-weight: 600;
      color: var(--text);
    }

    .nav-cards {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 10px;
    }

    .nav-card {
      display: flex;
      align-items: center;
      gap: 14px;
      text-decoration: none;
      cursor: pointer;

      &:hover { background: var(--surface-3); }
    }

    .nav-ic {
      flex: none;
      color: var(--accent);
      font-size: 24px;
      width: 24px;
      height: 24px;
    }

    .nav-title {
      font-size: 13.5px;
      font-weight: 600;
      color: var(--text);
    }

    .nav-desc {
      font-size: 12px;
      color: var(--text-muted);
      margin-top: 2px;
    }

    .nav-arrow {
      margin-left: auto;
      color: var(--text-faint);
    }
  `]
})
export class AdminDashboardComponent {
  sections: AdminSection[] = [
    { label: 'Usuários', desc: 'Gerenciar contas e permissões', icon: 'group', route: 'users' },
    { label: 'Tenants', desc: 'Organizações da plataforma', icon: 'apartment', route: 'tenants' },
    { label: 'Funções', desc: 'Papéis e permissões do sistema', icon: 'badge', route: 'roles' },
    { label: 'Feature Flags', desc: 'Controlar funcionalidades em rollout', icon: 'flag', route: 'feature-flags' },
    { label: 'Configurações', desc: 'Parâmetros globais do sistema', icon: 'settings', route: 'settings' },
    { label: 'Log de Auditoria', desc: 'Histórico de eventos do sistema', icon: 'history', route: 'audit-logs' }
  ];
}
