import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

export type SlaState = 'ok' | 'warn' | 'crit' | 'none';

const STATE_LABELS: Record<SlaState, string> = {
  ok: 'no prazo',
  warn: 'em atenção',
  crit: 'estourado',
  none: 'sem SLA',
};

/** ≤25% of the time left is "warn"; breached is "crit". */
export function slaState(remainingRatio: number | null, breached: boolean): SlaState {
  if (breached) return 'crit';
  if (remainingRatio === null) return 'none';
  return remainingRatio <= 0.25 ? 'warn' : 'ok';
}

/** SLA time chip in mono. A hidden state word accompanies the color for screen readers. */
@Component({
  selector: 'nx-sla-chip',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[attr.data-state]': 'state' },
  template: `<span class="sr-only">SLA {{ stateLabel() }}: </span><ng-content />`,
  styles: [`
    :host {
      --tone: var(--text-muted);
      --tone-soft: var(--surface-2);
      display: inline-flex;
      align-items: center;
      height: 20px;
      padding: 0 var(--sp-3);
      border: 1px solid color-mix(in srgb, var(--tone) 35%, transparent);
      border-radius: var(--radius-s);
      background: var(--tone-soft);
      color: var(--tone);
      font-family: var(--mono);
      font-variant-numeric: tabular-nums;
      font-size: var(--fs-xs);
      font-weight: var(--fw-semibold);
      white-space: nowrap;
    }
    :host([data-state='ok']) { --tone: var(--success); --tone-soft: var(--success-soft); }
    :host([data-state='warn']) { --tone: var(--warning); --tone-soft: var(--warning-soft); }
    :host([data-state='crit']) { --tone: var(--critical); --tone-soft: var(--critical-soft); }
  `]
})
export class SlaChipComponent {
  @Input() state: SlaState = 'none';

  stateLabel(): string {
    return STATE_LABELS[this.state];
  }
}
