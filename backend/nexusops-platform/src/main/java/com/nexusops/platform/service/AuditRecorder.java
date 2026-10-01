package com.nexusops.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusops.platform.domain.AuditLog;
import com.nexusops.platform.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Writes audit entries in their own transaction so a rolled-back business operation still leaves its
 * trail, and so an audit failure can never break the operation being audited.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditRecorder {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public record Entry(String eventType, String action, String tenantId, String userId, String resourceType,
                        String resourceId, Map<String, Object> payload, String ipAddress, String userAgent) {
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Entry entry) {
        try {
            auditLogRepository.save(AuditLog.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(truncate(entry.eventType(), 100))
                .action(truncate(entry.action(), 50))
                .tenantId(truncate(entry.tenantId(), 36))
                .userId(truncate(entry.userId(), 255))
                .resourceType(truncate(entry.resourceType(), 100))
                .resourceId(truncate(entry.resourceId(), 36))
                .payload(toJson(entry.payload()))
                .ipAddress(truncate(entry.ipAddress(), 45))
                .userAgent(truncate(entry.userAgent(), 500))
                .build());
        } catch (RuntimeException e) {
            log.warn("Could not write audit entry {}: {}", entry.eventType(), e.getMessage());
        }
    }

    private String toJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
