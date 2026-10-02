import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PlatformService, FeatureFlagDto } from '../../../core/platform/platform.service';
import { EmptyStateComponent, PageHeaderComponent, SkeletonComponent } from '../../../shared/components';

@Component({
  selector: 'app-feature-flag-management',
  standalone: true,
  imports: [CommonModule, EmptyStateComponent, PageHeaderComponent, SkeletonComponent],
  template: `
    <nx-page-header heading="Feature flags" />

    @if (error()) {
      <nx-empty-state variant="error" [heading]="error()!" />
    } @else if (loading()) {
      <div class="flag-list" aria-busy="true">
        <span class="sr-only" role="status">Carregando feature flags…</span>
        @for (i of [0, 1, 2]; track i) {
          <div class="card flag-row"><nx-skeleton width="40%" height="36px" /></div>
        }
      </div>
    } @else if (flags().length === 0) {
      <nx-empty-state heading="Nenhuma feature flag cadastrada" />
    } @else {
      <ul class="flag-list">
        @for (flag of flags(); track flag.id) {
          <li class="card flag-row">
            <div class="flag-info">
              <div class="flag-name">{{ flag.name }}</div>
              <div class="flag-key mono">{{ flag.key }}</div>
              @if (flag.description) {
                <div class="flag-desc">{{ flag.description }}</div>
              }
            </div>
            <div class="flag-actions">
              <span class="rollout mono">{{ flag.rolloutPercentage }}%</span>
              <span class="flag-state">{{ flag.enabled ? 'Ativa' : 'Inativa' }}</span>
              <button
                type="button"
                class="toggle"
                role="switch"
                [attr.aria-checked]="flag.enabled"
                [attr.aria-label]="flag.name"
                [disabled]="toggling() === flag.id"
                (click)="toggle(flag)"
              >
                <span class="toggle-thumb"></span>
              </button>
            </div>
          </li>
        }
      </ul>
    }
  `,
  styles: [`
    :host { display: block; }

    .flag-list { display: flex; flex-direction: column; gap: var(--sp-4); margin: 0; padding: 0; list-style: none; }
    .flag-row { display: flex; justify-content: space-between; align-items: center; gap: var(--sp-6); }
    .flag-name { font-size: var(--fs-md); font-weight: var(--fw-semibold); color: var(--text); }
    .flag-key { font-size: var(--fs-sm); color: var(--text-muted); margin-top: var(--sp-1); }
    .flag-desc { font-size: var(--fs-base); color: var(--text-muted); margin-top: var(--sp-2); max-width: 72ch; }
    .flag-actions { display: flex; align-items: center; gap: var(--sp-5); flex: none; }
    .rollout { font-size: var(--fs-sm); color: var(--text-muted); min-width: 36px; text-align: right; }
    .flag-state { font-size: var(--fs-sm); color: var(--text); min-width: 44px; }

    .toggle {
      position: relative;
      width: 36px;
      height: 20px;
      padding: 0;
      border: 1px solid var(--border-strong);
      border-radius: var(--radius-s);
      background: var(--surface-3);
      cursor: pointer;
      transition: background-color var(--dur-fast) var(--ease), border-color var(--dur-fast) var(--ease);

      &[aria-checked='true'] { background: var(--accent); border-color: var(--accent); }
      &:disabled { opacity: var(--disabled-opacity); cursor: not-allowed; }
    }

    .toggle-thumb {
      position: absolute;
      top: 2px;
      left: 2px;
      width: 14px;
      height: 14px;
      border-radius: var(--radius-xs);
      background: var(--text-muted);
      transition: transform var(--dur-fast) var(--ease), background-color var(--dur-fast) var(--ease);
    }

    .toggle[aria-checked='true'] .toggle-thumb {
      transform: translateX(16px);
      background: var(--on-accent);
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
