package com.nexusops.integration.repository;

import com.nexusops.integration.domain.Connector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConnectorRepository extends JpaRepository<Connector, String> {

    Optional<Connector> findByIdAndTenantId(String id, String tenantId);

    List<Connector> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<Connector> findByTenantIdAndTypeOrderByCreatedAtDesc(String tenantId, Connector.ConnectorType type);

    /** Grava só o resultado da verificação, fora da entidade (a verificação faz chamada de rede, sem transação). */
    @Transactional
    @Modifying
    @Query("update Connector c set c.lastCheckAt = :at, c.lastCheckStatus = :status, c.lastCheckMessage = :message "
        + "where c.id = :id and c.tenantId = :tenantId")
    int recordCheck(@Param("id") String id, @Param("tenantId") String tenantId, @Param("at") Instant at,
                    @Param("status") String status, @Param("message") String message);
}
