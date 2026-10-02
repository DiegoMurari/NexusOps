package com.nexusops.ticketing.workflow;

public class TicketTransitionException extends RuntimeException {

    public enum Reason {
        NOT_ALLOWED,
        ACTOR_NOT_PERMITTED
    }

    private final Reason reason;

    public TicketTransitionException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
