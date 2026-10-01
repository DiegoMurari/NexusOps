package com.nexusops.platform.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "system_settings", schema = "platform", indexes = {
    @Index(name = "idx_system_settings_key", columnList = "setting_key", unique = true),
    @Index(name = "idx_system_settings_public", columnList = "is_public")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemSetting {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "setting_key", nullable = false, unique = true, length = 100)
    private String settingKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "value", columnDefinition = "jsonb")
    private String value;

    @Builder.Default
    @Column(name = "value_type", nullable = false, length = 20)
    private String valueType = "STRING";

    @Column(name = "description", length = 1000)
    private String description;

    @Builder.Default
    @Column(name = "is_public", nullable = false)
    private boolean isPublic = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_schema", columnDefinition = "jsonb")
    private String validationSchema;

    @Column(name = "category", length = 50)
    private String category;

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

    public enum ValueType {
        STRING, NUMBER, BOOLEAN, JSON
    }
}