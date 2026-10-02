package com.nexusops.ticketing.repository;

import com.nexusops.ticketing.domain.TicketResolution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketResolutionRepository extends JpaRepository<TicketResolution, String> {

    List<TicketResolution> findByTicketIdOrderByCycleNoAsc(String ticketId);

    Optional<TicketResolution> findByTicketIdAndCycleNo(String ticketId, int cycleNo);
}
