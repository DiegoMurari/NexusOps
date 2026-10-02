import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

export type BarTone = 'accent' | 'success' | 'warning' | 'critical' | 'neutral';

export interface BarItem {
  label: string;
  value: number;
  tone?: BarTone;
  /** Texto que substitui o número (ex.: "12 · 31%"). */
  display?: string;
}

/**
 * Lista de barras horizontais para distribuições (situação, prioridade, idade do backlog…). A barra é
 * proporcional ao maior valor da lista; o número sempre aparece ao lado, a cor nunca é o único sinal.
 */
@Component({
  selector: 'nx-bar-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (items.length === 0 || total() === 0) {
      <p class="empty">{{ emptyText }}</p>
    } @else {
      <ul>
        @for (it of items; track it.label) {
          <li>
            <span class="label" [title]="it.label">{{ it.label }}</span>
            <span class="track" aria-hidden="true">
              <span class="fill" [attr.data-tone]="it.tone ?? 'accent'" [style.width.%]="width(it.value)"></span>
            </span>
            <span class="value">{{ it.display ?? it.value }}</span>
          </li>
        }
      </ul>
    }
  `,
  styles: [`
    :host { display: block; }
    ul { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--sp-4); }
    li { display: grid; grid-template-columns: minmax(80px, 38%) 1fr max-content; align-items: center; gap: var(--sp-5); font-size: var(--fs-base); }
    .label { color: var(--text-muted); min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .track { height: 8px; border-radius: var(--radius-xs); background: var(--surface-2); overflow: hidden; }
    .fill { display: block; height: 100%; border-radius: var(--radius-xs); background: var(--accent); min-width: 2px; }
    .fill[data-tone='success'] { background: var(--success); }
    .fill[data-tone='warning'] { background: var(--warning); }
    .fill[data-tone='critical'] { background: var(--critical); }
    .fill[data-tone='neutral'] { background: var(--text-faint); }
    .value { font-family: var(--mono); font-variant-numeric: tabular-nums; color: var(--text); text-align: right; }
    .empty { margin: 0; color: var(--text-faint); font-size: var(--fs-base); }
  `]
})
export class BarListComponent {
  @Input() items: BarItem[] = [];
  @Input() emptyText = 'Sem dados no período.';

  total(): number {
    return this.items.reduce((sum, it) => sum + it.value, 0);
  }

  width(value: number): number {
    const max = Math.max(...this.items.map(i => i.value), 0);
    return max === 0 ? 0 : Math.max((value / max) * 100, value > 0 ? 2 : 0);
  }
}
