package com.nexusops.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Um evento do sistema que um webhook pode assinar. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEventDto {
    private String name;
    private String description;
}
