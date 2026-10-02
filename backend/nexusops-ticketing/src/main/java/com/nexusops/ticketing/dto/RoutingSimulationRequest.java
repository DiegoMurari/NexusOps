package com.nexusops.ticketing.dto;

import java.util.Map;

/** Um chamado hipotético: o que o solicitante escolheu e respondeu. Nada é gravado. */
public record RoutingSimulationRequest(
    String topicId,
    String locationId,
    String priority,
    Map<String, Object> answers
) {
}
