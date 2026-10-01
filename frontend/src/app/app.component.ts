import { Component, signal, computed, inject, effect } from '@angular/core';
import { Router, RouterOutlet, RouterLink, RouterLinkActive, NavigationEnd } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatBadgeModule } from '@angular/material/badge';
import { AsyncPipe, NgIf, DatePipe, SlicePipe } from '@angular/common';
import { filter, map, startWith } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';
import { AuthService } from './core/auth/auth.service';
import { NotificationService } from './core/notification/notification.service';

interface NavItem {
  label: string;
  icon: string;
  route: string;
}

const SECTION_LABELS: Record<string, string> = {
  dashboard: 'Visão geral',
  tickets: 'Tickets',
  sla: 'SLA',
  assets: 'Assets',
  knowledge: 'Base de conhecimento',
  reports: 'Relatórios',
  integrations: 'Integrações',
  admin: 'Administração',
};

@Component({
  selector: 'nexusops-root',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatIconModule,
    MatMenuModule,
    MatTooltipModule,
    MatBadgeModule,
    AsyncPipe,
    NgIf,
    DatePipe,
    SlicePipe,
  ],
  template: `
    @if (authService.isAuthenticated()) {
      <div class="shell" [class.collapsed]="!expanded()">
        <aside class="sidebar">
          <div class="sb-top">
            <div class="sb-logo" (click)="toggle()" matTooltip="Alternar menu"></div>
            @if (expanded()) {
              <span class="sb-brand">NexusOps</span>
            }
          </div>

          <nav class="sb-nav">
            @for (item of navItems; track item.route) {
              <a
                class="sb-item"
                [routerLink]="item.route"
                routerLinkActive="active"
                [matTooltip]="expanded() ? '' : item.label"
                matTooltipPosition="right"
              >
                <mat-icon class="sb-ic">{{ item.icon }}</mat-icon>
                @if (expanded()) {
                  <span>{{ item.label }}</span>
                }
              </a>
            }
          </nav>

          <div class="sb-spacer"></div>

          @if (authService.isAdmin()) {
            <a class="sb-item" routerLink="/admin" routerLinkActive="active"
               [matTooltip]="expanded() ? '' : 'Administração'" matTooltipPosition="right">
              <mat-icon class="sb-ic">admin_panel_settings</mat-icon>
              @if (expanded()) { <span>Administração</span> }
            </a>
          }

          @if (expanded() && authService.user(); as u) {
            <div class="sb-tenant mono">tenant: {{ u.tenantId | slice:0:8 }}</div>
          }
        </aside>

        <div class="main">
          <header class="topbar">
            <div class="crumb">{{ sectionLabel() }}</div>

            <div class="search">
              <mat-icon>search</mat-icon>
              <input type="text" placeholder="Buscar tickets, assets, artigos…" (keyup.enter)="onSearch($event)" />
              <kbd>⌘K</kbd>
            </div>

            <div class="actions">
              <button class="icon-btn" [matMenuTriggerFor]="notificationsMenu" matTooltip="Notificações" aria-label="Notifications">
                <mat-icon [matBadge]="notificationService.unreadCount()" [matBadgeHidden]="notificationService.unreadCount() === 0" matBadgeColor="warn" matBadgeSize="small">notifications</mat-icon>
              </button>
              <mat-menu #notificationsMenu="matMenu" xPosition="before" class="notification-menu">
                <ng-template matMenuContent>
                  <div class="notification-dropdown">
                    <div class="notification-header">
                      <h3>Notificações</h3>
                      <button class="icon-btn" (click)="notificationService.markAllAsRead()" matTooltip="Marcar todas como lidas">
                        <mat-icon>done_all</mat-icon>
                      </button>
                    </div>
                    <div class="notification-list">
                      @for (notification of notificationService.notifications(); track notification.id) {
                        <div class="notification-item" [class.unread]="!notification.read" (click)="notificationService.markAsRead(notification.id)">
                          <span class="notification-title">{{ notification.title }}</span>
                          <span class="notification-message">{{ notification.message }}</span>
                          <span class="notification-time">{{ notification.createdAt | date:'short' }}</span>
                        </div>
                      } @empty {
                        <div class="notification-empty">Nenhuma notificação</div>
                      }
                    </div>
                  </div>
                </ng-template>
              </mat-menu>

              <button class="avatar-btn" [matMenuTriggerFor]="userMenu" matTooltip="Menu do usuário">
                <span class="avatar"></span>
              </button>
              <mat-menu #userMenu="matMenu" xPosition="before">
                <ng-template matMenuContent>
                  @if (authService.user(); as u) {
                    <div class="user-menu-header">
                      <div class="user-menu-name">{{ u.firstName }} {{ u.lastName }}</div>
                      <div class="user-menu-email">{{ u.email }}</div>
                    </div>
                  }
                  <button mat-menu-item (click)="authService.logout()">
                    <mat-icon>logout</mat-icon>
                    <span>Sair</span>
                  </button>
                </ng-template>
              </mat-menu>
            </div>
          </header>

          <main class="content">
            <router-outlet />
          </main>
        </div>
      </div>
    } @else {
      <router-outlet />
    }
  `,
  styles: [`
    :host {
      display: block;
      height: 100%;
    }

    .shell {
      display: grid;
      grid-template-columns: 212px 1fr;
      height: 100vh;
      background: var(--bg);
      transition: grid-template-columns 0.15s ease;
    }

    .shell.collapsed {
      grid-template-columns: 56px 1fr;
    }

    .sidebar {
      background: var(--surface-2);
      border-right: 1px solid var(--border);
      display: flex;
      flex-direction: column;
      padding: 12px 8px;
      overflow-y: auto;
      overflow-x: hidden;
    }

    .sb-top {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 4px 8px 16px;
      cursor: pointer;
    }

    .sb-logo {
      width: 26px;
      height: 26px;
      border-radius: 7px;
      background: linear-gradient(135deg, var(--accent), var(--accent-2));
      flex: none;
    }

    .sb-brand {
      font-weight: 600;
      font-size: 14px;
      color: var(--text);
      white-space: nowrap;
    }

    .sb-nav {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .sb-item {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 8px;
      border-radius: 8px;
      color: var(--text-muted);
      font-size: 13px;
      text-decoration: none;
      white-space: nowrap;
    }

    .shell.collapsed .sb-item {
      justify-content: center;
    }

    .sb-ic {
      flex: none;
      font-size: 20px;
      width: 20px;
      height: 20px;
    }

    .sb-item:hover:not(.active) {
      background: var(--surface-3);
      color: var(--text);
    }

    .sb-item.active {
      background: var(--accent-soft);
      color: var(--accent);
      font-weight: 600;
    }

    .sb-spacer {
      flex: 1;
    }

    .sb-tenant {
      font-size: 11px;
      color: var(--text-faint);
      padding: 8px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .main {
      display: flex;
      flex-direction: column;
      min-width: 0;
    }

    .topbar {
      height: 52px;
      border-bottom: 1px solid var(--border);
      background: var(--surface);
      display: flex;
      align-items: center;
      gap: 14px;
      padding: 0 18px;
      flex: none;
    }

    .crumb {
      font-size: 13px;
      font-weight: 600;
      color: var(--text);
      white-space: nowrap;
    }

    .search {
      flex: 1;
      max-width: 420px;
      height: 32px;
      border: 1px solid var(--border);
      border-radius: 8px;
      background: var(--surface-2);
      display: flex;
      align-items: center;
      padding: 0 10px;
      gap: 8px;
      color: var(--text-faint);

      mat-icon {
        font-size: 18px;
        width: 18px;
        height: 18px;
      }

      input {
        flex: 1;
        border: none;
        outline: none;
        background: transparent;
        font-size: 13px;
        font-family: var(--sans);
        color: var(--text);

        &::placeholder {
          color: var(--text-faint);
        }
      }

      kbd {
        font-family: var(--mono);
        font-size: 10.5px;
        border: 1px solid var(--border-strong);
        border-radius: 4px;
        padding: 1px 5px;
        color: var(--text-faint);
      }
    }

    .actions {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-left: auto;
    }

    .icon-btn {
      width: 32px;
      height: 32px;
      border-radius: 8px;
      border: 1px solid var(--border);
      background: var(--surface-2);
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      color: var(--text-muted);

      &:hover {
        background: var(--surface-3);
      }
    }

    .avatar-btn {
      border: none;
      background: none;
      padding: 0;
      cursor: pointer;
    }

    .avatar {
      display: block;
      width: 28px;
      height: 28px;
      border-radius: 50%;
      background: linear-gradient(135deg, var(--accent-2), var(--accent));
    }

    .content {
      flex: 1;
      overflow-y: auto;
      padding: 24px;
      max-width: 1400px;
      margin: 0 auto;
      width: 100%;
    }

    .notification-dropdown {
      min-width: 340px;
      max-height: 440px;
    }

    .notification-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 14px 16px;
      border-bottom: 1px solid var(--border);

      h3 {
        margin: 0;
        font-size: 14px;
        font-weight: 600;
        color: var(--text);
      }
    }

    .notification-list {
      max-height: 340px;
      overflow-y: auto;
    }

    .notification-item {
      display: flex;
      flex-direction: column;
      gap: 3px;
      padding: 12px 16px;
      border-bottom: 1px solid var(--border);
      cursor: pointer;

      &:hover { background: var(--surface-2); }
      &.unread { background: var(--accent-soft); }
    }

    .notification-title { font-weight: 600; font-size: 13px; color: var(--text); }
    .notification-message { font-size: 12.5px; color: var(--text-muted); }
    .notification-time { font-size: 11px; color: var(--text-faint); }
    .notification-empty { padding: 28px; text-align: center; color: var(--text-faint); font-size: 13px; }

    .user-menu-header {
      padding: 10px 16px;
      border-bottom: 1px solid var(--border);
      margin-bottom: 4px;
    }

    .user-menu-name { font-weight: 600; font-size: 13px; color: var(--text); }
    .user-menu-email { font-size: 12px; color: var(--text-muted); }
  `],
})
export class AppComponent {
  authService = inject(AuthService);
  notificationService = inject(NotificationService);
  private router = inject(Router);

  expanded = signal(true);

  constructor() {
    effect(() => {
      if (this.authService.isAuthenticated()) {
        this.notificationService.loadNotifications();
      }
    });
  }

  navItems: NavItem[] = [
    { label: 'Visão geral', icon: 'dashboard', route: '/dashboard' },
    { label: 'Tickets', icon: 'assignment', route: '/tickets' },
    { label: 'SLA', icon: 'schedule', route: '/sla' },
    { label: 'Assets', icon: 'dns', route: '/assets' },
    { label: 'Base de conhecimento', icon: 'menu_book', route: '/knowledge' },
    { label: 'Relatórios', icon: 'analytics', route: '/reports' },
    { label: 'Integrações', icon: 'link', route: '/integrations' },
  ];

  private currentSection = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map(e => e.urlAfterRedirects.split('/').filter(Boolean)[0] ?? ''),
      startWith(this.router.url.split('/').filter(Boolean)[0] ?? '')
    ),
    { initialValue: '' }
  );

  sectionLabel = computed(() => SECTION_LABELS[this.currentSection()] ?? 'NexusOps');

  toggle(): void {
    this.expanded.update(v => !v);
  }

  onSearch(event: Event): void {
    const value = (event.target as HTMLInputElement).value.trim();
    if (value) {
      this.router.navigate(['/tickets'], { queryParams: { q: value } });
    }
  }
}
