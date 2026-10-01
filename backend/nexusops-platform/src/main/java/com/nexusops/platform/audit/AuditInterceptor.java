package com.nexusops.platform.audit;

import com.nexusops.platform.service.AuditRecorder;
import com.nexusops.shared.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Records every state-changing API call (POST/PUT/PATCH/DELETE) made by an authenticated user. It
 * stores who, what, where from and the outcome, and never the request body, so credentials and
 * secrets cannot leak into the log. Anonymous calls (login, refresh) are covered by dedicated events.
 */
@Component
@RequiredArgsConstructor
public class AuditInterceptor implements HandlerInterceptor {

    private static final Set<String> MUTATING = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final AuditRecorder recorder;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!MUTATING.contains(request.getMethod())) {
            return;
        }
        String user = SecurityUtils.getCurrentUserId().orElse(null);
        String tenant = SecurityUtils.getCurrentTenantId().orElse(null);
        if (user == null || tenant == null) {
            return;
        }

        String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        @SuppressWarnings("unchecked")
        Map<String, String> vars = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String resourceType = resourceType(pattern);
        String action = action(request.getMethod());
        int status = response.getStatus();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("method", request.getMethod());
        payload.put("path", pattern != null ? pattern : request.getRequestURI());
        payload.put("status", status);
        payload.put("outcome", ex == null && status < 400 ? "SUCCESS" : "FAILURE");

        recorder.record(new AuditRecorder.Entry(
            "api." + (resourceType == null ? "unknown" : resourceType) + "." + action.toLowerCase(),
            action, tenant, user, resourceType, vars == null ? null : vars.get("id"), payload,
            clientIp(request), request.getHeader("User-Agent")));
    }

    /** First path segment of the matched pattern, e.g. "/scheduled-reports/{id}" -> "scheduled-reports". */
    static String resourceType(String pattern) {
        if (pattern == null) {
            return null;
        }
        for (String segment : pattern.split("/")) {
            if (!segment.isBlank() && !segment.startsWith("{")) {
                return segment;
            }
        }
        return null;
    }

    static String action(String method) {
        return switch (method) {
            case "POST" -> "CREATE";
            case "DELETE" -> "DELETE";
            default -> "UPDATE";
        };
    }

    /** The direct peer address; forwarding headers are client-controlled and are not trusted here. */
    private static String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
