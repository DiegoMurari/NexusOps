package com.nexusops.platform.service;

import com.nexusops.platform.domain.AuditLog;
import com.nexusops.platform.dto.AuditLogDto;
import com.nexusops.platform.mapper.AuditLogMapper;
import com.nexusops.platform.repository.AuditLogRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Read side of the audit trail. Every query is scoped to a tenant. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditService {

    private static final char ESCAPE = '\\';

    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper auditLogMapper;

    public List<AuditLogDto> findByTenantId(String tenantId) {
        return auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
            .map(auditLogMapper::toDto)
            .toList();
    }

    public Page<AuditLogDto> findByTenantId(String tenantId, Pageable pageable) {
        return auditLogRepository.findByTenantId(tenantId, pageable)
            .map(auditLogMapper::toDto);
    }

    /** Filtered, paged listing; blank filters are ignored. */
    public Page<AuditLogDto> search(String tenantId, String action, String resourceType, String userId,
                                    Instant since, Instant until, String q, Pageable pageable) {
        Specification<AuditLog> spec = (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
        if (hasText(action)) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), action.trim()));
        }
        if (hasText(resourceType)) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("resourceType"), resourceType.trim()));
        }
        if (hasText(userId)) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("userId"), userId.trim()));
        }
        if (since != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), since));
        }
        if (until != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), until));
        }
        if (hasText(q)) {
            String like = "%" + escapeLike(q.trim().toLowerCase()) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("eventType")), like, ESCAPE),
                cb.like(cb.lower(root.get("userId")), like, ESCAPE),
                cb.like(cb.lower(root.get("resourceType")), like, ESCAPE),
                cb.like(cb.lower(root.get("resourceId")), like, ESCAPE)));
        }
        return auditLogRepository.findAll(spec, pageable).map(auditLogMapper::toDto);
    }

    public List<AuditLogDto> findByTenantIdAndDateRange(
            String tenantId, Instant start, Instant end) {
        return auditLogRepository.findByTenantIdAndCreatedAtBetween(tenantId, start, end).stream()
            .map(auditLogMapper::toDto)
            .toList();
    }

    public List<AuditLogDto> findByUserId(String tenantId, String userId) {
        return auditLogRepository.findByTenantIdAndUserIdOrderByCreatedAtDesc(tenantId, userId).stream()
            .map(auditLogMapper::toDto)
            .toList();
    }

    public List<AuditLogDto> findByResource(String tenantId, String resourceType, String resourceId) {
        return auditLogRepository
            .findByTenantIdAndResourceTypeAndResourceIdOrderByCreatedAtDesc(tenantId, resourceType, resourceId).stream()
            .map(auditLogMapper::toDto)
            .toList();
    }

    public List<AuditLogDto> findRecentByTenant(String tenantId, Instant since) {
        return auditLogRepository.findRecentByTenant(tenantId, since).stream()
            .map(auditLogMapper::toDto)
            .toList();
    }

    public long countRecentByTenant(String tenantId, Instant since) {
        return auditLogRepository.countRecentByTenant(tenantId, since);
    }

    public AuditLogDto findById(String tenantId, String id) {
        return auditLogRepository.findByIdAndTenantId(id, tenantId)
            .map(auditLogMapper::toDto)
            .orElseThrow(() -> new ResourceNotFoundException("AuditLog", id));
    }

    /** Escapes LIKE wildcards so user text is matched literally. */
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
