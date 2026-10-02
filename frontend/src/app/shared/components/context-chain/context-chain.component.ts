import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

export interface ChainNode {
  /** Tipo da entidade: Ticket, Solicitante, Ativo, Serviço, SLA, Impacto. */
  kind: string;
  value: string;
  sub?: string;
  /** focus = nó em foco (violeta); impact = impactado (âmbar); crit = violado (vermelho). */
  tone?: 'focus' | 'impact' | 'crit';
  /** A junta que chega neste nó propaga impacto (tracejada âmbar). */
  impactJoint?: boolean;
}

/**
 * Cadeia de contexto do Nexus: ticket → solicitante → ativo → serviço → SLA → impacto. Uma linha
 * horizontal de nós tipados com juntas quadradas. Só entram os nós que existem para o chamado.
 */
@Component({
  selector: 'nx-context-chain',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ol class="chain" [attr.aria-label]="label">
      @for (n of nodes; track $index; let first = $first) {
        @if (!first) { <li class="j" [class.imp]="n.impactJoint" aria-hidden="true"></li> }
        <li class="cn" [attr.data-tone]="n.tone ?? null">
          <span class="k">
            @if (n.tone === 'focus') { <span class="node" aria-hidden="true"></span> }
            {{ n.kind }}
          </span>
          <span class="v">{{ n.value }}</span>
          @if (n.sub) { <span class="s">{{ n.sub }}</span> }
        </li>
      }
    </ol>
  `,
  styles: [`
    :host { display: block; }
    .chain { display: flex; align-items: stretch; margin: 0; padding: 2px; list-style: none; overflow-x: auto; }
    .cn {
      display: grid;
      gap: 1px;
      flex: 0 0 auto;
      min-width: 132px;
      max-width: 220px;
      padding: var(--sp-4) var(--sp-5);
      border: 1px solid var(--border);
      border-radius: var(--radius-s);
      background: var(--surface);
    }
    .k {
      display: flex;
      align-items: center;
      gap: var(--sp-3);
      font-family: var(--mono);
      font-size: var(--fs-xs);
      font-weight: var(--fw-medium);
      letter-spacing: .06em;
      text-transform: uppercase;
      color: var(--text-faint);
    }
    .node { width: 8px; height: 8px; border-radius: 2px; background: var(--accent-2); }
    .v { font-size: var(--fs-base); font-weight: var(--fw-medium); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .s { font-family: var(--mono); font-size: var(--fs-xs); color: var(--text-muted); }
    .cn[data-tone='focus'] { border-color: var(--accent-2); background: var(--accent-2-soft); }
    .cn[data-tone='impact'] { border-color: var(--warning); }
    .cn[data-tone='impact'] .s { color: var(--warning); }
    .cn[data-tone='crit'] { border-color: var(--critical); }
    .cn[data-tone='crit'] .s { color: var(--critical); }
    .j { position: relative; flex: 0 0 22px; }
    .j::before { content: ''; position: absolute; inset: 50% 0 auto 0; border-top: 1px solid var(--border-strong); }
    .j::after {
      content: '';
      position: absolute;
      left: 50%;
      top: 50%;
      width: 5px;
      height: 5px;
      margin: -2.5px 0 0 -2.5px;
      background: var(--surface);
      border: 1px solid var(--border-strong);
    }
    .j.imp::before { border-top-style: dashed; border-top-color: var(--warning); }
    .j.imp::after { border-color: var(--warning); }
  `]
})
export class ContextChainComponent {
  @Input({ required: true }) nodes: readonly ChainNode[] = [];
  @Input() label = 'Contexto do chamado';
}
