package com.nexusops.ticketing.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attachments", schema = "ticketing", indexes = {
    @Index(name = "idx_attachments_ticket", columnList = "ticket_id"),
    @Index(name = "idx_attachments_uploader", columnList = "uploader_id"),
    @Index(name = "idx_attachments_scan", columnList = "virus_scan_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attachment implements TenantAware {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    /** Nulo enquanto o anexo está "preparado": enviado no formulário de abertura, ainda sem chamado. */
    @Column(name = "ticket_id", length = 36)
    private String ticketId;

    /** A que ponto do histórico a evidência pertence: o chamado, um comentário ou um evento (etapa). */
    @Builder.Default
    @Column(name = "subject_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Subject subjectType = Subject.TICKET;

    @Column(name = "subject_id", length = 36)
    private String subjectId;

    /** Interna: só a equipe vê. O solicitante só enxerga evidência pública. */
    @Builder.Default
    @Column(name = "internal", nullable = false)
    private boolean internal = false;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "s3_key", nullable = false, length = 500)
    private String s3Key;

    @Column(name = "s3_bucket", length = 100)
    private String s3Bucket;

    @Column(name = "checksum", length = 64)
    private String checksum;

    @Column(name = "uploader_id", nullable = false, length = 36)
    private String uploaderId;

    @Builder.Default
    @Column(name = "virus_scan_status", length = 20)
    @Enumerated(EnumType.STRING)
    private VirusScanStatus virusScanStatus = VirusScanStatus.PENDING;

    @Column(name = "virus_scan_result", columnDefinition = "text")
    private String virusScanResult;

    @Column(name = "thumbnail_s3_key", length = 500)
    private String thumbnailS3Key;

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

    public enum Subject {
        TICKET,
        COMMENT,
        EVENT
    }

    public enum VirusScanStatus {
        PENDING,
        SCANNING,
        CLEAN,
        INFECTED,
        FAILED
    }
}