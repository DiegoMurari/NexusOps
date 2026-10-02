package com.nexusops.integration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusops.integration.domain.Connector;
import com.nexusops.integration.domain.IntegrationLog;
import com.nexusops.integration.dto.CreateConnectorRequest;
import com.nexusops.integration.dto.UpdateConnectorRequest;
import com.nexusops.integration.repository.ConnectorRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnectorServiceTest {

    private static final String TENANT = "tenant-1";
    private static final String USER = "user@example.com";
    private static final String BASE_URL = "https://acme.atlassian.net";

    @Mock
    private ConnectorRepository repository;
    @Mock
    private IntegrationLogService logService;
    @Mock
    private SafeHttpSender sender;

    private ConnectorService service;

    @BeforeEach
    void setUp() {
        service = new ConnectorService(repository, new ObjectMapper(), logService, sender);
    }

    private Connector jira() {
        return Connector.builder().id("c1").tenantId(TENANT).name("Jira").type(Connector.ConnectorType.JIRA)
            .configuration("{\"baseUrl\":\"" + BASE_URL + "\"}").build();
    }

    @Test
    void create_storesSettings_staysDisconnected_startsEnabled_andLogs() {
        when(repository.save(any(Connector.class))).thenAnswer(inv -> {
            Connector c = inv.getArgument(0);
            c.setId("c1");
            return c;
        });

        var dto = service.create(CreateConnectorRequest.builder().name(" Jira ").type(Connector.ConnectorType.JIRA)
            .configuration(Map.of("baseUrl", BASE_URL, "projectKey", "OPS"))
            .syncScheduleCron("0 0 * * * *").build(), TENANT, USER);

        assertThat(dto.getName()).isEqualTo("Jira");
        assertThat(dto.getStatus()).isEqualTo(Connector.ConnectorStatus.DISCONNECTED);
        assertThat(dto.isEnabled()).isTrue();
        assertThat(dto.getConfiguration()).containsEntry("projectKey", "OPS").hasSize(2);
        assertThat(dto.getSyncScheduleCron()).isEqualTo("0 0 * * * *");
        verify(logService).recordChange(TENANT, IntegrationLog.Kind.CONNECTOR, "c1", "Jira",
            IntegrationLog.Event.CREATED, USER);
    }

    @Test
    void create_rejectsCredentialLikeKeys() {
        for (String key : List.of("apiToken", "client_secret", "password", "API-KEY", "Authorization")) {
            assertThatThrownBy(() -> service.create(CreateConnectorRequest.builder().name("A")
                .type(Connector.ConnectorType.SLACK).configuration(Map.of(key, "x")).build(), TENANT, USER))
                .as(key).isInstanceOf(ValidationException.class);
        }
        verify(repository, never()).save(any());
    }

    @Test
    void create_rejectsInvalidCron() {
        assertThatThrownBy(() -> service.create(CreateConnectorRequest.builder().name("A")
            .type(Connector.ConnectorType.TEAMS).syncScheduleCron("every day").build(), TENANT, USER))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void update_clearsCronWhenBlank_andKeepsOtherFields() {
        Connector existing = Connector.builder().id("c1").tenantId(TENANT).name("A").type(Connector.ConnectorType.SLACK)
            .syncScheduleCron("0 0 * * * *").build();
        when(repository.findByIdAndTenantId("c1", TENANT)).thenReturn(Optional.of(existing));
        when(repository.save(any(Connector.class))).thenAnswer(inv -> inv.getArgument(0));

        var dto = service.update("c1", UpdateConnectorRequest.builder().syncScheduleCron("").build(), TENANT, USER);

        assertThat(dto.getSyncScheduleCron()).isNull();
        assertThat(dto.getName()).isEqualTo("A");
        assertThat(dto.getConfiguration()).isEmpty();
        assertThat(dto.isEnabled()).isTrue();
        verify(logService).recordChange(TENANT, IntegrationLog.Kind.CONNECTOR, "c1", "A", IntegrationLog.Event.UPDATED, USER);
    }

    @Test
    void update_togglesEnabled_andLogsOnlyWhenItActuallyChanges() {
        Connector existing = jira();
        when(repository.findByIdAndTenantId("c1", TENANT)).thenReturn(Optional.of(existing));
        when(repository.save(any(Connector.class))).thenAnswer(inv -> inv.getArgument(0));

        var off = service.update("c1", UpdateConnectorRequest.builder().enabled(false).build(), TENANT, USER);
        assertThat(off.isEnabled()).isFalse();
        assertThat(off.getConfiguration()).containsEntry("baseUrl", BASE_URL);
        verify(logService).recordChange(TENANT, IntegrationLog.Kind.CONNECTOR, "c1", "Jira",
            IntegrationLog.Event.DISABLED, USER);

        service.update("c1", UpdateConnectorRequest.builder().enabled(false).build(), TENANT, USER);
        verify(logService, org.mockito.Mockito.times(1))
            .recordChange(any(), any(), any(), any(), any(IntegrationLog.Event.class), any());

        service.update("c1", UpdateConnectorRequest.builder().enabled(true).build(), TENANT, USER);
        verify(logService).recordChange(TENANT, IntegrationLog.Kind.CONNECTOR, "c1", "Jira",
            IntegrationLog.Event.ENABLED, USER);
    }

    @Test
    void update_andDelete_areNotFoundOutsideTenant() {
        when(repository.findByIdAndTenantId("c1", TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("c1", new UpdateConnectorRequest(), TENANT, USER))
            .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.delete("c1", TENANT, USER)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.check("c1", TENANT, USER)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(logService, sender);
    }

    @Test
    void delete_logsBeforeRemoving() {
        Connector existing = jira();
        when(repository.findByIdAndTenantId("c1", TENANT)).thenReturn(Optional.of(existing));

        service.delete("c1", TENANT, USER);

        verify(logService).recordChange(TENANT, IntegrationLog.Kind.CONNECTOR, "c1", "Jira",
            IntegrationLog.Event.DELETED, USER);
        verify(repository).delete(existing);
    }

    @Test
    void check_recordsSuccess_whenTheAddressResponds_evenWith401() {
        when(repository.findByIdAndTenantId("c1", TENANT)).thenReturn(Optional.of(jira()));
        when(sender.get(BASE_URL, 10)).thenReturn(new SafeHttpSender.Result(true, 401, 80, null));

        var result = service.check("c1", TENANT, USER);

        assertThat(result.getOutcome()).isEqualTo(IntegrationLog.Outcome.SUCCESS);
        assertThat(result.getMessage()).isEqualTo("HTTP 401");
        verify(repository).recordCheck(eq("c1"), eq(TENANT), any(Instant.class), eq("SUCCESS"), eq("HTTP 401"));
        verify(logService).record(eq(TENANT), eq(IntegrationLog.Kind.CONNECTOR), eq("c1"), eq("Jira"),
            eq(IntegrationLog.Event.CHECK), eq(IntegrationLog.Outcome.SUCCESS), eq(401), eq(80L), eq("HTTP 401"), eq(USER));
    }

    @Test
    void check_recordsFailure_forServerErrorsAndForNoResponse() {
        when(repository.findByIdAndTenantId("c1", TENANT)).thenReturn(Optional.of(jira()));
        when(sender.get(BASE_URL, 10))
            .thenReturn(new SafeHttpSender.Result(true, 503, 80, null))
            .thenReturn(new SafeHttpSender.Result(false, null, 5, "DNS_FAILURE"));

        assertThat(service.check("c1", TENANT, USER).getOutcome()).isEqualTo(IntegrationLog.Outcome.FAILURE);
        assertThat(service.check("c1", TENANT, USER).getMessage()).isEqualTo("DNS_FAILURE");
        verify(repository).recordCheck(eq("c1"), eq(TENANT), any(Instant.class), eq("FAILURE"), eq("HTTP 503"));
        verify(repository).recordCheck(eq("c1"), eq(TENANT), any(Instant.class), eq("FAILURE"), eq("DNS_FAILURE"));
    }

    @Test
    void check_refusesDisabledConnectorsAndConnectorsWithoutBaseUrl() {
        Connector disabled = jira();
        disabled.setEnabled(false);
        Connector slack = Connector.builder().id("c2").tenantId(TENANT).name("Slack")
            .type(Connector.ConnectorType.SLACK).configuration("{\"channel\":\"#ops\"}").build();
        when(repository.findByIdAndTenantId("c1", TENANT)).thenReturn(Optional.of(disabled));
        when(repository.findByIdAndTenantId("c2", TENANT)).thenReturn(Optional.of(slack));

        assertThatThrownBy(() -> service.check("c1", TENANT, USER)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.check("c2", TENANT, USER)).isInstanceOf(ValidationException.class);
        verifyNoInteractions(sender, logService);
    }

    @Test
    void countByType_groupsConnectors() {
        when(repository.findByTenantIdOrderByCreatedAtDesc(TENANT)).thenReturn(List.of(
            Connector.builder().type(Connector.ConnectorType.JIRA).build(),
            Connector.builder().type(Connector.ConnectorType.JIRA).build(),
            Connector.builder().type(Connector.ConnectorType.SLACK).build()));

        assertThat(service.countByType(TENANT)).containsEntry("JIRA", 2L).containsEntry("SLACK", 1L);
    }

    @Test
    void countDisabledAndFailing_areIndependent() {
        Connector off = Connector.builder().type(Connector.ConnectorType.JIRA).enabled(false).build();
        Connector failing = Connector.builder().type(Connector.ConnectorType.SLACK).build();
        failing.setLastCheckStatus("FAILURE");
        when(repository.findByTenantIdOrderByCreatedAtDesc(TENANT)).thenReturn(
            List.of(off, failing, Connector.builder().type(Connector.ConnectorType.TEAMS).build()));

        assertThat(service.countDisabled(TENANT)).isEqualTo(1);
        assertThat(service.countFailing(TENANT)).isEqualTo(1);
    }
}
