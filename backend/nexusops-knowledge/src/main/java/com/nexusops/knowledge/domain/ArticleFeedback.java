package com.nexusops.knowledge.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "article_feedback", schema = "knowledge", indexes = {
    @Index(name = "idx_feedback_article", columnList = "article_id"),
    @Index(name = "idx_feedback_user", columnList = "user_id"),
    @Index(name = "idx_feedback_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArticleFeedback implements TenantAware {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "article_id", nullable = false, length = 36)
    private String articleId;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "helpful", nullable = false)
    private boolean helpful;

    @Column(name = "comment", columnDefinition = "text")
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        createdAt = Instant.now();
    }
}