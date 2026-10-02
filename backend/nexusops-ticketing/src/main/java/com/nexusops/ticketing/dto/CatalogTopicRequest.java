package com.nexusops.ticketing.dto;

import jakarta.validation.constraints.Size;

/** Criar exige área e nome. No PATCH, nulo mantém e texto vazio limpa fila, prioridade, SLA e categoria. */
public record CatalogTopicRequest(
    String areaId,
    @Size(max = 160) String name,
    @Size(max = 1000) String description,
    String defaultQueueId,
    String defaultPriority,
    String slaDefinitionId,
    String categoryId,
    Integer sortOrder,
    Boolean active
) {
}
