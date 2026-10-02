import { Component, ElementRef, HostListener, ViewChild, signal, computed, inject, effect, untracked } from '@angular/core';
import { Router, RouterOutlet, RouterLink, RouterLinkActive, NavigationEnd } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatBadgeModule } from '@angular/material/badge';
import { DatePipe, SlicePipe } from '@angular/common';
import { filter, map, startWith } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';
import { AuthService } from './core/auth/auth.service';
import { NotificationService } from './core/notification/notification.service';
import { TicketService } from './core/ticketing/ticket.service';
import { SlaService } from './core/sla/sla.service';
import { BrandMarkComponent, ThemeToggleComponent } from './shared/components';

interface NavItem {
  label: string;
  icon: string;
  route: string;
  /** Contador operacional: só aparece quando há algo a ler. */
  count?: 'open' | 'breach';
  adminOnly?: boolean;
  /** Recurso cuja leitura é preciso ter para ver o item (ex.: SLA, ASSET). */
  resource?: string;
}

interface NavGroup {
  label: string;
  items: NavItem[];
}

const NAV_GROUPS: NavGroup[] = [
  {
    label: 'Operação',
    items: [
      { label: 'Visão geral', icon: 'dashboard', route: '/dashboard' },
      { label: 'Tickets', icon: 'assignment', route: '/tickets', count: 'open' },
      { label: 'SLA', icon: 'schedule', route: '/sla', count: 'breach', resource: 'SLA' },
      { label: 'Ativos', icon: 'dns', route: '/assets', resource: 'ASSET' },
    ],
  },
  {
    label: 'Conhecimento',
    items: [
      { label: 'Base de conhecimento', icon: 'menu_book', route: '/knowledge', resource: 'KNOWLEDGE' },
      { label: 'Relatórios', icon: 'analytics', route: '/reports', resource: 'REPORT' },
    ],
  },
  {
    label: 'Plataforma',
    items: [
      { label: 'Integrações', icon: 'link', route: '/integrations', resource: 'INTEGRATION' },
      { label: 'Administração', icon: 'admin_panel_settings', route: '/admin', adminOnly: true },
    ],
  },
];

const SECTION_LABELS: Record<string, string> = {
  dashboard: 'visão geral',
  tickets: 'tickets',
  sla: 'sla',
  assets: 'ativos',
  knowledge: 'conhecimento',
  reports: 'relatórios',
  integrations: 'integrações',
  admin: 'administração',
};

const OPEN_STATUSES = ['OPEN', 'IN_PROGRESS', 'WAITING', 'ON_HOLD', 'REOPENED'];

@Component({
  selector: 'nexusops-root',
  standalone: true,
  imports: [
    RouterOutlet,
    ThemeToggleComponent,
    RouterLink,
    RouterLinkActive,
    MatIconModule,
    MatMenuModule,
    MatTooltipModule,
    MatBadgeModule,
    DatePipe,
    SlicePipe,
    BrandMarkComponent,
  ],
  template: `
    @if (authService.isAuthenticated() && !isPortal()) {
      <div class="shell" [class.collapsed]="!expanded()">
        <aside class="sidebar" aria-label="Navegação principal">
          <button type="button" class="brand" (click)="toggle()" [attr.aria-label]="expanded() ? 'Recolher menu' : 'Expandir menu'">
            <nx-brand-mark [size]="28" />
            @if (expanded()) {
              <span class="brand-text">
                <b>Nexus<span>Ops</span></b>
                <small>ops console</small>
              </span>
            }
          </button>

          <nav class="nav">
            @for (group of groups(); track group.label) {
              <div class="grp">
                @if (expanded()) {
                  <div class="gl">{{ group.label }}</div>
                } @else {
                  <div class="gl-rule" aria-hidden="true"></div>
                }
                @for (item of group.items; track item.route) {
                  <a
                    class="ni"
                    [routerLink]="item.route"
                    routerLinkActive="on"
                    [matTooltip]="expanded() ? '' : item.label"
                    matTooltipPosition="right"
                  >
                    <mat-icon class="ic">{{ item.icon }}</mat-icon>
                    @if (expanded()) {
                      <span class="lbl">{{ item.label }}</span>
                      @if (countOf(item); as n) {
                        <span class="n" [class.crit]="item.count === 'breach'">{{ n }}</span>
                      }
                    }
                  </a>
                }
              </div>
            }
          </nav>

          <div class="sb-spacer"></div>

          @if (expanded() && authService.user(); as u) {
            <div class="side-foot">
              <span class="node" aria-hidden="true"></span>
              <span class="tenant">tenant {{ u.tenantId | slice:0:8 }}</span>
            </div>
          }
        </aside>

        <div class="main">
          <header class="topbar">
            <div class="crumbs" aria-label="Localização">
              <span>ops</span>
              <span aria-hidden="true">›</span>
              <b>{{ sectionLabel() }}</b>
              @if (detailLabel(); as d) {
                <span aria-hidden="true">›</span>
                <b>{{ d }}</b>
              }
            </div>

            <label class="cmd">
              <mat-icon aria-hidden="true">search</mat-icon>
              <span class="sr-only">Buscar tickets, ativos e artigos</span>
              <input #cmd type="text" placeholder="Buscar ou executar comando…" (keyup.enter)="onSearch($event)" />
              <kbd aria-hidden="true">Ctrl K</kbd>
            </label>

            <div class="actions">
              <nx-theme-toggle />
              <button class="icon-btn" [matMenuTriggerFor]="notificationsMenu" matTooltip="Notificações" aria-label="Notificações">
                <mat-icon [matBadge]="notificationService.unreadCount()" [matBadgeHidden]="notificationService.unreadCount() === 0" matBadgeColor="warn" matBadgeSize="small">notifications</mat-icon>
              </button>
              <mat-menu #notificationsMenu="matMenu" xPosition="before" class="notification-menu">
                <ng-template matMenuContent>
                  <div class="notification-dropdown">
                    <div class="notification-header">
                      <h3>Notificações</h3>
                      <button class="icon-btn" (click)="notificationService.markAllAsRead()" matTooltip="Marcar todas como lidas" aria-label="Marcar todas como lidas">
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

              <button class="avatar-btn" [matMenuTriggerFor]="userMenu" aria-label="Menu do usuário" matTooltip="Menu do usuário">
                <span class="avatar" aria-hidden="true">{{ initials() }}</span>
              </button>
              <mat-menu #userMenu="matMenu" xPosition="before">
                <ng-template matMenuContent>
                  @if (authService.user(); as u) {
                    <div class="user-menu-header">
                      <div class="user-menu-name">{{ u.firstName }} {{ u.lastName }}</div>
                      <div class="user-menu-email">{{ u.email }}</div>
                    </div>
                  }
                  <a mat-menu-item routerLink="/account/security">
                    <mat-icon>shield</mat-icon>
                    <span>Segurança da conta</span>
                  </a>
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
    :host { display: block; height: 100%; }

    .shell {
      display: grid;
      grid-template-columns: 216px 1fr;
      height: 100vh;
      background: var(--bg);
      transition: grid-template-columns var(--dur-base) var(--ease);
    }
    .shell.collapsed { grid-template-columns: 56px 1fr; }

    .sidebar {
      display: flex;
      flex-direction: column;
      gap: var(--sp-6);
      padding: var(--sp-5) var(--sp-4);
      background: var(--surface);
      border-right: 1px solid var(--border);
      overflow-x: hidden;
      overflow-y: auto;
    }

    .brand {
      display: flex;
      align-items: center;
      gap: var(--sp-5);
      padding: var(--sp-2) var(--sp-4) var(--sp-3);
      border: 0;
      background: transparent;
      color: var(--text);
      text-align: start;
      cursor: pointer;
    }
    .shell.collapsed .brand { padding-inline: var(--sp-2); }
    .brand-text { display: grid; line-height: 1.2; }
    .brand-text b { font-size: var(--fs-md); font-weight: var(--fw-semibold); letter-spacing: -.01em; }
    .brand-text b span { font-weight: var(--fw-regular); color: var(--text-muted); }
    .brand-text small { font-family: var(--mono); font-size: var(--fs-xs); color: var(--text-faint); }

    .nav { display: grid; gap: var(--sp-6); }
    .grp { display: grid; gap: var(--sp-1); }
    .gl {
      padding: 0 var(--sp-5) var(--sp-2);
      font-family: var(--mono);
      font-size: var(--fs-xs);
      font-weight: var(--fw-medium);
      letter-spacing: .08em;
      text-transform: uppercase;
      color: var(--text-faint);
    }
    .gl-rule { height: 1px; margin: 0 var(--sp-3) var(--sp-2); background: var(--border); }

    .ni {
      position: relative;
      display: flex;
      align-items: center;
      gap: var(--sp-5);
      height: var(--control-h-md);
      padding: 0 var(--sp-5);
      border-radius: var(--radius-s);
      color: var(--text-muted);
      font-size: var(--fs-base);
      text-decoration: none;
      white-space: nowrap;
    }
    .shell.collapsed .ni { justify-content: center; padding: 0; }
    .ic { flex: none; width: 18px; height: 18px; font-size: 18px; }
    .lbl { overflow: hidden; text-overflow: ellipsis; }
    .ni:hover:not(.on) { background: var(--surface-2); color: var(--text); }
    .ni.on { background: var(--accent-soft); color: var(--text); font-weight: var(--fw-semibold); }
    .ni.on .ic { color: var(--accent); }
    /* Nexus Rail: trilho azul de 2px na borda da seção ativa. */
    .ni.on::before {
      content: '';
      position: absolute;
      left: calc(var(--sp-4) * -1);
      top: 5px;
      bottom: 5px;
      width: 2px;
      background: var(--accent);
    }
    .n {
      margin-left: auto;
      font-family: var(--mono);
      font-size: var(--fs-xs);
      font-weight: var(--fw-medium);
      font-variant-numeric: tabular-nums;
      color: var(--text-muted);
    }
    .n.crit { color: var(--critical); }

    .sb-spacer { flex: 1; }
    .side-foot {
      display: flex;
      align-items: center;
      gap: var(--sp-4);
      padding: var(--sp-4) var(--sp-5);
      border-top: 1px solid var(--border);
      font-family: var(--mono);
      font-size: var(--fs-xs);
      color: var(--text-faint);
    }
    .node { width: 8px; height: 8px; border-radius: 2px; background: var(--accent-2); flex: none; }
    .tenant { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

    .main { display: flex; flex-direction: column; min-width: 0; }

    .topbar {
      display: flex;
      align-items: center;
      gap: var(--sp-6);
      flex: none;
      height: 44px;
      padding: 0 var(--sp-7);
      background: var(--surface);
      border-bottom: 1px solid var(--border);
    }
    .crumbs {
      display: flex;
      align-items: center;
      gap: var(--sp-4);
      font-family: var(--mono);
      font-size: var(--fs-sm);
      color: var(--text-muted);
      white-space: nowrap;
    }
    .crumbs b { font-weight: var(--fw-medium); color: var(--text); }

    .cmd {
      display: flex;
      align-items: center;
      gap: var(--sp-4);
      flex: 1;
      max-width: 380px;
      height: var(--control-h-sm);
      margin-left: auto;
      padding: 0 var(--sp-4);
      border: 1px solid var(--border-strong);
      border-radius: var(--radius-s);
      background: var(--surface);
      color: var(--text-muted);
    }
    .cmd:focus-within { outline: var(--focus-ring); outline-offset: 0; border-color: var(--accent); }
    .cmd mat-icon { width: 16px; height: 16px; font-size: 16px; }
    .cmd input {
      flex: 1;
      min-width: 0;
      border: 0;
      outline: none;
      background: transparent;
      color: var(--text);
      font: var(--fw-regular) var(--fs-sm) var(--sans);
    }
    .cmd input::placeholder { color: var(--text-faint); }
    .cmd kbd {
      padding: 0 var(--sp-2);
      border: 1px solid var(--border-strong);
      border-radius: var(--radius-xs);
      font: var(--fw-medium) var(--fs-xs) var(--mono);
      color: var(--text-faint);
    }

    .actions { display: flex; align-items: center; gap: var(--sp-4); }
    .icon-btn {
      display: flex;
      align-items: center;
      justify-content: center;
      width: var(--control-h-md);
      height: var(--control-h-md);
      border: 1px solid var(--border);
      border-radius: var(--radius-s);
      background: var(--surface);
      color: var(--text-muted);
      cursor: pointer;
    }
    .icon-btn:hover { background: var(--surface-2); color: var(--text); }

    .avatar-btn { padding: 0; border: 0; background: none; cursor: pointer; }
    /* Identidade: o único acento violeta da região é o avatar. Sem gradiente. */
    .avatar {
      display: grid;
      place-items: center;
      width: 28px;
      height: 28px;
      border: 1px solid color-mix(in srgb, var(--accent-2) 40%, transparent);
      border-radius: 50%;
      background: var(--accent-2-soft);
      color: var(--accent-2);
      font: var(--fw-semibold) var(--fs-xs) var(--mono);
    }

    .content {
      flex: 1;
      width: 100%;
      max-width: var(--page-max);
      margin: 0 auto;
      padding: var(--sp-8);
      overflow-y: auto;
    }

    .notification-dropdown { min-width: 340px; max-height: 440px; }
    .notification-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: var(--sp-5) var(--sp-6);
      border-bottom: 1px solid var(--border);
    }
    .notification-header h3 { margin: 0; font-size: var(--fs-md); font-weight: var(--fw-semibold); color: var(--text); }
    .notification-list { max-height: 340px; overflow-y: auto; }
    .notification-item {
      display: flex;
      flex-direction: column;
      gap: 3px;
      padding: var(--sp-5) var(--sp-6);
      border-bottom: 1px solid var(--border);
      cursor: pointer;
    }
    .notification-item:hover { background: var(--surface-2); }
    .notification-item.unread { background: var(--accent-soft); }
    .notification-title { font-weight: var(--fw-semibold); font-size: var(--fs-base); color: var(--text); }
    .notification-message { font-size: var(--fs-sm); color: var(--text-muted); }
    .notification-time { font-size: var(--fs-xs); color: var(--text-faint); }
    .notification-empty { padding: var(--sp-9); text-align: center; color: var(--text-faint); font-size: var(--fs-base); }

    .user-menu-header { padding: var(--sp-4) var(--sp-6); border-bottom: 1px solid var(--border); margin-bottom: var(--sp-2); }
    .user-menu-name { font-weight: var(--fw-semibold); font-size: var(--fs-base); color: var(--text); }
    .user-menu-email { font-size: var(--fs-sm); color: var(--text-muted); }
  `],
})
export class AppComponent {
  authService = inject(AuthService);
  notificationService = inject(NotificationService);
  private router = inject(Router);
  private ticketService = inject(TicketService);
  private slaService = inject(SlaService);

  @ViewChild('cmd') private cmd?: ElementRef<HTMLInputElement>;

  expanded = signal(true);
  private openCount = signal(0);
  private breachCount = signal(0);

  groups = computed(() =>
    NAV_GROUPS.map(g => ({
      ...g,
      items: g.items.filter(i => (!i.adminOnly || this.authService.isAdmin()) && (!i.resource || this.authService.can(i.resource, 'READ'))),
    })).filter(g => g.items.length > 0)
  );

  private currentUrl = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map(e => e.urlAfterRedirects),
      startWith(this.router.url)
    ),
    { initialValue: '' }
  );

  private segments = computed(() => this.currentUrl().split('?')[0].split('/').filter(Boolean));
  /** O Portal do Solicitante tem layout próprio: não é o console com menos itens. */
  isPortal = computed(() => ['portal', 'account'].includes(this.segments()[0] ?? ''));
  sectionLabel = computed(() => SECTION_LABELS[this.segments()[0] ?? ''] ?? 'nexusops');
  detailLabel = computed(() => {
    const second = this.segments()[1];
    if (!second) return null;
    if (second === 'new') return 'novo';
    return /^[0-9a-f-]{8,}$/i.test(second) || second.length > 14 ? 'detalhe' : second;
  });

  initials = computed(() => {
    const u = this.authService.user();
    const first = u?.firstName?.[0] ?? u?.email?.[0] ?? '?';
    const last = u?.lastName?.[0] ?? '';
    return (first + last).toUpperCase();
  });

  constructor() {
    effect(() => {
      if (this.authService.isAuthenticated()) {
        this.notificationService.loadNotifications();
      }
    });

    // Os contadores do menu acompanham a navegação; falhar aqui nunca deve quebrar o shell.
    effect(() => {
      this.currentUrl();
      if (this.authService.isAuthenticated() && this.authService.isAgent() && !this.isPortal()) {
        untracked(() => this.refreshCounts());
      }
    });
  }

  countOf(item: NavItem): number {
    if (item.count === 'open') return this.openCount();
    if (item.count === 'breach') return this.breachCount();
    return 0;
  }

  toggle(): void {
    this.expanded.update(v => !v);
  }

  @HostListener('document:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      this.cmd?.nativeElement.focus();
    }
  }

  onSearch(event: Event): void {
    const value = (event.target as HTMLInputElement).value.trim();
    if (value) {
      this.router.navigate(['/tickets'], { queryParams: { q: value } });
    }
  }

  private refreshCounts(): void {
    // Cada contador falha sozinho: quem não pode ver violações de SLA ainda vê o total de abertos.
    this.ticketService.stats().subscribe({
      next: stats => this.openCount.set(OPEN_STATUSES.reduce((sum, s) => sum + (stats.countsByStatus[s] ?? 0), 0)),
      error: () => this.openCount.set(0),
    });
    if (!this.authService.can('SLA', 'READ')) {
      this.breachCount.set(0);
      return;
    }
    this.slaService.listActiveBreaches().subscribe({
      next: breaches => this.breachCount.set(breaches.length),
      error: () => this.breachCount.set(0),
    });
  }
}
