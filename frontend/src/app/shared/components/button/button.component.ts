import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'icon' | 'icon-danger';
export type ButtonSize = 'sm' | 'md' | 'lg';

/**
 * Single button primitive. Use as an attribute on a native element so keyboard,
 * form and link semantics stay native:
 *   <button nxButton variant="primary" [loading]="saving()" [disabled]="saving()">Salvar</button>
 *   <a nxButton variant="secondary" routerLink="/x">Voltar</a>
 * Icon-only variants ('icon', 'icon-danger') must carry an aria-label.
 * When `loading`, also bind [disabled] so the click is blocked for assistive tech.
 */
@Component({
  selector: 'button[nxButton], a[nxButton]',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'nx-btn',
    '[attr.data-variant]': 'variant',
    '[attr.data-size]': 'size',
    '[class.is-loading]': 'loading',
    '[attr.aria-busy]': 'loading ? "true" : null',
  },
  template: `@if (loading) { <span class="spinner" aria-hidden="true"></span> }<ng-content />`,
  styles: [`
    :host {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: var(--sp-3);
      height: var(--control-h-md);
      padding: 0 var(--sp-6);
      border: 1px solid transparent;
      border-radius: var(--radius-s);
      font: var(--fw-medium) var(--fs-base) / 1 var(--sans);
      text-decoration: none;
      white-space: nowrap;
      cursor: pointer;
      user-select: none;
      transition: background-color var(--dur-fast) var(--ease), border-color var(--dur-fast) var(--ease),
                  color var(--dur-fast) var(--ease), transform var(--dur-fast) var(--ease);
    }
    :host([data-size='sm']) { height: var(--control-h-sm); padding: 0 var(--sp-5); font-size: var(--fs-sm); }
    :host([data-size='lg']) { height: var(--control-h-lg); padding: 0 var(--sp-7); font-size: var(--fs-md); }

    :host(:active:not(:disabled):not(.is-loading)) { transform: translateY(1px); }
    :host(:disabled) { opacity: var(--disabled-opacity); cursor: not-allowed; }
    :host(.is-loading) { cursor: progress; pointer-events: none; }

    :host([data-variant='primary']) { background: var(--accent); color: var(--on-accent); }
    :host([data-variant='primary']:hover:not(:disabled)) { background: var(--accent-hover); }

    :host([data-variant='secondary']) { background: var(--surface); color: var(--text); border-color: var(--border-strong); }
    :host([data-variant='secondary']:hover:not(:disabled)) { background: var(--surface-2); }

    :host([data-variant='ghost']) { background: transparent; color: var(--accent); }
    :host([data-variant='ghost']:hover:not(:disabled)) { background: var(--accent-soft); }

    :host([data-variant='danger']) { background: var(--critical); color: var(--on-accent); }
    :host([data-variant='danger']:hover:not(:disabled)) { background: color-mix(in srgb, var(--critical) 88%, black); }

    :host([data-variant='icon']), :host([data-variant='icon-danger']) {
      width: var(--control-h-md);
      padding: 0;
      background: var(--surface);
      color: var(--text-muted);
      border-color: var(--border);
    }
    :host([data-variant='icon'][data-size='sm']), :host([data-variant='icon-danger'][data-size='sm']) { width: var(--control-h-sm); }
    :host([data-variant='icon']:hover:not(:disabled)) { background: var(--surface-2); color: var(--text); }
    :host([data-variant='icon-danger']:hover:not(:disabled)) { background: var(--critical-soft); color: var(--critical); border-color: var(--critical); }

    .spinner {
      width: 14px;
      height: 14px;
      border: 2px solid currentColor;
      border-top-color: transparent;
      border-radius: var(--radius-full);
      animation: nx-spin .7s linear infinite;
    }
    @keyframes nx-spin { to { transform: rotate(360deg); } }
  `]
})
export class ButtonComponent {
  @Input() variant: ButtonVariant = 'secondary';
  @Input() size: ButtonSize = 'md';
  @Input() loading = false;
}
