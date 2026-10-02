package com.nexusops.ticketing.dto;

import java.util.List;

/**
 * Resultado do roteamento: para onde o chamado vai e por quê. {@code source} é TOPIC (padrão do tópico),
 * RULE (uma regra casou), MANUAL (fila escolhida pelo atendente) ou NONE (nada definiu fila).
 */
public record RoutingDecisionDto(
    String source,
    String queueId,
    String queueName,
    String priority,
    /** E-mail do responsável (é assim que o chamado guarda o responsável). */
    String assigneeId,
    String assigneeName,
    String ruleId,
    String ruleName,
    List<RuleTrace> trace,
    List<String> notes
) {

    /** Como uma regra se saiu na avaliação; {@code applied} marca a que valeu. */
    public record RuleTrace(String ruleId, String ruleName, boolean matched, boolean applied, List<ConditionTrace> conditions) {
    }

    public record ConditionTrace(String field, List<String> values, String actual, boolean matched) {
    }
}
