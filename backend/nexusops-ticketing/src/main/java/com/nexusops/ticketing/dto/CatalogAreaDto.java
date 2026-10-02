package com.nexusops.ticketing.dto;

public record CatalogAreaDto(
    String id,
    String name,
    String description,
    String icon,
    int sortOrder,
    boolean active,
    long topicCount
) {
}
