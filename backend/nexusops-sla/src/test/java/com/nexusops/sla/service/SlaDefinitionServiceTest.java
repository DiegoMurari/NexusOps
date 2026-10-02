package com.nexusops.sla.service;

import com.nexusops.shared.event.TransactionalEventPublisher;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import com.nexusops.sla.domain.SlaDefinition;
import com.nexusops.sla.dto.CreateSlaDefinitionRequest;
import com.nexusops.sla.dto.UpdateSlaDefinitionRequest;
import com.nexusops.sla.mapper.SlaDefinitionMapper;
import com.nexusops.sla.repository.BusinessCalendarRepository;
import com.nexusops.sla.repository.SlaDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlaDefinitionServiceTest {

    private static final String TENANT = "tenant-1";

    @Mock
    private SlaDefinitionRepository repository;
    @Mock
    private BusinessCalendarRepository calendars;
    @Mock
    private TransactionalEventPublisher publisher;

    private SlaDefinitionService service;

    @BeforeEach
    void setUp() {
        service = new SlaDefinitionService(repository, calendars, Mappers.getMapper(SlaDefinitionMapper.class), publisher);
    }

    private SlaDefinition definition(String id, String tenant) {
        return SlaDefinition.builder().id(id).name("Crítico").tenantId(tenant).active(true)
            .appliesToPriority("CRITICAL").appliesToType("INCIDENT")
            .responseTimeMinutes(15).resolutionTimeMinutes(120).version(1).build();
    }

    private SlaDefinition stored(String id, String tenant) {
        SlaDefinition d = definition(id, tenant);
        when(repository.findById(id)).thenReturn(Optional.of(d));
        return d;
    }

    private void saveReturnsArgument() {
        when(repository.save(any(SlaDefinition.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void create_usesTheCallersTenant_andTurnsBlankCriteriaIntoAny() {
        saveReturnsArgument();
        var request = CreateSlaDefinitionRequest.builder().name("Padrão").tenantId("attacker-tenant")
            .appliesToType(" ").appliesToPriority("HIGH").responseTimeMinutes(30).resolutionTimeMinutes(240).build();

        var created = service.createDefinition(request, "admin@example.com", TENANT);

        assertThat(created.getTenantId()).isEqualTo(TENANT);
        assertThat(created.getAppliesToType()).isNull();
        assertThat(created.getAppliesToPriority()).isEqualTo("HIGH");
        assertThat(created.getVersion()).isEqualTo(1);
    }

    @Test
    void create_refusesATargetThatMakesNoSense() {
        var noTarget = CreateSlaDefinitionRequest.builder().name("A").build();
        var inverted = CreateSlaDefinitionRequest.builder().name("B").responseTimeMinutes(120).resolutionTimeMinutes(60).build();
        var zero = CreateSlaDefinitionRequest.builder().name("C").resolutionTimeMinutes(0).build();

        assertThatThrownBy(() -> service.createDefinition(noTarget, "a", TENANT)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.createDefinition(inverted, "a", TENANT)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.createDefinition(zero, "a", TENANT)).isInstanceOf(ValidationException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void aDefinitionOfAnotherTenant_isInvisibleAndImmutable() {
        stored("foreign", "tenant-2");

        assertThat(service.findById("foreign", TENANT)).isEmpty();
        assertThatThrownBy(() -> service.updateDefinition("foreign", TENANT, UpdateSlaDefinitionRequest.builder().name("x").build(), "a"))
            .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.deleteDefinition("foreign", TENANT)).isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).delete(any());
    }

    @Test
    void update_changesTargetsAndBumpsTheRevision() {
        stored("d1", TENANT);
        saveReturnsArgument();

        var updated = service.updateDefinition("d1", TENANT,
            UpdateSlaDefinitionRequest.builder().responseTimeMinutes(10).resolutionTimeMinutes(60).build(), "a");

        assertThat(updated.getResponseTimeMinutes()).isEqualTo(10);
        assertThat(updated.getResolutionTimeMinutes()).isEqualTo(60);
        assertThat(updated.getVersion()).isEqualTo(2);
    }

    @Test
    void update_aBlankCriterionClears_aNullKeeps() {
        stored("d1", TENANT);
        saveReturnsArgument();

        var updated = service.updateDefinition("d1", TENANT, UpdateSlaDefinitionRequest.builder().appliesToPriority("").build(), "a");

        assertThat(updated.getAppliesToPriority()).isNull();
        assertThat(updated.getAppliesToType()).isEqualTo("INCIDENT");
        assertThat(updated.getVersion()).isEqualTo(2);
    }

    @Test
    void update_renamingOnlyDoesNotCountAsANewRevision() {
        stored("d1", TENANT);
        when(repository.findByTenantIdAndName(TENANT, "Novo nome")).thenReturn(Optional.empty());
        saveReturnsArgument();

        var updated = service.updateDefinition("d1", TENANT, UpdateSlaDefinitionRequest.builder().name("Novo nome").build(), "a");

        assertThat(updated.getName()).isEqualTo("Novo nome");
        assertThat(updated.getVersion()).isEqualTo(1);
    }

    @Test
    void update_refusesANameAlreadyTakenInTheTenant_andANonsensicalTarget() {
        stored("d1", TENANT);
        when(repository.findByTenantIdAndName(TENANT, "Outro")).thenReturn(Optional.of(definition("d2", TENANT)));

        assertThatThrownBy(() -> service.updateDefinition("d1", TENANT, UpdateSlaDefinitionRequest.builder().name("Outro").build(), "a"))
            .isInstanceOf(ValidationException.class).hasMessageContaining("already exists");
        assertThatThrownBy(() -> service.updateDefinition("d1", TENANT, UpdateSlaDefinitionRequest.builder().resolutionTimeMinutes(5).build(), "a"))
            .isInstanceOf(ValidationException.class).hasMessageContaining("shorter");
        verify(repository, never()).save(any());
    }

    @Test
    void update_withoutRealChange_savesNothing() {
        stored("d1", TENANT);

        var same = service.updateDefinition("d1", TENANT,
            UpdateSlaDefinitionRequest.builder().appliesToPriority("CRITICAL").responseTimeMinutes(15).build(), "a");

        assertThat(same.getVersion()).isEqualTo(1);
        verify(repository, never()).save(any());
    }

    @Test
    void deactivating_countsAsMaterial() {
        stored("d1", TENANT);
        saveReturnsArgument();

        var off = service.updateDefinition("d1", TENANT, UpdateSlaDefinitionRequest.builder().active(false).build(), "a");

        assertThat(off.isActive()).isFalse();
        assertThat(off.getVersion()).isEqualTo(2);
    }
}
