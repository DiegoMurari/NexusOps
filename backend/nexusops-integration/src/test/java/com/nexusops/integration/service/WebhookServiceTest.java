package com.nexusops.integration.service;

import com.nexusops.integration.domain.Webhook;
import com.nexusops.integration.dto.CreateWebhookRequest;
import com.nexusops.integration.dto.UpdateWebhookRequest;
import com.nexusops.integration.repository.WebhookRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    private static final String TENANT = "tenant-1";
    private static final String USER = "user@example.com";

    @Mock
    private WebhookRepository repository;

    private WebhookService service;

    @BeforeEach
    void setUp() {
        service = new WebhookService(repository);
    }

    @Test
    void create_neverExposesSecret_andNormalizesEvents() {
        when(repository.save(any(Webhook.class))).thenAnswer(inv -> inv.getArgument(0));

        var dto = service.create(CreateWebhookRequest.builder().name(" Alerts ").targetUrl("https://hooks.example.com/a")
            .secret("s3cr3t").events(Set.of("Ticket.Created", "ticket.closed")).timeoutSeconds(10).build(), TENANT, USER);

        assertThat(dto.getName()).isEqualTo("Alerts");
        assertThat(dto.isHasSecret()).isTrue();
        assertThat(dto.getEvents()).containsExactly("ticket.closed", "ticket.created");
        assertThat(dto.getTimeoutSeconds()).isEqualTo(10);
        assertThat(dto.toString()).doesNotContain("s3cr3t");
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
    void update_changesOnlyProvidedFields_andClearsSecretWhenBlank() {
        Webhook existing = Webhook.builder().id("w1").tenantId(TENANT).name("A")
            .targetUrl("https://hooks.example.com/a").secret("old").build();
        when(repository.findByIdAndTenantId("w1", TENANT)).thenReturn(Optional.of(existing));
        when(repository.save(any(Webhook.class))).thenAnswer(inv -> inv.getArgument(0));

        var dto = service.update("w1", UpdateWebhookRequest.builder().secret("").status(Webhook.WebhookStatus.INACTIVE)
            .build(), TENANT, USER);

        assertThat(dto.isHasSecret()).isFalse();
        assertThat(dto.getStatus()).isEqualTo(Webhook.WebhookStatus.INACTIVE);
        assertThat(dto.getName()).isEqualTo("A");
        assertThat(existing.getUpdatedBy()).isEqualTo(USER);
    }

    @Test
    void update_andDelete_areNotFoundOutsideTenant() {
        when(repository.findByIdAndTenantId("w1", TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("w1", new UpdateWebhookRequest(), TENANT, USER))
            .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.delete("w1", TENANT)).isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).delete(any());
    }
}
