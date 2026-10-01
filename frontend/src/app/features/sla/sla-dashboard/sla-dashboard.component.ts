import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { SlaService } from '../../../core/sla/sla.service';

@Component({
  selector: 'app-sla-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, MatIconModule],
  template: `
    <h1 class="page-title">SLA</h1>

    <div class="stats-grid">
      <div class="kpi k-critical">
        <mat-icon class="kpi-ic">warning</mat-icon>
        <div class="num">{{ activeBreaches() }}</div>
        <div class="lbl">Violações Ativas</div>
      </div>
      <div class="kpi k-info">
        <mat-icon class="kpi-ic">rule</mat-icon>
        <div class="num">{{ activeDefinitions() }}</div>
        <div class="lbl">Definições Ativas</div>
      </div>
    </div>

    <div class="nav-cards">
      <a class="card nav-card" routerLink="breaches">
        <mat-icon class="nav-ic">warning</mat-icon>
        <div>
          <div class="nav-title">Violações de SLA</div>
          <div class="nav-desc">Acompanhar e reconhecer breaches</div>
        </div>
        <mat-icon class="nav-arrow">chevron_right</mat-icon>
      </a>
      <a class="card nav-card" routerLink="definitions">
        <mat-icon class="nav-ic">rule</mat-icon>
        <div>
          <div class="nav-title">Definições de SLA</div>
          <div class="nav-desc">Gerenciar políticas de tempo de resposta e resolução</div>
        </div>
        <mat-icon class="nav-arrow">chevron_right</mat-icon>
      </a>
    </div>
  `,
  styles: [`
    :host { display: block; }

    .page-title {
      margin: 0 0 20px;
      font-size: 1.5rem;
      font-weight: 600;
      color: var(--text);
    }

    .stats-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 14px;
      margin-bottom: 24px;
      max-width: 460px;
    }

    .kpi-ic {
      color: var(--text-faint);
      font-size: 20px;
      width: 20px;
      height: 20px;
      margin-bottom: 8px;
    }

    .nav-cards {
      display: flex;
      flex-direction: column;
      gap: 10px;
      max-width: 520px;
    }

    .nav-card {
      display: flex;
      align-items: center;
      gap: 14px;
      text-decoration: none;
      cursor: pointer;

      &:hover { background: var(--surface-3); }
    }

    .nav-ic {
      flex: none;
      color: var(--accent);
      font-size: 24px;
      width: 24px;
      height: 24px;
    }

    .nav-title {
      font-size: 13.5px;
      font-weight: 600;
      color: var(--text);
    }

    .nav-desc {
      font-size: 12px;
      color: var(--text-muted);
      margin-top: 2px;
    }

    .nav-arrow {
      margin-left: auto;
      color: var(--text-faint);
    }
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
