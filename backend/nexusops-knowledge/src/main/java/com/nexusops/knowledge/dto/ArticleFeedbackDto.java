package com.nexusops.knowledge.dto;

import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArticleFeedbackDto {

    private String id;
    private String articleId;
    private String tenantId;
    private String userId;
    private boolean helpful;
    private String comment;
    private Instant createdAt;
}
