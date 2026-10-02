package com.nexusops.ticketing.repository;

import com.nexusops.ticketing.domain.TicketCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketCycleRepository extends JpaRepository<TicketCycle, String> {

    List<TicketCycle> findByTicketIdOrderByCycleNoAsc(String ticketId);

    Optional<TicketCycle> findByTicketIdAndCycleNo(String ticketId, int cycleNo);
}
