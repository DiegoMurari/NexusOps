package com.nexusops.integration.service;

import com.nexusops.integration.domain.IntegrationLog;
import com.nexusops.integration.repository.IntegrationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationLogServiceTest {

    private static final String TENANT = "tenant-1";

    @Mock
    private IntegrationLogRepository repository;

    private IntegrationLogService service;

    @BeforeEach
    void setUp() {
        service = new IntegrationLogService(repository);
    }

    @Test
    void record_savesTheFactWithinTheTenant_andTruncatesLongMessages() {
        service.record(TENANT, IntegrationLog.Kind.WEBHOOK, "w1", "Alerts", IntegrationLog.Event.TEST,
            IntegrationLog.Outcome.FAILURE, 500, 42L, "x".repeat(900), "ana@x.com");

        ArgumentCaptor<IntegrationLog> saved = ArgumentCaptor.forClass(IntegrationLog.class);
        verify(repository).save(saved.capture());
        IntegrationLog log = saved.getValue();
        assertThat(log.getTenantId()).isEqualTo(TENANT);
        assertThat(log.getIntegrationName()).isEqualTo("Alerts");
        assertThat(log.getEvent()).isEqualTo(IntegrationLog.Event.TEST);
        assertThat(log.getOutcome()).isEqualTo(IntegrationLog.Outcome.FAILURE);
        assertThat(log.getHttpStatus()).isEqualTo(500);
        assertThat(log.getDurationMs()).isEqualTo(42L);
        assertThat(log.getActor()).isEqualTo("ana@x.com");
        assertThat(log.getMessage()).hasSize(500);
    }

    @Test
    void recordChange_isASuccessWithoutHttpDetails() {
        service.recordChange(TENANT, IntegrationLog.Kind.CONNECTOR, "c1", "Jira", IntegrationLog.Event.CREATED, "ana@x.com");

        ArgumentCaptor<IntegrationLog> saved = ArgumentCaptor.forClass(IntegrationLog.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getOutcome()).isEqualTo(IntegrationLog.Outcome.SUCCESS);
        assertThat(saved.getValue().getHttpStatus()).isNull();
        assertThat(saved.getValue().getMessage()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_capsThePageSize_sortsNewestFirst_andMapsToDtos() {
        IntegrationLog row = IntegrationLog.builder().id("l1").tenantId(TENANT).integrationKind(IntegrationLog.Kind.WEBHOOK)
            .integrationId("w1").integrationName("Alerts").event(IntegrationLog.Event.TEST)
            .outcome(IntegrationLog.Outcome.SUCCESS).createdAt(Instant.parse("2026-10-02T10:00:00Z")).build();
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(row)));

        Page<?> page = service.list(TENANT, null, null, null, -3, 5000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(IntegrationLogService.MAX_PAGE_SIZE);
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(page.getContent()).hasSize(1);
    }
}
