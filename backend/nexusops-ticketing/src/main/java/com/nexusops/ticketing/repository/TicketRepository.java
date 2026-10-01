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
public interface TicketRepository extends JpaRepository<Ticket, String> {

    Optional<Ticket> findByTicketNumber(String ticketNumber);

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

    @Query("SELECT t FROM Ticket t WHERE t.tenantId = :tenantId AND (t.ticketNumber LIKE :search OR t.title LIKE :search OR t.description LIKE :search)")
    List<Ticket> searchTickets(@Param("tenantId") String tenantId, @Param("search") String search);
}