package com.nexusops.sla.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateSlaDefinitionRequest {

    @Size(max = 255)
    private String name;

    @Size(max = 1000)
    private String description;

    private Boolean active;

    @Size(max = 50)
    private String appliesToType;

    @Size(max = 100)
    private String appliesToCategory;

    @Size(max = 50)
    private String appliesToPriority;

    @Size(max = 50)
    private String appliesToCustomerTier;

    @Positive
    private Integer responseTimeMinutes;

    @Positive
    private Integer resolutionTimeMinutes;

    private String businessCalendarId;

    private Boolean pauseOnHold;

    private Boolean stopOnFirstResponse;

    private Boolean breachWarning80;

    private Boolean breachWarning90;
}