package com.nexusops.ticketing.dto;

import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeEntryDto {

    private String id;
    private String ticketId;
    private String tenantId;
    private String userId;
    private String description;
    private Instant startTime;
    private Instant endTime;
    private Integer durationMinutes;
    private boolean billable;
    private Instant createdAt;
    private Instant updatedAt;
}