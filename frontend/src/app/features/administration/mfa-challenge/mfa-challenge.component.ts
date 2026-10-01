import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-mfa-challenge',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule
  ],
  template: `
    <div class="mfa-container">
      <mat-card class="mfa-card">
        <mat-card-header>
          <div class="mfa-header">
            <mat-icon class="mfa-logo">security</mat-icon>
            <div class="mfa-title-group">
              <h1 class="mfa-title">Verificação em Duas Etapas</h1>
              <p class="mfa-subtitle">Digite o código do seu autenticador</p>
            </div>
          </div>
        </mat-card-header>

        <mat-card-content>
          <form [formGroup]="mfaForm" (ngSubmit)="onSubmit()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Código de 6 dígitos</mat-label>
              <input matInput type="text" formControlName="code" placeholder="000000" maxlength="6" autocomplete="one-time-code">
              <mat-icon matPrefix>pin</mat-icon>
              @if (mfaForm.get('code')?.hasError('required')) {
                <mat-error>Código é obrigatório</mat-error>
              }
              @if (mfaForm.get('code')?.hasError('pattern')) {
                <mat-error>Digite um código válido de 6 dígitos</mat-error>
              }
            </mat-form-field>

            <div class="mfa-options">
              <button type="button" mat-stroked-button (click)="useRecoveryCode()">
                <mat-icon>vpn_key</mat-icon>
                Usar código de recuperação
              </button>
            </div>

            @if (errorMessage) {
              <div class="error-message" role="alert">
                <mat-icon>error</mat-icon>
                {{ errorMessage }}
              </div>
            }

            <button 
              mat-raised-button 
              color="primary" 
              type="submit" 
              class="submit-btn"
              [disabled]="mfaForm.invalid || loading()">
              @if (loading()) {
                <mat-spinner diameter="20"></mat-spinner>
              } @else {
                <mat-icon>verified</mat-icon>
                <span>Verificar</span>
              }
            </button>
          </form>
        </mat-card-content>
        
        <mat-card-footer>
          <button mat-button (click)="goBackToLogin()">
            <mat-icon>arrow_back</mat-icon>
            Voltar ao login
          </button>
        </mat-card-footer>
      </mat-card>
    </div>
  `,
  styles: [`
    .mfa-container {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 24px;
      background: linear-gradient(135deg, var(--mat-sys-surface-container-lowest) 0%, var(--mat-sys-surface-container-low) 100%);
    }

    .mfa-card {
      width: 100%;
      max-width: 420px;
      box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
    }

    .mfa-header {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 8px;
    }

    .mfa-logo {
      font-size: 48px;
      width: 48px;
      height: 48px;
      color: var(--mat-sys-primary);
    }

    .mfa-title-group {
      display: flex;
      flex-direction: column;
    }

    .mfa-title {
      margin: 0;
      font-size: 1.25rem;
      font-weight: 600;
      color: var(--mat-sys-on-surface);
    }

    .mfa-subtitle {
      margin: 0;
      font-size: 0.875rem;
      color: var(--mat-sys-on-surface-variant);
    }

    .full-width {
      width: 100%;
      margin-bottom: 16px;
    }

    .mfa-options {
      margin-bottom: 16px;
    }

    .error-message {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 12px 16px;
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
      border-radius: 8px;
      margin-bottom: 16px;
      font-size: 0.875rem;
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
    }

    mat-card-footer {
      text-align: center;
      padding-top: 16px;
    }
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
  }

  onSubmit(): void {
    if (this.mfaForm.invalid) {
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

  useRecoveryCode(): void {
    // TODO: Implement recovery code flow
    alert('Funcionalidade de código de recuperação em desenvolvimento');
  }

  goBackToLogin(): void {
    this.router.navigate(['/login']);
  }
}