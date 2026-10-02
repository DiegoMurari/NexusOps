package com.nexusops.ticketing.service;

import com.nexusops.sla.dto.SlaTargetsDto;
import com.nexusops.sla.service.SlaCalculationService;
import com.nexusops.ticketing.domain.CatalogTopic;
import com.nexusops.ticketing.domain.Category;
import com.nexusops.ticketing.domain.Change;
import com.nexusops.ticketing.domain.Problem;
import com.nexusops.ticketing.domain.Ticket;
import com.nexusops.ticketing.repository.CatalogTopicRepository;
import com.nexusops.ticketing.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Aplica o SLA a um chamado: escolhe a definição, grava os prazos no chamado e no ciclo corrente, pausa e
 * retoma o relógio. O módulo sla só calcula; quem decide quando aplicar e onde guardar é o ticketing.
 *
 * <p>Regras (ADR-013): o SLA é por ciclo e nunca sobrescrito (reabrir recomeça com prazos novos, o ciclo
 * anterior mantém os dele); o relógio pausa em espera e aguardando o solicitante quando a definição pede.
 */
@Service
@RequiredArgsConstructor
public class TicketSlaService {

    private final SlaCalculationService slaCalculationService;
    private final CategoryRepository categoryRepository;
    private final CatalogTopicRepository topicRepository;
    private final TicketCycleService cycleService;

    /** Chamado novo: meta a partir da abertura. Deve rodar antes de abrir o primeiro ciclo. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void applyOnCreate(Ticket ticket) {
        assign(ticket, ticket.getCreatedAt() != null ? ticket.getCreatedAt() : Instant.now());
    }

    /**
     * Novo ciclo (contestação): metas novas a partir de agora; o relógio volta a correr e a resposta é
     * medida de novo. Os prazos do ciclo anterior ficam como estavam.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void restartForNewCycle(Ticket ticket, Instant now) {
        ticket.setSlaPausedAt(null);
        ticket.setFirstResponseAt(null);
        assign(ticket, now);
        cycleService.syncSla(ticket, 0);
    }

    /**
     * Chamado em atendimento que ainda não tem SLA (anterior à regra ou sem definição na época): aplica a
     * partir do início do ciclo corrente. Devolve verdadeiro se passou a ter meta.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean backfill(Ticket ticket) {
        Instant start = cycleService.findCurrent(ticket).map(c -> c.getOpenedAt()).orElse(ticket.getCreatedAt());
        assign(ticket, start != null ? start : Instant.now());
        if (ticket.getSlaDefinitionId() == null) {
            return false;
        }
        cycleService.syncSla(ticket, 0);
        return true;
    }

    /** O chamado entrou em espera ou aguarda o solicitante: para o relógio, se a definição pede. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void pause(Ticket ticket, Instant now) {
        if (ticket.getSlaDefinitionId() == null || ticket.getSlaPausedAt() != null) {
            return;
        }
        if (slaCalculationService.pausesOnHold(ticket.getSlaDefinitionId())) {
            ticket.setSlaPausedAt(now);
        }
    }

    /** O atendimento voltou: estende os prazos pelo tempo pausado e soma esse tempo no ciclo. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void resume(Ticket ticket, Instant now) {
        Instant pausedAt = ticket.getSlaPausedAt();
        if (pausedAt == null) {
            return;
        }
        ticket.setSlaPausedAt(null);
        if (ticket.getSlaDefinitionId() == null) {
            return;
        }
        String definitionId = ticket.getSlaDefinitionId();
        String tenantId = ticket.getTenantId();
        if (ticket.getFirstResponseAt() == null) {
            ticket.setResponseDueAt(slaCalculationService.extendDue(
                definitionId, tenantId, ticket.getResponseDueAt(), pausedAt, now));
        }
        ticket.setResolutionDueAt(slaCalculationService.extendDue(
            definitionId, tenantId, ticket.getResolutionDueAt(), pausedAt, now));
        cycleService.syncSla(ticket, Duration.between(pausedAt, now).getSeconds());
    }

    private void assign(Ticket ticket, Instant start) {
        // O SLA do tópico (quando há) vale mais que o da categoria; sem nenhum, vence a definição mais específica.
        String explicitDefinitionId = ticket.getTopicId() == null ? null
            : topicRepository.findById(ticket.getTopicId()).map(CatalogTopic::getSlaDefinitionId).orElse(null);
        if (explicitDefinitionId == null && ticket.getCategoryId() != null) {
            explicitDefinitionId = categoryRepository.findById(ticket.getCategoryId())
                .map(Category::getSlaDefinitionId).orElse(null);
        }

        Optional<SlaTargetsDto> targets = slaCalculationService.resolveTargets(
            ticket.getTenantId(), typeOf(ticket), ticket.getCategoryId(), ticket.getPriority().name(),
            null, explicitDefinitionId, start);

        if (targets.isEmpty()) {
            ticket.setSlaDefinitionId(null);
            ticket.setResponseDueAt(null);
            ticket.setResolutionDueAt(null);
            return;
        }
        SlaTargetsDto t = targets.get();
        ticket.setSlaDefinitionId(t.definitionId());
        ticket.setResponseDueAt(t.responseDueAt());
        ticket.setResolutionDueAt(t.resolutionDueAt());
    }

    /** O tipo é a classe concreta (herança JOINED), como em TicketMapper. */
    private static String typeOf(Ticket ticket) {
        if (ticket instanceof Problem) {
            return "PROBLEM";
        }
        if (ticket instanceof Change) {
            return "CHANGE";
        }
        return "INCIDENT";
    }
}
