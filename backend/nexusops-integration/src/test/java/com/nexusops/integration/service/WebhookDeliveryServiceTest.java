package com.nexusops.integration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusops.integration.domain.IntegrationLog;
import com.nexusops.integration.domain.Webhook;
import com.nexusops.integration.domain.WebhookDelivery;
import com.nexusops.integration.repository.WebhookDeliveryRepository;
import com.nexusops.integration.repository.WebhookRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookDeliveryServiceTest {

    private static final String TENANT = "tenant-1";
    private static final String URL = "https://hooks.example.com/in";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private WebhookRepository webhookRepository;
    @Mock
    private WebhookDeliveryRepository deliveryRepository;
    @Mock
    private IntegrationLogService logService;
    @Mock
    private SafeHttpSender sender;

    private WebhookDeliveryService service;

    @BeforeEach
    void setUp() {
        service = new WebhookDeliveryService(webhookRepository, deliveryRepository, logService, sender, MAPPER);
    }

    private Webhook webhook(String id, Webhook.WebhookStatus status, String... events) {
        return Webhook.builder().id(id).tenantId(TENANT).name("Hook " + id).targetUrl(URL).secret("s3cr3t")
            .timeoutSeconds(10).status(status).events(Set.of(events)).build();
    }

    private WebhookDelivery delivery(int attempts) {
        return WebhookDelivery.builder().id("d1").tenantId(TENANT).webhookId("w1").eventType("ticket.created")
            .eventId("e1").payload("{\"event\":\"ticket.created\"}").attempts(attempts)
            .status(WebhookDelivery.Status.SENDING).nextAttemptAt(Instant.now()).build();
    }

    private void givenWebhook() {
        when(webhookRepository.findByIdAndTenantId("w1", TENANT))
            .thenReturn(Optional.of(webhook("w1", Webhook.WebhookStatus.ACTIVE, "ticket.created")));
    }

    @Test
    void enqueue_createsADeliveryOnlyForActiveWebhooksSubscribedToTheEvent() throws Exception {
        when(webhookRepository.findByTenantIdOrderByCreatedAtDesc(TENANT)).thenReturn(List.of(
            webhook("w1", Webhook.WebhookStatus.ACTIVE, "ticket.created"),
            webhook("w2", Webhook.WebhookStatus.ACTIVE, "ticket.closed"),
            webhook("w3", Webhook.WebhookStatus.INACTIVE, "ticket.created")));

        int created = service.enqueue(TENANT, "ticket.created", "evt-1", Instant.parse("2026-10-02T10:00:00Z"),
            Map.of("ticketNumber", "INC-1"));

        assertThat(created).isEqualTo(1);
        ArgumentCaptor<WebhookDelivery> saved = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveryRepository).save(saved.capture());
        WebhookDelivery d = saved.getValue();
        assertThat(d.getWebhookId()).isEqualTo("w1");
        assertThat(d.getTenantId()).isEqualTo(TENANT);
        assertThat(d.getStatus()).isEqualTo(WebhookDelivery.Status.PENDING);
        JsonNode body = MAPPER.readTree(d.getPayload());
        assertThat(body.get("id").asText()).isEqualTo(d.getId());
        assertThat(body.get("event").asText()).isEqualTo("ticket.created");
        assertThat(body.get("occurredAt").asText()).isEqualTo("2026-10-02T10:00:00Z");
        assertThat(body.get("data").get("ticketNumber").asText()).isEqualTo("INC-1");
        assertThat(d.getPayload()).doesNotContain("s3cr3t");
    }

    @Test
    void enqueue_isIdempotentPerWebhookAndEvent() {
        when(webhookRepository.findByTenantIdOrderByCreatedAtDesc(TENANT))
            .thenReturn(List.of(webhook("w1", Webhook.WebhookStatus.ACTIVE, "ticket.created")));
        when(deliveryRepository.existsByWebhookIdAndEventId("w1", "evt-1")).thenReturn(true);

        assertThat(service.enqueue(TENANT, "ticket.created", "evt-1", Instant.now(), Map.of())).isZero();
        verify(deliveryRepository, never()).save(any());
    }

    @Test
    void claimDue_marksTheBatchAsSendingWithALease() {
        WebhookDelivery due = delivery(0);
        due.setStatus(WebhookDelivery.Status.PENDING);
        Instant now = Instant.parse("2026-10-02T10:00:00Z");
        when(deliveryRepository.lockDue(now, WebhookDeliveryService.BATCH_SIZE)).thenReturn(List.of(due));

        List<WebhookDelivery> claimed = service.claimDue(now);

        assertThat(claimed).containsExactly(due);
        assertThat(due.getStatus()).isEqualTo(WebhookDelivery.Status.SENDING);
        assertThat(due.getNextAttemptAt()).isEqualTo(now.plus(WebhookDeliveryService.LEASE));
    }

    @Test
    void deliver_marksDeliveredAndSignsTheExactPayload() {
        givenWebhook();
        when(sender.post(eq(URL), anyString(), anyMap(), eq(10))).thenReturn(new SafeHttpSender.Result(true, 204, 80, null));
        WebhookDelivery d = delivery(0);

        service.deliver(d);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(sender).post(eq(URL), eq(d.getPayload()), headers.capture(), eq(10));
        assertThat(headers.getValue()).containsEntry("X-NexusOps-Event", "ticket.created")
            .containsEntry("X-NexusOps-Delivery", "d1");
        assertThat(headers.getValue().get("X-NexusOps-Signature")).isEqualTo("sha256="
            + WebhookService.sign("s3cr3t", headers.getValue().get("X-NexusOps-Timestamp"), d.getPayload()));
        assertThat(d.getStatus()).isEqualTo(WebhookDelivery.Status.DELIVERED);
        assertThat(d.getAttempts()).isEqualTo(1);
        assertThat(d.getDeliveredAt()).isNotNull();
        assertThat(d.getLastError()).isNull();
        verify(deliveryRepository).save(d);
        verify(webhookRepository).recordDelivery(eq("w1"), eq(TENANT), any(Instant.class), eq("SUCCESS"), eq(204), isNull());
        verify(logService).record(eq(TENANT), eq(IntegrationLog.Kind.WEBHOOK), eq("w1"), eq("Hook w1"),
            eq(IntegrationLog.Event.DELIVERY), eq(IntegrationLog.Outcome.SUCCESS), eq(204), eq(80L),
            eq("ticket.created · tentativa 1 · HTTP 204"), eq("system"));
    }

    @Test
    void deliver_retriesLaterWithGrowingBackoff_afterATransientFailure() {
        givenWebhook();
        when(sender.post(anyString(), anyString(), anyMap(), eq(10))).thenReturn(new SafeHttpSender.Result(true, 503, 30, null));
        WebhookDelivery d = delivery(2);

        Instant before = Instant.now();
        service.deliver(d);

        assertThat(d.getStatus()).isEqualTo(WebhookDelivery.Status.PENDING);
        assertThat(d.getAttempts()).isEqualTo(3);
        assertThat(d.getLastError()).isEqualTo("HTTP_503");
        assertThat(Duration.between(before, d.getNextAttemptAt())).isBetween(
            WebhookDeliveryService.BACKOFF.get(2).minusSeconds(5), WebhookDeliveryService.BACKOFF.get(2).plusSeconds(5));
        verify(webhookRepository).recordDelivery(eq("w1"), eq(TENANT), any(Instant.class), eq("FAILURE"), eq(503), eq("HTTP_503"));
    }

    @Test
    void deliver_givesUpAfterTheLastAttempt() {
        givenWebhook();
        when(sender.post(anyString(), anyString(), anyMap(), eq(10)))
            .thenReturn(new SafeHttpSender.Result(false, null, 10_000, "TIMEOUT"));
        WebhookDelivery d = delivery(WebhookDeliveryService.MAX_ATTEMPTS - 1);

        service.deliver(d);

        assertThat(d.getStatus()).isEqualTo(WebhookDelivery.Status.FAILED);
        assertThat(d.getAttempts()).isEqualTo(WebhookDeliveryService.MAX_ATTEMPTS);
        assertThat(d.getLastError()).isEqualTo("TIMEOUT");
        verify(logService).record(eq(TENANT), eq(IntegrationLog.Kind.WEBHOOK), eq("w1"), eq("Hook w1"),
            eq(IntegrationLog.Event.DELIVERY), eq(IntegrationLog.Outcome.FAILURE), isNull(), eq(10_000L),
            eq("ticket.created · tentativa 6 · TIMEOUT · desistiu"), eq("system"));
    }

    @Test
    void deliver_doesNotRetryPermanentErrors_butRetriesTimeoutsAndRateLimits() {
        givenWebhook();
        when(sender.post(anyString(), anyString(), anyMap(), eq(10)))
            .thenReturn(new SafeHttpSender.Result(true, 404, 10, null))
            .thenReturn(new SafeHttpSender.Result(true, 429, 10, null))
            .thenReturn(new SafeHttpSender.Result(true, 408, 10, null))
            .thenReturn(new SafeHttpSender.Result(false, null, 1, "BLOCKED_ADDRESS"));
        WebhookDelivery notFound = delivery(0);
        WebhookDelivery limited = delivery(0);
        WebhookDelivery timedOut = delivery(0);
        WebhookDelivery blocked = delivery(0);

        service.deliver(notFound);
        service.deliver(limited);
        service.deliver(timedOut);
        service.deliver(blocked);

        assertThat(notFound.getStatus()).isEqualTo(WebhookDelivery.Status.FAILED);
        assertThat(limited.getStatus()).isEqualTo(WebhookDelivery.Status.PENDING);
        assertThat(timedOut.getStatus()).isEqualTo(WebhookDelivery.Status.PENDING);
        assertThat(blocked.getStatus()).isEqualTo(WebhookDelivery.Status.FAILED);
    }

    @Test
    void deliver_cancelsWithoutSending_whenTheWebhookIsGoneOrDisabled() {
        when(webhookRepository.findByIdAndTenantId("w1", TENANT)).thenReturn(Optional.empty())
            .thenReturn(Optional.of(webhook("w1", Webhook.WebhookStatus.INACTIVE, "ticket.created")));
        WebhookDelivery removed = delivery(0);
        WebhookDelivery disabled = delivery(0);

        service.deliver(removed);
        service.deliver(disabled);

        assertThat(removed.getStatus()).isEqualTo(WebhookDelivery.Status.CANCELLED);
        assertThat(removed.getLastError()).isEqualTo("WEBHOOK_REMOVED");
        assertThat(disabled.getStatus()).isEqualTo(WebhookDelivery.Status.CANCELLED);
        assertThat(disabled.getLastError()).isEqualTo("WEBHOOK_DISABLED");
        verifyNoInteractions(sender, logService);
    }

    @Test
    void deliver_withoutSecret_sendsNoSignature() {
        Webhook noSecret = webhook("w1", Webhook.WebhookStatus.ACTIVE, "ticket.created");
        noSecret.setSecret(null);
        when(webhookRepository.findByIdAndTenantId("w1", TENANT)).thenReturn(Optional.of(noSecret));
        when(sender.post(anyString(), anyString(), anyMap(), eq(10))).thenReturn(new SafeHttpSender.Result(true, 200, 1, null));

        service.deliver(delivery(0));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(sender).post(anyString(), anyString(), headers.capture(), eq(10));
        assertThat(headers.getValue()).doesNotContainKey("X-NexusOps-Signature");
    }

    @Test
    void retry_requeuesOnlyFailedDeliveriesOfTheTenant() {
        WebhookDelivery failed = delivery(6);
        failed.setStatus(WebhookDelivery.Status.FAILED);
        failed.setLastError("TIMEOUT");
        when(deliveryRepository.findByIdAndTenantId("d1", TENANT)).thenReturn(Optional.of(failed));
        when(deliveryRepository.save(any(WebhookDelivery.class))).thenAnswer(inv -> inv.getArgument(0));

        var dto = service.retry("d1", TENANT);

        assertThat(dto.getStatus()).isEqualTo(WebhookDelivery.Status.PENDING);
        assertThat(dto.getAttempts()).isZero();
        assertThat(dto.getLastError()).isNull();
    }

    @Test
    void retry_rejectsDeliveriesThatAreNotFailed_andOtherTenants() {
        WebhookDelivery delivered = delivery(1);
        delivered.setStatus(WebhookDelivery.Status.DELIVERED);
        when(deliveryRepository.findByIdAndTenantId("d1", TENANT)).thenReturn(Optional.of(delivered));
        when(deliveryRepository.findByIdAndTenantId("d1", "other")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.retry("d1", TENANT)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.retry("d1", "other")).isInstanceOf(ResourceNotFoundException.class);
        verify(deliveryRepository, never()).save(any());
    }

    @Test
    void purge_removesOnlyOldFinishedDeliveriesAndOldLogs() {
        Instant now = Instant.parse("2026-10-02T10:00:00Z");

        service.purge(now);

        verify(deliveryRepository).deleteFinishedBefore(now.minus(WebhookDeliveryService.DELIVERY_RETENTION));
        verify(logService).purgeOlderThan(now.minus(WebhookDeliveryService.LOG_RETENTION));
    }

    @Test
    void isPermanent_classifiesResults() {
        assertThat(WebhookDeliveryService.isPermanent(new SafeHttpSender.Result(true, 400, 1, null))).isTrue();
        assertThat(WebhookDeliveryService.isPermanent(new SafeHttpSender.Result(true, 410, 1, null))).isTrue();
        assertThat(WebhookDeliveryService.isPermanent(new SafeHttpSender.Result(true, 408, 1, null))).isFalse();
        assertThat(WebhookDeliveryService.isPermanent(new SafeHttpSender.Result(true, 429, 1, null))).isFalse();
        assertThat(WebhookDeliveryService.isPermanent(new SafeHttpSender.Result(true, 500, 1, null))).isFalse();
        assertThat(WebhookDeliveryService.isPermanent(new SafeHttpSender.Result(false, null, 1, "TIMEOUT"))).isFalse();
        assertThat(WebhookDeliveryService.isPermanent(new SafeHttpSender.Result(false, null, 1, "INVALID_URL"))).isTrue();
    }
}
