import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../core/auth/auth.service';
import { PortalService } from '../../core/portal/portal.service';
import { BrandMarkComponent, ButtonComponent, ThemeToggleComponent } from '../../shared/components';

/**
 * Casca do Portal do Solicitante (ADR-013). É uma experiência própria, não o console com menos itens: sem
 * menu de operação, sem contadores de SLA; só "meus pedidos" e "novo pedido".
 */
@Component({
  selector: 'app-portal-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatIconModule, BrandMarkComponent, ButtonComponent, ThemeToggleComponent],
  template: `
    <a class="skip" href="#portal-main">Ir para o conteúdo</a>
    <header class="top">
      <div class="bar">
        <a class="brand" routerLink="/portal" aria-label="NexusOps, Portal do Solicitante">
          <nx-brand-mark [size]="26" />
          <span class="brand-text"><b>Nexus<span>Ops</span></b><small>portal do solicitante</small></span>
        </a>
        <nav class="nav" aria-label="Portal">
          <a routerLink="/portal" routerLinkActive="on" [routerLinkActiveOptions]="{ exact: true }">
            Meus pedidos
            @if (attention() > 0) { <span class="dot" [attr.aria-label]="attention() + ' precisam da sua atenção'">{{ attention() }}</span> }
          </a>
        </nav>
        <span class="spacer"></span>
        <a nxButton variant="primary" routerLink="/portal/novo"><mat-icon>add</mat-icon>Novo pedido</a>
        @if (auth.isAgent()) {
          <a class="plain" routerLink="/dashboard">Console</a>
        }
        <nx-theme-toggle />
        <a class="plain" routerLink="/account/security">Segurança</a>
        <span class="who">{{ auth.user()?.firstName }}</span>
        <button type="button" class="plain" (click)="auth.logout()">Sair</button>
      </div>
    </header>
    <main id="portal-main" class="main"><router-outlet (activate)="refresh()" /></main>
  `,
  styles: [`
    :host { display: block; min-height: 100vh; background: var(--bg); color: var(--text); }
    .skip { position: absolute; left: -999px; top: 0; background: var(--surface); padding: var(--sp-3) var(--sp-5); z-index: 10; }
    .skip:focus { left: var(--sp-4); top: var(--sp-4); outline: 2px solid var(--signal); }
    .top { background: var(--surface); border-bottom: 1px solid var(--border); }
    .bar { max-width: 1040px; margin: 0 auto; padding: var(--sp-4) var(--sp-6); display: flex; align-items: center; gap: var(--sp-6); flex-wrap: wrap; }
    .brand { display: inline-flex; align-items: center; gap: var(--sp-4); text-decoration: none; color: var(--text); }
    .brand-text { display: grid; line-height: 1.1; }
    .brand-text b span { color: var(--identity, var(--signal)); }
    .brand-text small { font-family: var(--font-mono); font-size: var(--fs-xs); color: var(--text-muted); }
    .nav a { color: var(--text-muted); text-decoration: none; font-size: var(--fs-sm); padding: var(--sp-3) var(--sp-4); border-bottom: 2px solid transparent; display: inline-flex; gap: var(--sp-3); align-items: center; }
    .nav a.on { color: var(--text); border-bottom-color: var(--signal); font-weight: var(--fw-medium); }
    .dot { background: var(--warning); color: #fff; font-size: var(--fs-xs); font-family: var(--font-mono); padding: 0 var(--sp-3); border-radius: var(--radius-xs); }
    .spacer { flex: 1; }
    .who { color: var(--text-muted); font-size: var(--fs-sm); }
    .plain { background: none; border: 0; color: var(--text-muted); font: inherit; font-size: var(--fs-sm); cursor: pointer; text-decoration: none; padding: var(--sp-2) var(--sp-3); }
    .plain:hover { color: var(--text); text-decoration: underline; }
    .main { max-width: 1040px; margin: 0 auto; padding: var(--sp-8) var(--sp-6) 64px; }
    @media (max-width: 640px) { .who { display: none; } .bar { padding-inline: var(--sp-5); } .main { padding-inline: var(--sp-5); } }
  `],
})
export class PortalShellComponent implements OnInit {
  protected auth = inject(AuthService);
  private portal = inject(PortalService);
  protected attention = signal(0);

  ngOnInit(): void {
    this.refresh();
  }

  /** O contador do menu acompanha a navegação; falhar aqui nunca quebra a página. */
  refresh(): void {
    this.portal.tickets().subscribe({
      next: list => this.attention.set(list.filter(t => t.needsAttention).length),
      error: () => this.attention.set(0),
    });
  }
}
