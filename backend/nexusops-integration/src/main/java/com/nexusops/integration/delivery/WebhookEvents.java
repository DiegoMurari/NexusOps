package com.nexusops.integration.delivery;

import com.nexusops.integration.dto.WebhookEventDto;

import java.util.List;

/**
 * Catálogo dos eventos que um webhook pode assinar. Só entram eventos que o sistema realmente publica; os
 * dados enviados nunca incluem o texto de comentários, notas internas nem a solução do chamado.
 */
public final class WebhookEvents {

    public static final String TICKET_CREATED = "ticket.created";
    public static final String TICKET_STATUS_CHANGED = "ticket.status_changed";
    public static final String TICKET_ASSIGNED = "ticket.assigned";
    public static final String TICKET_RESOLVED = "ticket.resolved";
    public static final String TICKET_CLOSED = "ticket.closed";
    public static final String TICKET_COMMENT_ADDED = "ticket.comment_added";

    private static final List<WebhookEventDto> CATALOG = List.of(
        new WebhookEventDto(TICKET_CREATED, "Um chamado foi aberto"),
        new WebhookEventDto(TICKET_STATUS_CHANGED, "A situação de um chamado mudou"),
        new WebhookEventDto(TICKET_ASSIGNED, "Um chamado foi atribuído ou reatribuído"),
        new WebhookEventDto(TICKET_RESOLVED, "Um chamado foi resolvido"),
        new WebhookEventDto(TICKET_CLOSED, "O solicitante aceitou a solução e o chamado foi encerrado"),
        new WebhookEventDto(TICKET_COMMENT_ADDED, "Um comentário público foi adicionado (sem o texto)"));

    private WebhookEvents() {
    }

    public static List<WebhookEventDto> catalog() {
        return CATALOG;
    }
}
