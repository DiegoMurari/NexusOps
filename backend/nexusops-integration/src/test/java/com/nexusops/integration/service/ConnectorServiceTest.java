package com.nexusops.integration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusops.integration.domain.Connector;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnectorServiceTest {

    private static final String TENANT = "tenant-1";
    private static final String USER = "user@example.com";

    @Mock
    private ConnectorRepository repository;

    private ConnectorService service;

    @BeforeEach
    void setUp() {
        service = new ConnectorService(repository, new ObjectMapper());
    }

    @Test
    void create_storesSettings_andStaysDisconnected() {
        when(repository.save(any(Connector.class))).thenAnswer(inv -> inv.getArgument(0));

        var dto = service.create(CreateConnectorRequest.builder().name(" Jira ").type(Connector.ConnectorType.JIRA)
            .configuration(Map.of("baseUrl", "https://acme.atlassian.net", "projectKey", "OPS"))
            .syncScheduleCron("0 0 * * * *").build(), TENANT, USER);

        assertThat(dto.getName()).isEqualTo("Jira");
        assertThat(dto.getStatus()).isEqualTo(Connector.ConnectorStatus.DISCONNECTED);
        assertThat(dto.getConfiguration()).containsEntry("projectKey", "OPS").hasSize(2);
        assertThat(dto.getSyncScheduleCron()).isEqualTo("0 0 * * * *");
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
    }

    @Test
    void update_andDelete_areNotFoundOutsideTenant() {
        when(repository.findByIdAndTenantId("c1", TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("c1", new UpdateConnectorRequest(), TENANT, USER))
            .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.delete("c1", TENANT)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void countByType_groupsConnectors() {
        when(repository.findByTenantIdOrderByCreatedAtDesc(TENANT)).thenReturn(List.of(
            Connector.builder().type(Connector.ConnectorType.JIRA).build(),
            Connector.builder().type(Connector.ConnectorType.JIRA).build(),
            Connector.builder().type(Connector.ConnectorType.SLACK).build()));

        assertThat(service.countByType(TENANT)).containsEntry("JIRA", 2L).containsEntry("SLACK", 1L);
    }
}
