package com.nexusops.integration.delivery;

import com.nexusops.integration.dto.WebhookEventDto;
import com.nexusops.integration.service.WebhookDeliveryService;
import com.nexusops.ticketing.event.CommentAddedEvent;
import com.nexusops.ticketing.event.TicketAssignedEvent;
import com.nexusops.ticketing.event.TicketClosedEvent;
import com.nexusops.ticketing.event.TicketCreatedEvent;
import com.nexusops.ticketing.event.TicketResolvedEvent;
import com.nexusops.ticketing.event.TicketStatusChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class WebhookEventListenerTest {

    private static final String TENANT = "tenant-1";

    @Mock
    private WebhookDeliveryService deliveryService;

    private WebhookEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new WebhookEventListener(deliveryService);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturedData(String eventType) {
        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(deliveryService).enqueue(eq(TENANT), eq(eventType), anyString(), any(), data.capture());
        return data.getValue();
    }

    @Test
    void ticketCreated_sendsMetadata_andSkipsNulls() {
        listener.on(new TicketCreatedEvent("t1", 1, "INC-1", "Impressora parada", "INCIDENT", "HIGH", null, null, "ana@x.com", TENANT));

        assertThat(capturedData("ticket.created")).containsEntry("ticketId", "t1")
            .containsEntry("ticketNumber", "INC-1").containsEntry("title", "Impressora parada")
            .containsEntry("priority", "HIGH").containsEntry("requester", "ana@x.com")
            .doesNotContainKeys("categoryId", "assignee");
    }

    @Test
    void statusChangedAndAssigned_carryTheTransition() {
        listener.on(new TicketStatusChangedEvent("t1", 2, "INC-1", "OPEN", "IN_PROGRESS", "bob@x.com", TENANT));
        listener.on(new TicketAssignedEvent("t1", 3, "INC-1", null, "bob@x.com", "lead@x.com", TENANT));

        assertThat(capturedData("ticket.status_changed")).containsEntry("oldStatus", "OPEN")
            .containsEntry("newStatus", "IN_PROGRESS").containsEntry("by", "bob@x.com");
        assertThat(capturedData("ticket.assigned")).containsEntry("newAssignee", "bob@x.com")
            .doesNotContainKey("oldAssignee");
    }

    @Test
    void resolvedAndClosed_neverCarryTheSolutionText() {
        listener.on(new TicketResolvedEvent("t1", 4, "t1", "INC-1", "Reiniciei o spooler (texto livre)", "bob@x.com", TENANT));
        listener.on(new TicketClosedEvent("t1", 5, "t1", "INC-1", "ana@x.com", TENANT));

        Map<String, Object> resolved = capturedData("ticket.resolved");
        assertThat(resolved).containsEntry("by", "bob@x.com");
        assertThat(resolved.values()).noneMatch(v -> String.valueOf(v).contains("spooler"));
        assertThat(capturedData("ticket.closed")).containsEntry("by", "ana@x.com");
    }

    @Test
    void internalNotesNeverLeave_publicCommentsDoButWithoutText() {
        listener.on(new CommentAddedEvent("c1", 1, "t1", "INC-1", "bob@x.com", false, TENANT));
        verifyNoInteractions(deliveryService);

        listener.on(new CommentAddedEvent("c2", 1, "t1", "INC-1", "bob@x.com", true, TENANT));
        assertThat(capturedData("ticket.comment_added")).containsEntry("by", "bob@x.com")
            .containsOnlyKeys("ticketId", "ticketNumber", "by");
    }

    @Test
    void eventsWithoutATenantAreIgnored() {
        listener.on(new TicketClosedEvent("t1", 1, "t1", "INC-1", "ana@x.com", null));

        verifyNoInteractions(deliveryService);
    }

    @Test
    void anEnqueueFailureNeverPropagatesToTheCaller() {
        doThrow(new IllegalStateException("db down")).when(deliveryService)
            .enqueue(anyString(), anyString(), anyString(), any(), anyMap());

        listener.on(new TicketClosedEvent("t1", 1, "t1", "INC-1", "ana@x.com", TENANT));
        // não lançou
    }

    @Test
    void theCatalogListsExactlyTheEventsTheListenerEmits() {
        List<String> catalog = WebhookEvents.catalog().stream().map(WebhookEventDto::getName).toList();

        assertThat(catalog).containsExactlyInAnyOrder("ticket.created", "ticket.status_changed", "ticket.assigned",
            "ticket.resolved", "ticket.closed", "ticket.comment_added");
    }
}
