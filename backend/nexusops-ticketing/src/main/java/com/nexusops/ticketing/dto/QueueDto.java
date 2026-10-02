package com.nexusops.ticketing.dto;

import java.time.Instant;

public record QueueDto(
    String id,
    String name,
    String code,
    String description,
    boolean active,
    long memberCount,
    Instant createdAt,
    Instant updatedAt
) {
}
