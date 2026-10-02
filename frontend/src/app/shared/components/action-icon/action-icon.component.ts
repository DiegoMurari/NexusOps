import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

export type ActionName = 'take' | 'assign' | 'escalate' | 'resolve' | 'reopen' | 'hold' | 'run' | 'accept';

/**
 * Ícones dos verbos operacionais (assumir, atribuir, escalar, resolver, reabrir, em espera,
 * executar): grade de 16, traço 1,5 e cantos retos. Decorativos; o texto do botão dá o nome.
 */
@Component({
  selector: 'nx-action-icon',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"
         stroke-linecap="square" stroke-linejoin="miter" aria-hidden="true" focusable="false">
      @switch (name) {
        @case ('take') { <path d="M2 8h8M7 4.5L10.5 8 7 11.5M13.5 3v10" /> }
        @case ('assign') { <path d="M1.5 8h5M4.5 5.5L7 8l-2.5 2.5" /><circle cx="12" cy="5.5" r="2" /><path d="M8.5 13.5c0-2 1.5-3.2 3.5-3.2s3.5 1.2 3.5 3.2" /> }
        @case ('escalate') { <path d="M3 7l5-4 5 4M3 12l5-4 5 4" /> }
        @case ('resolve') { <path d="M2.5 2.5h11v11h-11zM5 8.2l2.2 2.2L11 6" /> }
        @case ('accept') { <path d="M2.5 2.5h11v11h-11zM5 8.2l2.2 2.2L11 6" /> }
        @case ('reopen') { <path d="M3 8a5 5 0 1 0 1.8-3.8M3 2.5v3h3" /> }
        @case ('hold') { <path d="M5.5 3v10M10.5 3v10" /> }
        @case ('run') { <path d="M4 3l9 5-9 5z" /> }
      }
    </svg>
  `,
  styles: [`:host { display: inline-flex; flex: none; line-height: 0; }`]
})
export class ActionIconComponent {
  @Input({ required: true }) name!: ActionName;
}
