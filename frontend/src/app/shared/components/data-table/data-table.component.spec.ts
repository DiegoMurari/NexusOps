import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DataTableComponent, NxCellDirective, NxColumn, NxSort } from './data-table.component';

@Component({
  standalone: true,
  imports: [DataTableComponent, NxCellDirective],
  template: `
    <nx-data-table caption="Usuários" [columns]="columns" [rows]="rows" [loading]="loading" [sort]="sort"
                   [rowClickable]="true" emptyTitle="Nada por aqui"
                   (sortChange)="sorted = $event" (rowActivate)="activated = $event">
      <ng-template nxCell="status" let-r><b class="cell-status">{{ r.status }}</b> <button class="inner">x</button></ng-template>
    </nx-data-table>
  `,
})
class HostComponent {
  columns: NxColumn[] = [
    { key: 'name', header: 'Nome', rowHeader: true, sortable: true },
    { key: 'email', header: 'Email' },
    { key: 'status', header: 'Status' },
  ];
  rows: unknown[] = [
    { name: 'Ana', email: 'ana@x.com', status: 'ok' },
    { name: 'Bruno', email: null, status: 'off' },
  ];
  loading = false;
  sort: NxSort | null = null;
  sorted: NxSort | null = null;
  activated: unknown = null;
}

describe('DataTableComponent', () => {
  let fixture: ComponentFixture<HostComponent>;
  let host: HostComponent;
  const el = () => fixture.nativeElement as HTMLElement;

  beforeEach(() => {
    fixture = TestBed.createComponent(HostComponent);
    host = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('exposes table semantics: caption, scoped headers and a row header', () => {
    expect(el().querySelector('caption')?.textContent).toBe('Usuários');
    const heads = el().querySelectorAll('thead th');
    expect(heads.length).toBe(3);
    heads.forEach(th => expect(th.getAttribute('scope')).toBe('col'));
    expect(el().querySelectorAll('tbody th[scope="row"]').length).toBe(2);
    expect(el().querySelector('.dt-wrap')?.getAttribute('role')).toBe('region');
  });

  it('renders custom cell templates and a dash for empty values', () => {
    expect(el().querySelectorAll('.cell-status').length).toBe(2);
    const cells = Array.from(el().querySelectorAll('tbody tr:nth-child(2) td')).map(td => td.textContent?.trim());
    expect(cells[0]).toBe('—');
  });

  it('shows skeleton rows with aria-busy while loading instead of data rows', () => {
    host.loading = true;
    fixture.detectChanges();
    expect(el().querySelector('table')?.getAttribute('aria-busy')).toBe('true');
    expect(el().querySelectorAll('tr.dt-skeleton').length).toBeGreaterThan(0);
    expect(el().querySelector('.cell-status')).toBeNull();
    expect(el().querySelector('[role="status"]')?.textContent).toContain('Carregando');
  });

  it('shows the empty state and no table when there are no rows', () => {
    host.rows = [];
    fixture.detectChanges();
    expect(el().querySelector('table')).toBeNull();
    expect(el().querySelector('nx-empty-state')?.textContent).toContain('Nada por aqui');
  });

  it('reflects sort state in aria-sort and toggles asc -> desc', () => {
    const th = () => el().querySelector('thead th') as HTMLElement;
    expect(th().getAttribute('aria-sort')).toBe('none');
    (th().querySelector('button') as HTMLButtonElement).click();
    expect(host.sorted).toEqual({ key: 'name', dir: 'asc' });

    host.sort = { key: 'name', dir: 'asc' };
    fixture.detectChanges();
    expect(th().getAttribute('aria-sort')).toBe('ascending');
    (th().querySelector('button') as HTMLButtonElement).click();
    expect(host.sorted).toEqual({ key: 'name', dir: 'desc' });
  });

  it('activates a row on click but ignores clicks on controls inside the row', () => {
    (el().querySelector('tbody tr') as HTMLElement).click();
    expect(host.activated).toBe(host.rows[0]);

    host.activated = null;
    (el().querySelector('tbody tr button.inner') as HTMLButtonElement).click();
    expect(host.activated).toBeNull();
  });
});
