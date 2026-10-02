package com.nexusops.ticketing.service;

import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.ticketing.domain.Ticket;
import com.nexusops.ticketing.domain.TicketCycle;
import com.nexusops.ticketing.domain.TicketResolution;
import com.nexusops.ticketing.dto.TicketCyclesDto;
import com.nexusops.ticketing.repository.TicketCycleRepository;
import com.nexusops.ticketing.repository.TicketRepository;
import com.nexusops.ticketing.repository.TicketResolutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ciclos de atendimento e resoluções (ADR-013). O SLA é medido por ciclo: reabrir inicia um novo
 * ciclo e o anterior permanece intacto, com a sua solução e o seu desfecho.
 */
@Service
@RequiredArgsConstructor
public class TicketCycleService {

    private final TicketCycleRepository cycleRepository;
    private final TicketResolutionRepository resolutionRepository;
    private final TicketRepository ticketRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public TicketCycle openFirstCycle(Ticket ticket) {
        return cycleRepository.save(TicketCycle.builder()
            .ticketId(ticket.getId())
            .tenantId(ticket.getTenantId())
            .cycleNo(1)
            .openedReason(TicketCycle.OpenedReason.CREATED)
            .slaDefinitionId(ticket.getSlaDefinitionId())
            .responseDueAt(ticket.getResponseDueAt())
            .resolutionDueAt(ticket.getResolutionDueAt())
            .openedAt(ticket.getCreatedAt())
            .build());
    }

    /**
     * Copia para o ciclo corrente a definição e os prazos de SLA vigentes no chamado e soma o tempo que o
     * relógio ficou pausado. O prazo é do ciclo: reabrir cria outro ciclo com prazos próprios.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void syncSla(Ticket ticket, long addedPausedSeconds) {
        TicketCycle cycle = currentCycle(ticket);
        cycle.setSlaDefinitionId(ticket.getSlaDefinitionId());
        cycle.setResponseDueAt(ticket.getResponseDueAt());
        cycle.setResolutionDueAt(ticket.getResolutionDueAt());
        cycle.setPausedSeconds(cycle.getPausedSeconds() + Math.max(0, addedPausedSeconds));
        cycleRepository.save(cycle);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<TicketCycle> findCurrent(Ticket ticket) {
        return cycleRepository.findByTicketIdAndCycleNo(ticket.getId(), ticket.getCycleNo());
    }

    /** Registra a primeira resposta do ciclo corrente (uma vez por ciclo). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordFirstResponse(Ticket ticket, Instant now) {
        TicketCycle cycle = currentCycle(ticket);
        if (cycle.getFirstResponseAt() == null) {
            cycle.setFirstResponseAt(now);
            cycleRepository.save(cycle);
        }
    }

    /** O técnico apresentou a solução: cria a resolução imutável do ciclo e congela o SLA dele. */
    @Transactional(propagation = Propagation.MANDATORY)
    public TicketResolution recordResolved(Ticket ticket, Instant now, String solutionText, String resolvedBy) {
        TicketCycle cycle = currentCycle(ticket);
        cycle.setStatus(TicketCycle.Status.AWAITING_VALIDATION);
        cycle.setResolvedAt(now);
        cycle.setResponseDueAt(ticket.getResponseDueAt());
        cycle.setResolutionDueAt(ticket.getResolutionDueAt());
        cycleRepository.save(cycle);

        return resolutionRepository.save(TicketResolution.builder()
            .ticketId(ticket.getId())
            .tenantId(ticket.getTenantId())
            .cycleNo(cycle.getCycleNo())
            .solutionText(solutionText)
            .resolvedBy(resolvedBy)
            .resolvedAt(now)
            .build());
    }

    /** O solicitante aceitou a solução: o ciclo e a resolução ficam decididos. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAccepted(Ticket ticket, Instant now, String acceptedBy, String comment) {
        TicketCycle cycle = currentCycle(ticket);
        cycle.setStatus(TicketCycle.Status.ACCEPTED);
        cycle.setValidatedAt(now);
        cycleRepository.save(cycle);
        decideResolution(ticket, cycle.getCycleNo(), TicketResolution.Outcome.ACCEPTED, acceptedBy, now, comment);
    }

    /**
     * O solicitante contestou: fecha o ciclo como contestado, preserva a solução anterior e abre o
     * próximo ciclo. Devolve o novo ciclo.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public TicketCycle recordContestedAndReopen(Ticket ticket, Instant now, String contestedBy, String comment) {
        TicketCycle cycle = currentCycle(ticket);
        cycle.setStatus(TicketCycle.Status.CONTESTED);
        cycle.setValidatedAt(now);
        cycleRepository.save(cycle);
        decideResolution(ticket, cycle.getCycleNo(), TicketResolution.Outcome.CONTESTED, contestedBy, now, comment);

        return cycleRepository.save(TicketCycle.builder()
            .ticketId(ticket.getId())
            .tenantId(ticket.getTenantId())
            .cycleNo(cycle.getCycleNo() + 1)
            .openedReason(TicketCycle.OpenedReason.REOPENED)
            .reopenComment(comment)
            .slaDefinitionId(ticket.getSlaDefinitionId())
            .openedAt(now)
            .build());
    }

    private void decideResolution(Ticket ticket, int cycleNo, TicketResolution.Outcome outcome,
                                  String decidedBy, Instant now, String comment) {
        // Chamados anteriores à migração podem não ter registro de resolução.
        resolutionRepository.findByTicketIdAndCycleNo(ticket.getId(), cycleNo).ifPresent(resolution -> {
            resolution.setOutcome(outcome);
            resolution.setDecidedBy(decidedBy);
            resolution.setDecidedAt(now);
            resolution.setDecisionComment(comment);
            resolutionRepository.save(resolution);
        });
    }

    private TicketCycle currentCycle(Ticket ticket) {
        return cycleRepository.findByTicketIdAndCycleNo(ticket.getId(), ticket.getCycleNo())
            .orElseGet(() -> cycleRepository.save(TicketCycle.builder()
                .ticketId(ticket.getId())
                .tenantId(ticket.getTenantId())
                .cycleNo(ticket.getCycleNo())
                .openedReason(ticket.getCycleNo() == 1
                    ? TicketCycle.OpenedReason.CREATED : TicketCycle.OpenedReason.REOPENED)
                .slaDefinitionId(ticket.getSlaDefinitionId())
                .openedAt(ticket.getCreatedAt())
                .build()));
    }

    @Transactional(readOnly = true)
    public TicketCyclesDto cycles(String ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
        List<TicketCycle> cycles = cycleRepository.findByTicketIdOrderByCycleNoAsc(ticketId);
        Map<Integer, TicketResolution> resolutions = resolutionRepository.findByTicketIdOrderByCycleNoAsc(ticketId)
            .stream().collect(Collectors.toMap(TicketResolution::getCycleNo, Function.identity()));

        List<TicketCyclesDto.Cycle> items = cycles.stream()
            .map(c -> toDto(c, resolutions.get(c.getCycleNo())))
            .toList();

        TicketCyclesDto.Summary summary = new TicketCyclesDto.Summary(
            cycles.size(),
            Math.max(0, cycles.size() - 1),
            items.isEmpty() ? null : items.get(0).timeToResolveSeconds(),
            items.size() < 2 ? null : items.get(1).timeToResolveSeconds(),
            totalSeconds(ticket, cycles));
        return new TicketCyclesDto(summary, items);
    }

    private static Long totalSeconds(Ticket ticket, List<TicketCycle> cycles) {
        if (cycles.isEmpty()) {
            return null;
        }
        Instant end = ticket.getClosedAt() != null ? ticket.getClosedAt() : Instant.now();
        return between(cycles.get(0).getOpenedAt(), end);
    }

    private static TicketCyclesDto.Cycle toDto(TicketCycle c, TicketResolution r) {
        TicketCyclesDto.Resolution resolution = r == null ? null : new TicketCyclesDto.Resolution(
            r.getSolutionText(), r.getResolvedBy(), r.getResolvedAt(), r.getOutcome().name(),
            r.getDecidedBy(), r.getDecidedAt(), r.getDecisionComment());
        return new TicketCyclesDto.Cycle(
            c.getCycleNo(), c.getOpenedReason().name(), c.getReopenComment(), c.getStatus().name(),
            c.getSlaDefinitionId(), c.getResponseDueAt(), c.getResolutionDueAt(), c.getOpenedAt(),
            c.getFirstResponseAt(), c.getResolvedAt(), c.getValidatedAt(), c.getPausedSeconds(),
            between(c.getOpenedAt(), c.getFirstResponseAt()),
            between(c.getOpenedAt(), c.getResolvedAt()),
            between(c.getResolvedAt(), c.getValidatedAt()),
            resolution);
    }

    private static Long between(Instant from, Instant to) {
        return from == null || to == null ? null : Duration.between(from, to).getSeconds();
    }
}
