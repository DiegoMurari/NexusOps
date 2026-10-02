package com.nexusops.ticketing.service;

import com.nexusops.ticketing.dto.RoutingDecisionDto.ConditionTrace;
import com.nexusops.ticketing.dto.RoutingRuleDto;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Motor de regras de roteamento (ADR-013, Fase E). Puro: recebe as regras já em ordem e o contexto do chamado e
 * diz quais casam e por quê. Não toca banco nem sabe aplicar ações; isso é do {@link RoutingService}.
 */
@Service
public class RoutingEngineService {

    /** O que o roteamento enxerga do chamado. {@code answers} são as respostas já validadas do formulário. */
    public record Context(String topicId, String areaId, String locationId, String priority, Map<String, Object> answers) {
        public Context {
            answers = answers == null ? Map.of() : answers;
        }
    }

    public record Evaluation(RoutingRuleDto rule, boolean matched, List<ConditionTrace> conditions) {
    }

    /** Avalia todas as regras (a primeira que casar é a que vale; as demais ficam no rastro para o simulador). */
    public List<Evaluation> evaluateAll(List<RoutingRuleDto> orderedRules, Context ctx) {
        List<Evaluation> result = new ArrayList<>();
        for (RoutingRuleDto rule : orderedRules) {
            List<ConditionTrace> traces = new ArrayList<>();
            boolean all = true;
            for (RoutingRuleDto.Condition c : rule.conditions()) {
                List<String> actual = actualValues(c.field(), ctx);
                boolean hit = actual.stream().anyMatch(c.values()::contains);
                traces.add(new ConditionTrace(c.field(), c.values(), String.join(", ", actual), hit));
                all &= hit;
            }
            // Regra sem condições casa sempre: serve de regra padrão ao fim da lista.
            result.add(new Evaluation(rule, all, traces));
        }
        return result;
    }

    public Optional<Evaluation> firstMatch(List<Evaluation> evaluations) {
        return evaluations.stream().filter(Evaluation::matched).findFirst();
    }

    private List<String> actualValues(String field, Context ctx) {
        if (field == null) {
            return List.of();
        }
        if (field.startsWith(RoutingRuleDto.ANSWER_PREFIX)) {
            return answerValues(ctx.answers().get(field.substring(RoutingRuleDto.ANSWER_PREFIX.length())));
        }
        String single = switch (field) {
            case "TOPIC" -> ctx.topicId();
            case "AREA" -> ctx.areaId();
            case "LOCATION" -> ctx.locationId();
            case "PRIORITY" -> ctx.priority();
            default -> null;
        };
        return single == null || single.isBlank() ? List.of() : List.of(single);
    }

    private static List<String> answerValues(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> items) {
            return items.stream().map(RoutingEngineService::asText).toList();
        }
        return List.of(asText(value));
    }

    private static String asText(Object v) {
        if (v instanceof BigDecimal n) {
            return n.stripTrailingZeros().toPlainString();
        }
        return String.valueOf(v);
    }
}
