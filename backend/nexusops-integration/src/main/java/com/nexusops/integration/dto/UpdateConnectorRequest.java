package com.nexusops.integration.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.Map;

/** Every field is optional; only provided fields change. An empty cron clears the schedule. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateConnectorRequest {

    @Size(max = 255)
    private String name;

    @Size(max = 20)
    private Map<@Size(max = 50) String, @Size(max = 500) String> configuration;

    @Size(max = 100)
    private String syncScheduleCron;

    /** Liga ou desliga o conector sem perder a configuração. */
    private Boolean enabled;
}
