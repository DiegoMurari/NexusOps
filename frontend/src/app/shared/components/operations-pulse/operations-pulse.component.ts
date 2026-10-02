import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';

export type PulseState = 'calm' | 'warn' | 'crit';

export interface PulseReading {
  key: string;
  /** Rótulo fixo em maiúsculas (INCIDENTS, SLA, UNASSIGNED, ASSETS, AUTOMATIONS). */
  label: string;
  value: string | number;
  detail: string;
  state: PulseState;
  /** Quando informado, o segmento leva para a lista já filtrada. */
  link?: string | unknown[];
  queryParams?: Record<string, string>;
}

const STATE_WORD: Record<PulseState, string> = { calm: '', warn: 'atenção', crit: 'alerta' };

/**
 * Operations Pulse (faixa de leituras): cinco leituras fixas, sempre na mesma ordem. Em estado
 * normal tudo fica neutro; quando algo desvia, o segmento ganha um trilho de 2px no topo e a cor
 * do valor. Não é um conjunto de cards de KPI: é uma única faixa de instrumento.
 */
@Component({
  selector: 'nx-operations-pulse',
  standalone: true,
  imports: [NgTemplateOutlet, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pulse" role="group" aria-label="Operations Pulse" [attr.aria-busy]="loading ? 'true' : null">
      @for (r of readings; track r.key) {
        @if (r.link && !loading) {
          <a class="seg" [routerLink]="r.link" [queryParams]="r.queryParams" [attr.data-state]="r.state">
            <ng-container *ngTemplateOutlet="body; context: { $implicit: r }" />
          </a>
        } @else {
          <div class="seg" [attr.data-state]="loading ? 'calm' : r.state">
            <ng-container *ngTemplateOutlet="body; context: { $implicit: r }" />
          </div>
        }
      }
    </div>
    <ng-template #body let-r>
      <span class="l">{{ r.label }}</span>
      <span class="v">{{ loading ? '—' : r.value }}<span class="sr-only"> {{ stateWord(r.state) }}</span></span>
      <span class="d">{{ loading ? 'carregando' : r.detail }}</span>
    </ng-template>
  `,
  styles: [`
    :host { display: block; }
    .pulse {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(168px, 1fr));
      gap: 1px;
      background: var(--border);
      border: 1px solid var(--border);
      border-radius: var(--radius-m);
      overflow: hidden;
    }
    .seg {
      position: relative;
      display: grid;
      gap: 2px;
      min-width: 0;
      padding: var(--sp-5) var(--sp-6);
      background: var(--surface);
      color: inherit;
      text-decoration: none;
    }
    .seg::before { content: ''; position: absolute; inset: 0 0 auto 0; height: 2px; background: transparent; }
    .seg[data-state='warn']::before { background: var(--warning); }
    .seg[data-state='crit']::before { background: var(--critical); }
    a.seg:hover { background: var(--surface-2); }
    a.seg:focus-visible { outline: var(--focus-ring); outline-offset: -2px; }
    .l {
      font-family: var(--mono);
      font-size: var(--fs-xs);
      font-weight: var(--fw-medium);
      letter-spacing: .06em;
      text-transform: uppercase;
      color: var(--text-muted);
    }
    .v {
      font-family: var(--mono);
      font-size: var(--fs-2xl);
      font-weight: var(--fw-semibold);
      line-height: 1.15;
      font-variant-numeric: tabular-nums;
      color: var(--text);
    }
    .seg[data-state='warn'] .v { color: var(--warning); }
    .seg[data-state='crit'] .v { color: var(--critical); }
    .d {
      font-family: var(--mono);
      font-size: var(--fs-xs);
      color: var(--text-muted);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  `]
})
export class OperationsPulseComponent {
  @Input({ required: true }) readings: readonly PulseReading[] = [];
  @Input() loading = false;

  stateWord(state: PulseState): string {
    return STATE_WORD[state];
  }
}
