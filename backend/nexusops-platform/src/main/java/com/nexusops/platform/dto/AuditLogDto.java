package com.nexusops.platform.dto;

import com.nexusops.platform.domain.AuditLog;
import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogDto {

    private String id;
    private String eventId;
    private String eventType;
    private String aggregateId;
    private String aggregateType;
    private String tenantId;
    private String userId;
    private String resourceType;
    private String resourceId;
    private String action;
    private String payload;
    private String previousState;
    private String ipAddress;
    private String userAgent;
    private String correlationId;
    private Instant createdAt;
}