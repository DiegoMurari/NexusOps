import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { PortalService, PortalTicket, STAGE_LABELS, STAGE_TONES } from '../../core/portal/portal.service';
import { ButtonComponent, EmptyStateComponent, StatusBadgeComponent } from '../../shared/components';

@Component({
  selector: 'app-portal-home',
  standalone: true,
  imports: [DatePipe, NgTemplateOutlet, RouterLink, MatIconModule, ButtonComponent, EmptyStateComponent, StatusBadgeComponent],
  template: `
    <h1 class="title">Meus pedidos</h1>

    @if (error()) {
      <nx-empty-state variant="error" [heading]="error()!" />
    } @else if (loading()) {
      <p class="muted" role="status">Carregando seus pedidos…</p>
    } @else if (all().length === 0) {
      <section class="empty">
        <h2>Você ainda não abriu nenhum pedido</h2>
        <p>Escolha o assunto, responda ao formulário e acompanhe o andamento por aqui.</p>
        <a nxButton variant="primary" routerLink="/portal/novo"><mat-icon>add</mat-icon>Abrir o primeiro pedido</a>
      </section>
    } @else {
      @if (attention().length > 0) {
        <section class="attention" aria-labelledby="att-title">
          <h2 id="att-title"><mat-icon aria-hidden="true">notification_important</mat-icon>Precisa da sua atenção</h2>
          <ul class="list">
            @for (t of attention(); track t.id) {
              <li>
                <a class="row" [routerLink]="['/portal', t.id]">
                  <span class="num mono">{{ t.ticketNumber }}</span>
                  <span class="main">
                    <b>{{ t.title }}</b>
                    <span class="why">{{ reason(t) }}</span>
                  </span>
                  <span class="go">Abrir<mat-icon aria-hidden="true">chevron_right</mat-icon></span>
                </a>
              </li>
            }
          </ul>
        </section>
      }

      @if (open().length > 0) {
        <section aria-labelledby="open-title">
          <h2 id="open-title" class="sub">Em andamento</h2>
          <ul class="list">
            @for (t of open(); track t.id) { <li><ng-container *ngTemplateOutlet="rowTpl; context: { $implicit: t }" /></li> }
          </ul>
        </section>
      }

      @if (closed().length > 0) {
        <section aria-labelledby="done-title">
          <h2 id="done-title" class="sub">Concluídos</h2>
          <ul class="list">
            @for (t of closed(); track t.id) { <li><ng-container *ngTemplateOutlet="rowTpl; context: { $implicit: t }" /></li> }
          </ul>
        </section>
      }
    }

    <ng-template #rowTpl let-t>
      <a class="row" [routerLink]="['/portal', t.id]">
        <span class="num mono">{{ t.ticketNumber }}</span>
        <span class="main">
          <b>{{ t.title }}</b>
          <span class="meta">{{ t.areaName }}@if (t.topicName) { › {{ t.topicName }} } · atualizado {{ t.updatedAt | date:'dd/MM HH:mm' }}</span>
        </span>
        <nx-status-badge [tone]="tone(t)">{{ label(t) }}</nx-status-badge>
      </a>
    </ng-template>
  `,
  styles: [`
    :host { display: block; }
    .title { margin: 0 0 var(--sp-7); font-size: 1.5rem; font-weight: var(--fw-semibold); }
    .sub { margin: var(--sp-8) 0 var(--sp-4); font-size: var(--fs-sm); font-weight: var(--fw-semibold); color: var(--text-muted); text-transform: uppercase; letter-spacing: .06em; }
    .muted { color: var(--text-muted); }
    .empty { border: 1px dashed var(--border-strong); padding: 48px var(--sp-7); display: grid; gap: var(--sp-4); justify-items: start; }
    .empty h2 { margin: 0; font-size: 1.125rem; }
    .empty p { margin: 0; color: var(--text-muted); max-width: 60ch; }
    .attention { border: 1px solid var(--warning); background: var(--warning-soft, var(--surface-2)); padding: var(--sp-6); }
    .attention h2 { margin: 0 0 var(--sp-4); display: flex; gap: var(--sp-3); align-items: center; font-size: 1rem; }
    .attention h2 mat-icon { color: var(--warning); }
    .list { list-style: none; margin: 0; padding: 0; display: grid; }
    .list li { border-top: 1px solid var(--border); }
    .list li:first-child { border-top: 0; }
    .row { display: grid; grid-template-columns: 112px 1fr auto; gap: var(--sp-5); align-items: center; padding: var(--sp-5) var(--sp-3); text-decoration: none; color: var(--text); }
    .row:hover { background: var(--surface-2); }
    .row:focus-visible { outline: 2px solid var(--signal); outline-offset: -2px; }
    .num { color: var(--text-muted); font-size: var(--fs-xs); }
    .main { display: grid; gap: var(--sp-2); min-width: 0; }
    .main b { font-weight: var(--fw-medium); overflow-wrap: anywhere; }
    .meta, .why { font-size: var(--fs-sm); color: var(--text-muted); }
    .why { color: var(--text); }
    .go { display: inline-flex; align-items: center; color: var(--signal); font-size: var(--fs-sm); font-weight: var(--fw-medium); }
    .go mat-icon { width: 18px; height: 18px; font-size: 18px; }
    @media (max-width: 640px) { .row { grid-template-columns: 1fr; gap: var(--sp-2); } }
  `],
})
export class PortalHomeComponent implements OnInit {
  private portal = inject(PortalService);

  all = signal<PortalTicket[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  attention = computed(() => this.all().filter(t => t.needsAttention));
  open = computed(() => this.all().filter(t => !t.needsAttention && t.stage !== 'CLOSED'));
  closed = computed(() => this.all().filter(t => t.stage === 'CLOSED'));

  ngOnInit(): void {
    this.portal.tickets().subscribe({
      next: list => {
        this.all.set(list);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Não foi possível carregar seus pedidos.');
        this.loading.set(false);
      },
    });
  }

  label(t: PortalTicket): string { return STAGE_LABELS[t.stage]; }
  tone(t: PortalTicket) { return STAGE_TONES[t.stage]; }

  reason(t: PortalTicket): string {
    return t.stage === 'WAITING_YOU'
      ? 'A equipe pediu mais informações para continuar.'
      : 'Há uma solução proposta: confirme se resolveu.';
  }
}
