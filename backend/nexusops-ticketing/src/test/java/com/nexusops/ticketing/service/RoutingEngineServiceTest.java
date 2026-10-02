package com.nexusops.ticketing.service;

import com.nexusops.ticketing.dto.RoutingRuleDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingEngineServiceTest {

    private final RoutingEngineService engine = new RoutingEngineService();

    private static RoutingRuleDto rule(String id, RoutingRuleDto.Condition... conditions) {
        return new RoutingRuleDto(id, id, null, 1, true, List.of(conditions),
            new RoutingRuleDto.Actions("q", null, RoutingRuleDto.AssigneeStrategy.NONE, null));
    }

    private static RoutingEngineService.Context ctx(Map<String, Object> answers) {
        return new RoutingEngineService.Context("t1", "a1", "l1", "HIGH", answers);
    }

    @Test
    void allConditionsMustMatchAndValuesAreOr() {
        var r = rule("r", new RoutingRuleDto.Condition("TOPIC", List.of("t0", "t1")),
            new RoutingRuleDto.Condition("PRIORITY", List.of("HIGH")));
        assertThat(engine.evaluateAll(List.of(r), ctx(null)).get(0).matched()).isTrue();

        var miss = rule("m", new RoutingRuleDto.Condition("TOPIC", List.of("t1")),
            new RoutingRuleDto.Condition("PRIORITY", List.of("LOW")));
        var ev = engine.evaluateAll(List.of(miss), ctx(null)).get(0);
        assertThat(ev.matched()).isFalse();
        assertThat(ev.conditions()).extracting("matched").containsExactly(true, false);
    }

    @Test
    void firstMatchWinsInOrder() {
        var a = rule("a", new RoutingRuleDto.Condition("AREA", List.of("zzz")));
        var b = rule("b", new RoutingRuleDto.Condition("AREA", List.of("a1")));
        var c = rule("c", new RoutingRuleDto.Condition("AREA", List.of("a1")));
        var all = engine.evaluateAll(List.of(a, b, c), ctx(null));
        assertThat(engine.firstMatch(all).orElseThrow().rule().id()).isEqualTo("b");
        assertThat(all).extracting(RoutingEngineService.Evaluation::matched).containsExactly(false, true, true);
    }

    @Test
    void answersMatchScalarsNumbersAndMultiselect() {
        var scalar = rule("s", new RoutingRuleDto.Condition("ANSWER:tipo", List.of("rede")));
        var number = rule("n", new RoutingRuleDto.Condition("ANSWER:qtd", List.of("3")));
        var multi = rule("m", new RoutingRuleDto.Condition("ANSWER:itens", List.of("b")));
        var answers = Map.<String, Object>of("tipo", "rede", "qtd", new BigDecimal("3.0"), "itens", List.of("a", "b"));
        var res = engine.evaluateAll(List.of(scalar, number, multi), ctx(answers));
        assertThat(res).extracting(RoutingEngineService.Evaluation::matched).containsExactly(true, true, true);
    }

    @Test
    void missingAnswerDoesNotMatchAndRuleWithoutConditionsAlwaysMatches() {
        var needs = rule("n", new RoutingRuleDto.Condition("ANSWER:tipo", List.of("rede")));
        var fallback = rule("f");
        var res = engine.evaluateAll(List.of(needs, fallback), ctx(Map.of()));
        assertThat(res.get(0).matched()).isFalse();
        assertThat(res.get(0).conditions().get(0).actual()).isEmpty();
        assertThat(res.get(1).matched()).isTrue();
    }

    @Test
    void unknownFieldNeverMatches() {
        var r = rule("x", new RoutingRuleDto.Condition("COLOR", List.of("red")));
        assertThat(engine.evaluateAll(List.of(r), ctx(null)).get(0).matched()).isFalse();
    }
}
