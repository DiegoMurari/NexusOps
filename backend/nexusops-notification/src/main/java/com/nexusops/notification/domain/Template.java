package com.nexusops.notification.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "templates", schema = "notification", indexes = {
    @Index(name = "idx_templates_key", columnList = "template_key", unique = true),
    @Index(name = "idx_templates_tenant", columnList = "tenant_id"),
    @Index(name = "idx_templates_channel", columnList = "channel")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Template implements TenantAware {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "template_key", nullable = false, unique = true, length = 100)
    private String templateKey;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "tenant_id", length = 36)
    private String tenantId;

    @Column(name = "channel", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Channel channel;

    @Column(name = "subject_template", length = 500)
    private String subjectTemplate;

    @Column(name = "content_template", columnDefinition = "text", nullable = false)
    private String contentTemplate;

    @Column(name = "content_html_template", columnDefinition = "text")
    private String contentHtmlTemplate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variables_schema", columnDefinition = "jsonb")
    private String variablesSchema;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "updated_by", length = 36)
    private String updatedBy;

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
}