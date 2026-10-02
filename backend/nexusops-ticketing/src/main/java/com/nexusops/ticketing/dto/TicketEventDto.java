package com.nexusops.ticketing.dto;

import java.time.Instant;
import java.util.Map;

public record TicketEventDto(
    String id,
    int seq,
    int cycleNo,
    String type,
    String actorId,
    String actorKind,
    String visibility,
    Map<String, Object> payload,
    Instant occurredAt
) {
}
