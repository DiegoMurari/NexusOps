import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { AuthService, MfaSetupResponse } from '../../core/auth/auth.service';
import { BrandMarkComponent, ThemeToggleComponent } from '../../shared/components';

/**
 * Segurança da conta: ativar e desativar a verificação em duas etapas. Serve console e Portal, então a página
 * tem a sua própria moldura em vez de depender do menu de nenhum dos dois.
 */
@Component({
  selector: 'app-account-security',
  standalone: true,
  imports: [FormsModule, RouterLink, MatIconModule, BrandMarkComponent, ThemeToggleComponent],
  template: `
    <header class="top">
      <a class="brand" [routerLink]="home()" aria-label="Voltar ao NexusOps"><nx-brand-mark [size]="24" /><b>Nexus<span>Ops</span></b></a>
      <span class="spacer"></span>
      <nx-theme-toggle />
    </header>

    <main class="page">
      <a class="back" [routerLink]="home()"><mat-icon aria-hidden="true">arrow_back</mat-icon>Voltar</a>
      <h1>Segurança da conta</h1>

      <section class="panel" aria-labelledby="mfa-h">
        <div class="head">
          <h2 id="mfa-h">Verificação em duas etapas</h2>
          <span class="state" [class.on]="enabled()">{{ enabled() ? 'Ativa' : 'Desativada' }}</span>
        </div>

        @if (done()) {
          <p class="ok" role="status"><mat-icon aria-hidden="true">check_circle</mat-icon>{{ done() }}</p>
        }
        @if (error()) {
          <p class="banner" role="alert"><mat-icon aria-hidden="true">error_outline</mat-icon>{{ error() }}</p>
        }

        @if (enabled()) {
          <p class="muted">Ao entrar, além da senha você informa o código de 6 dígitos do seu aplicativo autenticador.</p>
          <form class="stack" (ngSubmit)="disable()" novalidate>
            <label class="field">
              <span>Confirme com a sua senha para desativar</span>
              <input type="password" name="password" [(ngModel)]="password" autocomplete="current-password" />
            </label>
            <button type="submit" class="nx-verb" [disabled]="busy() || !password">Desativar verificação</button>
          </form>
        } @else if (!setup()) {
          <p class="muted">Proteja a conta com um código temporário gerado por um aplicativo autenticador (Google Authenticator, Microsoft Authenticator, 1Password e similares).</p>
          <button type="button" class="nx-verb primary start" [disabled]="busy()" (click)="start()">Configurar</button>
        } @else {
          <ol class="steps">
            <li>
              <b>Cadastre a chave no aplicativo.</b> Escolha "inserir chave manualmente" e informe:
              <code class="secret" aria-label="Chave secreta">{{ spaced(setup()!.secret) }}</code>
              <button type="button" class="linklike" (click)="copy(setup()!.secret)">Copiar chave</button>
            </li>
            <li>
              <b>Guarde os códigos de recuperação.</b> Eles são mostrados só agora.
              <ul class="codes">
                @for (c of setup()!.recoveryCodes; track c) { <li>{{ c }}</li> }
              </ul>
            </li>
            <li>
              <b>Confirme com um código do aplicativo.</b>
              <form class="inline" (ngSubmit)="enable()" novalidate>
                <label class="sr-only" for="code">Código de 6 dígitos</label>
                <input id="code" name="code" inputmode="numeric" maxlength="6" placeholder="000000" autocomplete="one-time-code" [(ngModel)]="code" />
                <button type="submit" class="nx-verb primary" [disabled]="busy() || code.length !== 6">Ativar</button>
                <button type="button" class="nx-verb" (click)="cancel()">Cancelar</button>
              </form>
            </li>
          </ol>
        }
      </section>
    </main>
  `,
  styles: [`
    :host { display: block; min-height: 100dvh; background: var(--bg); color: var(--text); }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
    .top { display: flex; align-items: center; gap: var(--sp-5); padding: var(--sp-4) var(--sp-7); background: var(--surface); border-bottom: 1px solid var(--border); }
    .brand { display: inline-flex; align-items: center; gap: var(--sp-4); color: var(--text); text-decoration: none; }
    .brand span { color: var(--accent-2); }
    .spacer { flex: 1; }
    .page { max-width: 640px; margin: 0 auto; padding: var(--sp-7); display: grid; gap: var(--sp-6); }
    .back { display: inline-flex; align-items: center; gap: var(--sp-2); color: var(--text-muted); font-size: var(--fs-sm); text-decoration: none; }
    .back mat-icon { width: 16px; height: 16px; font-size: 16px; }
    h1 { margin: 0; font-size: var(--fs-xl); line-height: 28px; font-weight: var(--fw-semibold); }
    h2 { margin: 0; font-size: var(--fs-base); font-weight: var(--fw-semibold); }
    .panel { display: grid; gap: var(--sp-5); padding: var(--sp-6); background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-m); }
    .head { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-5); }
    .state { padding: 0 var(--sp-3); border: 1px solid var(--border-strong); border-radius: var(--radius-xs); font-size: var(--fs-xs); color: var(--text-muted); }
    .state.on { color: var(--success); border-color: var(--success); }
    .muted { margin: 0; font-size: var(--fs-sm); color: var(--text-muted); }
    .start { justify-self: start; }
    .stack { display: grid; gap: var(--sp-4); max-width: 360px; }
    .stack .nx-verb { justify-self: start; }
    .field { display: grid; gap: var(--sp-3); font-size: var(--fs-sm); font-weight: var(--fw-medium); }
    input { height: var(--control-h-md); padding: 0 var(--sp-4); font: var(--fw-regular) var(--fs-base) var(--sans); color: var(--text); background: var(--surface); border: 1px solid var(--border-strong); border-radius: var(--radius-s); }
    input:focus-visible { outline: var(--focus-ring); outline-offset: 1px; border-color: var(--accent); }
    .steps { margin: 0; padding-inline-start: var(--sp-6); display: grid; gap: var(--sp-6); font-size: var(--fs-sm); }
    .steps li { display: grid; gap: var(--sp-3); }
    .secret { display: block; padding: var(--sp-4); background: var(--surface-2); border: 1px solid var(--border); border-radius: var(--radius-s); font-family: var(--mono); font-size: var(--fs-base); letter-spacing: .08em; overflow-wrap: anywhere; user-select: all; }
    .codes { list-style: none; margin: 0; padding: var(--sp-4); display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--sp-2) var(--sp-6); background: var(--surface-2); border: 1px solid var(--border); border-radius: var(--radius-s); font-family: var(--mono); }
    .inline { display: flex; flex-wrap: wrap; gap: var(--sp-4); align-items: center; }
    .inline input { width: 140px; font-family: var(--mono); letter-spacing: .2em; }
    .linklike { justify-self: start; background: none; border: 0; padding: 0; font: inherit; color: var(--signal); cursor: pointer; }
    .linklike:hover { text-decoration: underline; }
    .ok { display: flex; align-items: center; gap: var(--sp-4); margin: 0; padding: var(--sp-4) var(--sp-5); border: 1px solid var(--success); border-radius: var(--radius-s); color: var(--success); font-size: var(--fs-sm); }
    .banner { display: flex; align-items: center; gap: var(--sp-4); margin: 0; padding: var(--sp-4) var(--sp-5); border: 1px solid var(--critical); border-radius: var(--radius-s); background: var(--critical-soft); color: var(--critical); font-size: var(--fs-sm); }
    .ok mat-icon, .banner mat-icon { width: 16px; height: 16px; font-size: 16px; flex: none; }
  `]
})
export class AccountSecurityComponent {
  private auth = inject(AuthService);

  enabled = signal(this.auth.user()?.mfaEnabled ?? false);
  setup = signal<MfaSetupResponse | null>(null);
  busy = signal(false);
  error = signal<string | null>(null);
  done = signal<string | null>(null);
  code = '';
  password = '';

  /** Quem não é da equipe volta para o Portal. */
  home = computed(() => (this.auth.isAgent() ? '/dashboard' : '/portal'));

  start(): void {
    this.begin();
    this.auth.setupMfa().subscribe({
      next: s => { this.setup.set(s); this.busy.set(false); },
      error: e => this.fail(e, 'Não foi possível iniciar a configuração.'),
    });
  }

  cancel(): void {
    this.setup.set(null);
    this.code = '';
    this.error.set(null);
  }

  enable(): void {
    this.begin();
    this.auth.enableMfa(this.code.trim()).subscribe({
      next: () => {
        this.auth.markMfaEnabled(true);
        this.enabled.set(true);
        this.setup.set(null);
        this.code = '';
        this.busy.set(false);
        this.done.set('Verificação em duas etapas ativada. No próximo login, o código será pedido.');
      },
      error: e => this.fail(e, 'Código inválido. Confira o aplicativo e tente de novo.'),
    });
  }

  disable(): void {
    this.begin();
    this.auth.disableMfa(this.password).subscribe({
      next: () => {
        this.auth.markMfaEnabled(false);
        this.enabled.set(false);
        this.password = '';
        this.busy.set(false);
        this.done.set('Verificação em duas etapas desativada.');
      },
      error: e => this.fail(e, 'Não foi possível desativar. Confira a senha.'),
    });
  }

  spaced(secret: string): string {
    return secret.replace(/(.{4})/g, '$1 ').trim();
  }

  copy(text: string): void {
    navigator.clipboard?.writeText(text).then(() => this.done.set('Chave copiada.'), () => undefined);
  }

  private begin(): void {
    this.busy.set(true);
    this.error.set(null);
    this.done.set(null);
  }

  private fail(err: HttpErrorResponse, fallback: string): void {
    this.busy.set(false);
    const detail = typeof err.error?.detail === 'string' && err.status === 422 ? err.error.detail : null;
    this.error.set(detail === 'Invalid MFA code' ? 'Código inválido. Confira o aplicativo e tente de novo.' : detail ?? fallback);
  }
}
