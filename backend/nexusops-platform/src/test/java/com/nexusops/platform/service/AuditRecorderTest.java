package com.nexusops.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusops.platform.domain.AuditLog;
import com.nexusops.platform.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditRecorderTest {

    @Mock
    private AuditLogRepository repository;

    private AuditRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new AuditRecorder(repository, new ObjectMapper());
    }

    @Test
    void savesEntry_withJsonPayload_andTruncatesOverlongFields() {
        recorder.record(new AuditRecorder.Entry("x".repeat(150), "CREATE", "t".repeat(60), "user@example.com",
            "reports", "r".repeat(50), Map.of("status", 200), "203.0.113.9", "agent"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getEventType()).hasSize(100);
        assertThat(saved.getTenantId()).hasSize(36);
        assertThat(saved.getResourceId()).hasSize(36);
        assertThat(saved.getPayload()).isEqualTo("{\"status\":200}");
        assertThat(saved.getEventId()).isNotBlank();
    }

    @Test
    void neverPropagatesPersistenceFailures() {
        when(repository.save(any(AuditLog.class))).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> recorder.record(new AuditRecorder.Entry("e", "CREATE", "t", "u", null, null, null, null, null)))
            .doesNotThrowAnyException();
    }
}
