package com.nexusops.iam.domain;

import com.nexusops.shared.crypto.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mfa_secrets", schema = "iam")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MfaSecret {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Version
    private Long version;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "secret", nullable = false, length = 255)
    @Convert(converter = EncryptedStringConverter.class)
    private String secret;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "backup_codes", columnDefinition = "jsonb")
    private String backupCodes; // JSON array of hashed codes

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}