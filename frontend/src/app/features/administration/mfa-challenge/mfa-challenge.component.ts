import { Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../../core/auth/auth.service';
import { BrandMarkComponent, ThemeToggleComponent } from '../../../shared/components';

/** Segunda etapa do login: o código de 6 dígitos do autenticador. Mesmo visual da tela de login. */
@Component({
  selector: 'app-mfa-challenge',
  standalone: true,
  imports: [ReactiveFormsModule, MatIconModule, BrandMarkComponent, ThemeToggleComponent],
  template: `
    <div class="wrap">
      <div class="theme-corner"><nx-theme-toggle /></div>
      <form class="panel" [formGroup]="mfaForm" (ngSubmit)="onSubmit()" novalidate>
        <div class="lockup">
          <nx-brand-mark [size]="40" />
          <div>
            <h1>Verificação em duas etapas</h1>
            <p>Código do autenticador</p>
          </div>
        </div>

        <div class="field">
          <label for="code">Código de 6 dígitos</label>
          <input id="code" type="text" inputmode="numeric" formControlName="code" maxlength="6"
            autocomplete="one-time-code" placeholder="000000"
            [attr.aria-invalid]="showError() ? 'true' : null" [attr.aria-describedby]="showError() ? 'code-err' : null" />
          @if (showError()) {
            <span id="code-err" class="err">
              {{ mfaForm.get('code')?.hasError('required') ? 'Informe o código.' : 'O código tem 6 dígitos.' }}
            </span>
          }
        </div>

        @if (errorMessage) {
          <p class="banner" role="alert"><mat-icon aria-hidden="true">error_outline</mat-icon>{{ errorMessage }}</p>
        }

        <button type="submit" class="nx-verb primary submit" [disabled]="loading()">
          {{ loading() ? 'Verificando…' : 'Verificar' }}
        </button>

        <p class="foot"><button type="button" class="back" (click)="goBackToLogin()">Voltar ao login</button></p>
      </form>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .theme-corner { position: fixed; top: var(--sp-5); right: var(--sp-5); }
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
    h1 { margin: 0; font-size: var(--fs-lg); line-height: 24px; font-weight: var(--fw-semibold); }
    .lockup p { margin: 2px 0 0; font: var(--fw-regular) var(--fs-xs) var(--mono); color: var(--text-muted); letter-spacing: .04em; text-transform: uppercase; }

    .field { display: grid; gap: var(--sp-3); }
    label { font-size: var(--fs-sm); font-weight: var(--fw-medium); color: var(--text); }
    input {
      width: 100%;
      box-sizing: border-box;
      height: var(--control-h-lg);
      padding: 0 var(--sp-5);
      font: var(--fw-medium) var(--fs-lg) var(--mono);
      letter-spacing: .3em;
      color: var(--text);
      background: var(--surface);
      border: 1px solid var(--border-strong);
      border-radius: var(--radius-s);
    }
    input::placeholder { color: var(--text-faint); }
    input:focus-visible { outline: var(--focus-ring); outline-offset: 1px; border-color: var(--accent); }
    input[aria-invalid='true'] { border-color: var(--critical); }
    .err { font-size: var(--fs-sm); color: var(--critical); }

    .banner { display: flex; align-items: center; gap: var(--sp-4); margin: 0; padding: var(--sp-4) var(--sp-5); border: 1px solid var(--critical); border-radius: var(--radius-s); background: var(--critical-soft); color: var(--critical); font-size: var(--fs-sm); }
    .banner mat-icon { width: 16px; height: 16px; font-size: 16px; flex: none; }

    .submit { width: 100%; justify-content: center; height: var(--control-h-lg); }
    .foot { margin: 0; text-align: center; font-size: var(--fs-sm); }
    .back { border: 0; background: none; padding: 0; font: inherit; font-weight: var(--fw-medium); color: var(--accent); cursor: pointer; }
    .back:hover { text-decoration: underline; }
    .back:focus-visible { outline: var(--focus-ring); outline-offset: 2px; }
  `]
})
export class MfaChallengeComponent {
  mfaForm: FormGroup;
  errorMessage = '';
  loading = this.authService.loading;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.mfaForm = this.fb.group({
      code: ['', [Validators.required, Validators.pattern('^\\d{6}$')]]
    });
    // Sem um login em andamento não há o que verificar (ex.: página aberta direto ou recarregada).
    if (!this.authService.hasPendingMfa()) {
      this.router.navigate(['/login']);
    }
  }

  showError(): boolean {
    const c = this.mfaForm.get('code');
    return !!c && c.invalid && (c.touched || c.dirty);
  }

  onSubmit(): void {
    if (this.mfaForm.invalid) {
      this.mfaForm.markAllAsTouched();
      return;
    }

    this.errorMessage = '';
    this.authService.verifyMfa(this.mfaForm.value.code).subscribe({
      next: () => {
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.errorMessage = err.error?.detail || 'Código inválido. Tente novamente.';
      }
    });
  }

  goBackToLogin(): void {
    this.router.navigate(['/login']);
  }
}
