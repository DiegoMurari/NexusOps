import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

export type SlaRulerState = 'ok' | 'warn' | 'crit' | 'none';

export interface SlaReading {
  /** Percentual do prazo já consumido; passa de 100 quando estourou. */
  pct: number;
  state: SlaRulerState;
  /** Tempo restante (HH:MM ou "2d 04h") ou, quando estourado, "+HH:MM". */
  label: string;
}

const TERMINAL = new Set(['RESOLVED', 'CLOSED']);

function formatSpan(ms: number): string {
  const totalMin = Math.floor(Math.abs(ms) / 60000);
  const days = Math.floor(totalMin / 1440);
  const hours = Math.floor((totalMin % 1440) / 60);
  const minutes = totalMin % 60;
  const pad = (n: number) => String(n).padStart(2, '0');
  return days > 0 ? `${days}d ${pad(hours)}h` : `${pad(hours)}:${pad(minutes)}`;
}

/**
 * Lê o SLA de resolução de um chamado. Devolve null quando não há prazo definido ou o chamado
 * já saiu da fila de atendimento (resolvido/fechado): estado saudável é silencioso.
 */
export function slaFromTicket(
  t: { createdAt: string; resolutionDueAt: string | null; status: string },
  now: number = Date.now()
): SlaReading | null {
  if (!t.resolutionDueAt || TERMINAL.has(t.status)) return null;
  const start = new Date(t.createdAt).getTime();
  const due = new Date(t.resolutionDueAt).getTime();
  const total = due - start;
  if (!(total > 0)) return null;

  const remaining = due - now;
  const pct = ((now - start) / total) * 100;
  if (remaining < 0) return { pct, state: 'crit', label: '+' + formatSpan(remaining) };
  return { pct, state: remaining / total <= 0.25 ? 'warn' : 'ok', label: formatSpan(remaining) };
}

const STATE_WORD: Record<SlaRulerState, string> = {
  ok: 'no prazo',
  warn: 'em atenção',
  crit: 'estourado',
  none: 'sem SLA',
};

/**
 * SLA Ruler: régua de 12 marcas com o limite fixo. As marcas consumidas ficam cinza (saudável é
 * silencioso), âmbar a partir de 75% e vermelhas quando passam do limite, com as marcas extras
 * depois dele. O texto em mono acompanha a cor; a cor nunca é o único sinal.
 */
@Component({
  selector: 'nx-sla-ruler',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    role: 'img',
    '[attr.aria-label]': '"SLA " + stateWord() + ": " + label',
    '[attr.data-state]': 'state',
  },
  template: `
    <span class="r" aria-hidden="true">
      @for (c of cells(); track $index) {
        <i [class.f]="c === 'f'"></i>
      }
      <u></u>
      @for (c of overCells(); track $index) {
        <i class="x"></i>
      }
    </span>
    <span class="t">{{ label }}</span>
  `,
  styles: [`
    :host { display: inline-flex; align-items: center; gap: var(--sp-4); white-space: nowrap; --on: var(--text-faint); }
    :host([data-state='warn']) { --on: var(--warning); }
    :host([data-state='crit']) { --on: var(--critical); }
    .r { display: inline-flex; align-items: center; gap: 1px; height: 14px; }
    i { display: block; width: 4px; height: 10px; border-radius: 1px; background: var(--border-strong); }
    i.f { background: var(--on); }
    i.x { background: var(--critical); }
    u { display: block; width: 1px; height: 14px; margin: 0 3px 0 2px; background: var(--text); }
    .t {
      font-family: var(--mono);
      font-size: var(--fs-xs);
      font-weight: var(--fw-medium);
      font-variant-numeric: tabular-nums;
      color: var(--text-muted);
    }
    :host([data-state='warn']) .t { color: var(--warning); }
    :host([data-state='crit']) .t { color: var(--critical); }
  `]
})
export class SlaRulerComponent {
  @Input() pct = 0;
  @Input() state: SlaRulerState = 'ok';
  @Input() label = '';

  private static readonly TICKS = 12;

  cells(): ('f' | 'e')[] {
    const filled = Math.min(SlaRulerComponent.TICKS, Math.round((Math.max(0, this.pct) / 100) * SlaRulerComponent.TICKS));
    return Array.from({ length: SlaRulerComponent.TICKS }, (_, i) => (i < filled ? 'f' : 'e'));
  }

  overCells(): number[] {
    if (this.pct <= 100) return [];
    const extra = Math.max(1, Math.round(((this.pct - 100) / 100) * SlaRulerComponent.TICKS));
    return Array.from({ length: Math.min(4, extra) }, (_, i) => i);
  }

  stateWord(): string {
    return STATE_WORD[this.state];
  }
}
