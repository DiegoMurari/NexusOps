package com.nexusops.ticketing.dto;

/** Visão administrativa: inclui os padrões internos (fila, prioridade, SLA). */
public record CatalogTopicDto(
    String id,
    String areaId,
    String name,
    String description,
    String defaultQueueId,
    String defaultPriority,
    String slaDefinitionId,
    String categoryId,
    int sortOrder,
    boolean active
) {
}
