package com.nexusops.ticketing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Regra de roteamento (ADR-013, Fase E). Todas as condições precisam casar; dentro de uma condição, basta um
 * dos valores. Campos de condição: TOPIC, AREA, LOCATION, PRIORITY e ANSWER:chave (resposta do formulário).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RoutingRuleDto(
    String id,
    String name,
    String description,
    int position,
    boolean active,
    List<Condition> conditions,
    Actions actions
) {

    public static final String ANSWER_PREFIX = "ANSWER:";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Condition(String field, List<String> values) {
        public Condition {
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    /** Estratégia de responsável: NONE (só a fila), USER (uma pessoa) ou LEAST_LOADED (membro da fila com menos chamados abertos). */
    public enum AssigneeStrategy { NONE, USER, LEAST_LOADED }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Actions(String queueId, String priority, AssigneeStrategy assigneeStrategy, String assigneeId) {
    }

    public RoutingRuleDto {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }
}
