import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ButtonComponent } from './button/button.component';
import { EmptyStateComponent } from './empty-state/empty-state.component';
import { NoticeComponent } from './notice/notice.component';
import { PageHeaderComponent } from './page-header/page-header.component';
import { PriorityMarkComponent } from './priority-mark/priority-mark.component';
import { SlaChipComponent, slaState } from './sla-chip/sla-chip.component';
import { StatusBadgeComponent, ticketStatusTone } from './status-badge/status-badge.component';

@Component({
  standalone: true,
  imports: [
    ButtonComponent, EmptyStateComponent, NoticeComponent, PageHeaderComponent, PriorityMarkComponent,
    SlaChipComponent, StatusBadgeComponent,
  ],
  template: `
    <button nxButton variant="primary" size="sm" [loading]="loading" [disabled]="loading">Salvar</button>
    <nx-status-badge tone="warning">Aguardando</nx-status-badge>
    <nx-priority-mark level="high" />
    <nx-sla-chip state="crit">00:12</nx-sla-chip>
    <nx-notice tone="critical">Falhou</nx-notice>
    <nx-notice>Aviso</nx-notice>
    <nx-empty-state heading="Vazio" variant="error" />
    <nx-page-header heading="Título" description="Descrição"><span class="action">ação</span></nx-page-header>
  `,
})
class HostComponent {
  loading = false;
}

describe('shared primitives', () => {
  const setup = () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  };

  it('button exposes variant/size and aria-busy only while loading', () => {
    const { fixture, el } = setup();
    const button = el.querySelector('button') as HTMLButtonElement;
    expect(button.dataset['variant']).toBe('primary');
    expect(button.dataset['size']).toBe('sm');
    expect(button.getAttribute('aria-busy')).toBeNull();
    expect(button.querySelector('.spinner')).toBeNull();

    fixture.componentInstance.loading = true;
    fixture.detectChanges();
    expect(button.getAttribute('aria-busy')).toBe('true');
    expect(button.querySelector('.spinner')).not.toBeNull();
    expect(button.disabled).toBeTrue();
  });

  it('status badge keeps its text as the primary content and carries the tone', () => {
    const { el } = setup();
    const badge = el.querySelector('nx-status-badge') as HTMLElement;
    expect(badge.textContent?.trim()).toBe('Aguardando');
    expect(badge.getAttribute('data-tone')).toBe('warning');
  });

  it('priority mark renders a label so color is never the only signal', () => {
    const { el } = setup();
    expect(el.querySelector('nx-priority-mark')?.textContent?.trim()).toBe('Alta');
  });

  it('sla chip adds a screen-reader state prefix', () => {
    const { el } = setup();
    expect(el.querySelector('nx-sla-chip .sr-only')?.textContent).toContain('estourado');
  });

  it('notice announces critical as alert and others as status', () => {
    const { el } = setup();
    const notices = el.querySelectorAll('nx-notice');
    expect(notices[0].getAttribute('role')).toBe('alert');
    expect(notices[1].getAttribute('role')).toBe('status');
  });

  it('empty state announces errors as alert', () => {
    const { el } = setup();
    expect(el.querySelector('nx-empty-state')?.getAttribute('role')).toBe('alert');
  });

  it('page header renders one h1, description and projected actions', () => {
    const { el } = setup();
    expect(el.querySelectorAll('nx-page-header h1').length).toBe(1);
    expect(el.querySelector('nx-page-header h1')?.textContent).toBe('Título');
    expect(el.querySelector('nx-page-header .ph-desc')?.textContent).toBe('Descrição');
    expect(el.querySelector('nx-page-header .action')).not.toBeNull();
  });

  it('maps ticket statuses to semantic tones', () => {
    expect(ticketStatusTone('OPEN')).toBe('info');
    expect(ticketStatusTone('IN_PROGRESS')).toBe('info');
    expect(ticketStatusTone('PENDING')).toBe('warning');
    expect(ticketStatusTone('RESOLVED')).toBe('success');
    expect(ticketStatusTone('REOPENED')).toBe('critical');
    expect(ticketStatusTone('CLOSED')).toBe('neutral');
    expect(ticketStatusTone('ON_HOLD')).toBe('neutral');
  });

  it('derives sla state: breached wins, then <=25% remaining warns', () => {
    expect(slaState(0.9, true)).toBe('crit');
    expect(slaState(0.25, false)).toBe('warn');
    expect(slaState(0.26, false)).toBe('ok');
    expect(slaState(null, false)).toBe('none');
  });
});
