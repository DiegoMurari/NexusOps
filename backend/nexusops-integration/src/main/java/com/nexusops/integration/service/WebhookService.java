package com.nexusops.integration.service;

import com.nexusops.integration.domain.Webhook;
import com.nexusops.integration.dto.CreateWebhookRequest;
import com.nexusops.integration.dto.UpdateWebhookRequest;
import com.nexusops.integration.dto.WebhookDto;
import com.nexusops.integration.repository.WebhookRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Webhook definitions. Delivery is not implemented yet: this only manages configuration. */
@Service
@RequiredArgsConstructor
@Transactional
public class WebhookService {

    private static final Pattern EVENT_NAME = Pattern.compile("^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)*$");

    private final WebhookRepository webhookRepository;

    public WebhookDto create(CreateWebhookRequest request, String tenantId, String userId) {
        Webhook webhook = Webhook.builder()
            .tenantId(tenantId)
            .name(request.getName().trim())
            .targetUrl(WebhookUrlValidator.validate(request.getTargetUrl()))
            .secret(blankToNull(request.getSecret()))
            .events(normalizeEvents(request.getEvents()))
            .createdBy(userId)
            .updatedBy(userId)
            .build();
        if (request.getTimeoutSeconds() != null) {
            webhook.setTimeoutSeconds(request.getTimeoutSeconds());
        }
        return WebhookDto.from(webhookRepository.save(webhook));
    }

    @Transactional(readOnly = true)
    public List<WebhookDto> list(String tenantId) {
        return webhookRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(WebhookDto::from).toList();
    }

    public WebhookDto update(String id, UpdateWebhookRequest request, String tenantId, String userId) {
        Webhook webhook = find(id, tenantId);
        if (request.getName() != null) {
            if (request.getName().isBlank()) {
                throw new ValidationException("Name must not be blank");
            }
            webhook.setName(request.getName().trim());
        }
        if (request.getTargetUrl() != null) {
            webhook.setTargetUrl(WebhookUrlValidator.validate(request.getTargetUrl()));
        }
        if (request.getSecret() != null) {
            webhook.setSecret(blankToNull(request.getSecret()));
        }
        if (request.getEvents() != null) {
            Set<String> events = normalizeEvents(request.getEvents());
            webhook.getEvents().clear();
            webhook.getEvents().addAll(events);
        }
        if (request.getStatus() != null) {
            webhook.setStatus(request.getStatus());
        }
        if (request.getTimeoutSeconds() != null) {
            webhook.setTimeoutSeconds(request.getTimeoutSeconds());
        }
        webhook.setUpdatedBy(userId);
        return WebhookDto.from(webhookRepository.save(webhook));
    }

    public void delete(String id, String tenantId) {
        webhookRepository.delete(find(id, tenantId));
    }

    @Transactional(readOnly = true)
    public long count(String tenantId) {
        return webhookRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).size();
    }

    @Transactional(readOnly = true)
    public long countActive(String tenantId) {
        return webhookRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
            .filter(w -> w.getStatus() == Webhook.WebhookStatus.ACTIVE).count();
    }

    private Webhook find(String id, String tenantId) {
        return webhookRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Webhook", id));
    }

    private static Set<String> normalizeEvents(Set<String> events) {
        Set<String> result = new HashSet<>();
        if (events == null) {
            return result;
        }
        for (String event : events) {
            String normalized = event == null ? "" : event.trim().toLowerCase();
            if (!EVENT_NAME.matcher(normalized).matches()) {
                throw new ValidationException("Invalid event name: " + event);
            }
            result.add(normalized);
        }
        return result;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
