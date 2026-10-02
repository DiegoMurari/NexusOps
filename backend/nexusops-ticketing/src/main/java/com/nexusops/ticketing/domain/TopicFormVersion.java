package com.nexusops.ticketing.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Versão do formulário de um tópico. Só o rascunho é editável; publicar congela a versão e arquiva a anterior.
 * Chamados guardam o ID da versão com que foram abertos.
 */
@Entity
@Table(name = "topic_form_versions", schema = "ticketing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicFormVersion implements TenantAware {

    public enum Status { DRAFT, PUBLISHED, ARCHIVED }

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "topic_id", nullable = false, length = 36)
    private String topicId;

    @Column(name = "version", nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private Status status;

    /** Definição serializada (campos, regras e política de evidência). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definition", nullable = false, columnDefinition = "jsonb")
    private String definition;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by", length = 255)
    private String publishedBy;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
