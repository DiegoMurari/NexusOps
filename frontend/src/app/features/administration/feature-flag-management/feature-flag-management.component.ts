import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { PlatformService, FeatureFlagDto } from '../../../core/platform/platform.service';

@Component({
  selector: 'app-feature-flag-management',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  template: `
    <div class="page-header">
      <h1 class="page-title">Feature Flags</h1>
    </div>

    @if (loading()) {
      <div class="card empty-state">
        <mat-icon class="empty-ic">hourglass_empty</mat-icon>
        <p class="empty-text">Carregando feature flags…</p>
      </div>
    } @else if (error()) {
      <div class="card empty-state error">
        <mat-icon class="empty-ic">error_outline</mat-icon>
        <p class="empty-text">{{ error() }}</p>
      </div>
    } @else if (flags().length === 0) {
      <div class="card empty-state">
        <mat-icon class="empty-ic">flag</mat-icon>
        <p class="empty-text">Nenhuma feature flag cadastrada</p>
      </div>
    } @else {
      <div class="flag-list">
        @for (flag of flags(); track flag.id) {
          <div class="card flag-row">
            <div class="flag-info">
              <div class="flag-name">{{ flag.name }}</div>
              <div class="flag-key mono">{{ flag.key }}</div>
              @if (flag.description) {
                <div class="flag-desc">{{ flag.description }}</div>
              }
            </div>
            <div class="flag-actions">
              <span class="rollout mono">{{ flag.rolloutPercentage }}%</span>
              <button
                class="toggle"
                [class.on]="flag.enabled"
                [disabled]="toggling() === flag.id"
                (click)="toggle(flag)"
                [attr.aria-label]="flag.enabled ? 'Desativar' : 'Ativar'"
              >
                <span class="toggle-thumb"></span>
              </button>
            </div>
          </div>
        }
      </div>
    }
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

    .flag-list {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .flag-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 16px;
    }

    .flag-name {
      font-size: 13.5px;
      font-weight: 600;
      color: var(--text);
    }

    .flag-key {
      font-size: 11.5px;
      color: var(--text-faint);
      margin-top: 2px;
    }

    .flag-desc {
      font-size: 12.5px;
      color: var(--text-muted);
      margin-top: 4px;
    }

    .flag-actions {
      display: flex;
      align-items: center;
      gap: 14px;
      flex: none;
    }

    .rollout {
      font-size: 12px;
      color: var(--text-faint);
      min-width: 36px;
      text-align: right;
    }

    .toggle {
      position: relative;
      width: 40px;
      height: 22px;
      border-radius: 999px;
      border: none;
      background: var(--surface-3);
      cursor: pointer;
      transition: background 0.15s ease;

      &.on { background: var(--success); }
      &:disabled { opacity: 0.5; cursor: default; }
    }

    .toggle-thumb {
      position: absolute;
      top: 2px;
      left: 2px;
      width: 18px;
      height: 18px;
      border-radius: 50%;
      background: #fff;
      transition: transform 0.15s ease;
    }

    .toggle.on .toggle-thumb {
      transform: translateX(18px);
    }

    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 12px;
      padding: 64px 24px;
      text-align: center;

      &.error { color: var(--critical); }
    }

    .empty-ic {
      font-size: 40px;
      width: 40px;
      height: 40px;
      color: inherit;
    }

    .empty-text {
      margin: 0;
      color: var(--text-muted);
      font-size: 13.5px;
    }
  `]
})
export class FeatureFlagManagementComponent implements OnInit {
  flags = signal<FeatureFlagDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  toggling = signal<string | null>(null);

  constructor(private platformService: PlatformService) {}

  ngOnInit(): void {
    this.platformService.listFeatureFlags().subscribe({
      next: flags => {
        this.flags.set(flags);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Não foi possível carregar as feature flags.');
        this.loading.set(false);
      }
    });
  }

  toggle(flag: FeatureFlagDto): void {
    this.toggling.set(flag.id);
    this.platformService.updateFeatureFlag(flag.id, { enabled: !flag.enabled }).subscribe({
      next: updated => {
        this.flags.update(list => list.map(f => f.id === updated.id ? updated : f));
        this.toggling.set(null);
      },
      error: () => this.toggling.set(null)
    });
  }
}
