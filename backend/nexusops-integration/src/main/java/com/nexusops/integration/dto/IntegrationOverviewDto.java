package com.nexusops.integration.dto;

import lombok.*;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationOverviewDto {

    private long webhooks;
    private long activeWebhooks;
    private long connectors;
    private Map<String, Long> connectorsByType;
    /** False while outbound delivery/sync is not implemented; the UI states this to users. */
    private boolean deliveryAvailable;
}
