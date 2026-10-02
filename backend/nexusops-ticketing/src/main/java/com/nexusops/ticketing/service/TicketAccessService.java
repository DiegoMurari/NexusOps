package com.nexusops.ticketing.service;

import com.nexusops.shared.directory.UserDirectory;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.security.SecurityUtils;
import com.nexusops.ticketing.domain.Ticket;
import com.nexusops.ticketing.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quem pode ver e mexer em um chamado (ADR-013). A equipe enxerga os chamados do próprio tenant; o solicitante
 * só os que abriu. Chamado de outro tenant ou de outra pessoa responde como inexistente (404), para não revelar
 * que ele existe.
 */
@Service
@RequiredArgsConstructor
public class TicketAccessService {

    public static final String[] STAFF_ROLES = {"SUPER_ADMIN", "ADMIN", "MANAGER", "TEAM_LEAD", "AGENT"};

    private final TicketRepository ticketRepository;
    private final UserDirectory userDirectory;

    public boolean isStaff() {
        return SecurityUtils.hasAnyRole(STAFF_ROLES);
    }

    /** Carrega o chamado se o chamador puder vê-lo; do contrário, finge que não existe. */
    @Transactional(readOnly = true)
    public Ticket requireReadable(String ticketId, String tenantId, String principal) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .filter(t -> tenantId.equals(t.getTenantId()))
            .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
        if (isStaff() || isRequesterOf(ticket, tenantId, principal)) {
            return ticket;
        }
        throw new ResourceNotFoundException("Ticket", ticketId);
    }

    /** O solicitante é guardado pelo ID do usuário; o principal autenticado é o e-mail. */
    public boolean isRequesterOf(Ticket ticket, String tenantId, String principal) {
        if (ticket.getReporterId() == null || principal == null) {
            return false;
        }
        return userDirectory.findActiveByEmail(principal, tenantId)
            .map(u -> u.id().equals(ticket.getReporterId()))
            .orElse(false);
    }
}
