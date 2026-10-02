import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { SlaService } from '../../../core/sla/sla.service';
import { PageHeaderComponent } from '../../../shared/components';

@Component({
  selector: 'app-sla-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, MatIconModule, PageHeaderComponent],
  template: `
    <nx-page-header heading="SLA" />

    <div class="stats-grid">
      <div class="kpi k-critical">
        <div class="num">{{ activeBreaches() }}</div>
        <div class="lbl">Violações ativas</div>
      </div>
      <div class="kpi k-info">
        <div class="num">{{ activeDefinitions() }}</div>
        <div class="lbl">Definições ativas</div>
      </div>
    </div>

    <div class="nav-cards">
      <a class="card nav-card" routerLink="breaches">
        <mat-icon class="nav-ic" aria-hidden="true">warning</mat-icon>
        <div>
          <div class="nav-title">Violações de SLA</div>
          <div class="nav-desc">Acompanhar e reconhecer breaches</div>
        </div>
        <mat-icon class="nav-arrow" aria-hidden="true">chevron_right</mat-icon>
      </a>
      <a class="card nav-card" routerLink="definitions">
        <mat-icon class="nav-ic" aria-hidden="true">rule</mat-icon>
        <div>
          <div class="nav-title">Definições de SLA</div>
          <div class="nav-desc">Gerenciar políticas de tempo de resposta e resolução</div>
        </div>
        <mat-icon class="nav-arrow" aria-hidden="true">chevron_right</mat-icon>
      </a>
    </div>
  `,
  styles: [`
    :host { display: block; }

    .stats-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: var(--sp-5);
      margin-bottom: var(--sp-8);
      max-width: 460px;
    }

    .nav-cards { display: flex; flex-direction: column; gap: var(--sp-4); max-width: 520px; }

    .nav-card {
      display: flex;
      align-items: center;
      gap: var(--sp-5);
      text-decoration: none;
      cursor: pointer;
      transition: background-color var(--dur-fast) var(--ease);

      &:hover { background: var(--surface-2); }
    }

    .nav-ic { flex: none; color: var(--text-muted); font-size: 24px; width: 24px; height: 24px; }
    .nav-title { font-size: var(--fs-md); font-weight: var(--fw-semibold); color: var(--text); }
    .nav-desc { font-size: var(--fs-sm); color: var(--text-muted); margin-top: var(--sp-1); }
    .nav-arrow { margin-left: auto; color: var(--text-muted); }
  `]
})
export class SlaDashboardComponent implements OnInit {
  activeBreaches = signal(0);
  activeDefinitions = signal(0);

  constructor(private slaService: SlaService) {}

  ngOnInit(): void {
    this.slaService.listActiveBreaches().subscribe({
      next: breaches => this.activeBreaches.set(breaches.length),
      error: () => this.activeBreaches.set(0)
    });
    this.slaService.listDefinitions(true).subscribe({
      next: defs => this.activeDefinitions.set(defs.length),
      error: () => this.activeDefinitions.set(0)
    });
  }
}
