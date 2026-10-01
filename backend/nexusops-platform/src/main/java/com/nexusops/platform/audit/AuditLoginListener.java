package com.nexusops.platform.audit;

import com.nexusops.iam.event.LoginAttemptedEvent;
import com.nexusops.platform.service.AuditRecorder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/** Turns login attempts published by iam into audit entries. */
@Component
@RequiredArgsConstructor
public class AuditLoginListener {

    private final AuditRecorder recorder;

    @EventListener
    public void onLoginAttempted(LoginAttemptedEvent event) {
        String ip = null;
        String userAgent = null;
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            HttpServletRequest request = servlet.getRequest();
            ip = request.getRemoteAddr();
            userAgent = request.getHeader("User-Agent");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("outcome", event.success() ? "SUCCESS" : "FAILURE");
        if (event.reason() != null) {
            payload.put("reason", event.reason());
        }
        recorder.record(new AuditRecorder.Entry(
            event.success() ? "auth.login.success" : "auth.login.failure",
            "LOGIN", event.tenantId(), event.username(), "auth", null, payload, ip, userAgent));
    }
}
