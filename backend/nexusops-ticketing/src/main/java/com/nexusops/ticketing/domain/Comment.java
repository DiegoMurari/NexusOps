package com.nexusops.ticketing.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "comments", schema = "ticketing", indexes = {
    @Index(name = "idx_comments_ticket", columnList = "ticket_id"),
    @Index(name = "idx_comments_author", columnList = "author_id"),
    @Index(name = "idx_comments_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comment implements TenantAware {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "ticket_id", nullable = false, length = 36)
    private String ticketId;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "author_id", nullable = false, length = 36)
    private String authorId;

    @Column(name = "content", columnDefinition = "text", nullable = false)
    private String content;

    @Column(name = "content_html", columnDefinition = "text")
    private String contentHtml;

    @Builder.Default
    @Column(name = "public_comment", nullable = false)
    private boolean publicComment = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mentions", columnDefinition = "jsonb")
    private String mentions;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}