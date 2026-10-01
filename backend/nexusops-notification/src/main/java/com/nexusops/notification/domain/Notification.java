package com.nexusops.notification.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications", schema = "notification", indexes = {
    @Index(name = "idx_notifications_recipient", columnList = "recipient_id"),
    @Index(name = "idx_notifications_tenant", columnList = "tenant_id"),
    @Index(name = "idx_notifications_status", columnList = "status"),
    @Index(name = "idx_notifications_priority", columnList = "priority"),
    @Index(name = "idx_notifications_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification implements TenantAware {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "recipient_id", nullable = false, length = 36)
    private String recipientId;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "channel", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Channel channel;

    @Column(name = "template_key", length = 100)
    private String templateKey;

    @Column(name = "subject", length = 500)
    private String subject;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    @Column(name = "content_html", columnDefinition = "text")
    private String contentHtml;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variables", columnDefinition = "jsonb")
    private String variables;

    @Column(name = "priority", length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Priority priority = Priority.NORMAL;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "read", nullable = false)
    @Builder.Default
    private boolean read = false;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    @Builder.Default
    private int maxRetries = 3;

    @Column(name = "correlation_id", length = 36)
    private String correlationId;

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

    public enum Channel {
        IN_APP,
        EMAIL,
        WEBSOCKET,
        SSE,
        PUSH
    }

    public enum Priority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }

    public enum Status {
        PENDING,
        SENT,
        DELIVERED,
        READ,
        FAILED,
        EXPIRED
    }
}