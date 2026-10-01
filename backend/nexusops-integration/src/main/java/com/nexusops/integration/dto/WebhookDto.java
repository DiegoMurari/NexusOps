package com.nexusops.integration.dto;

import com.nexusops.integration.domain.Webhook;
import lombok.*;

import java.time.Instant;
import java.util.TreeSet;
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

    public static WebhookDto from(Webhook w) {
        return WebhookDto.builder()
            .id(w.getId()).name(w.getName()).targetUrl(w.getTargetUrl())
            .events(new TreeSet<>(w.getEvents())).status(w.getStatus())
            .timeoutSeconds(w.getTimeoutSeconds())
            .hasSecret(w.getSecret() != null && !w.getSecret().isBlank())
            .createdAt(w.getCreatedAt()).updatedAt(w.getUpdatedAt())
            .build();
    }
}
