package com.nexusops.ticketing.domain;

/**
 * Tipos de evento da timeline do chamado. Eventos que revelam a estrutura interna de filas e
 * roteamento são INTERNOS: o solicitante não vê a estrutura de filas.
 */
public enum TicketEventType {
    CREATED(TicketEvent.Visibility.PUBLIC),
    ROUTED(TicketEvent.Visibility.INTERNAL),
    QUEUE_CHANGED(TicketEvent.Visibility.INTERNAL),
    ASSIGNED(TicketEvent.Visibility.PUBLIC),
    REASSIGNED(TicketEvent.Visibility.PUBLIC),
    UNASSIGNED(TicketEvent.Visibility.PUBLIC),
    TAKEN(TicketEvent.Visibility.PUBLIC),
    HELD(TicketEvent.Visibility.PUBLIC),
    INFO_REQUESTED(TicketEvent.Visibility.PUBLIC),
    RESUMED(TicketEvent.Visibility.PUBLIC),
    REQUESTER_REPLIED(TicketEvent.Visibility.PUBLIC),
    COMMENT_ADDED(TicketEvent.Visibility.PUBLIC),
    INTERNAL_NOTE_ADDED(TicketEvent.Visibility.INTERNAL),
    EVIDENCE_ADDED(TicketEvent.Visibility.PUBLIC),
    EVIDENCE_REMOVED(TicketEvent.Visibility.PUBLIC),
    PRIORITY_CHANGED(TicketEvent.Visibility.PUBLIC),
    TICKET_UPDATED(TicketEvent.Visibility.PUBLIC),
    RESOLVED(TicketEvent.Visibility.PUBLIC),
    ACCEPTED(TicketEvent.Visibility.PUBLIC),
    AUTO_ACCEPTED(TicketEvent.Visibility.PUBLIC),
    CONTESTED(TicketEvent.Visibility.PUBLIC),
    REOPENED(TicketEvent.Visibility.PUBLIC),
    SLA_WARNED(TicketEvent.Visibility.INTERNAL),
    SLA_BREACHED(TicketEvent.Visibility.INTERNAL);

    private final TicketEvent.Visibility defaultVisibility;

    TicketEventType(TicketEvent.Visibility defaultVisibility) {
        this.defaultVisibility = defaultVisibility;
    }

    public TicketEvent.Visibility defaultVisibility() {
        return defaultVisibility;
    }
}
