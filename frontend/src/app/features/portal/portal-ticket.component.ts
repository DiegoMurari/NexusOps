import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { PortalDetail, PortalEvent, PortalService, STAGE_LABELS, STAGE_TONES } from '../../core/portal/portal.service';
import { EVIDENCE_ACCEPT, EvidenceDto, EvidenceService } from '../../core/ticketing/evidence.service';
import { ButtonComponent, EmptyStateComponent, StatusBadgeComponent } from '../../shared/components';

/**
 * Um pedido na visão do solicitante (ADR-013): andamento em linguagem dele, o que a equipe precisa dele agora
 * e o histórico público. Sem fila, prioridade, SLA nem notas internas.
 */
@Component({
  selector: 'app-portal-ticket',
  standalone: true,
  imports: [DatePipe, NgTemplateOutlet, RouterLink, FormsModule, MatIconModule, ButtonComponent, EmptyStateComponent, StatusBadgeComponent],
  template: `
    <a class="back" routerLink="/portal"><mat-icon aria-hidden="true">arrow_back</mat-icon>Meus pedidos</a>

    @if (detail(); as d) {
      <header class="head">
        <span class="num mono">{{ d.ticket.ticketNumber }}</span>
        <h1>{{ d.ticket.title }}</h1>
        <div class="badges">
          <nx-status-badge [tone]="tone()">{{ stageLabel() }}</nx-status-badge>
          <span class="muted">
            {{ d.ticket.areaName }}@if (d.ticket.topicName) { › {{ d.ticket.topicName }} }
            · aberto em {{ d.ticket.createdAt | date:'dd/MM/yyyy HH:mm' }}
            @if (d.ticket.assigneeName) { · atendido por {{ d.ticket.assigneeName }} }
          </span>
        </div>
      </header>

      @if (d.ticket.stage === 'WAITING_YOU') {
        <section class="callout" aria-labelledby="c1">
          <h2 id="c1"><mat-icon aria-hidden="true">help_outline</mat-icon>A equipe precisa de uma informação sua</h2>
          @if (lastRequest(); as q) { <p class="quote">{{ q }}</p> }
          <ng-container *ngTemplateOutlet="replyTpl" />
        </section>
      } @else if (d.ticket.stage === 'AWAITING_VALIDATION') {
        <section class="callout" aria-labelledby="c2">
          <h2 id="c2"><mat-icon aria-hidden="true">task_alt</mat-icon>Confirme se o problema foi resolvido</h2>
          @if (d.pendingSolution) { <p class="quote">{{ d.pendingSolution }}</p> }
          @if (!contesting()) {
            <label class="field">
              <span class="label">Comentário (opcional)</span>
              <input class="input" name="acceptComment" [(ngModel)]="decisionText" maxlength="5000" />
            </label>
            <div class="row">
              <button nxButton variant="primary" type="button" [loading]="busy()" [disabled]="busy()" (click)="acceptSolution()">
                <mat-icon>check</mat-icon>Sim, resolveu
              </button>
              <button nxButton type="button" [disabled]="busy()" (click)="startContest()">Não resolveu</button>
            </div>
          } @else {
            <label class="field">
              <span class="label">O que continua sem funcionar? <span class="req" aria-hidden="true">*</span></span>
              <textarea class="input" rows="3" name="contestText" [(ngModel)]="decisionText" maxlength="5000"></textarea>
            </label>
            <div class="row">
              <button nxButton variant="primary" type="button" [loading]="busy()" [disabled]="busy() || !decisionText.trim()" (click)="contestSolution()">
                Contestar a solução
              </button>
              <button nxButton type="button" [disabled]="busy()" (click)="contesting.set(false)">Voltar</button>
            </div>
          }
          @if (actionError()) { <p class="err" role="alert">{{ actionError() }}</p> }
        </section>
      }

      <div class="grid">
        <div class="col">
          <section class="panel" aria-labelledby="h-tl">
            <h2 id="h-tl">Andamento</h2>
            <ol class="tl">
              @for (e of d.timeline; track e.id) {
                <li [attr.data-actor]="e.actor">
                  <span class="when mono">{{ e.occurredAt | date:'dd/MM HH:mm' }}</span>
                  <div>
                    <b class="what">{{ label(e) }}</b>
                    @if (e.cycleNo > 1) { <span class="tag">Nova rodada</span> }
                    @if (e.text) { <p class="text">{{ e.text }}</p> }
                    @for (a of evidenceOf(e); track a.id) {
                      <button type="button" class="linklike chip" (click)="download(a)"><mat-icon aria-hidden="true">attach_file</mat-icon>{{ a.fileName }}</button>
                    }
                  </div>
                </li>
              }
            </ol>
          </section>

          @if (d.canReply && d.ticket.stage !== 'WAITING_YOU') {
            <section class="panel" aria-labelledby="h-reply">
              <h2 id="h-reply">Enviar mensagem à equipe</h2>
              <ng-container *ngTemplateOutlet="replyTpl" />
            </section>
          }
        </div>

        <aside class="col">
          <section class="panel" aria-labelledby="h-info">
            <h2 id="h-info">Seu pedido</h2>
            @if (d.description) { <p class="text">{{ d.description }}</p> }
            @if (d.answers.length > 0) {
              <dl class="dl">
                @for (a of d.answers; track a.label) { <dt>{{ a.label }}</dt><dd>{{ a.value }}</dd> }
              </dl>
            }
          </section>

          <section class="panel" aria-labelledby="h-ev">
            <h2 id="h-ev">Anexos</h2>
            @if (ticketEvidence().length === 0) {
              <p class="muted">Nenhum anexo.</p>
            } @else {
              <ul class="files">
                @for (a of ticketEvidence(); track a.id) {
                  <li>
                    <button type="button" class="linklike" (click)="download(a)">{{ a.fileName }}</button>
                    <span class="muted mono">{{ evidence.formatSize(a.fileSize) }}</span>
                  </li>
                }
              </ul>
            }
            @if (d.ticket.stage !== 'CLOSED') {
              <label class="pick">
                <mat-icon aria-hidden="true">upload_file</mat-icon>Anexar arquivo
                <input type="file" hidden [accept]="fileAccept" (change)="pickTicketFile($event)" />
              </label>
              @if (fileError()) { <p class="err" role="alert">{{ fileError() }}</p> }
            }
          </section>
        </aside>
      </div>

      <ng-template #replyTpl>
        <label class="field">
          <span class="label">Sua mensagem</span>
          <textarea class="input" rows="3" name="reply" [(ngModel)]="replyText" maxlength="5000"
                    placeholder="Escreva aqui"></textarea>
        </label>
        <ul class="files">
          @for (f of replyFiles(); track $index) {
            <li><mat-icon aria-hidden="true">attach_file</mat-icon>{{ f.name }}
              <button type="button" class="linklike" (click)="dropReplyFile($index)" [attr.aria-label]="'Remover ' + f.name">Remover</button></li>
          }
        </ul>
        <div class="row">
          <label class="pick">
            <mat-icon aria-hidden="true">attach_file</mat-icon>Anexar
            <input type="file" hidden [accept]="fileAccept" (change)="pickReplyFile($event)" />
          </label>
          <span class="spacer"></span>
          <button nxButton variant="primary" type="button" [loading]="busy()" [disabled]="busy() || !replyText.trim()" (click)="reply()">
            <mat-icon>send</mat-icon>Enviar
          </button>
        </div>
        @if (fileError()) { <p class="err" role="alert">{{ fileError() }}</p> }
        @if (actionError()) { <p class="err" role="alert">{{ actionError() }}</p> }
      </ng-template>
    } @else if (error()) {
      <nx-empty-state variant="error" [heading]="error()!" />
    } @else {
      <p class="muted" role="status">Carregando o pedido…</p>
    }
  `,
  styles: [`
    :host { display: block; }
    .back { display: inline-flex; align-items: center; gap: var(--sp-2); color: var(--text-muted); text-decoration: none; font-size: var(--fs-sm); }
    .back mat-icon { width: 16px; height: 16px; font-size: 16px; }
    .head { margin: var(--sp-5) 0 var(--sp-7); display: grid; gap: var(--sp-3); }
    .head h1 { margin: 0; font-size: 1.5rem; font-weight: var(--fw-semibold); overflow-wrap: anywhere; }
    .num { color: var(--text-muted); font-size: var(--fs-xs); }
    .badges { display: flex; align-items: center; gap: var(--sp-5); flex-wrap: wrap; }
    .muted { color: var(--text-muted); font-size: var(--fs-sm); }
    .callout { border: 1px solid var(--warning); background: var(--warning-soft, var(--surface-2)); padding: var(--sp-6); margin-bottom: var(--sp-7); display: grid; gap: var(--sp-4); }
    .callout h2 { margin: 0; display: flex; align-items: center; gap: var(--sp-3); font-size: 1rem; }
    .callout h2 mat-icon { color: var(--warning); }
    .quote { margin: 0; padding-inline-start: var(--sp-5); border-inline-start: 3px solid var(--warning); white-space: pre-wrap; }
    .grid { display: grid; grid-template-columns: minmax(0, 3fr) minmax(0, 2fr); gap: var(--sp-7); align-items: start; }
    .col { display: grid; gap: var(--sp-6); min-width: 0; }
    .panel { border: 1px solid var(--border); background: var(--surface); padding: var(--sp-6); display: grid; gap: var(--sp-4); }
    .panel h2 { margin: 0; font-size: var(--fs-sm); text-transform: uppercase; letter-spacing: .06em; color: var(--text-muted); font-weight: var(--fw-semibold); }
    .tl { list-style: none; margin: 0; padding: 0; display: grid; }
    .tl li { display: grid; grid-template-columns: 84px 1fr; gap: var(--sp-5); padding: var(--sp-4) 0; border-top: 1px solid var(--border); font-size: var(--fs-sm); }
    .tl li:first-child { border-top: 0; }
    .when { color: var(--text-muted); font-size: var(--fs-xs); padding-top: 2px; }
    .what { font-weight: var(--fw-medium); }
    .tag { margin-inline-start: var(--sp-3); border: 1px solid var(--border-strong); border-radius: var(--radius-xs); padding: 0 var(--sp-3); font-size: var(--fs-xs); color: var(--text-muted); }
    .text { margin: var(--sp-2) 0 0; white-space: pre-wrap; overflow-wrap: anywhere; }
    .dl { display: grid; grid-template-columns: max-content 1fr; gap: var(--sp-2) var(--sp-5); margin: 0; font-size: var(--fs-sm); }
    .dl dt { color: var(--text-muted); }
    .dl dd { margin: 0; overflow-wrap: anywhere; }
    .field { display: grid; gap: var(--sp-2); }
    .req { color: var(--critical); }
    .err { margin: 0; color: var(--critical); font-size: var(--fs-sm); }
    .row { display: flex; align-items: center; gap: var(--sp-4); flex-wrap: wrap; }
    .spacer { flex: 1; }
    .files { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--sp-3); font-size: var(--fs-sm); }
    .files li { display: flex; align-items: center; gap: var(--sp-4); }
    .files mat-icon { width: 16px; height: 16px; font-size: 16px; color: var(--text-muted); }
    .linklike { background: none; border: 0; padding: 0; font: inherit; color: var(--signal); cursor: pointer; text-align: start; overflow-wrap: anywhere; }
    .linklike:hover { text-decoration: underline; }
    .chip { display: inline-flex; align-items: center; gap: var(--sp-2); margin: var(--sp-2) var(--sp-4) 0 0; font-size: var(--fs-xs); }
    .chip mat-icon { width: 14px; height: 14px; font-size: 14px; }
    .pick { display: inline-flex; align-items: center; gap: var(--sp-3); color: var(--signal); font-size: var(--fs-sm); font-weight: var(--fw-medium); cursor: pointer; }
    .pick:focus-within { outline: 2px solid var(--signal); outline-offset: 2px; }
    .pick mat-icon { width: 18px; height: 18px; font-size: 18px; }
    @media (max-width: 800px) { .grid { grid-template-columns: 1fr; } .tl li { grid-template-columns: 1fr; gap: var(--sp-2); } }
  `],
})
export class PortalTicketComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private portal = inject(PortalService);
  protected evidence = inject(EvidenceService);

  readonly fileAccept = EVIDENCE_ACCEPT;

  detail = signal<PortalDetail | null>(null);
  error = signal<string | null>(null);
  busy = signal(false);
  actionError = signal<string | null>(null);
  fileError = signal<string | null>(null);
  contesting = signal(false);
  replyFiles = signal<File[]>([]);
  replyText = '';
  decisionText = '';

  stageLabel = computed(() => (this.detail() ? STAGE_LABELS[this.detail()!.ticket.stage] : ''));
  tone = computed(() => (this.detail() ? STAGE_TONES[this.detail()!.ticket.stage] : 'neutral'));
  ticketEvidence = computed(() => (this.detail()?.evidence ?? []).filter(a => a.subjectType === 'TICKET'));

  private id = '';

  ngOnInit(): void {
    this.id = this.route.snapshot.paramMap.get('id') ?? '';
    this.portal.ticket(this.id).subscribe({
      next: d => this.detail.set(d),
      error: () => this.error.set('Não encontramos este pedido.'),
    });
  }

  /** O pedido de informação mais recente da equipe. */
  lastRequest(): string | null {
    const list = this.detail()?.timeline ?? [];
    for (let i = list.length - 1; i >= 0; i--) {
      if (list[i].type === 'INFO_REQUESTED') return list[i].text;
    }
    return null;
  }

  evidenceOf(e: PortalEvent): EvidenceDto[] {
    return (this.detail()?.evidence ?? []).filter(a => a.subjectType === 'EVENT' && a.subjectId === e.id);
  }

  label(e: PortalEvent): string {
    switch (e.type) {
      case 'CREATED': return 'Pedido aberto';
      case 'TAKEN': return 'A equipe começou a atender';
      case 'INFO_REQUESTED': return 'A equipe pediu mais informações';
      case 'REQUESTER_REPLIED': return 'Você respondeu';
      case 'COMMENT_ADDED': return e.actor === 'REQUESTER' ? 'Você enviou uma mensagem' : 'A equipe enviou uma mensagem';
      case 'EVIDENCE_ADDED': return 'Arquivo anexado';
      case 'EVIDENCE_REMOVED': return 'Arquivo removido';
      case 'RESOLVED': return 'Solução proposta';
      case 'ACCEPTED': return 'Você confirmou a solução';
      case 'AUTO_ACCEPTED': return 'Solução aceita automaticamente';
      case 'CONTESTED': return 'Você contestou a solução';
      case 'REOPENED': return 'Pedido reaberto para nova análise';
      default: return e.type;
    }
  }

  download(a: EvidenceDto): void {
    this.evidence.download(a);
  }

  pickReplyFile(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    const problem = this.evidence.clientError(file);
    this.fileError.set(problem);
    if (!problem) this.replyFiles.update(l => [...l, file]);
  }

  dropReplyFile(i: number): void {
    this.replyFiles.update(l => l.filter((_, idx) => idx !== i));
  }

  /** Envia a mensagem e depois liga os arquivos escolhidos ao evento que ela criou. */
  reply(): void {
    const files = this.replyFiles();
    this.run(this.portal.reply(this.id, this.replyText.trim()), d => {
      this.replyText = '';
      this.replyFiles.set([]);
      const mine = [...d.timeline].reverse().find(e => e.actor === 'REQUESTER' && (e.type === 'REQUESTER_REPLIED' || e.type === 'COMMENT_ADDED'));
      if (files.length === 0 || !mine) return;
      let pending = files.length;
      const done = () => { if (--pending === 0) this.reload(); };
      files.forEach(f => this.evidence.attach(this.id, f, { subjectType: 'EVENT', subjectId: mine.id }).subscribe({
        next: done,
        error: (err: HttpErrorResponse) => { this.fileError.set(this.message(err, 'Um anexo não foi enviado.')); done(); },
      }));
    });
  }

  acceptSolution(): void {
    this.run(this.portal.accept(this.id, this.decisionText.trim()), () => { this.decisionText = ''; });
  }

  startContest(): void {
    this.decisionText = '';
    this.actionError.set(null);
    this.contesting.set(true);
  }

  contestSolution(): void {
    this.run(this.portal.contest(this.id, this.decisionText.trim()), () => {
      this.decisionText = '';
      this.contesting.set(false);
    });
  }

  pickTicketFile(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    const problem = this.evidence.clientError(file);
    this.fileError.set(problem);
    if (problem) return;
    this.evidence.attach(this.id, file).subscribe({
      next: () => this.reload(),
      error: (err: HttpErrorResponse) => this.fileError.set(this.message(err, 'Não foi possível anexar o arquivo.')),
    });
  }

  private run(call: ReturnType<PortalService['reply']>, then: (d: PortalDetail) => void): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.actionError.set(null);
    call.subscribe({
      next: d => {
        this.detail.set(d);
        this.busy.set(false);
        then(d);
      },
      error: (err: HttpErrorResponse) => {
        this.busy.set(false);
        this.actionError.set(this.message(err, 'Não foi possível concluir. Tente novamente.'));
      },
    });
  }

  private reload(): void {
    this.portal.ticket(this.id).subscribe({ next: d => this.detail.set(d), error: () => undefined });
  }

  private message(err: HttpErrorResponse, fallback: string): string {
    const ext = err.error?.extensions as Record<string, string> | undefined;
    const first = ext ? Object.values(ext)[0] : undefined;
    return first ?? (err.status === 422 && typeof err.error?.detail === 'string' ? err.error.detail : fallback);
  }
}
