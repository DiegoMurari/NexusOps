import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

/**
 * Monograma A do NexusOps ("peça com nó"): N em traço sobre a peça Nexus Violet, com um nó
 * Signal Blue recortado no canto. O recorte é transparente, então funciona sobre qualquer fundo
 * e nos dois temas. Decorativo: o nome acessível fica no texto ao lado.
 */
@Component({
  selector: 'nx-brand-mark',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg [attr.width]="size" [attr.height]="size" viewBox="0 0 32 32" aria-hidden="true" focusable="false">
      <defs>
        <mask id="nx-mark-cut" maskUnits="userSpaceOnUse" x="0" y="0" width="32" height="32">
          <rect width="32" height="32" fill="#fff" />
          <rect x="22" y="0" width="10" height="10" rx="2.5" fill="#000" />
        </mask>
      </defs>
      <g mask="url(#nx-mark-cut)">
        <rect width="32" height="32" rx="5" fill="var(--accent-2)" />
        <path d="M9 23V9l14 14V9" fill="none" stroke="var(--on-violet, #fff)" stroke-width="2.8"
              stroke-linecap="square" stroke-linejoin="miter" />
      </g>
      <rect x="24.5" y="2.5" width="5" height="5" rx="1" fill="var(--accent)" />
    </svg>
  `,
  styles: [`:host { display: inline-flex; flex: none; line-height: 0; }`]
})
export class BrandMarkComponent {
  @Input() size = 28;
}
