package com.nexusops.platform.audit;

import com.nexusops.platform.service.AuditRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.servlet.HandlerMapping;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditInterceptorTest {

    @Mock
    private AuditRecorder recorder;

    private AuditInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new AuditInterceptor(recorder);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate() {
        User principal = new User("agent@example.com", "x", List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("TENANT_tenant-1"))));
    }

    private MockHttpServletRequest request(String method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v1/scheduled-reports/abc");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/scheduled-reports/{id}");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("id", "abc"));
        request.addHeader("User-Agent", "JUnit");
        request.setRemoteAddr("203.0.113.9");
        return request;
    }

    @Test
    void recordsAuthenticatedMutation_withoutBody() {
        authenticate();
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        MockHttpServletRequest request = request("PATCH");
        request.setContent("{\"password\":\"hunter2\"}".getBytes());

        interceptor.afterCompletion(request, response, new Object(), null);

        ArgumentCaptor<AuditRecorder.Entry> captor = ArgumentCaptor.forClass(AuditRecorder.Entry.class);
        verify(recorder).record(captor.capture());
        AuditRecorder.Entry entry = captor.getValue();
        assertThat(entry.eventType()).isEqualTo("api.scheduled-reports.update");
        assertThat(entry.action()).isEqualTo("UPDATE");
        assertThat(entry.tenantId()).isEqualTo("tenant-1");
        assertThat(entry.userId()).isEqualTo("agent@example.com");
        assertThat(entry.resourceType()).isEqualTo("scheduled-reports");
        assertThat(entry.resourceId()).isEqualTo("abc");
        assertThat(entry.ipAddress()).isEqualTo("203.0.113.9");
        assertThat(entry.payload()).containsEntry("outcome", "SUCCESS").containsEntry("status", 200);
        assertThat(entry.payload().toString()).doesNotContain("hunter2");
    }

    @Test
    void marksFailureOnErrorStatus_andMapsActions() {
        authenticate();
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(403);

        interceptor.afterCompletion(request("DELETE"), response, new Object(), null);

        ArgumentCaptor<AuditRecorder.Entry> captor = ArgumentCaptor.forClass(AuditRecorder.Entry.class);
        verify(recorder).record(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo("DELETE");
        assertThat(captor.getValue().payload()).containsEntry("outcome", "FAILURE");
        assertThat(AuditInterceptor.action("POST")).isEqualTo("CREATE");
    }

    @Test
    void ignoresReadsAndAnonymousCalls() {
        interceptor.afterCompletion(request("GET"), new MockHttpServletResponse(), new Object(), null);
        authenticate();
        interceptor.afterCompletion(request("GET"), new MockHttpServletResponse(), new Object(), null);
        SecurityContextHolder.clearContext();
        interceptor.afterCompletion(request("POST"), new MockHttpServletResponse(), new Object(), null);

        verify(recorder, never()).record(any());
    }
}
