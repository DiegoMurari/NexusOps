package com.nexusops.integration.repository;

import com.nexusops.integration.domain.Connector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConnectorRepository extends JpaRepository<Connector, String> {

    Optional<Connector> findByIdAndTenantId(String id, String tenantId);

    List<Connector> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<Connector> findByTenantIdAndTypeOrderByCreatedAtDesc(String tenantId, Connector.ConnectorType type);
}
