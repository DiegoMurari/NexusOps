package com.nexusops.sla.dto;

import com.nexusops.sla.domain.SlaDefinition;
import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlaDefinitionDto {

    private String id;
    private String name;
    private String description;
    private String tenantId;
    private boolean active;
    private String appliesToType;
    private String appliesToCategory;
    private String appliesToPriority;
    private String appliesToCustomerTier;
    private Integer responseTimeMinutes;
    private Integer resolutionTimeMinutes;
    private String businessCalendarId;
    private boolean pauseOnHold;
    private boolean stopOnFirstResponse;
    private boolean breachWarning80;
    private boolean breachWarning90;
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;
}