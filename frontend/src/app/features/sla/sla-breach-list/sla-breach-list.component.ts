import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SlaService, SlaBreachDto } from '../../../core/sla/sla.service';
import {
  ButtonComponent, DataTableComponent, EmptyStateComponent, NxCellDirective, NxColumn, PageHeaderComponent,
  StatusBadgeComponent,
} from '../../../shared/components';

@Component({
  selector: 'app-sla-breach-list',
  standalone: true,
  imports: [
    CommonModule, ButtonComponent, DataTableComponent, EmptyStateComponent, NxCellDirective, PageHeaderComponent,
    StatusBadgeComponent,
  ],
  template: `
    <nx-page-header heading="Violações de SLA" />

    @if (error()) {
      <nx-empty-state variant="error" [heading]="error()!" />
    } @else {
      <nx-data-table caption="Violações de SLA" [columns]="columns" [rows]="breaches()" [loading]="loading()"
                     emptyTitle="Nenhuma violação de SLA registrada">
        <ng-template nxCell="ticketId" let-b>{{ b.ticketId | slice:0:8 }}</ng-template>
        <ng-template nxCell="breachType" let-b>{{ b.breachType === 'RESPONSE' ? 'Resposta' : 'Resolução' }}</ng-template>
        <ng-template nxCell="breachTime" let-b>{{ b.breachTime | date:'dd/MM/yyyy HH:mm' }}</ng-template>
        <ng-template nxCell="status" let-b>
          @if (b.resolved) {
            <nx-status-badge tone="success">Resolvida</nx-status-badge>
          } @else if (b.acknowledged) {
            <nx-status-badge tone="warning">Reconhecida</nx-status-badge>
          } @else {
            <nx-status-badge tone="critical">Pendente</nx-status-badge>
          }
        </ng-template>
        <ng-template nxCell="actions" let-b>
          @if (!b.acknowledged) {
            <button nxButton size="sm" [disabled]="acting() === b.id" (click)="acknowledge(b)">Reconhecer</button>
          }
          @if (!b.resolved) {
            <button nxButton size="sm" [disabled]="acting() === b.id" (click)="resolve(b)">Resolver</button>
          }
        </ng-template>
      </nx-data-table>
    }
  `,
  styles: [`:host { display: block; }`]
})
export class SlaBreachListComponent implements OnInit {
  readonly columns: NxColumn[] = [
    { key: 'ticketId', header: 'Ticket', rowHeader: true, mono: true },
    { key: 'breachType', header: 'Tipo' },
    { key: 'breachTime', header: 'Detectada em', mono: true, muted: true },
    { key: 'status', header: 'Status' },
    { key: 'actions', header: 'Ações' },
  ];

  breaches = signal<SlaBreachDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  acting = signal<string | null>(null);

  constructor(private slaService: SlaService) {}

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.slaService.listBreaches().subscribe({
      next: res => {
        this.breaches.set(res.content);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Não foi possível carregar as violações de SLA.');
        this.loading.set(false);
      }
    });
  }

  acknowledge(breach: SlaBreachDto): void {
    this.acting.set(breach.id);
    this.slaService.acknowledgeBreach(breach.id).subscribe({
      next: updated => {
        this.breaches.update(list => list.map(b => b.id === updated.id ? updated : b));
        this.acting.set(null);
      },
      error: () => this.acting.set(null)
    });
  }

  resolve(breach: SlaBreachDto): void {
    this.acting.set(breach.id);
    this.slaService.resolveBreach(breach.id).subscribe({
      next: updated => {
        this.breaches.update(list => list.map(b => b.id === updated.id ? updated : b));
        this.acting.set(null);
      },
      error: () => this.acting.set(null)
    });
  }
}
