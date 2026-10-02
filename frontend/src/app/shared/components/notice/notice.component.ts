import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

export type NoticeTone = 'info' | 'success' | 'warning' | 'critical';

const ICONS: Record<NoticeTone, string> = {
  info: 'info',
  success: 'check_circle',
  warning: 'warning',
  critical: 'error',
};

/** Inline message block. Body text stays in the primary text color; tone colors the border, fill and icon. */
@Component({
  selector: 'nx-notice',
  standalone: true,
  imports: [MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[attr.role]': 'tone === "critical" ? "alert" : "status"',
    '[attr.data-tone]': 'tone',
  },
  template: `
    <mat-icon class="nt-icon" aria-hidden="true">{{ icon() }}</mat-icon>
    <div class="nt-body">
      @if (heading) { <strong class="nt-heading">{{ heading }}</strong> }
      <ng-content />
    </div>
  `,
  styles: [`
    :host {
      --tone: var(--info);
      --tone-soft: var(--info-soft);
      display: flex;
      align-items: flex-start;
      gap: var(--sp-4);
      margin-bottom: var(--sp-6);
      padding: var(--sp-4) var(--sp-5);
      border: 1px solid color-mix(in srgb, var(--tone) 35%, transparent);
      border-radius: var(--radius-m);
      background: var(--tone-soft);
      color: var(--text);
      font-size: var(--fs-base);
    }
    :host([data-tone='success']) { --tone: var(--success); --tone-soft: var(--success-soft); }
    :host([data-tone='warning']) { --tone: var(--warning); --tone-soft: var(--warning-soft); }
    :host([data-tone='critical']) { --tone: var(--critical); --tone-soft: var(--critical-soft); }
    .nt-icon { flex: none; width: 18px; height: 18px; font-size: 18px; color: var(--tone); }
    .nt-body { min-width: 0; }
    .nt-heading { display: block; font-weight: var(--fw-semibold); }
  `]
})
export class NoticeComponent {
  @Input() tone: NoticeTone = 'info';
  @Input() heading?: string;

  icon(): string {
    return ICONS[this.tone];
  }
}
