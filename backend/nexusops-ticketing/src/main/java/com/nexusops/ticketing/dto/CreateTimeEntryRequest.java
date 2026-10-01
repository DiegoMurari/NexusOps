package com.nexusops.ticketing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTimeEntryRequest {

    @NotNull
    private String userId;

    private String description;

    @NotNull
    private Instant startTime;

    private Instant endTime;

    private Integer durationMinutes;

    private boolean billable = true;
}