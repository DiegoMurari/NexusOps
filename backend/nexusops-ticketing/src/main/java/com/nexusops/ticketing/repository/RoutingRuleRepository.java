package com.nexusops.ticketing.repository;

import com.nexusops.ticketing.domain.RoutingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoutingRuleRepository extends JpaRepository<RoutingRule, String> {

    List<RoutingRule> findByTenantIdOrderByPositionAsc(String tenantId);

    List<RoutingRule> findByTenantIdAndActiveTrueOrderByPositionAsc(String tenantId);

    Optional<RoutingRule> findByIdAndTenantId(String id, String tenantId);

    boolean existsByTenantIdAndNameIgnoreCase(String tenantId, String name);

    boolean existsByTenantIdAndNameIgnoreCaseAndIdNot(String tenantId, String name, String id);

    @Query("SELECT COALESCE(MAX(r.position), 0) FROM RoutingRule r WHERE r.tenantId = :tenantId")
    int maxPosition(@Param("tenantId") String tenantId);
}
