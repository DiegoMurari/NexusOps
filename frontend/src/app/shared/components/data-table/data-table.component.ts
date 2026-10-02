import {
  ChangeDetectionStrategy, Component, ContentChildren, Directive, EventEmitter, Input, Output, QueryList, TemplateRef,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { EmptyStateComponent } from '../empty-state/empty-state.component';
import { SkeletonComponent } from '../skeleton/skeleton.component';

export interface NxColumn {
  key: string;
  header: string;
  align?: 'start' | 'end';
  /** Render this cell as <th scope="row"> (the row's identifying cell). */
  rowHeader?: boolean;
  sortable?: boolean;
  mono?: boolean;
  muted?: boolean;
  /** Truncate overflowing text with an ellipsis, up to this max width (e.g. '280px'). */
  maxWidth?: string;
  width?: string;
  /** Keep the header accessible but visually hidden (e.g. an actions column). */
  hideHeader?: boolean;
  /** Cells never wrap by default (the table scrolls horizontally); opt in for long free text. */
  wrap?: boolean;
}

export interface NxSort {
  key: string;
  dir: 'asc' | 'desc';
}

// Row type is erased on purpose: Angular cannot infer the row type of a projected ng-template.
export interface NxCellContext {
  $implicit: any;
  index: number;
}

/** Custom cell content: <ng-template nxCell="status" let-row>…</ng-template> */
@Directive({ selector: 'ng-template[nxCell]', standalone: true })
export class NxCellDirective {
  @Input('nxCell') key!: string;
  constructor(public template: TemplateRef<NxCellContext>) {}
}

/**
 * Accessible data table: caption, <th scope>, optional row header, aria-sort,
 * skeleton loading state (aria-busy), empty state, selection and keyboard row activation.
 */
@Component({
  selector: 'nx-data-table',
  standalone: true,
  imports: [NgTemplateOutlet, MatIconModule, EmptyStateComponent, SkeletonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="sr-only" role="status">{{ loading ? loadingLabel : '' }}</span>
    @if (loading || rows.length > 0) {
      <div class="dt-wrap" role="region" tabindex="0" [attr.aria-label]="caption">
        <table class="dt" [class.dt-comfortable]="density === 'comfortable'" [attr.aria-busy]="loading ? 'true' : null">
          <caption class="sr-only">{{ caption }}</caption>
          <thead>
            <tr>
              @for (c of columns; track c.key) {
                <th scope="col" [class.end]="c.align === 'end'" [style.width]="c.width" [attr.aria-sort]="ariaSort(c)">
                  @if (c.sortable) {
                    <button type="button" class="dt-sort" (click)="toggleSort(c)">
                      {{ c.header }}
                      <mat-icon aria-hidden="true">{{ sortIcon(c) }}</mat-icon>
                    </button>
                  } @else if (c.hideHeader) {
                    <span class="sr-only">{{ c.header }}</span>
                  } @else {
                    {{ c.header }}
                  }
                </th>
              }
            </tr>
          </thead>
          <tbody>
            @if (loading) {
              @for (i of skeletonRows; track i) {
                <tr class="dt-skeleton">
                  @for (c of columns; track c.key) {
                    <td><nx-skeleton [width]="c.hideHeader ? '56px' : '70%'" /></td>
                  }
                </tr>
              }
            } @else {
              @for (row of rows; track row; let i = $index) {
                <tr
                  [class.is-selected]="isSelected(row)"
                  [class.is-clickable]="rowClickable"
                  [attr.data-rail]="rail(row)"
                  [attr.aria-selected]="selectedRow !== undefined ? isSelected(row) : null"
                  [attr.tabindex]="rowClickable ? 0 : null"
                  (click)="activate(row, $event)"
                  (keydown.enter)="activate(row, $event)"
                >
                  @for (c of columns; track c.key) {
                    @if (c.rowHeader) {
                      <th scope="row" class="dt-cell" [class.wrap]="c.wrap" [class.end]="c.align === 'end'" [class.mono]="c.mono"
                          [class.muted]="c.muted" [class.truncate]="!!c.maxWidth" [style.max-width]="c.maxWidth">
                        <ng-container *ngTemplateOutlet="content; context: { c: c, row: row, i: i }" />
                      </th>
                    } @else {
                      <td class="dt-cell" [class.wrap]="c.wrap" [class.end]="c.align === 'end'" [class.mono]="c.mono"
                          [class.muted]="c.muted" [class.truncate]="!!c.maxWidth" [style.max-width]="c.maxWidth">
                        <ng-container *ngTemplateOutlet="content; context: { c: c, row: row, i: i }" />
                      </td>
                    }
                  }
                </tr>
              }
            }
          </tbody>
        </table>
      </div>
    } @else {
      <nx-empty-state [heading]="emptyTitle" [description]="emptyDescription"><ng-content /></nx-empty-state>
    }

    <ng-template #content let-c="c" let-row="row" let-i="i">
      @if (cellTemplate(c.key); as tpl) {
        <ng-container *ngTemplateOutlet="tpl; context: { $implicit: row, index: i }" />
      } @else {
        {{ display(row, c.key) }}
      }
    </ng-template>
  `,
  styles: [`
    :host { display: block; }
    .dt-wrap { overflow-x: auto; border: 1px solid var(--border); border-radius: var(--radius-m); background: var(--surface); }
    .dt { width: 100%; border-collapse: separate; border-spacing: 0; font-size: var(--fs-base); font-variant-numeric: tabular-nums; }

    th {
      height: var(--row-h-compact);
      padding: 0 var(--sp-6);
      text-align: start;
      font-size: var(--fs-xs);
      font-weight: var(--fw-semibold);
      letter-spacing: .04em;
      text-transform: uppercase;
      color: var(--text-muted);
      background: var(--surface-2);
      border-bottom: 1px solid var(--border);
      white-space: nowrap;
    }
    .dt-cell {
      height: var(--row-h-compact);
      padding: 0 var(--sp-6);
      border-bottom: 1px solid var(--border);
      color: var(--text);
      font-size: var(--fs-base);
      font-weight: var(--fw-regular);
      letter-spacing: normal;
      text-transform: none;
      text-align: start;
      background: transparent;
      vertical-align: middle;
      white-space: nowrap;
    }
    .dt-cell.wrap { white-space: normal; }
    .dt-skeleton td { height: var(--row-h-compact); padding: 0 var(--sp-6); border-bottom: 1px solid var(--border); }
    .dt-comfortable .dt-cell, .dt-comfortable .dt-skeleton td { height: var(--row-h-comfortable); }
    tbody tr:last-child > * { border-bottom: 0; }
    .end { text-align: end; }
    .mono { font-family: var(--mono); }
    .muted { color: var(--text-muted); }
    .truncate { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

    tbody tr:hover > .dt-cell { background: var(--surface-2); }
    tr.is-clickable { cursor: pointer; }
    tr.is-clickable:focus-visible { outline: var(--focus-ring); outline-offset: -2px; }
    /* Nexus Rail: trilho de 2px na borda da linha para urgência (âmbar/vermelho); o saudável não marca. */
    tr[data-rail='warn'] > :first-child { box-shadow: inset 2px 0 0 var(--warning); }
    tr[data-rail='crit'] > :first-child { box-shadow: inset 2px 0 0 var(--critical); }
    tr.is-selected > .dt-cell { background: var(--accent-soft); }
    tr.is-selected > .dt-cell:first-child { box-shadow: inset 2px 0 0 var(--accent); }

    .dt-sort {
      display: inline-flex;
      align-items: center;
      gap: var(--sp-2);
      padding: 0;
      border: 0;
      background: transparent;
      color: inherit;
      font: inherit;
      letter-spacing: inherit;
      text-transform: inherit;
      cursor: pointer;
    }
    .dt-sort:hover { color: var(--text); }
    .dt-sort mat-icon { width: 14px; height: 14px; font-size: 14px; }
  `]
})
export class DataTableComponent {
  @Input({ required: true }) columns: NxColumn[] = [];
  @Input({ required: true }) rows: readonly unknown[] = [];
  /** Accessible name of the table (rendered as a visually hidden <caption>). */
  @Input({ required: true }) caption!: string;
  @Input() density: 'compact' | 'comfortable' = 'compact';
  @Input() loading = false;
  @Input() loadingLabel = 'Carregando…';
  @Input() emptyTitle = 'Nenhum registro encontrado';
  @Input() emptyDescription?: string;
  @Input() sort: NxSort | null = null;
  @Input() rowClickable = false;
  /** Opcional: devolve 'warn' ou 'crit' para marcar a linha com a Nexus Rail. */
  @Input() rowRail?: (row: any) => 'warn' | 'crit' | null;
  @Input() selectedRow?: unknown;
  @Output() sortChange = new EventEmitter<NxSort>();
  @Output() rowActivate = new EventEmitter<unknown>();

  @ContentChildren(NxCellDirective) private cells!: QueryList<NxCellDirective>;

  readonly skeletonRows = [0, 1, 2, 3, 4];

  cellTemplate(key: string): TemplateRef<NxCellContext> | null {
    return this.cells?.find(c => c.key === key)?.template ?? null;
  }

  display(row: unknown, key: string): string {
    const value = (row as Record<string, unknown>)[key];
    return value === null || value === undefined || value === '' ? '—' : String(value);
  }

  rail(row: unknown): 'warn' | 'crit' | null {
    return this.rowRail ? this.rowRail(row) : null;
  }

  isSelected(row: unknown): boolean {
    return this.selectedRow !== undefined && this.selectedRow === row;
  }

  ariaSort(c: NxColumn): 'ascending' | 'descending' | 'none' | null {
    if (!c.sortable) return null;
    if (this.sort?.key !== c.key) return 'none';
    return this.sort.dir === 'asc' ? 'ascending' : 'descending';
  }

  sortIcon(c: NxColumn): string {
    if (this.sort?.key !== c.key) return 'unfold_more';
    return this.sort.dir === 'asc' ? 'arrow_upward' : 'arrow_downward';
  }

  toggleSort(c: NxColumn): void {
    const dir = this.sort?.key === c.key && this.sort.dir === 'asc' ? 'desc' : 'asc';
    this.sortChange.emit({ key: c.key, dir });
  }

  activate(row: unknown, event: Event): void {
    if (!this.rowClickable) return;
    // Ignore activations that originate from controls inside the row.
    if ((event.target as HTMLElement).closest('button, a, input, select, textarea')) return;
    this.rowActivate.emit(row);
  }
}
