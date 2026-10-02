package com.nexusops.ticketing.service;

import com.nexusops.shared.directory.TicketDirectory;
import com.nexusops.ticketing.domain.Ticket;
import com.nexusops.ticketing.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TicketDirectoryService implements TicketDirectory {

    private final TicketRepository ticketRepository;

    @Override
    public Optional<TicketRef> find(String ticketId, String tenantId) {
        if (ticketId == null || tenantId == null) {
            return Optional.empty();
        }
        return ticketRepository.findByIdAndTenantId(ticketId, tenantId).map(TicketDirectoryService::toRef);
    }

    @Override
    public Map<String, TicketRef> findByIds(Collection<String> ticketIds, String tenantId) {
        Map<String, TicketRef> result = new LinkedHashMap<>();
        if (ticketIds == null || ticketIds.isEmpty() || tenantId == null) {
            return result;
        }
        for (Ticket ticket : ticketRepository.findByTenantIdAndIdIn(tenantId, ticketIds)) {
            result.put(ticket.getId(), toRef(ticket));
        }
        return result;
    }

    private static TicketRef toRef(Ticket ticket) {
        return new TicketRef(ticket.getId(), ticket.getTicketNumber(), ticket.getTitle(), ticket.getStatus().name());
    }
}
