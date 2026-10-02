package com.nexusops.platform.audit;

import com.nexusops.iam.event.SessionEndedEvent;
import com.nexusops.platform.service.AuditRecorder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/** Turns session endings published by iam (logout, logout from every device) into audit entries. */
@Component
@RequiredArgsConstructor
public class AuditSessionListener {

    private final AuditRecorder recorder;

    @EventListener
    public void onSessionEnded(SessionEndedEvent event) {
        String ip = null;
        String userAgent = null;
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            HttpServletRequest request = servlet.getRequest();
            ip = request.getRemoteAddr();
            userAgent = request.getHeader("User-Agent");
        }
        recorder.record(new AuditRecorder.Entry(
            event.allDevices() ? "auth.logout.all" : "auth.logout",
            "LOGOUT", event.tenantId(), event.username(), "auth", null,
            Map.of("outcome", "SUCCESS", "allDevices", event.allDevices()), ip, userAgent));
    }
}
