package com.nexusops.ticketing.dto;

import java.time.Instant;
import java.util.List;

/**
 * Ciclos de atendimento de um chamado, com a resolução de cada ciclo e as métricas derivadas.
 * Os valores em segundos são tempo corrido; nulos quando o marco ainda não aconteceu.
 */
public record TicketCyclesDto(Summary summary, List<Cycle> cycles) {

    public record Summary(
        int cycleCount,
        int reopenCount,
        Long timeToFirstResolutionSeconds,
        Long secondAttendanceSeconds,
        Long totalSeconds
    ) {
    }

    public record Cycle(
        int cycleNo,
        String openedReason,
        String reopenComment,
        String status,
        String slaDefinitionId,
        Instant responseDueAt,
        Instant resolutionDueAt,
        Instant openedAt,
        Instant firstResponseAt,
        Instant resolvedAt,
        Instant validatedAt,
        long pausedSeconds,
        Long timeToFirstResponseSeconds,
        Long timeToResolveSeconds,
        Long validationSeconds,
        Resolution resolution
    ) {
    }

    public record Resolution(
        String solutionText,
        String resolvedBy,
        Instant resolvedAt,
        String outcome,
        String decidedBy,
        Instant decidedAt,
        String decisionComment
    ) {
    }
}
