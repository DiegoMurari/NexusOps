import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

/** Page title block. Projected content is rendered as the action group (right side). */
@Component({
  selector: 'nx-page-header',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="ph">
      <div class="ph-text">
        <h1 class="ph-title">{{ heading }}</h1>
        @if (description) { <p class="ph-desc">{{ description }}</p> }
      </div>
      <div class="ph-actions"><ng-content /></div>
    </header>
  `,
  styles: [`
    :host { display: block; margin-bottom: var(--sp-6); }
    .ph { display: flex; flex-wrap: wrap; align-items: flex-start; justify-content: space-between; gap: var(--sp-5); }
    .ph-text { min-width: 0; }
    .ph-title { margin: 0; font-size: var(--fs-xl); line-height: 28px; font-weight: var(--fw-semibold); color: var(--text); }
    .ph-desc { margin: var(--sp-2) 0 0; max-width: 72ch; font-size: var(--fs-base); color: var(--text-muted); }
    .ph-actions { display: flex; flex-wrap: wrap; align-items: center; gap: var(--sp-4); }
    .ph-actions:empty { display: none; }
  `]
})
export class PageHeaderComponent {
  @Input({ required: true }) heading!: string;
  @Input() description?: string;
}
