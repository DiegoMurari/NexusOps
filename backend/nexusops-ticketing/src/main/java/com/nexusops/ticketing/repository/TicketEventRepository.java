package com.nexusops.ticketing.repository;

import com.nexusops.ticketing.domain.TicketEvent;
import com.nexusops.ticketing.domain.TicketEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketEventRepository extends JpaRepository<TicketEvent, String> {

    List<TicketEvent> findByTicketIdOrderBySeqAsc(String ticketId);

    List<TicketEvent> findByTicketIdAndVisibilityOrderBySeqAsc(String ticketId, TicketEvent.Visibility visibility);

    @Query("SELECT COALESCE(MAX(e.seq), 0) + 1 FROM TicketEvent e WHERE e.ticketId = :ticketId")
    int nextSeq(@Param("ticketId") String ticketId);

    boolean existsByTicketId(String ticketId);

    boolean existsByTicketIdAndCycleNoAndEventType(String ticketId, int cycleNo, TicketEventType eventType);
}
