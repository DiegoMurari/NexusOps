package com.nexusops.platform.repository;

import com.nexusops.platform.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<AuditLog> findByTenantIdAndCreatedAtBetween(
        String tenantId, Instant start, Instant end);

    Page<AuditLog> findByTenantId(String tenantId, Pageable pageable);

    Optional<AuditLog> findByIdAndTenantId(String id, String tenantId);

    List<AuditLog> findByTenantIdAndUserIdOrderByCreatedAtDesc(String tenantId, String userId);

    List<AuditLog> findByTenantIdAndResourceTypeAndResourceIdOrderByCreatedAtDesc(
        String tenantId, String resourceType, String resourceId);

    @Query("SELECT a FROM AuditLog a WHERE a.tenantId = :tenantId AND a.createdAt >= :since ORDER BY a.createdAt DESC")
    List<AuditLog> findRecentByTenant(@Param("tenantId") String tenantId, @Param("since") Instant since);

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.tenantId = :tenantId AND a.createdAt >= :since")
    long countRecentByTenant(@Param("tenantId") String tenantId, @Param("since") Instant since);
}
