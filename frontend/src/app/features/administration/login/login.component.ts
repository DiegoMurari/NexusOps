import { Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../../core/auth/auth.service';
import { BrandMarkComponent, ThemeToggleComponent } from '../../../shared/components';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, MatIconModule, BrandMarkComponent, ThemeToggleComponent],
  template: `
    <div class="wrap">
      <div class="theme-corner"><nx-theme-toggle /></div>
      <form class="panel" [formGroup]="loginForm" (ngSubmit)="onSubmit()" novalidate>
        <div class="lockup">
          <nx-brand-mark [size]="40" />
          <div>
            <h1>Nexus<span>Ops</span></h1>
            <p>Operations Console</p>
          </div>
        </div>

        <div class="field">
          <label for="username">E-mail</label>
          <input id="username" type="email" formControlName="username" autocomplete="email" placeholder="voce@empresa.com"
            [attr.aria-invalid]="showError('username')" [attr.aria-describedby]="showError('username') ? 'username-err' : null" />
          @if (showError('username')) {
            <span id="username-err" class="err">
              {{ loginForm.get('username')?.hasError('required') ? 'Informe o e-mail.' : 'E-mail inválido.' }}
            </span>
          }
        </div>

        <div class="field">
          <label for="password">Senha</label>
          <div class="pw">
            <input id="password" [type]="hidePassword ? 'password' : 'text'" formControlName="password" autocomplete="current-password"
              [attr.aria-invalid]="showError('password')" [attr.aria-describedby]="showError('password') ? 'password-err' : null" />
            <button type="button" class="reveal" (click)="hidePassword = !hidePassword"
              [attr.aria-label]="hidePassword ? 'Mostrar senha' : 'Ocultar senha'" [attr.aria-pressed]="!hidePassword">
              <mat-icon aria-hidden="true">{{ hidePassword ? 'visibility_off' : 'visibility' }}</mat-icon>
            </button>
          </div>
          @if (showError('password')) {
            <span id="password-err" class="err">
              {{ loginForm.get('password')?.hasError('required') ? 'Informe a senha.' : 'A senha deve ter pelo menos 8 caracteres.' }}
            </span>
          }
        </div>

        <div class="options">
          <label class="check"><input type="checkbox" formControlName="rememberMe" />Lembrar-me</label>
          <a routerLink="/auth/forgot-password">Esqueci a senha</a>
        </div>

        @if (errorMessage) {
          <p class="banner" role="alert"><mat-icon aria-hidden="true">error_outline</mat-icon>{{ errorMessage }}</p>
        }

        <button type="submit" class="nx-verb primary submit" [disabled]="loading()">
          {{ loading() ? 'Entrando…' : 'Entrar' }}
        </button>

        <p class="foot">Não tem uma conta? <a routerLink="/auth/register">Registre-se</a></p>
      </form>
    </div>
  `,
  styles: [`
    .theme-corner { position: fixed; top: var(--sp-5); right: var(--sp-5); }
    :host { display: block; }
    .wrap { min-height: 100dvh; display: grid; place-items: center; padding: var(--sp-7); background: var(--bg); }
    .panel {
      width: 100%;
      max-width: 400px;
      display: grid;
      gap: var(--sp-6);
      padding: var(--sp-8);
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: var(--radius-m);
    }
    .lockup { display: flex; align-items: center; gap: var(--sp-5); }
    h1 { margin: 0; font-size: var(--fs-xl); line-height: 24px; font-weight: var(--fw-semibold); letter-spacing: -.01em; }
    h1 span { color: var(--accent-2); }
    .lockup p { margin: 2px 0 0; font: var(--fw-regular) var(--fs-xs) var(--mono); color: var(--text-muted); letter-spacing: .04em; text-transform: uppercase; }

    .field { display: grid; gap: var(--sp-3); }
    label { font-size: var(--fs-sm); font-weight: var(--fw-medium); color: var(--text); }
    input[type='email'], input[type='password'], input[type='text'] {
      width: 100%;
      box-sizing: border-box;
      height: var(--control-h-lg);
      padding: 0 var(--sp-5);
      font: var(--fw-regular) var(--fs-base) var(--sans);
      color: var(--text);
      background: var(--surface);
      border: 1px solid var(--border-strong);
      border-radius: var(--radius-s);
    }
    input::placeholder { color: var(--text-faint); }
    input:focus-visible { outline: var(--focus-ring); outline-offset: 1px; border-color: var(--accent); }
    input[aria-invalid='true'] { border-color: var(--critical); }
    .pw { position: relative; }
    .pw input { padding-inline-end: var(--control-h-lg); }
    .reveal {
      position: absolute; inset-block: 0; inset-inline-end: 0;
      width: var(--control-h-lg);
      display: grid; place-items: center;
      border: 0; background: transparent; color: var(--text-muted); cursor: pointer;
    }
    .reveal:focus-visible { outline: var(--focus-ring); outline-offset: -2px; }
    .reveal mat-icon { width: 18px; height: 18px; font-size: 18px; }
    .err { font-size: var(--fs-sm); color: var(--critical); }

    .options { display: flex; align-items: center; justify-content: space-between; font-size: var(--fs-sm); }
    .check { display: inline-flex; align-items: center; gap: var(--sp-3); font-weight: var(--fw-regular); color: var(--text-muted); }
    .check input { width: 16px; height: 16px; accent-color: var(--accent); }
    a { color: var(--accent); text-decoration: none; font-weight: var(--fw-medium); }
    a:hover { text-decoration: underline; }
    a:focus-visible { outline: var(--focus-ring); outline-offset: 2px; }

    .banner { display: flex; align-items: center; gap: var(--sp-4); margin: 0; padding: var(--sp-4) var(--sp-5); border: 1px solid var(--critical); border-radius: var(--radius-s); background: var(--critical-soft); color: var(--critical); font-size: var(--fs-sm); }
    .banner mat-icon { width: 16px; height: 16px; font-size: 16px; flex: none; }

    .submit { width: 100%; justify-content: center; height: var(--control-h-lg); }
    .foot { margin: 0; text-align: center; font-size: var(--fs-sm); color: var(--text-muted); }
  `]
})
export class LoginComponent {
  loginForm: FormGroup;
  hidePassword = true;
  errorMessage = '';
  loading = this.authService.loading;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.loginForm = this.fb.group({
      username: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8)]],
      rememberMe: [false]
    });
  }

  showError(control: string): 'true' | null {
    const c = this.loginForm.get(control);
    return c && c.invalid && (c.touched || c.dirty) ? 'true' : null;
  }

  onSubmit(): void {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    this.errorMessage = '';
    this.authService.login(this.loginForm.value).subscribe({
      next: (response) => {
        if (response.mfaRequired) {
          this.router.navigate(['/mfa-challenge']);
        } else {
          this.router.navigate(['/dashboard']);
        }
      },
      error: (err) => {
        if (this.authService.hasPendingMfa()) {
          this.router.navigate(['/mfa-challenge']);
          return;
        }
        this.errorMessage = err.error?.detail || 'Erro ao fazer login. Verifique suas credenciais.';
      }
    });
  }
}
