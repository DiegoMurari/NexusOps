package com.nexusops.sla.dto;

import java.time.Instant;

/**
 * Metas de SLA calculadas para um ciclo de atendimento: a definição escolhida e os prazos de resposta e
 * de resolução (já em horário útil, quando há calendário). Prazos nulos significam "sem meta".
 */
public record SlaTargetsDto(
    String definitionId,
    String definitionName,
    int definitionVersion,
    Instant responseDueAt,
    Instant resolutionDueAt,
    boolean pauseOnHold
) {
}
