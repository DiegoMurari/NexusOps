import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatCheckboxModule
  ],
  template: `
    <div class="login-container">
      <mat-card class="login-card">
        <mat-card-header>
          <div class="login-header">
            <mat-icon class="login-logo">security</mat-icon>
            <div class="login-title-group">
              <h1 class="login-title">NexusOps</h1>
              <p class="login-subtitle">Service Desk & IT Operations Platform</p>
            </div>
          </div>
        </mat-card-header>
        
        <mat-card-content>
          <form [formGroup]="loginForm" (ngSubmit)="onSubmit()" class="login-form">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Email</mat-label>
              <input matInput type="email" formControlName="username" placeholder="seu@email.com" autocomplete="email">
              <mat-icon matPrefix>email</mat-icon>
              @if (loginForm.get('username')?.hasError('required')) {
                <mat-error>Email é obrigatório</mat-error>
              }
              @if (loginForm.get('username')?.hasError('email')) {
                <mat-error>Email inválido</mat-error>
              }
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Senha</mat-label>
              <input matInput [type]="hidePassword ? 'password' : 'text'" formControlName="password" placeholder="Sua senha" autocomplete="current-password">
              <button mat-icon-button matSuffix type="button" (click)="hidePassword = !hidePassword" [attr.aria-label]="'Ocultar senha'" [attr.aria-pressed]="hidePassword">
                <mat-icon>{{ hidePassword ? 'visibility_off' : 'visibility' }}</mat-icon>
              </button>
              @if (loginForm.get('password')?.hasError('required')) {
                <mat-error>Senha é obrigatória</mat-error>
              }
              @if (loginForm.get('password')?.hasError('minlength')) {
                <mat-error>Senha deve ter pelo menos 12 caracteres</mat-error>
              }
            </mat-form-field>

            <div class="form-options">
              <mat-checkbox formControlName="rememberMe" color="primary">Lembrar-me</mat-checkbox>
              <a routerLink="/auth/forgot-password" class="forgot-password">Esqueci a senha</a>
            </div>

            @if (errorMessage) {
              <div class="error-message" role="alert">
                <mat-icon>error_outline</mat-icon>
                {{ errorMessage }}
              </div>
            }

            <button 
              mat-raised-button 
              color="primary" 
              type="submit" 
              class="submit-btn"
              [disabled]="loginForm.invalid || loading()">
              @if (loading()) {
                <mat-spinner diameter="20"></mat-spinner>
              } @else {
                <mat-icon>login</mat-icon>
                <span>Entrar</span>
              }
            </button>
          </form>
        </mat-card-content>
        
        <mat-card-footer>
          <p>Não tem uma conta? <a routerLink="/auth/register">Registre-se</a></p>
        </mat-card-footer>
      </mat-card>
    </div>
  `,
  styles: [`
    .login-container {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 24px;
      background: linear-gradient(135deg, var(--mdc-theme-surface-container-lowest) 0%, var(--mdc-theme-surface-container-low) 100%);
    }

    .login-card {
      width: 100%;
      max-width: 420px;
      box-shadow: 0 12px 40px rgba(0, 0, 0, 0.12);
    }

    .login-header {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 8px;
    }

    .login-logo {
      font-size: 48px;
      width: 48px;
      height: 48px;
      color: var(--mdc-theme-primary);
    }

    .login-title-group {
      display: flex;
      flex-direction: column;
    }

    .login-title {
      margin: 0;
      font-size: 1.5rem;
      font-weight: 600;
      color: var(--mdc-theme-on-surface);
    }

    .login-subtitle {
      margin: 0;
      font-size: 0.875rem;
      color: var(--mdc-theme-on-surface-variant);
    }

    .login-form {
      display: flex;
      flex-direction: column;
      gap: 16px;
      margin-top: 8px;
    }

    .full-width {
      width: 100%;
    }

    .form-options {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-top: 8px;
    }

    .forgot-password {
      color: var(--mdc-theme-primary);
      text-decoration: none;
      font-size: 0.875rem;
      font-weight: 500;
    }

    .forgot-password:hover {
      text-decoration: underline;
    }

    .error-message {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 12px 16px;
      background: var(--mdc-theme-error-container);
      color: var(--mdc-theme-on-error-container);
      border-radius: 8px;
      font-size: 0.875rem;
      animation: slideIn 0.3s ease-out;
    }

    @keyframes slideIn {
      from {
        opacity: 0;
        transform: translateY(-10px);
      }
      to {
        opacity: 1;
        transform: translateY(0);
      }
    }

    .submit-btn {
      width: 100%;
      height: 48px;
      font-size: 1rem;
      font-weight: 500;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      margin-top: 8px;
    }

    .submit-btn mat-spinner {
      --mdc-circular-progress-active-indicator-color: var(--mdc-theme-on-primary);
    }

    mat-card-footer {
      text-align: center;
      padding-top: 16px;
      padding-bottom: 8px;
    }

    mat-card-footer p {
      margin: 0;
      color: var(--mdc-theme-on-surface-variant);
      font-size: 0.875rem;
    }

    mat-card-footer a {
      color: var(--mdc-theme-primary);
      text-decoration: none;
      font-weight: 500;
    }

    mat-card-footer a:hover {
      text-decoration: underline;
    }

    .login-card {
      width: 100%;
      max-width: 420px;
      box-shadow: 0 12px 40px rgba(0, 0, 0, 0.12);
    }

    .full-width {
      width: 100%;
      margin-bottom: 8px;
    }

    .form-options {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-top: 8px;
    }

    .forgot-password {
      color: var(--mdc-theme-primary);
      text-decoration: none;
      font-size: 0.875rem;
      font-weight: 500;
    }

    .forgot-password:hover {
      text-decoration: underline;
    }

    .error-message {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 12px 16px;
      background: var(--mdc-theme-error-container);
      color: var(--mdc-theme-on-error-container);
      border-radius: 8px;
      font-size: 0.875rem;
      animation: slideIn 0.3s ease-out;
    }

    @keyframes slideIn {
      from {
        opacity: 0;
        transform: translateY(-10px);
      }
      to {
        opacity: 1;
        transform: translateY(0);
      }
    }

    .submit-btn {
      width: 100%;
      height: 48px;
      font-size: 1rem;
      font-weight: 500;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      margin-top: 8px;
    }

    mat-card-footer {
      text-align: center;
      padding-top: 16px;
      padding-bottom: 8px;
    }

    mat-card-footer p {
      margin: 0;
      color: var(--mdc-theme-on-surface-variant);
      font-size: 0.875rem;
    }

    mat-card-footer a {
      color: var(--mdc-theme-primary);
      text-decoration: none;
      font-weight: 500;
    }

    mat-card-footer a:hover {
      text-decoration: underline;
    }
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

  onSubmit(): void {
    if (this.loginForm.invalid) {
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
        this.errorMessage = err.error?.detail || 'Erro ao fazer login. Verifique suas credenciais.';
      }
    });
  }
}