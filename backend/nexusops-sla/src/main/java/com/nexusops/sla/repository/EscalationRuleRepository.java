package com.nexusops.sla.repository;

import com.nexusops.sla.domain.EscalationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EscalationRuleRepository extends JpaRepository<EscalationRule, String> {

    List<EscalationRule> findByTenantId(String tenantId);

    List<EscalationRule> findByTenantIdAndActiveTrue(String tenantId);

    List<EscalationRule> findBySlaDefinitionId(String slaDefinitionId);

    Optional<EscalationRule> findByTenantIdAndName(String tenantId, String name);
}