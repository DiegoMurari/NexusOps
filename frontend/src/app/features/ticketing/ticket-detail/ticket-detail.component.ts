import { Component, OnInit, signal, computed } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { HttpErrorResponse } from '@angular/common/http';
import {
  TicketService, TicketDto, TicketStatus, TransitionRequest, VALID_TRANSITIONS,
  TicketEventDto, TicketCyclesDto, ConsoleContext,
} from '../../../core/ticketing/ticket.service';
import { CatalogService, QueueDto, QueueMemberDto } from '../../../core/catalog/catalog.service';
import { AssetService, LinkedAssetDto } from '../../../core/asset/asset.service';
import { ArticleDto, KnowledgeService, LinkedArticleDto } from '../../../core/knowledge/knowledge.service';
import { AuthService } from '../../../core/auth/auth.service';
import { EvidenceDto, EvidenceService, EvidenceSubject, EVIDENCE_ACCEPT } from '../../../core/ticketing/evidence.service';
import {
  ActionIconComponent, ActionName, ChainNode, ContextChainComponent, PriorityLevel, PriorityMarkComponent,
  SlaRulerComponent, StatusBadgeComponent, slaFromTicket, ticketStatusGlyph, ticketStatusTone,
} from '../../../shared/components';

const STATUS_LABELS: Record<TicketStatus, string> = {
  OPEN: 'Aberto',
  IN_PROGRESS: 'Em andamento',
  WAITING: 'Aguardando solicitante',
  ON_HOLD: 'Em espera',
  RESOLVED: 'Resolvido',
  CLOSED: 'Fechado',
  REOPENED: 'Reaberto',
};

const PRIORITY_LABELS: Record<string, string> = {
  LOW: 'Baixa',
  MEDIUM: 'Média',
  HIGH: 'Alta',
  CRITICAL: 'Crítica',
};

const EVENT_LABELS: Record<string, string> = {
  CREATED: 'Chamado aberto',
  ROUTED: 'Roteado para a fila',
  QUEUE_CHANGED: 'Fila alterada',
  ASSIGNED: 'Responsável atribuído',
  REASSIGNED: 'Responsável alterado',
  UNASSIGNED: 'Responsável removido',
  TAKEN: 'Atendimento assumido',
  HELD: 'Colocado em espera',
  INFO_REQUESTED: 'Informação solicitada ao solicitante',
  RESUMED: 'Atendimento retomado',
  REQUESTER_REPLIED: 'Solicitante respondeu',
  COMMENT_ADDED: 'Comentário',
  INTERNAL_NOTE_ADDED: 'Nota interna',
  EVIDENCE_ADDED: 'Evidência anexada',
  EVIDENCE_REMOVED: 'Evidência removida',
  PRIORITY_CHANGED: 'Prioridade alterada',
  TICKET_UPDATED: 'Chamado atualizado',
  RESOLVED: 'Solução registrada',
  ACCEPTED: 'Solução aceita pelo solicitante',
  AUTO_ACCEPTED: 'Solução aceita automaticamente',
  CONTESTED: 'Solução contestada',
  REOPENED: 'Novo ciclo de atendimento',
  SLA_WARNED: 'SLA em atenção',
  SLA_BREACHED: 'SLA estourado',
};

/** Verbo operacional de cada transição: rótulo e ícone. A transição IN_PROGRESS muda de nome conforme a origem. */
function verbFor(from: TicketStatus, target: TicketStatus): { label: string; icon: ActionName | null; primary: boolean } {
  switch (target) {
    case 'IN_PROGRESS':
      return from === 'OPEN' || from === 'REOPENED'
        ? { label: 'Assumir', icon: 'take', primary: true }
        : { label: 'Retomar', icon: 'take', primary: true };
    case 'WAITING': return { label: 'Aguardar solicitante', icon: null, primary: false };
    case 'ON_HOLD': return { label: 'Em espera', icon: 'hold', primary: false };
    case 'RESOLVED': return { label: 'Resolver', icon: 'resolve', primary: false };
    default: return { label: STATUS_LABELS[target], icon: null, primary: false };
  }
}

@Component({
  selector: 'app-ticket-detail',
  standalone: true,
  imports: [
    DatePipe, RouterLink, FormsModule, MatIconModule, ActionIconComponent, ContextChainComponent,
    PriorityMarkComponent, SlaRulerComponent, StatusBadgeComponent,
  ],
  template: `
    @if (ticket(); as t) {
      <header class="head">
        <a class="back" routerLink="/tickets"><mat-icon aria-hidden="true">arrow_back</mat-icon>Tickets</a>
        <div class="title-row">
          <span class="num">{{ t.ticketNumber }}</span>
          <nx-status-badge [tone]="tone(t.status)" [glyph]="glyph(t.status)">{{ statusLabel(t.status) }}</nx-status-badge>
          <nx-priority-mark [level]="level(t.priority)" [label]="priorityLabel(t.priority)" />
          @if (sla(); as s) {
            <nx-sla-ruler [pct]="s.pct" [state]="s.state" [label]="s.label" />
          }
        </div>
        <h1>{{ t.title }}</h1>
      </header>

      <nx-context-chain [nodes]="chain()" />

      @if (actionError()) {
        <p class="form-error" role="alert"><mat-icon aria-hidden="true">error_outline</mat-icon>{{ actionError() }}</p>
      }

      <section class="verbs" aria-label="Ações operacionais">
        @if (t.status === 'RESOLVED') {
          <p class="note">Aguardando a validação do solicitante. O chamado só é fechado quando ele aceitar a solução.</p>
        } @else if (t.status === 'CLOSED') {
          <p class="note">Chamado fechado pelo solicitante. Somente leitura.</p>
        } @else if (resolving()) {
          <div class="resolve">
            <label for="solution">Solução aplicada <span class="req">obrigatória</span></label>
            <textarea id="solution" rows="4" [(ngModel)]="solutionText" placeholder="Descreva o que foi feito para resolver."></textarea>
            <div class="row">
              <button type="button" class="nx-verb primary" [disabled]="acting() || !solutionText.trim()" (click)="confirmResolve()">
                <nx-action-icon name="resolve" />Registrar solução
              </button>
              <button type="button" class="nx-verb" [disabled]="acting()" (click)="cancelResolve()">Cancelar</button>
            </div>
          </div>
        } @else {
          <div class="row">
            @for (v of verbs(); track v.target) {
              <button type="button" class="nx-verb" [class.primary]="v.primary" [disabled]="acting()" (click)="transition(v.target)">
                @if (v.icon) { <nx-action-icon [name]="v.icon" /> }{{ v.label }}
              </button>
            }
            <div class="assign">
              <label class="sr-only" for="assignee">Atribuir a</label>
              <select id="assignee" [(ngModel)]="assigneeInput">
                <option value="">Atribuir a…</option>
                @for (m of members(); track m.userId) {
                  <option [value]="m.email ?? ''">{{ m.name }}</option>
                }
              </select>
              <button type="button" class="nx-verb" [disabled]="acting() || !assigneeInput.trim()" (click)="assign()">
                <nx-action-icon name="assign" />Atribuir
              </button>
            </div>
            <div class="assign queue-move">
              <label class="sr-only" for="queue-target">Mover para a fila</label>
              <select id="queue-target" [(ngModel)]="queueTarget">
                <option value="">Mover para a fila…</option>
                @for (q of queues(); track q.id) {
                  @if (q.id !== t.queueId) { <option [value]="q.id">{{ q.name }}</option> }
                }
              </select>
              <button type="button" class="nx-verb" [disabled]="acting() || !queueTarget" (click)="moveQueue()">Mover</button>
            </div>
          </div>
        }
      </section>

      <div class="grid">
        <div class="main">
          <section class="panel">
            <h2>Descrição</h2>
            <p class="desc">{{ t.description || 'Sem descrição.' }}</p>
          </section>

          @if (resolutions().length > 0) {
            <section class="panel">
              <h2>Soluções</h2>
              @for (c of resolutions(); track c.cycleNo) {
                <article class="sol">
                  <header>
                    <span class="mono">Ciclo {{ c.cycleNo }}</span>
                    @if (c.resolution?.outcome === 'ACCEPTED') { <span class="tag ok">Aceita</span> }
                    @else if (c.resolution?.outcome === 'CONTESTED') { <span class="tag crit">Contestada</span> }
                    @else { <span class="tag">Aguardando validação</span> }
                  </header>
                  <p>{{ c.resolution?.solutionText }}</p>
                  @if (c.resolution?.decisionComment) {
                    <p class="decision">Motivo do solicitante: {{ c.resolution?.decisionComment }}</p>
                  }
                  <footer class="mono">
                    {{ c.resolution?.resolvedBy }} · {{ c.resolution?.resolvedAt | date:'dd/MM/yyyy HH:mm' }}
                  </footer>
                </article>
              }
            </section>
          }

          <section class="panel">
            <h2>Comentar</h2>
            <label class="sr-only" for="comment-box">Comentário</label>
            <textarea id="comment-box" rows="3" [(ngModel)]="commentText" placeholder="Escreva uma mensagem ou nota."></textarea>
            <div class="ev-add">
              <label class="ev-internal">
                <input type="checkbox" [(ngModel)]="commentInternal" name="commentInternal" /> Nota interna (o solicitante não vê)
              </label>
              <button type="button" class="nx-verb primary" [disabled]="acting() || !commentText.trim()" (click)="sendComment()">
                {{ commentInternal ? 'Registrar nota' : 'Responder ao solicitante' }}
              </button>
            </div>
          </section>

          <section class="panel">
            <h2>Evidências</h2>
            @if (evidence().length === 0) {
              <p class="note">Nenhuma evidência anexada.</p>
            } @else {
              <ul class="ev">
                @for (a of evidence(); track a.id) {
                  <li>
                    <mat-icon aria-hidden="true">{{ a.mimeType.startsWith('image/') ? 'image' : 'description' }}</mat-icon>
                    <div class="ev-main">
                      <button type="button" class="linklike" (click)="downloadEvidence(a)">{{ a.fileName }}</button>
                      <span class="ev-meta mono">{{ evidenceService.formatSize(a.fileSize) }} · {{ evidenceSubject(a) }}</span>
                    </div>
                    @if (a.internal) { <span class="tag">Interna</span> }
                    <button type="button" class="icon-btn" (click)="removeEvidence(a)" [attr.aria-label]="'Remover ' + a.fileName">
                      <mat-icon aria-hidden="true">close</mat-icon>
                    </button>
                  </li>
                }
              </ul>
            }
            <div class="ev-add">
              <label class="ev-pick">
                <mat-icon aria-hidden="true">attach_file</mat-icon>
                Anexar ao chamado
                <input type="file" [accept]="evidenceAccept" (change)="pickEvidence($event)" hidden />
              </label>
              <label class="ev-internal">
                <input type="checkbox" [(ngModel)]="evidenceInternal" name="evidenceInternal" /> Interna (só a equipe vê)
              </label>
            </div>
            @if (evidenceError()) { <p class="form-error" role="alert">{{ evidenceError() }}</p> }
          </section>

          <section class="panel">
            <h2>Linha do tempo</h2>
            @if (events().length === 0) {
              <p class="note">Sem eventos registrados.</p>
            } @else {
              <ol class="tl">
                @for (e of events(); track e.id) {
                  <li [attr.data-internal]="e.visibility === 'INTERNAL' ? '' : null">
                    <span class="when mono">{{ e.occurredAt | date:'dd/MM HH:mm' }}</span>
                    <div>
                      <span class="what">{{ eventLabel(e.type) }}</span>
                      @if (e.visibility === 'INTERNAL') { <span class="tag">Interno</span> }
                      @if (e.cycleNo > 1) { <span class="tag">Ciclo {{ e.cycleNo }}</span> }
                      @if (eventText(e); as text) { <p class="evt-text">{{ text }}</p> }
                      @for (a of evidenceOf(e); track a.id) {
                        <button type="button" class="linklike ev-chip" (click)="downloadEvidence(a)">
                          <mat-icon aria-hidden="true">attach_file</mat-icon>{{ a.fileName }}
                        </button>
                      }
                      <span class="who mono">{{ e.actorId || e.actorKind }}</span>
                      <button type="button" class="linklike ev-step" (click)="attachToEvent(e, stepFile)">Anexar a esta etapa</button>
                    </div>
                  </li>
                }
              </ol>
              <input #stepFile type="file" [accept]="evidenceAccept" (change)="pickStepEvidence($event)" hidden />
            }
          </section>
        </div>

        <aside class="side">
          @if (context(); as c) {
            <section class="panel" aria-label="Origem e atendimento">
              <h2>Contexto</h2>
              <dl>
                <dt>Fila</dt><dd>{{ c.queueName || '—' }}</dd>
                <dt>Área</dt><dd>{{ c.areaName || '—' }}</dd>
                <dt>Tópico</dt><dd>{{ c.topicName || '—' }}</dd>
                <dt>Solicitante</dt><dd>{{ c.reporterName || '—' }}</dd>
                <dt>Responsável</dt><dd>{{ c.assigneeName || 'Sem responsável' }}</dd>
              </dl>
              @if (c.answers.length > 0) {
                <h2 class="sub">Respostas do formulário</h2>
                <dl>
                  @for (a of c.answers; track a.label) { <dt>{{ a.label }}</dt><dd>{{ a.value }}</dd> }
                </dl>
              }
            </section>
          }
          @if (canReadAssets) {
            <section class="panel" aria-label="Ativos relacionados">
              <h2>Ativos relacionados</h2>
              @if (assets().length === 0) {
                <p class="note">Nenhum ativo vinculado. O vínculo é feito na tela do ativo.</p>
              } @else {
                <ul class="asset-list">
                  @for (a of assets(); track a.assetId) {
                    <li>
                      <a [routerLink]="['/assets', a.assetId]"><span class="mono">{{ a.assetTag }}</span> {{ a.name }}</a>
                    </li>
                  }
                </ul>
              }
            </section>
          }
          @if (canReadKnowledge) {
            <section class="panel" aria-label="Artigos relacionados">
              <h2>Artigos relacionados</h2>
              @if (articleError()) { <p class="form-error" role="alert">{{ articleError() }}</p> }
              @if (canLinkArticles) {
                <input class="input" type="search" placeholder="Buscar artigo publicado" aria-label="Buscar artigo para vincular"
                       [ngModel]="articleQuery()" (ngModelChange)="searchArticles($event)" />
                @if (articleResults().length > 0) {
                  <ul class="asset-list picks" role="listbox" aria-label="Artigos encontrados">
                    @for (r of articleResults(); track r.id) {
                      <li><button type="button" class="pick" (click)="linkArticle(r.id)">{{ r.title }}</button></li>
                    }
                  </ul>
                }
              }
              @if (articles().length === 0) {
                <p class="note">Nenhum artigo vinculado a este ticket.</p>
              } @else {
                <ul class="asset-list">
                  @for (a of articles(); track a.articleId) {
                    <li class="art-row">
                      <a [routerLink]="['/knowledge', a.articleId]">{{ a.title }}</a>
                      @if (canLinkArticles) {
                        <button type="button" class="pick-remove" (click)="unlinkArticle(a.articleId)"
                                [attr.aria-label]="'Desvincular ' + a.title">Remover</button>
                      }
                    </li>
                  }
                </ul>
              }
            </section>
          }
          <section class="panel">
            <h2>Detalhes</h2>
            <dl>
              <dt>Tipo</dt><dd>{{ t.ticketType }}</dd>
              <dt>Urgência</dt><dd>{{ t.urgency ? priorityLabel(t.urgency) : '—' }}</dd>
              <dt>Impacto</dt><dd>{{ t.impact ? priorityLabel(t.impact) : '—' }}</dd>
              <dt>Solicitante</dt><dd>{{ context()?.reporterName ?? t.reporterId }}</dd>
              <dt>Responsável</dt><dd>{{ context()?.assigneeName ?? (t.assigneeId || '—') }}</dd>
              <dt>Grupo</dt><dd class="mono">{{ t.groupId || '—' }}</dd>
              <dt>Criado em</dt><dd class="mono">{{ t.createdAt | date:'dd/MM/yyyy HH:mm' }}</dd>
              <dt>Atualizado em</dt><dd class="mono">{{ t.updatedAt | date:'dd/MM/yyyy HH:mm' }}</dd>
              @if (t.resolvedAt) { <dt>Resolvido em</dt><dd class="mono">{{ t.resolvedAt | date:'dd/MM/yyyy HH:mm' }}</dd> }
              @if (cycles(); as c) {
                @if (c.summary.cycleCount > 1) {
                  <dt>Ciclos</dt><dd class="mono">{{ c.summary.cycleCount }} · {{ c.summary.reopenCount }} reabertura(s)</dd>
                }
              }
            </dl>
          </section>
        </aside>
      </div>
    } @else if (loading()) {
      <p class="note" role="status">Carregando ticket…</p>
    } @else if (error()) {
      <p class="form-error" role="alert"><mat-icon aria-hidden="true">error_outline</mat-icon>{{ error() }}</p>
    }
  `,
  styles: [`
    :host { display: grid; gap: var(--sp-6); }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }

    .back { display: inline-flex; align-items: center; gap: var(--sp-2); color: var(--text-muted); font-size: var(--fs-sm); text-decoration: none; }
    .back:hover { color: var(--text); }
    .back mat-icon { width: 16px; height: 16px; font-size: 16px; }
    .head { display: grid; gap: var(--sp-4); }
    .title-row { display: flex; flex-wrap: wrap; align-items: center; gap: var(--sp-5); }
    .num { font-family: var(--mono); font-size: var(--fs-sm); font-weight: var(--fw-medium); color: var(--text-muted); }
    h1 { margin: 0; font-size: var(--fs-xl); line-height: 28px; font-weight: var(--fw-semibold); }
    h2 { margin: 0 0 var(--sp-5); font-size: var(--fs-xs); letter-spacing: .04em; text-transform: uppercase; color: var(--text-muted); font-weight: var(--fw-semibold); }

    .verbs { display: grid; gap: var(--sp-4); }
    .row { display: flex; flex-wrap: wrap; align-items: center; gap: var(--sp-4); }
    .assign { display: flex; gap: var(--sp-3); margin-inline-start: auto; }
    .assign input, .assign select { width: 220px; }
    select { height: var(--control-h-md); padding: 0 var(--sp-4); border: 1px solid var(--border-strong); border-radius: var(--radius-s); background: var(--surface); color: var(--text); font: var(--fw-regular) var(--fs-base) var(--sans); }
    .queue-move { margin-inline-start: 0; }
    h2.sub { margin-top: var(--sp-6); }
    .asset-list { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--sp-3); }
    .asset-list a { color: var(--text); text-decoration: none; overflow-wrap: anywhere; }
    .asset-list a:hover { text-decoration: underline; }
    .asset-list .mono { color: var(--text-muted); margin-inline-end: var(--sp-3); }
    .panel input[type='search'] { width: 100%; box-sizing: border-box; margin-bottom: var(--sp-3); }
    .asset-list.picks { margin: var(--sp-3) 0 var(--sp-5); border: 1px solid var(--border); border-radius: var(--radius-s); gap: 0; }
    .pick { width: 100%; text-align: left; padding: var(--sp-3) var(--sp-4); border: 0; background: transparent; color: var(--text); cursor: pointer; }
    .pick:hover, .pick:focus-visible { background: var(--surface-2); }
    .art-row { display: flex; align-items: baseline; justify-content: space-between; gap: var(--sp-4); }
    .pick-remove { border: 0; background: transparent; color: var(--text-muted); cursor: pointer; font-size: var(--fs-xs); }
    .pick-remove:hover { color: var(--critical); }
    input, textarea {
      font: var(--fw-regular) var(--fs-base) var(--sans);
      color: var(--text);
      background: var(--surface);
      border: 1px solid var(--border-strong);
      border-radius: var(--radius-s);
      padding: 0 var(--sp-4);
      height: var(--control-h-md);
    }
    textarea { height: auto; min-height: 88px; padding: var(--sp-4); resize: vertical; width: 100%; box-sizing: border-box; }
    input:focus-visible, textarea:focus-visible { outline: var(--focus-ring); outline-offset: 1px; }
    .resolve { display: grid; gap: var(--sp-4); max-width: 640px; }
    .resolve label { font-size: var(--fs-sm); font-weight: var(--fw-semibold); }
    .req { color: var(--text-muted); font-weight: var(--fw-regular); }
    .nx-verb nx-action-icon { display: inline-flex; }

    .note { margin: 0; font-size: var(--fs-sm); color: var(--text-muted); }
    .form-error { display: flex; align-items: center; gap: var(--sp-4); margin: 0; padding: var(--sp-4) var(--sp-5); border: 1px solid var(--critical); border-radius: var(--radius-s); background: var(--critical-soft); color: var(--critical); font-size: var(--fs-sm); }
    .form-error mat-icon { width: 16px; height: 16px; font-size: 16px; }

    .grid { display: grid; grid-template-columns: minmax(0, 2fr) minmax(240px, 1fr); gap: var(--sp-7); align-items: start; }
    @media (max-width: 900px) { .grid { grid-template-columns: 1fr; } }
    .main, .side { display: grid; gap: var(--sp-6); }
    .panel { border: 1px solid var(--border); border-radius: var(--radius-m); background: var(--surface); padding: var(--sp-6); }
    .desc { margin: 0; font-size: var(--fs-base); line-height: 1.6; white-space: pre-wrap; }

    dl { display: grid; grid-template-columns: auto 1fr; gap: var(--sp-3) var(--sp-6); margin: 0; font-size: var(--fs-sm); }
    dt { color: var(--text-muted); }
    dd { margin: 0; text-align: end; overflow-wrap: anywhere; }
    .mono { font-family: var(--mono); }

    .tag { display: inline-block; padding: 0 var(--sp-3); border: 1px solid var(--border-strong); border-radius: var(--radius-xs); font-size: var(--fs-xs); color: var(--text-muted); margin-inline-start: var(--sp-3); }
    .tag.ok { color: var(--success); border-color: var(--success); }
    .tag.crit { color: var(--critical); border-color: var(--critical); }

    .sol { padding-block: var(--sp-4); border-top: 1px solid var(--border); }
    .sol:first-of-type { border-top: 0; padding-top: 0; }
    .sol header { display: flex; align-items: center; gap: var(--sp-3); font-size: var(--fs-sm); }
    .sol p { margin: var(--sp-3) 0; white-space: pre-wrap; font-size: var(--fs-base); }
    .sol .decision { color: var(--text-muted); font-size: var(--fs-sm); }
    .sol footer { font-size: var(--fs-xs); color: var(--text-faint); }

    .ev { list-style: none; margin: 0 0 var(--sp-5); padding: 0; display: grid; }
    .ev li { display: flex; align-items: center; gap: var(--sp-4); padding: var(--sp-3) 0; border-top: 1px solid var(--border); font-size: var(--fs-sm); }
    .ev li:first-child { border-top: 0; }
    .ev li > mat-icon { width: 18px; height: 18px; font-size: 18px; color: var(--text-muted); }
    .ev-main { flex: 1; min-width: 0; display: grid; }
    .ev-meta { color: var(--text-faint); font-size: var(--fs-xs); }
    .ev-add { display: flex; align-items: center; gap: var(--sp-6); flex-wrap: wrap; margin-top: var(--sp-4); }
    .ev-pick { display: inline-flex; align-items: center; gap: var(--sp-3); cursor: pointer; font-size: var(--fs-sm); font-weight: var(--fw-medium); color: var(--signal); }
    .ev-pick mat-icon { width: 16px; height: 16px; font-size: 16px; }
    .ev-pick:focus-within { outline: 2px solid var(--signal); outline-offset: 2px; }
    .ev-internal { display: inline-flex; align-items: center; gap: var(--sp-3); font-size: var(--fs-sm); color: var(--text-muted); }
    .linklike { background: none; border: 0; padding: 0; font: inherit; color: var(--signal); cursor: pointer; text-align: start; overflow-wrap: anywhere; }
    .linklike:hover { text-decoration: underline; }
    .ev-chip { display: inline-flex; align-items: center; gap: var(--sp-2); margin-inline-end: var(--sp-4); font-size: var(--fs-xs); }
    .ev-chip mat-icon { width: 14px; height: 14px; font-size: 14px; }
    .ev-step { display: block; margin-top: var(--sp-2); font-size: var(--fs-xs); color: var(--text-muted); }
    .icon-btn { background: none; border: 0; padding: var(--sp-2); cursor: pointer; color: var(--text-muted); border-radius: var(--radius-xs); display: inline-flex; }
    .icon-btn:hover { color: var(--critical); }
    .icon-btn mat-icon { width: 16px; height: 16px; font-size: 16px; }

    .tl { list-style: none; margin: 0; padding: 0; display: grid; }
    .tl li { display: grid; grid-template-columns: 92px 1fr; gap: var(--sp-5); padding: var(--sp-4) 0; border-top: 1px solid var(--border); font-size: var(--fs-sm); }
    .tl li:first-child { border-top: 0; }
    .tl li[data-internal] { background: var(--surface-2); margin-inline: calc(var(--sp-4) * -1); padding-inline: var(--sp-4); }
    .when { color: var(--text-muted); font-size: var(--fs-xs); padding-top: 2px; }
    .what { font-weight: var(--fw-medium); }
    .evt-text { margin: var(--sp-2) 0; white-space: pre-wrap; color: var(--text); }
    .who { display: block; color: var(--text-faint); font-size: var(--fs-xs); }
  `]
})
export class TicketDetailComponent implements OnInit {
  ticket = signal<TicketDto | null>(null);
  events = signal<TicketEventDto[]>([]);
  evidence = signal<EvidenceDto[]>([]);
  evidenceError = signal<string | null>(null);
  evidenceInternal = false;
  readonly evidenceAccept = EVIDENCE_ACCEPT;
  private stepEventId: string | null = null;
  cycles = signal<TicketCyclesDto | null>(null);
  loading = signal(true);
  error = signal<string | null>(null);
  acting = signal(false);
  actionError = signal<string | null>(null);
  assigneeInput = '';
  queueTarget = '';
  commentText = '';
  commentInternal = false;
  context = signal<ConsoleContext | null>(null);
  queues = signal<QueueDto[]>([]);
  members = signal<QueueMemberDto[]>([]);
  solutionText = '';
  resolving = signal(false);

  availableTransitions = computed<TicketStatus[]>(() => {
    const t = this.ticket();
    return t ? VALID_TRANSITIONS[t.status] : [];
  });

  verbs = computed(() => {
    const t = this.ticket();
    return t ? VALID_TRANSITIONS[t.status].map(target => ({ target, ...verbFor(t.status, target) })) : [];
  });

  sla = computed(() => {
    const t = this.ticket();
    return t ? slaFromTicket(t) : null;
  });

  resolutions = computed(() => (this.cycles()?.cycles ?? []).filter(c => c.resolution));

  chain = computed<ChainNode[]>(() => {
    const t = this.ticket();
    if (!t) return [];
    const nodes: ChainNode[] = [{ kind: 'Ticket', value: t.ticketNumber, tone: 'focus' }];
    nodes.push({ kind: 'Solicitante', value: this.context()?.reporterName ?? t.reporterId });
    const linked = this.assets();
    if (linked.length > 0) nodes.push({ kind: 'Ativo', value: linked.length === 1 ? linked[0].assetTag : `${linked[0].assetTag} +${linked.length - 1}` });
    else if (t.ciReference) nodes.push({ kind: 'Ativo', value: t.ciReference });
    const s = this.sla();
    if (s) nodes.push({ kind: 'SLA', value: s.label, tone: s.state === 'crit' ? 'crit' : undefined });
    return nodes;
  });

  tone = ticketStatusTone;
  glyph = ticketStatusGlyph;

  assets = signal<LinkedAssetDto[]>([]);
  articles = signal<LinkedArticleDto[]>([]);
  articleError = signal<string | null>(null);
  articleQuery = signal('');
  articleResults = signal<ArticleDto[]>([]);
  readonly canReadAssets: boolean;
  readonly canReadKnowledge: boolean;
  readonly canLinkArticles: boolean;
  private articleTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private route: ActivatedRoute,
    private ticketService: TicketService,
    protected evidenceService: EvidenceService,
    private catalog: CatalogService,
    private assetService: AssetService,
    private knowledge: KnowledgeService,
    auth: AuthService
  ) {
    this.canReadAssets = auth.can('ASSET', 'READ');
    this.canReadKnowledge = auth.can('KNOWLEDGE', 'READ');
    this.canLinkArticles = auth.can('KNOWLEDGE', 'UPDATE');
  }

  private loadArticles(id: string): void {
    this.knowledge.articlesOfTicket(id).subscribe({
      next: a => this.articles.set(a),
      error: () => this.articles.set([])
    });
  }

  searchArticles(query: string): void {
    this.articleQuery.set(query);
    if (this.articleTimer) clearTimeout(this.articleTimer);
    const q = query.trim();
    if (q.length < 2) {
      this.articleResults.set([]);
      return;
    }
    this.articleTimer = setTimeout(() => {
      this.knowledge.listArticles({ status: 'PUBLISHED', search: q, size: 6 }).subscribe({
        next: res => {
          const linked = new Set(this.articles().map(a => a.articleId));
          this.articleResults.set(res.content.filter(a => !linked.has(a.id)));
        },
        error: () => this.articleResults.set([])
      });
    }, 300);
  }

  linkArticle(articleId: string): void {
    const t = this.ticket();
    if (!t) return;
    this.articleError.set(null);
    this.knowledge.linkTicket(articleId, t.id).subscribe({
      next: () => {
        this.articleQuery.set('');
        this.articleResults.set([]);
        this.loadArticles(t.id);
      },
      error: () => this.articleError.set('Não foi possível vincular o artigo.')
    });
  }

  unlinkArticle(articleId: string): void {
    const t = this.ticket();
    if (!t) return;
    this.articleError.set(null);
    this.knowledge.unlinkTicket(articleId, t.id).subscribe({
      next: () => this.loadArticles(t.id),
      error: () => this.articleError.set('Não foi possível desvincular o artigo.')
    });
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.error.set('Ticket inválido.');
      this.loading.set(false);
      return;
    }
    this.load(id);
  }

  private load(id: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.ticketService.get(id).subscribe({
      next: t => {
        this.ticket.set(t);
        this.loading.set(false);
        this.loadHistory(id);
      },
      error: () => {
        this.error.set('Ticket não encontrado.');
        this.loading.set(false);
      }
    });
  }

  /** Linha do tempo e ciclos são complementares: se falharem, o chamado continua operável. */
  private loadHistory(id: string): void {
    this.ticketService.context(id).subscribe({
      next: c => {
        this.context.set(c);
        if (c.queueId) this.catalog.queueMembers(c.queueId).subscribe({ next: m => this.members.set(m.filter(x => x.userActive && x.email)), error: () => this.members.set([]) });
        else this.members.set([]);
      },
      error: () => this.context.set(null),
    });
    this.catalog.listQueues(false).subscribe({ next: q => this.queues.set(q), error: () => this.queues.set([]) });
    this.ticketService.timeline(id).subscribe({ next: e => this.events.set(e), error: () => this.events.set([]) });
    this.ticketService.cycles(id).subscribe({ next: c => this.cycles.set(c), error: () => this.cycles.set(null) });
    this.evidenceService.list(id).subscribe({ next: a => this.evidence.set(a), error: () => this.evidence.set([]) });
    if (this.canReadAssets) {
      this.assetService.assetsOfTicket(id).subscribe({ next: a => this.assets.set(a), error: () => this.assets.set([]) });
    }
    if (this.canReadKnowledge) this.loadArticles(id);
  }

  evidenceOf(e: TicketEventDto): EvidenceDto[] {
    return this.evidence().filter(a => a.subjectType === 'EVENT' && a.subjectId === e.id);
  }

  evidenceSubject(a: EvidenceDto): string {
    if (a.subjectType === 'COMMENT') return 'Comentário';
    if (a.subjectType === 'EVENT') {
      const ev = this.events().find(x => x.id === a.subjectId);
      return 'Etapa: ' + (ev ? this.eventLabel(ev.type) : 'evento');
    }
    return 'Chamado';
  }

  downloadEvidence(a: EvidenceDto): void {
    this.evidenceService.download(a);
  }

  pickEvidence(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (file) this.sendEvidence(file, 'TICKET', undefined, this.evidenceInternal);
  }

  attachToEvent(e: TicketEventDto, input: HTMLInputElement): void {
    this.stepEventId = e.id;
    input.click();
  }

  pickStepEvidence(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    const eventId = this.stepEventId;
    this.stepEventId = null;
    if (file && eventId) this.sendEvidence(file, 'EVENT', eventId, this.evidenceInternal);
  }

  removeEvidence(a: EvidenceDto): void {
    const id = this.ticket()?.id;
    this.evidenceError.set(null);
    this.evidenceService.remove(a.id).subscribe({
      next: () => { if (id) this.loadHistory(id); },
      error: (err: HttpErrorResponse) => this.evidenceError.set(this.evidenceMessage(err, 'Não foi possível remover a evidência.')),
    });
  }

  private sendEvidence(file: File, type: EvidenceSubject, subjectId: string | undefined, internal: boolean): void {
    const id = this.ticket()?.id;
    if (!id) return;
    const problem = this.evidenceService.clientError(file);
    if (problem) {
      this.evidenceError.set(problem);
      return;
    }
    this.evidenceError.set(null);
    this.evidenceService.attach(id, file, { subjectType: type, subjectId, internal }).subscribe({
      next: () => this.loadHistory(id),
      error: (err: HttpErrorResponse) => this.evidenceError.set(this.evidenceMessage(err, 'Não foi possível anexar a evidência.')),
    });
  }

  private evidenceMessage(err: HttpErrorResponse, fallback: string): string {
    const fields = err.error?.extensions as Record<string, string> | undefined;
    const first = fields ? Object.values(fields)[0] : undefined;
    return first ?? (typeof err.error?.detail === 'string' && err.status === 422 ? err.error.detail : fallback);
  }

  transition(target: TicketStatus): void {
    // Resolver abre uma etapa: a solução é obrigatória e fica registrada no histórico do chamado.
    if (target === 'RESOLVED') {
      this.actionError.set(null);
      this.resolving.set(true);
      return;
    }
    this.sendTransition({ targetStatus: target });
  }

  confirmResolve(): void {
    const solution = this.solutionText.trim();
    if (!solution) return;
    this.sendTransition({ targetStatus: 'RESOLVED', resolution: solution }, () => {
      this.resolving.set(false);
      this.solutionText = '';
    });
  }

  cancelResolve(): void {
    this.resolving.set(false);
    this.solutionText = '';
    this.actionError.set(null);
  }

  private sendTransition(request: TransitionRequest, onDone?: () => void): void {
    const t = this.ticket();
    if (!t) return;

    this.acting.set(true);
    this.actionError.set(null);
    this.ticketService.transition(t.id, request).subscribe({
      next: updated => {
        this.ticket.set(updated);
        this.acting.set(false);
        onDone?.();
        this.loadHistory(updated.id);
      },
      error: (err: HttpErrorResponse) => {
        this.acting.set(false);
        this.actionError.set(err.error?.detail ?? 'Não foi possível alterar o status.');
      }
    });
  }

  assign(): void {
    const t = this.ticket();
    if (!t || !this.assigneeInput.trim()) return;

    this.acting.set(true);
    this.actionError.set(null);
    this.ticketService.assign(t.id, { assigneeId: this.assigneeInput.trim() }).subscribe({
      next: updated => {
        this.ticket.set(updated);
        this.assigneeInput = '';
        this.acting.set(false);
        this.loadHistory(updated.id);
      },
      error: (err: HttpErrorResponse) => {
        this.acting.set(false);
        this.actionError.set(err.error?.detail ?? 'Não foi possível atribuir o ticket.');
      }
    });
  }

  moveQueue(): void {
    const t = this.ticket();
    if (!t || !this.queueTarget) return;
    this.acting.set(true);
    this.actionError.set(null);
    this.ticketService.changeQueue(t.id, this.queueTarget).subscribe({
      next: updated => {
        this.ticket.set(updated);
        this.queueTarget = '';
        this.acting.set(false);
        this.loadHistory(updated.id);
      },
      error: (err: HttpErrorResponse) => {
        this.acting.set(false);
        this.actionError.set(err.error?.detail ?? 'Não foi possível mover o chamado de fila.');
      }
    });
  }

  sendComment(): void {
    const t = this.ticket();
    const text = this.commentText.trim();
    if (!t || !text) return;
    this.acting.set(true);
    this.actionError.set(null);
    this.ticketService.addComment(t.id, text, !this.commentInternal).subscribe({
      next: () => {
        this.commentText = '';
        this.acting.set(false);
        this.loadHistory(t.id);
      },
      error: (err: HttpErrorResponse) => {
        this.acting.set(false);
        this.actionError.set(err.error?.detail ?? 'Não foi possível registrar o comentário.');
      }
    });
  }

  statusLabel(status: TicketStatus): string {
    return STATUS_LABELS[status];
  }

  priorityLabel(priority: string): string {
    return PRIORITY_LABELS[priority] ?? priority;
  }

  level(priority: string): PriorityLevel {
    return priority.toLowerCase() as PriorityLevel;
  }

  eventLabel(type: string): string {
    return EVENT_LABELS[type] ?? type;
  }

  /** Texto livre do evento (comentário, solução ou motivo), quando o payload o traz. */
  eventText(e: TicketEventDto): string | null {
    const p = e.payload;
    if (!p) return null;
    for (const key of ['comment', 'content', 'note', 'solution', 'reason', 'text', 'fileName']) {
      const v = p[key];
      if (typeof v === 'string' && v.trim()) return v;
    }
    return null;
  }
}
