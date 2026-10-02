import { Injectable, signal } from '@angular/core';

export type Theme = 'light' | 'dark';

const KEY = 'nexusops.theme';

/**
 * Tema da interface. Claro é o padrão e não depende do tema do sistema; escuro só entra por escolha da pessoa,
 * que fica guardada neste navegador. O index.html aplica a escolha antes do Angular subir, para não piscar.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<Theme>(this.read());

  constructor() {
    this.apply(this.theme());
  }

  toggle(): void {
    this.set(this.theme() === 'dark' ? 'light' : 'dark');
  }

  set(theme: Theme): void {
    this.theme.set(theme);
    this.apply(theme);
    try {
      localStorage.setItem(KEY, theme);
    } catch {
      /* sem armazenamento: vale só nesta sessão */
    }
  }

  private read(): Theme {
    try {
      return localStorage.getItem(KEY) === 'dark' ? 'dark' : 'light';
    } catch {
      return 'light';
    }
  }

  private apply(theme: Theme): void {
    document.documentElement.setAttribute('data-theme', theme);
  }
}
