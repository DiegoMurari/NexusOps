package com.nexusops.sla.repository;

import com.nexusops.sla.domain.SlaDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SlaDefinitionRepository extends JpaRepository<SlaDefinition, String> {

    List<SlaDefinition> findByTenantId(String tenantId);

    List<SlaDefinition> findByTenantIdAndActiveTrue(String tenantId);

    Optional<SlaDefinition> findByTenantIdAndName(String tenantId, String name);

    @Query("SELECT s FROM SlaDefinition s WHERE s.tenantId = :tenantId AND s.active = true " +
           "AND (s.appliesToType IS NULL OR s.appliesToType = :type) " +
           "AND (s.appliesToCategory IS NULL OR s.appliesToCategory = :category) " +
           "AND (s.appliesToPriority IS NULL OR s.appliesToPriority = :priority) " +
           "AND (s.appliesToCustomerTier IS NULL OR s.appliesToCustomerTier = :customerTier) " +
           "ORDER BY s.version DESC")
    List<SlaDefinition> findMatchingDefinitions(
        @Param("tenantId") String tenantId,
        @Param("type") String type,
        @Param("category") String category,
        @Param("priority") String priority,
        @Param("customerTier") String customerTier
    );
}