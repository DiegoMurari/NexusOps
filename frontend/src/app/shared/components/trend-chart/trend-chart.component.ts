import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

export interface TrendPoint {
  label: string;
  created: number;
  resolved: number;
}

const W = 640;
const H = 180;
const PAD = { top: 12, right: 12, bottom: 24, left: 32 };

/**
 * Tendência diária de tickets: barras para os criados e linha para os resolvidos, na mesma escala.
 * A escala e os rótulos saem dos dados (nada decorativo); cada dia tem um título com os números e o
 * gráfico traz um resumo em texto para leitores de tela.
 */
@Component({
  selector: 'nx-trend-chart',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (points.length === 0) {
      <p class="empty">Sem dados no período.</p>
    } @else {
      <figure>
        <svg [attr.viewBox]="'0 0 ' + w + ' ' + h" role="img" [attr.aria-label]="summary()" preserveAspectRatio="none">
          @for (t of ticks(); track t) {
            <line [attr.x1]="pad.left" [attr.x2]="w - pad.right" [attr.y1]="y(t)" [attr.y2]="y(t)" class="grid" />
            <text [attr.x]="pad.left - 6" [attr.y]="y(t) + 3" class="axis" text-anchor="end">{{ t }}</text>
          }
          @for (p of points; track p.label; let i = $index) {
            <rect [attr.x]="x(i) - barW() / 2" [attr.y]="y(p.created)" [attr.width]="barW()" [attr.height]="base() - y(p.created)" class="bar">
              <title>{{ p.label }}: {{ p.created }} criados, {{ p.resolved }} resolvidos</title>
            </rect>
          }
          <polyline [attr.points]="line()" class="line" fill="none" />
          @for (i of labelIndexes(); track i) {
            <text [attr.x]="x(i)" [attr.y]="h - 6" class="axis" text-anchor="middle">{{ short(points[i].label) }}</text>
          }
        </svg>
        <figcaption>
          <span><i class="key bar-key"></i>Criados</span>
          <span><i class="key line-key"></i>Resolvidos</span>
        </figcaption>
      </figure>
    }
  `,
  styles: [`
    :host { display: block; }
    figure { margin: 0; }
    svg { display: block; width: 100%; height: 180px; }
    .grid { stroke: var(--border); stroke-width: 1; }
    .axis { fill: var(--text-faint); font-family: var(--mono); font-size: 10px; }
    .bar { fill: var(--accent); opacity: .85; }
    .line { stroke: var(--success); stroke-width: 2; stroke-linejoin: round; stroke-linecap: round; vector-effect: non-scaling-stroke; }
    figcaption { display: flex; gap: var(--sp-6); margin-top: var(--sp-3); font-size: var(--fs-sm); color: var(--text-muted); }
    .key { display: inline-block; width: 10px; height: 10px; margin-inline-end: var(--sp-3); border-radius: 2px; vertical-align: -1px; }
    .bar-key { background: var(--accent); }
    .line-key { height: 3px; background: var(--success); vertical-align: 2px; }
    .empty { margin: 0; color: var(--text-faint); font-size: var(--fs-base); }
  `]
})
export class TrendChartComponent {
  @Input() points: TrendPoint[] = [];

  readonly w = W;
  readonly h = H;
  readonly pad = PAD;

  private max(): number {
    return Math.max(...this.points.flatMap(p => [p.created, p.resolved]), 1);
  }

  /** Teto do eixo: um valor "redondo" acima do maior ponto. */
  private top(): number {
    const m = this.max();
    if (m <= 5) return m;
    const step = Math.pow(10, Math.floor(Math.log10(m)));
    return Math.ceil(m / step) * step;
  }

  base(): number { return H - PAD.bottom; }

  y(v: number): number {
    return this.base() - (v / this.top()) * (H - PAD.top - PAD.bottom);
  }

  x(i: number): number {
    const n = this.points.length;
    return PAD.left + ((i + 0.5) / n) * (W - PAD.left - PAD.right);
  }

  barW(): number {
    return Math.max(((W - PAD.left - PAD.right) / this.points.length) * 0.6, 2);
  }

  ticks(): number[] {
    const t = this.top();
    return t <= 5 ? Array.from({ length: t + 1 }, (_, i) => i) : [0, t / 2, t];
  }

  line(): string {
    return this.points.map((p, i) => `${this.x(i)},${this.y(p.resolved)}`).join(' ');
  }

  labelIndexes(): number[] {
    const n = this.points.length;
    if (n <= 1) return [0];
    return [0, Math.floor((n - 1) / 2), n - 1];
  }

  short(label: string): string {
    // "2026-10-02" -> "02/10"
    const [, m, d] = label.split('-');
    return m && d ? `${d}/${m}` : label;
  }

  summary(): string {
    const c = this.points.reduce((s, p) => s + p.created, 0);
    const r = this.points.reduce((s, p) => s + p.resolved, 0);
    return `Tendência diária: ${c} tickets criados e ${r} resolvidos em ${this.points.length} dias.`;
  }
}
