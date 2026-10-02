import { Component, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { ThemeService } from '../../../core/theme/theme.service';

/** Alterna entre o tema claro (padrão) e o escuro. Fica no topo do console, do Portal e do login. */
@Component({
  selector: 'nx-theme-toggle',
  standalone: true,
  imports: [MatIconModule],
  template: `
    <button type="button" class="toggle" (click)="theme.toggle()"
            [attr.aria-pressed]="theme.theme() === 'dark'"
            [attr.aria-label]="theme.theme() === 'dark' ? 'Mudar para o tema claro' : 'Mudar para o tema escuro'"
            [title]="theme.theme() === 'dark' ? 'Tema claro' : 'Tema escuro'">
      <mat-icon aria-hidden="true">{{ theme.theme() === 'dark' ? 'light_mode' : 'dark_mode' }}</mat-icon>
    </button>
  `,
  styles: [`
    .toggle {
      display: flex;
      align-items: center;
      justify-content: center;
      width: var(--control-h-md);
      height: var(--control-h-md);
      border: 1px solid var(--border);
      border-radius: var(--radius-s);
      background: var(--surface);
      color: var(--text-muted);
      cursor: pointer;
      transition: background var(--dur-base) var(--ease), color var(--dur-base) var(--ease);
    }
    .toggle:hover { background: var(--surface-2); color: var(--text); }
    .toggle:focus-visible { outline: var(--focus-ring); outline-offset: 1px; }
    mat-icon { width: 18px; height: 18px; font-size: 18px; }
  `]
})
export class ThemeToggleComponent {
  protected theme = inject(ThemeService);
}
