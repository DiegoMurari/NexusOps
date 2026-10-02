package com.nexusops.integration.dto;

import com.nexusops.integration.domain.Webhook;
import lombok.*;

import java.time.Instant;
import java.util.Set;

/** The signing secret is never exposed; only whether one is configured. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookDto {

    private String id;
    private String name;
    private String targetUrl;
    private Set<String> events;
    private Webhook.WebhookStatus status;
    private int timeoutSeconds;
    private boolean hasSecret;
    private Instant createdAt;
    private Instant updatedAt;
}
