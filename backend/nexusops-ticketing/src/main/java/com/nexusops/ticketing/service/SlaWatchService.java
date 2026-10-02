package com.nexusops.ticketing.service;

import com.nexusops.sla.domain.SlaBreach;
import com.nexusops.sla.service.SlaBreachService;
import com.nexusops.ticketing.domain.Ticket;
import com.nexusops.ticketing.domain.TicketCycle;
import com.nexusops.ticketing.domain.TicketEvent;
import com.nexusops.ticketing.domain.TicketEventType;
import com.nexusops.ticketing.repository.TicketEventRepository;
import com.nexusops.ticketing.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Vigia de SLA. A cada minuto avalia os chamados em atendimento cujo relógio está correndo: aplica o SLA
 * aos que ainda não têm, avisa ao passar de 80% da meta e registra a violação quando o prazo estoura
 * (uma vez por chamado, tipo e prazo). Cada chamado é avaliado na sua própria transação, para que um
 * defeito num deles não impeça os outros.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SlaWatchService {

    private static final List<Ticket.TicketStatus> WATCHED = List.of(
        Ticket.TicketStatus.OPEN, Ticket.TicketStatus.IN_PROGRESS, Ticket.TicketStatus.REOPENED,
        Ticket.TicketStatus.WAITING, Ticket.TicketStatus.ON_HOLD);

    private static final double WARNING_FRACTION = 0.8;
    private static final String SYSTEM = "system";

    private final TicketRepository ticketRepository;
    private final TicketEventRepository eventRepository;
    private final TicketTimelineService timelineService;
    private final TicketSlaService ticketSlaService;
    private final TicketCycleService cycleService;
    private final SlaBreachService slaBreachService;
    private final PlatformTransactionManager transactionManager;

    @Scheduled(initialDelay = 30_000, fixedDelay = 60_000)
    public void tick() {
        try {
            evaluateAll(Instant.now());
        } catch (Exception e) {
            log.error("SLA watch failed", e);
        }
    }

    /** Avalia todos os chamados vigiados. Devolve quantos tiveram alguma mudança. */
    public int evaluateAll(Instant now) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        int changed = 0;
        for (String ticketId : ticketRepository.findIdsForSlaWatch(WATCHED)) {
            try {
                if (Boolean.TRUE.equals(tx.execute(status -> evaluate(ticketId, now)))) {
                    changed++;
                }
            } catch (Exception e) {
                log.warn("SLA watch skipped ticket {}: {}", ticketId, e.toString());
            }
        }
        return changed;
    }

    /** Deve rodar dentro de uma transação. Devolve verdadeiro se alterou algo. */
    boolean evaluate(String ticketId, Instant now) {
        Ticket ticket = ticketRepository.findById(ticketId).orElse(null);
        if (ticket == null || !WATCHED.contains(ticket.getStatus()) || ticket.getSlaPausedAt() != null) {
            return false;
        }

        boolean changed = false;
        if (ticket.getSlaDefinitionId() == null && ticket.getResponseDueAt() == null && ticket.getResolutionDueAt() == null) {
            if (!ticketSlaService.backfill(ticket)) {
                return false;
            }
            changed = true;
        }
        // Prazos sem definição (dados antigos) não têm a quem atribuir uma violação.
        if (ticket.getSlaDefinitionId() == null) {
            return changed;
        }

        if (ticket.getFirstResponseAt() == null && ticket.getResponseDueAt() != null
            && now.isAfter(ticket.getResponseDueAt())) {
            changed |= recordBreach(ticket, SlaBreach.BreachType.RESPONSE, ticket.getResponseDueAt());
        }
        if (ticket.getResolutionDueAt() != null && now.isAfter(ticket.getResolutionDueAt())) {
            changed |= recordBreach(ticket, SlaBreach.BreachType.RESOLUTION, ticket.getResolutionDueAt());
        } else {
            changed |= warnIfClose(ticket, now);
        }
        return changed;
    }

    private boolean recordBreach(Ticket ticket, SlaBreach.BreachType type, Instant dueAt) {
        boolean created = slaBreachService.recordBreach(
            ticket.getId(), ticket.getSlaDefinitionId(), ticket.getTenantId(), type, dueAt, 100);
        if (created) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", type.name());
            payload.put("dueAt", dueAt.toString());
            timelineService.append(ticket, TicketEventType.SLA_BREACHED, SYSTEM, TicketEvent.ActorKind.SYSTEM, payload);
        }
        return created;
    }

    /** Aviso único por ciclo ao consumir 80% do tempo de resolução (sem contar o tempo pausado). */
    private boolean warnIfClose(Ticket ticket, Instant now) {
        Instant due = ticket.getResolutionDueAt();
        if (due == null || eventRepository.existsByTicketIdAndCycleNoAndEventType(
            ticket.getId(), ticket.getCycleNo(), TicketEventType.SLA_WARNED)) {
            return false;
        }
        Optional<TicketCycle> cycle = cycleService.findCurrent(ticket);
        Instant start = cycle.map(TicketCycle::getOpenedAt).orElse(ticket.getCreatedAt());
        long paused = cycle.map(TicketCycle::getPausedSeconds).orElse(0L);
        if (start == null) {
            return false;
        }
        long total = Duration.between(start, due).getSeconds() - paused;
        long elapsed = Duration.between(start, now).getSeconds() - paused;
        if (total <= 0 || elapsed < total * WARNING_FRACTION) {
            return false;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("percentage", Math.min(99, (int) (elapsed * 100 / total)));
        payload.put("dueAt", due.toString());
        timelineService.append(ticket, TicketEventType.SLA_WARNED, SYSTEM, TicketEvent.ActorKind.SYSTEM, payload);
        return true;
    }
}
