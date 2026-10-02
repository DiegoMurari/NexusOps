import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

export type Tone = 'neutral' | 'info' | 'success' | 'warning' | 'critical';

/** Glifo de estado: a forma identifica o estado, a cor só reforça. */
export type StatusGlyph = 'open' | 'progress' | 'waiting' | 'hold' | 'done' | 'reopened' | 'closed';

/**
 * Compact rectangular status badge. The projected text is the primary
 * information; color is only reinforcement (never the sole signal).
 */
@Component({
  selector: 'nx-status-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[attr.data-tone]': 'tone' },
  template: `
    @if (glyph) {
      <svg class="g" viewBox="0 0 12 12" aria-hidden="true" focusable="false">
        @switch (glyph) {
          @case ('open') { <circle cx="6" cy="6" r="4.5" fill="none" stroke="currentColor" stroke-width="1.5" /> }
          @case ('progress') { <circle cx="6" cy="6" r="4.5" fill="none" stroke="currentColor" stroke-width="1.5" /><path d="M6 1.5a4.5 4.5 0 0 1 0 9z" fill="currentColor" /> }
          @case ('waiting') { <circle cx="6" cy="6" r="4.5" fill="none" stroke="currentColor" stroke-width="1.5" /><path d="M6 3.4V6l1.8 1.1" fill="none" stroke="currentColor" stroke-width="1.3" /> }
          @case ('hold') { <rect x="2.5" y="2" width="2.4" height="8" fill="currentColor" /><rect x="7.1" y="2" width="2.4" height="8" fill="currentColor" /> }
          @case ('done') { <circle cx="6" cy="6" r="4.5" fill="none" stroke="currentColor" stroke-width="1.5" /><path d="M3.8 6.2l1.6 1.6 2.9-3.2" fill="none" stroke="currentColor" stroke-width="1.5" /> }
          @case ('reopened') { <path d="M6 1.2l4.8 4.8L6 10.8 1.2 6z" fill="currentColor" /> }
          @case ('closed') { <circle cx="6" cy="6" r="4.5" fill="none" stroke="currentColor" stroke-width="1.5" /><path d="M4.2 4.2l3.6 3.6M7.8 4.2L4.2 7.8" fill="none" stroke="currentColor" stroke-width="1.4" /> }
        }
      </svg>
    }
    <ng-content />
  `,
  styles: [`
    :host {
      --tone: var(--text-muted);
      --tone-soft: var(--surface-2);
      display: inline-flex;
      align-items: center;
      gap: var(--sp-3);
      height: 20px;
      padding: 0 var(--sp-3);
      border: 1px solid color-mix(in srgb, var(--tone) 35%, transparent);
      border-radius: var(--radius-s);
      background: var(--tone-soft);
      color: var(--tone);
      font-size: var(--fs-sm);
      font-weight: var(--fw-medium);
      white-space: nowrap;
    }
    .g { width: 11px; height: 11px; flex: none; }
    :host([data-tone='info']) { --tone: var(--info); --tone-soft: var(--info-soft); }
    :host([data-tone='success']) { --tone: var(--success); --tone-soft: var(--success-soft); }
    :host([data-tone='warning']) { --tone: var(--warning); --tone-soft: var(--warning-soft); }
    :host([data-tone='critical']) { --tone: var(--critical); --tone-soft: var(--critical-soft); }
  `]
})
export class StatusBadgeComponent {
  @Input() tone: Tone = 'neutral';
  @Input() glyph: StatusGlyph | null = null;
}

/** Status do ticket → glifo. */
export function ticketStatusGlyph(status: string): StatusGlyph {
  switch (status?.toUpperCase()) {
    case 'OPEN': return 'open';
    case 'IN_PROGRESS': return 'progress';
    case 'WAITING':
    case 'PENDING': return 'waiting';
    case 'ON_HOLD': return 'hold';
    case 'RESOLVED': return 'done';
    case 'REOPENED': return 'reopened';
    default: return 'closed';
  }
}

/** Ticket status → tone. Open and in-progress are informational, not "action". */
export function ticketStatusTone(status: string): Tone {
  switch (status?.toUpperCase()) {
    case 'OPEN':
    case 'IN_PROGRESS':
      return 'info';
    case 'PENDING':
    case 'WAITING':
      return 'warning';
    case 'RESOLVED':
      return 'success';
    case 'REOPENED':
      return 'critical';
    default:
      return 'neutral';
  }
}
