package com.nexusops.integration.delivery;

import com.nexusops.integration.service.WebhookDeliveryService;
import com.nexusops.shared.event.DomainEvent;
import com.nexusops.ticketing.event.CommentAddedEvent;
import com.nexusops.ticketing.event.TicketAssignedEvent;
import com.nexusops.ticketing.event.TicketClosedEvent;
import com.nexusops.ticketing.event.TicketCreatedEvent;
import com.nexusops.ticketing.event.TicketResolvedEvent;
import com.nexusops.ticketing.event.TicketStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Transforma eventos de domínio em entregas de webhook. Roda depois do commit de quem gerou o evento (o
 * chamado já existe) e nunca o desfaz: qualquer falha aqui é só registrada. {@code fallbackExecution} cobre o
 * evento republicado fora de transação; o mesmo evento nunca vira duas entregas (idempotência por webhook).
 *
 * <p>Só vai o que é seguro para um destino externo: identificadores e metadados, nunca o texto de comentários,
 * notas internas ou da solução.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookEventListener {

    private final WebhookDeliveryService deliveryService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(TicketCreatedEvent e) {
        dispatch(WebhookEvents.TICKET_CREATED, e.getTenantId(), e, data(
            "ticketId", e.getAggregateId(), "ticketNumber", e.getTicketNumber(), "title", e.getTitle(),
            "type", e.getTicketType(), "priority", e.getPriority(), "categoryId", e.getCategoryId(),
            "assignee", e.getAssigneeId(), "requester", e.getReporterId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(TicketStatusChangedEvent e) {
        dispatch(WebhookEvents.TICKET_STATUS_CHANGED, e.getTenantId(), e, data(
            "ticketId", e.getAggregateId(), "ticketNumber", e.getTicketNumber(),
            "oldStatus", e.getOldStatus(), "newStatus", e.getNewStatus(), "by", e.getChangedBy()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(TicketAssignedEvent e) {
        dispatch(WebhookEvents.TICKET_ASSIGNED, e.getTenantId(), e, data(
            "ticketId", e.getAggregateId(), "ticketNumber", e.getTicketNumber(),
            "oldAssignee", e.getOldAssigneeId(), "newAssignee", e.getNewAssigneeId(), "by", e.getAssignedBy()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(TicketResolvedEvent e) {
        // a solução (texto livre) não sai
        dispatch(WebhookEvents.TICKET_RESOLVED, e.getTenantId(), e, data(
            "ticketId", e.getTicketId(), "ticketNumber", e.getTicketNumber(), "by", e.getResolvedBy()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(TicketClosedEvent e) {
        dispatch(WebhookEvents.TICKET_CLOSED, e.getTenantId(), e, data(
            "ticketId", e.getTicketId(), "ticketNumber", e.getTicketNumber(), "by", e.getClosedBy()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(CommentAddedEvent e) {
        if (!e.isPublicComment()) {
            return; // nota interna nunca sai do sistema
        }
        dispatch(WebhookEvents.TICKET_COMMENT_ADDED, e.getTenantId(), e, data(
            "ticketId", e.getTicketId(), "ticketNumber", e.getTicketNumber(), "by", e.getAuthorId()));
    }

    private void dispatch(String eventType, String tenantId, DomainEvent event, Map<String, Object> data) {
        if (tenantId == null) {
            return;
        }
        try {
            deliveryService.enqueue(tenantId, eventType, event.getEventId(), event.getOccurredAt(), data);
        } catch (RuntimeException ex) {
            // Nunca derruba quem gerou o evento; só a categoria do erro vai para o log (nada do conteúdo).
            log.warn("could not enqueue webhook deliveries for {}: {}", eventType, ex.getClass().getSimpleName());
        }
    }

    /** Pares chave/valor em ordem; valores nulos ficam de fora. */
    static Map<String, Object> data(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            if (keyValues[i + 1] != null) {
                map.put((String) keyValues[i], keyValues[i + 1]);
            }
        }
        return map;
    }
}
