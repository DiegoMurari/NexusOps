import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

/**
 * Referência a outra entidade (TKT, CI, USR, SVC, SLA) como token mono com prefixo de tipo.
 * Neutro por padrão; violeta quando é o foco da tela; âmbar quando está impactado.
 */
@Component({
  selector: 'nx-entity',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[attr.data-tone]': 'tone' },
  template: `<b class="k">{{ kind }}</b><span><ng-content /></span>`,
  styles: [`
    :host {
      display: inline-flex;
      align-items: center;
      gap: var(--sp-3);
      height: 20px;
      padding: 0 var(--sp-3);
      border: 1px solid var(--border-strong);
      border-radius: var(--radius-s);
      background: var(--surface);
      color: var(--text);
      font-family: var(--mono);
      font-size: var(--fs-xs);
      font-weight: var(--fw-medium);
      white-space: nowrap;
      vertical-align: 1px;
    }
    .k { color: var(--text-faint); font-weight: var(--fw-semibold); letter-spacing: .04em; }
    :host([data-tone='focus']) { border-color: var(--accent-2); background: var(--accent-2-soft); }
    :host([data-tone='impact']) { border-color: var(--warning); }
  `]
})
export class EntityTokenComponent {
  @Input({ required: true }) kind!: string;
  @Input() tone: 'neutral' | 'focus' | 'impact' = 'neutral';
}
