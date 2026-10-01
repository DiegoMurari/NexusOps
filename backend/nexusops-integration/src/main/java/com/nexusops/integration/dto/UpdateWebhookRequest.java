package com.nexusops.integration.dto;

import com.nexusops.integration.domain.Webhook;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.Set;

/** Every field is optional; only provided fields change. An empty secret clears it. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateWebhookRequest {

    @Size(max = 255)
    private String name;

    @Size(max = 500)
    private String targetUrl;

    @ToString.Exclude
    @Size(max = 255)
    private String secret;

    @Size(max = 50)
    private Set<@Size(max = 100) String> events;

    private Webhook.WebhookStatus status;

    @Min(1)
    @Max(60)
    private Integer timeoutSeconds;
}
