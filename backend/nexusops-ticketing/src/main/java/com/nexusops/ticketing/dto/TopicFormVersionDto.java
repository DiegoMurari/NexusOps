package com.nexusops.ticketing.dto;

import java.time.Instant;

public record TopicFormVersionDto(
    String id,
    String topicId,
    int version,
    String status,
    FormDefinitionDto definition,
    Instant createdAt,
    String createdBy,
    Instant updatedAt,
    Instant publishedAt,
    String publishedBy
) {
}
