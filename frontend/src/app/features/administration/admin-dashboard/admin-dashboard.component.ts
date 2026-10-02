import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { PageHeaderComponent } from '../../../shared/components';

interface AdminSection {
  label: string;
  desc: string;
  icon: string;
  route: string;
}

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, MatIconModule, PageHeaderComponent],
  template: `
    <nx-page-header heading="Painel de administração" />

    <div class="nav-cards">
      @for (section of sections; track section.route) {
        <a class="card nav-card" [routerLink]="section.route">
          <mat-icon class="nav-ic" aria-hidden="true">{{ section.icon }}</mat-icon>
          <div>
            <div class="nav-title">{{ section.label }}</div>
            <div class="nav-desc">{{ section.desc }}</div>
          </div>
          <mat-icon class="nav-arrow" aria-hidden="true">chevron_right</mat-icon>
        </a>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }

    .nav-cards {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: var(--sp-5);
    }

    .nav-card {
      display: flex;
      align-items: center;
      gap: var(--sp-5);
      text-decoration: none;
      cursor: pointer;
      transition: background-color var(--dur-fast) var(--ease);

      &:hover { background: var(--surface-2); }
    }

    .nav-ic {
      flex: none;
      color: var(--text-muted);
      font-size: 24px;
      width: 24px;
      height: 24px;
    }

    .nav-title { font-size: var(--fs-md); font-weight: var(--fw-semibold); color: var(--text); }
    .nav-desc { font-size: var(--fs-sm); color: var(--text-muted); margin-top: var(--sp-1); }
    .nav-arrow { margin-left: auto; color: var(--text-muted); }
  `]
})
export class AdminDashboardComponent {
  sections: AdminSection[] = [
    { label: 'Usuários', desc: 'Gerenciar contas e permissões', icon: 'group', route: 'users' },
    { label: 'Filas', desc: 'Equipes de atendimento e seus membros', icon: 'groups', route: 'queues' },
    { label: 'Catálogo de serviços', desc: 'Áreas e tópicos do Portal, com fila e SLA padrão', icon: 'category', route: 'catalog' },
    { label: 'Roteamento', desc: 'Regras que decidem fila, prioridade e responsável, com simulador', icon: 'alt_route', route: 'routing' },
    { label: 'Tenants', desc: 'Organizações da plataforma', icon: 'apartment', route: 'tenants' },
    { label: 'Funções', desc: 'Papéis e permissões do sistema', icon: 'badge', route: 'roles' },
    { label: 'Feature Flags', desc: 'Controlar funcionalidades em rollout', icon: 'flag', route: 'feature-flags' },
    { label: 'Configurações', desc: 'Parâmetros globais do sistema', icon: 'settings', route: 'settings' },
    { label: 'Log de Auditoria', desc: 'Histórico de eventos do sistema', icon: 'history', route: 'audit-logs' }
  ];
}
