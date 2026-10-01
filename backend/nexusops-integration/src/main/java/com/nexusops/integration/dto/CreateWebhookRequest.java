package com.nexusops.integration.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateWebhookRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @NotBlank
    @Size(max = 500)
    private String targetUrl;

    @ToString.Exclude
    @Size(max = 255)
    private String secret;

    /** Event names such as ticket.created. */
    @Size(max = 50)
    private Set<@Size(max = 100) String> events;

    @Min(1)
    @Max(60)
    private Integer timeoutSeconds;
}
