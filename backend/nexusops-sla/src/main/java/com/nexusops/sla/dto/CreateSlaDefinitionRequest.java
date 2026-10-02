package com.nexusops.sla.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateSlaDefinitionRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 1000)
    private String description;

    /** Ignorado: a definição sempre pertence ao tenant do chamador. */
    private String tenantId;

    private boolean active = true;

    @Size(max = 50)
    private String appliesToType;

    @Size(max = 100)
    private String appliesToCategory;

    @Size(max = 50)
    private String appliesToPriority;

    @Size(max = 50)
    private String appliesToCustomerTier;

    private Integer responseTimeMinutes;

    private Integer resolutionTimeMinutes;

    private String businessCalendarId;

    private boolean pauseOnHold = true;

    private boolean stopOnFirstResponse = true;

    private boolean breachWarning80 = true;

    private boolean breachWarning90 = true;
}