package com.nexusops.integration.service;

import com.nexusops.integration.domain.IntegrationLog;
import com.nexusops.integration.domain.Webhook;
import com.nexusops.integration.dto.CreateWebhookRequest;
import com.nexusops.integration.dto.UpdateWebhookRequest;
import com.nexusops.integration.repository.WebhookRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class WebhookServiceTest {

    private static final String TENANT = "tenant-1";
    private static final String USER = "user@example.com";
    private static final String URL = "https://hooks.example.com/a";

    @Mock
    private WebhookRepository repository;
    @Mock
    private IntegrationLogService logService;
    @Mock
    private SafeHttpSender sender;

    private WebhookService service;

    @BeforeEach
    void setUp() {
        service = new WebhookService(repository, logService, sender);
    }

    private Webhook existing() {
        return Webhook.builder().id("w1").tenantId(TENANT).name("A").targetUrl(URL).secret("s3cr3t")
            .timeoutSeconds(10).build();
    }

    @Test
    void create_neverExposesSecret_normalizesEvents_andLogsCreation() {
        when(repository.save(any(Webhook.class))).thenAnswer(inv -> {
            Webhook w = inv.getArgument(0);
            w.setId("w1");
            return w;
        });

        var dto = service.create(CreateWebhookRequest.builder().name(" Alerts ").targetUrl("https://hooks.example.com/a")
            .secret("s3cr3t").events(Set.of("Ticket.Created", "ticket.closed")).timeoutSeconds(10).build(), TENANT, USER);

        assertThat(dto.getName()).isEqualTo("Alerts");
        assertThat(dto.isHasSecret()).isTrue();
        assertThat(dto.getEvents()).containsExactly("ticket.closed", "ticket.created");
        assertThat(dto.getTimeoutSeconds()).isEqualTo(10);
        assertThat(dto.toString()).doesNotContain("s3cr3t");
        verify(logService).recordChange(TENANT, IntegrationLog.Kind.WEBHOOK, "w1", "Alerts",
            IntegrationLog.Event.CREATED, USER);
    }

    @Test
    void create_usesDefaultsWhenOptionalFieldsMissing() {
        when(repository.save(any(Webhook.class))).thenAnswer(inv -> inv.getArgument(0));

        var dto = service.create(CreateWebhookRequest.builder().name("A").targetUrl("https://hooks.example.com/a").build(),
            TENANT, USER);

        assertThat(dto.getStatus()).isEqualTo(Webhook.WebhookStatus.ACTIVE);
        assertThat(dto.getTimeoutSeconds()).isEqualTo(30);
        assertThat(dto.isHasSecret()).isFalse();
        assertThat(dto.getEvents()).isEmpty();
        assertThat(dto.getLastDeliveryStatus()).isNull();
    }

    @Test
    void create_rejectsUnsafeUrlAndBadEventName() {
        assertThatThrownBy(() -> service.create(CreateWebhookRequest.builder().name("A")
            .targetUrl("https://169.254.169.254/x").build(), TENANT, USER)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.create(CreateWebhookRequest.builder().name("A")
            .targetUrl("https://hooks.example.com/a").events(Set.of("Bad Event!")).build(), TENANT, USER))
            .isInstanceOf(ValidationException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void update_changesOnlyProvidedFields_clearsSecretWhenBlank_andLogsBothEvents() {
        Webhook existing = existing();
        when(repository.findByIdAndTenantId("w1", TENANT)).thenReturn(Optional.of(existing));
        when(repository.save(any(Webhook.class))).thenAnswer(inv -> inv.getArgument(0));

        var dto = service.update("w1", UpdateWebhookRequest.builder().secret("").status(Webhook.WebhookStatus.INACTIVE)
            .build(), TENANT, USER);

        assertThat(dto.isHasSecret()).isFalse();
        assertThat(dto.getStatus()).isEqualTo(Webhook.WebhookStatus.INACTIVE);
        assertThat(dto.getName()).isEqualTo("A");
        assertThat(existing.getUpdatedBy()).isEqualTo(USER);
        verify(logService).recordChange(TENANT, IntegrationLog.Kind.WEBHOOK, "w1", "A", IntegrationLog.Event.UPDATED, USER);
        verify(logService).recordChange(TENANT, IntegrationLog.Kind.WEBHOOK, "w1", "A", IntegrationLog.Event.DISABLED, USER);
    }

    @Test
    void update_enablingLogsEnabled_andSameStatusLogsNothing() {
        Webhook inactive = existing();
        inactive.setStatus(Webhook.WebhookStatus.INACTIVE);
        when(repository.findByIdAndTenantId("w1", TENANT)).thenReturn(Optional.of(inactive));
        when(repository.save(any(Webhook.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update("w1", UpdateWebhookRequest.builder().status(Webhook.WebhookStatus.ACTIVE).build(), TENANT, USER);
        verify(logService).recordChange(TENANT, IntegrationLog.Kind.WEBHOOK, "w1", "A", IntegrationLog.Event.ENABLED, USER);

        service.update("w1", UpdateWebhookRequest.builder().status(Webhook.WebhookStatus.ACTIVE).build(), TENANT, USER);
        verify(logService, org.mockito.Mockito.times(1))
            .recordChange(any(), any(), any(), any(), any(IntegrationLog.Event.class), any());
    }

    @Test
    void update_rejectsSystemOwnedStatuses() {
        when(repository.findByIdAndTenantId("w1", TENANT)).thenReturn(Optional.of(existing()));

        assertThatThrownBy(() -> service.update("w1",
            UpdateWebhookRequest.builder().status(Webhook.WebhookStatus.FAILED).build(), TENANT, USER))
            .isInstanceOf(ValidationException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void update_andDelete_areNotFoundOutsideTenant() {
        when(repository.findByIdAndTenantId("w1", TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("w1", new UpdateWebhookRequest(), TENANT, USER))
            .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.delete("w1", TENANT, USER)).isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).delete(any());
        verifyNoInteractions(logService);
    }

    @Test
    void delete_logsBeforeRemoving_keepingTheName() {
        Webhook existing = existing();
        when(repository.findByIdAndTenantId("w1", TENANT)).thenReturn(Optional.of(existing));

        service.delete("w1", TENANT, USER);

        verify(logService).recordChange(TENANT, IntegrationLog.Kind.WEBHOOK, "w1", "A", IntegrationLog.Event.DELETED, USER);
        verify(repository).delete(existing);
    }

    @Test
    void sendTest_signsThePayload_recordsSuccess_andNeverLogsTheSecret() {
        when(repository.findByIdAndTenantId("w1", TENANT)).thenReturn(Optional.of(existing()));
        when(sender.post(eq(URL), anyString(), anyMap(), eq(10)))
            .thenReturn(new SafeHttpSender.Result(true, 204, 120, null));

        var result = service.sendTest("w1", TENANT, USER);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(sender).post(eq(URL), body.capture(), headers.capture(), eq(10));
        assertThat(headers.getValue().get("X-NexusOps-Signature")).isEqualTo(
            "sha256=" + WebhookService.sign("s3cr3t", headers.getValue().get("X-NexusOps-Timestamp"), body.getValue()));
        assertThat(headers.getValue()).containsEntry("X-NexusOps-Event", "ping");
        assertThat(body.getValue()).contains("\"event\":\"ping\"").doesNotContain("s3cr3t");

        assertThat(result.getOutcome()).isEqualTo(IntegrationLog.Outcome.SUCCESS);
        assertThat(result.getHttpStatus()).isEqualTo(204);
        assertThat(result.getMessage()).isEqualTo("HTTP 204");
        verify(repository).recordDelivery(eq("w1"), eq(TENANT), any(Instant.class), eq("SUCCESS"), eq(204), isNull());
        verify(logService).record(eq(TENANT), eq(IntegrationLog.Kind.WEBHOOK), eq("w1"), eq("A"),
            eq(IntegrationLog.Event.TEST), eq(IntegrationLog.Outcome.SUCCESS), eq(204), eq(120L), eq("HTTP 204"), eq(USER));
    }

    @Test
    void sendTest_withoutSecret_sendsNoSignature() {
        Webhook noSecret = existing();
        noSecret.setSecret(null);
        when(repository.findByIdAndTenantId("w1", TENANT)).thenReturn(Optional.of(noSecret));
        when(sender.post(eq(URL), anyString(), anyMap(), eq(10)))
            .thenReturn(new SafeHttpSender.Result(true, 200, 5, null));

        service.sendTest("w1", TENANT, USER);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(sender).post(eq(URL), anyString(), headers.capture(), eq(10));
        assertThat(headers.getValue()).doesNotContainKey("X-NexusOps-Signature");
    }

    @Test
    void sendTest_recordsFailureCategory_whenThereIsNoResponse() {
        when(repository.findByIdAndTenantId("w1", TENANT)).thenReturn(Optional.of(existing()));
        when(sender.post(eq(URL), anyString(), anyMap(), eq(10)))
            .thenReturn(new SafeHttpSender.Result(false, null, 10_000, "TIMEOUT"));

        var result = service.sendTest("w1", TENANT, USER);

        assertThat(result.getOutcome()).isEqualTo(IntegrationLog.Outcome.FAILURE);
        assertThat(result.getMessage()).isEqualTo("TIMEOUT");
        verify(repository).recordDelivery(eq("w1"), eq(TENANT), any(Instant.class), eq("FAILURE"), isNull(), eq("TIMEOUT"));
    }

    @Test
    void sendTest_treatsNon2xxAsFailure_withTheHttpCode() {
        when(repository.findByIdAndTenantId("w1", TENANT)).thenReturn(Optional.of(existing()));
        when(sender.post(eq(URL), anyString(), anyMap(), eq(10)))
            .thenReturn(new SafeHttpSender.Result(true, 500, 40, null));

        var result = service.sendTest("w1", TENANT, USER);

        assertThat(result.getOutcome()).isEqualTo(IntegrationLog.Outcome.FAILURE);
        verify(repository).recordDelivery(eq("w1"), eq(TENANT), any(Instant.class), eq("FAILURE"), eq(500), eq("HTTP_500"));
    }

    @Test
    void sendTest_isRateLimitedPerWebhook() {
        Webhook justTested = existing();
        justTested.setLastDeliveryAt(Instant.now());
        when(repository.findByIdAndTenantId("w1", TENANT)).thenReturn(Optional.of(justTested));

        assertThatThrownBy(() -> service.sendTest("w1", TENANT, USER)).isInstanceOf(ValidationException.class);
        verifyNoInteractions(sender);
    }

    @Test
    void sendTest_isNotFoundOutsideTheTenant_andSendsNothing() {
        when(repository.findByIdAndTenantId("w1", TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendTest("w1", TENANT, USER)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(sender);
    }

    @Test
    void countFailing_countsOnlyWebhooksWhoseLastDeliveryFailed() {
        Webhook failed = existing();
        failed.setLastDeliveryStatus("FAILURE");
        Webhook ok = existing();
        ok.setLastDeliveryStatus("SUCCESS");
        when(repository.findByTenantIdOrderByCreatedAtDesc(TENANT)).thenReturn(List.of(failed, ok, existing()));

        assertThat(service.countFailing(TENANT)).isEqualTo(1);
    }
}
