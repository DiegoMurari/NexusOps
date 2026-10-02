package com.nexusops.ticketing.repository;

import com.nexusops.ticketing.domain.Ticket;
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
public interface TicketRepository extends JpaRepository<Ticket, String>,
    org.springframework.data.jpa.repository.JpaSpecificationExecutor<Ticket> {

    boolean existsByQueueId(String queueId);

    long countByTenantIdAndAssigneeIdAndStatusIn(String tenantId, String assigneeId, java.util.Collection<Ticket.TicketStatus> statuses);

    Optional<Ticket> findByTicketNumber(String ticketNumber);

    Optional<Ticket> findByIdAndTenantId(String id, String tenantId);

    List<Ticket> findByTenantIdAndIdIn(String tenantId, java.util.Collection<String> ids);

    List<Ticket> findByTenantId(String tenantId);

    List<Ticket> findByTenantIdAndStatus(String tenantId, Ticket.TicketStatus status);

    List<Ticket> findByTenantIdAndAssigneeId(String tenantId, String assigneeId);

    List<Ticket> findByTenantIdAndReporterId(String tenantId, String reporterId);

    Page<Ticket> findByTenantId(String tenantId, Pageable pageable);

    @Query("SELECT t FROM Ticket t WHERE t.tenantId = :tenantId AND t.status IN :statuses")
    List<Ticket> findByTenantIdAndStatusIn(@Param("tenantId") String tenantId, @Param("statuses") List<Ticket.TicketStatus> statuses);

    @Query("SELECT t FROM Ticket t WHERE t.tenantId = :tenantId AND t.createdAt BETWEEN :start AND :end")
    List<Ticket> findByTenantIdAndCreatedAtBetween(@Param("tenantId") String tenantId, @Param("start") Instant start, @Param("end") Instant end);

    long countByTenantIdAndStatus(String tenantId, Ticket.TicketStatus status);

    long countByTenantIdAndAssigneeIdAndStatusIn(String tenantId, String assigneeId, List<Ticket.TicketStatus> statuses);

    @Query("SELECT t FROM Ticket t WHERE t.tenantId = :tenantId AND t.slaDefinitionId IS NOT NULL AND t.resolutionDueAt < :now AND t.status NOT IN ('RESOLVED', 'CLOSED')")
    List<Ticket> findOverdueTickets(@Param("tenantId") String tenantId, @Param("now") Instant now);

    /** Chamados em atendimento cujo relógio de SLA está correndo: é o que o vigia de SLA avalia. */
    @Query("SELECT t.id FROM Ticket t WHERE t.status IN :statuses AND t.slaPausedAt IS NULL")
    List<String> findIdsForSlaWatch(@Param("statuses") List<Ticket.TicketStatus> statuses);

    @Query("SELECT t FROM Ticket t WHERE t.tenantId = :tenantId AND (t.ticketNumber LIKE :search OR t.title LIKE :search OR t.description LIKE :search)")
    List<Ticket> searchTickets(@Param("tenantId") String tenantId, @Param("search") String search);
}