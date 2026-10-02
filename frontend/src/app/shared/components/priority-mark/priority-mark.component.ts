import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

export type PriorityLevel = 'critical' | 'high' | 'medium' | 'low';

const LABELS: Record<PriorityLevel, string> = {
  critical: 'Crítica',
  high: 'Alta',
  medium: 'Média',
  low: 'Baixa',
};

/** Priority = colored vertical bar + text label (text stays in the primary text color). */
@Component({
  selector: 'nx-priority-mark',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[attr.data-level]': 'level' },
  template: `<span class="sig" aria-hidden="true"><i></i><i></i><i></i><i></i></span><span>{{ text() }}</span>`,
  styles: [`
    :host {
      --bar: var(--text-faint);
      display: inline-flex;
      align-items: center;
      gap: var(--sp-3);
      font-size: var(--fs-sm);
      font-weight: var(--fw-medium);
      color: var(--text);
      white-space: nowrap;
    }
    /* Signal Bars: 1 a 4 barras ascendentes. A forma (quantidade de barras) diz a prioridade;
       a cor só reforça. Baixa = 1 barra neutra, Média = 2 azul-info, Alta = 3 âmbar, Crítica = 4 vermelhas. */
    .sig { display: inline-flex; align-items: flex-end; gap: 2px; height: 14px; }
    .sig i { display: block; width: 3px; border-radius: 1px; background: var(--border-strong); }
    .sig i:nth-child(1) { height: 5px; }
    .sig i:nth-child(2) { height: 8px; }
    .sig i:nth-child(3) { height: 11px; }
    .sig i:nth-child(4) { height: 14px; }
    :host([data-level='low']) .sig i:nth-child(-n+1) { background: var(--text-faint); }
    :host([data-level='medium']) .sig i:nth-child(-n+2) { background: var(--info); }
    :host([data-level='high']) .sig i:nth-child(-n+3) { background: var(--warning); }
    :host([data-level='critical']) .sig i { background: var(--critical); }
  `]
})
export class PriorityMarkComponent {
  @Input({ required: true }) level!: PriorityLevel;
  @Input() label?: string;

  text(): string {
    return this.label ?? LABELS[this.level];
  }
}
