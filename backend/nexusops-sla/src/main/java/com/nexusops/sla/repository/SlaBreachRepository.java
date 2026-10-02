package com.nexusops.sla.repository;

import com.nexusops.sla.domain.SlaBreach;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface SlaBreachRepository extends JpaRepository<SlaBreach, String> {

    List<SlaBreach> findByTenantId(String tenantId);

    List<SlaBreach> findByTenantIdAndAcknowledgedAndEscalatedAndResolved(
        String tenantId, boolean acknowledged, boolean escalated, boolean resolved);

    List<SlaBreach> findByTicketId(String ticketId);

    Optional<SlaBreach> findByTicketIdAndSlaDefinitionIdAndBreachType(
        String ticketId, String slaDefinitionId, SlaBreach.BreachType breachType);

    List<SlaBreach> findByTenantIdAndBreachTimeBetween(
        String tenantId, Instant start, Instant end);

    Page<SlaBreach> findByTenantId(String tenantId, Pageable pageable);

    @Query("SELECT b FROM SlaBreach b WHERE b.tenantId = :tenantId AND b.acknowledged = false AND b.escalated = false")
    List<SlaBreach> findUnacknowledgedUnescalated(@Param("tenantId") String tenantId);

    @Query("SELECT b FROM SlaBreach b WHERE b.tenantId = :tenantId AND b.escalated = false AND b.breachTime < :now")
    List<SlaBreach> findPendingEscalation(@Param("tenantId") String tenantId, @Param("now") Instant now);

    @Query("SELECT b FROM SlaBreach b WHERE b.escalated = false AND b.breachTime < :now")
    List<SlaBreach> findAllPendingEscalation(@Param("now") Instant now);

    long countByTenantIdAndResolvedFalse(String tenantId);

    List<SlaBreach> findByTenantIdAndResolvedFalse(String tenantId);

    List<SlaBreach> findByTicketIdAndResolvedFalse(String ticketId);

    boolean existsByTicketIdAndBreachTypeAndBreachTime(String ticketId, SlaBreach.BreachType breachType, Instant breachTime);
}