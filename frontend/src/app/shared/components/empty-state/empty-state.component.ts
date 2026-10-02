import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

/**
 * Empty or error placeholder for a region. Projected content is the action
 * area (e.g. a "Criar" button). The icon is optional and off by default.
 */
@Component({
  selector: 'nx-empty-state',
  standalone: true,
  imports: [MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[attr.role]': 'variant === "error" ? "alert" : "status"',
    '[attr.data-variant]': 'variant',
  },
  template: `
    @if (icon) { <mat-icon class="es-icon" aria-hidden="true">{{ icon }}</mat-icon> }
    <p class="es-title">{{ heading }}</p>
    @if (description) { <p class="es-desc">{{ description }}</p> }
    <div class="es-actions"><ng-content /></div>
  `,
  styles: [`
    :host {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--sp-3);
      padding: var(--sp-10) var(--sp-6);
      border: 1px solid var(--border);
      border-radius: var(--radius-m);
      background: var(--surface);
      text-align: center;
    }
    :host([data-variant='error']) { border-color: color-mix(in srgb, var(--critical) 35%, transparent); }
    .es-icon { width: 20px; height: 20px; font-size: 20px; color: var(--text-muted); }
    :host([data-variant='error']) .es-icon, :host([data-variant='error']) .es-title { color: var(--critical); }
    .es-title { margin: 0; font-size: var(--fs-md); font-weight: var(--fw-medium); color: var(--text); }
    .es-desc { margin: 0; max-width: 72ch; font-size: var(--fs-base); color: var(--text-muted); }
    .es-actions { display: flex; gap: var(--sp-4); margin-top: var(--sp-3); }
    .es-actions:empty { display: none; }
  `]
})
export class EmptyStateComponent {
  @Input({ required: true }) heading!: string;
  @Input() description?: string;
  @Input() icon?: string;
  @Input() variant: 'empty' | 'error' = 'empty';
}
