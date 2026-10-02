package com.nexusops.ticketing.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/** Criar exige nome e ao menos uma ação. No PATCH, nulo mantém o valor. */
public record RoutingRuleRequest(
    @Size(max = 160) String name,
    @Size(max = 500) String description,
    Boolean active,
    List<RoutingRuleDto.Condition> conditions,
    RoutingRuleDto.Actions actions
) {
}
