package com.nexusops.integration.dto;

import com.nexusops.integration.domain.Connector;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateConnectorRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @NotNull
    private Connector.ConnectorType type;

    /** Non-secret settings only; keys that look like credentials are rejected. */
    @Size(max = 20)
    private Map<@Size(max = 50) String, @Size(max = 500) String> configuration;

    /** Spring 6-field cron expression. */
    @Size(max = 100)
    private String syncScheduleCron;
}
