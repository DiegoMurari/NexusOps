import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

/**
 * Loading placeholder shaped like the content it stands in for. Purely visual
 * (aria-hidden): the owning region must expose aria-busy / a status message.
 */
@Component({
  selector: 'nx-skeleton',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    'aria-hidden': 'true',
    '[style.width]': 'width',
    '[style.height]': 'height',
  },
  template: '',
  styles: [`
    :host {
      display: block;
      max-width: 100%;
      border-radius: var(--radius-s);
      background: var(--surface-3);
      animation: nx-pulse 1.4s ease-in-out infinite;
    }
    @keyframes nx-pulse { 0%, 100% { opacity: 1; } 50% { opacity: .5; } }
  `]
})
export class SkeletonComponent {
  @Input() width = '100%';
  @Input() height = '12px';
}
