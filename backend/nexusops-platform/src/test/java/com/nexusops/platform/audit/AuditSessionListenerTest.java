package com.nexusops.platform.audit;

import com.nexusops.iam.event.SessionEndedEvent;
import com.nexusops.platform.service.AuditRecorder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditSessionListenerTest {

    private final AuditRecorder recorder = mock(AuditRecorder.class);
    private final AuditSessionListener listener = new AuditSessionListener(recorder);

    @Test
    void recordsLogoutOfASingleDevice() {
        listener.onSessionEnded(new SessionEndedEvent("t1", "ana@nexusops.com", false));

        AuditRecorder.Entry entry = captured();
        assertThat(entry.eventType()).isEqualTo("auth.logout");
        assertThat(entry.action()).isEqualTo("LOGOUT");
        assertThat(entry.tenantId()).isEqualTo("t1");
        assertThat(entry.userId()).isEqualTo("ana@nexusops.com");
        assertThat(entry.payload()).containsEntry("outcome", "SUCCESS").containsEntry("allDevices", false);
    }

    @Test
    void recordsLogoutFromEveryDevice() {
        listener.onSessionEnded(new SessionEndedEvent("t1", "ana@nexusops.com", true));

        assertThat(captured().eventType()).isEqualTo("auth.logout.all");
    }

    private AuditRecorder.Entry captured() {
        ArgumentCaptor<AuditRecorder.Entry> captor = ArgumentCaptor.forClass(AuditRecorder.Entry.class);
        verify(recorder).record(captor.capture());
        return captor.getValue();
    }
}
