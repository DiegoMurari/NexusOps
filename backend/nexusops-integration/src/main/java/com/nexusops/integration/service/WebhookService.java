package com.nexusops.integration.service;

import com.nexusops.integration.domain.IntegrationLog;
import com.nexusops.integration.domain.Webhook;
import com.nexusops.integration.dto.CreateWebhookRequest;
import com.nexusops.integration.dto.IntegrationTestResultDto;
import com.nexusops.integration.dto.UpdateWebhookRequest;
import com.nexusops.integration.dto.WebhookDto;
import com.nexusops.integration.mapper.WebhookMapper;
import com.nexusops.integration.repository.WebhookRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Definições de webhook, liga/desliga, evento de teste assinado e histórico. O envio dos eventos do sistema
 * para os webhooks ainda não existe: hoje a única saída é o teste ({@code ping}) disparado por quem tem
 * permissão, que também serve de verificação de saúde.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class WebhookService {

    private static final Pattern EVENT_NAME = Pattern.compile("^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)*$");
    static final int TEST_COOLDOWN_SECONDS = 5;

    private final WebhookRepository webhookRepository;
    private final IntegrationLogService logService;
    private final SafeHttpSender sender;

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
        Webhook saved = webhookRepository.save(webhook);
        logService.recordChange(tenantId, IntegrationLog.Kind.WEBHOOK, saved.getId(), saved.getName(),
            IntegrationLog.Event.CREATED, userId);
        return WebhookMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<WebhookDto> list(String tenantId) {
        return webhookRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(WebhookMapper::toDto).toList();
    }

    public WebhookDto update(String id, UpdateWebhookRequest request, String tenantId, String userId) {
        Webhook webhook = find(id, tenantId);
        boolean configChanged = false;
        IntegrationLog.Event statusEvent = null;

        if (request.getName() != null) {
            if (request.getName().isBlank()) {
                throw new ValidationException("Name must not be blank");
            }
            webhook.setName(request.getName().trim());
            configChanged = true;
        }
        if (request.getTargetUrl() != null) {
            webhook.setTargetUrl(WebhookUrlValidator.validate(request.getTargetUrl()));
            configChanged = true;
        }
        if (request.getSecret() != null) {
            webhook.setSecret(blankToNull(request.getSecret()));
            configChanged = true;
        }
        if (request.getEvents() != null) {
            Set<String> events = normalizeEvents(request.getEvents());
            webhook.getEvents().clear();
            webhook.getEvents().addAll(events);
            configChanged = true;
        }
        if (request.getStatus() != null) {
            // FAILED/DISABLED são estados que o sistema decide; quem edita só liga ou desliga.
            if (request.getStatus() != Webhook.WebhookStatus.ACTIVE
                && request.getStatus() != Webhook.WebhookStatus.INACTIVE) {
                throw new ValidationException("Status can only be set to ACTIVE or INACTIVE");
            }
            if (request.getStatus() != webhook.getStatus()) {
                webhook.setStatus(request.getStatus());
                statusEvent = request.getStatus() == Webhook.WebhookStatus.ACTIVE
                    ? IntegrationLog.Event.ENABLED : IntegrationLog.Event.DISABLED;
            }
        }
        if (request.getTimeoutSeconds() != null) {
            webhook.setTimeoutSeconds(request.getTimeoutSeconds());
            configChanged = true;
        }
        webhook.setUpdatedBy(userId);
        Webhook saved = webhookRepository.save(webhook);

        if (configChanged) {
            logService.recordChange(tenantId, IntegrationLog.Kind.WEBHOOK, saved.getId(), saved.getName(),
                IntegrationLog.Event.UPDATED, userId);
        }
        if (statusEvent != null) {
            logService.recordChange(tenantId, IntegrationLog.Kind.WEBHOOK, saved.getId(), saved.getName(),
                statusEvent, userId);
        }
        return WebhookMapper.toDto(saved);
    }

    public void delete(String id, String tenantId, String userId) {
        Webhook webhook = find(id, tenantId);
        logService.recordChange(tenantId, IntegrationLog.Kind.WEBHOOK, webhook.getId(), webhook.getName(),
            IntegrationLog.Event.DELETED, userId);
        webhookRepository.delete(webhook);
    }

    /**
     * Envia um evento {@code ping} assinado (HMAC-SHA256 sobre {@code timestamp.corpo}) e guarda o resultado.
     * Roda fora de transação: a chamada de rede não pode segurar uma conexão do banco aberta.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public IntegrationTestResultDto sendTest(String id, String tenantId, String userId) {
        Webhook webhook = find(id, tenantId);
        Instant previous = webhook.getLastDeliveryAt();
        if (previous != null && previous.isAfter(Instant.now().minusSeconds(TEST_COOLDOWN_SECONDS))) {
            throw new ValidationException("Wait a few seconds before sending another test");
        }

        Instant sentAt = Instant.now();
        String body = "{\"event\":\"ping\",\"webhookId\":\"" + webhook.getId() + "\",\"sentAt\":\"" + sentAt + "\"}";
        String timestamp = String.valueOf(sentAt.getEpochSecond());
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-NexusOps-Event", "ping");
        headers.put("X-NexusOps-Timestamp", timestamp);
        if (webhook.getSecret() != null && !webhook.getSecret().isBlank()) {
            headers.put("X-NexusOps-Signature", "sha256=" + sign(webhook.getSecret(), timestamp, body));
        }

        SafeHttpSender.Result result = sender.post(webhook.getTargetUrl(), body, headers, webhook.getTimeoutSeconds());
        boolean ok = result.delivered();
        IntegrationLog.Outcome outcome = ok ? IntegrationLog.Outcome.SUCCESS : IntegrationLog.Outcome.FAILURE;
        String error = ok ? null : (result.responded() ? "HTTP_" + result.httpStatus() : result.failure());
        Instant finishedAt = Instant.now();

        webhookRepository.recordDelivery(webhook.getId(), tenantId, finishedAt, outcome.name(),
            result.httpStatus(), error);
        logService.record(tenantId, IntegrationLog.Kind.WEBHOOK, webhook.getId(), webhook.getName(),
            IntegrationLog.Event.TEST, outcome, result.httpStatus(), result.durationMs(), result.summary(), userId);

        return IntegrationTestResultDto.builder()
            .outcome(outcome).httpStatus(result.httpStatus()).durationMs(result.durationMs())
            .message(result.summary()).checkedAt(finishedAt)
            .build();
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

    @Transactional(readOnly = true)
    public long countFailing(String tenantId) {
        return webhookRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
            .filter(w -> "FAILURE".equals(w.getLastDeliveryStatus())).count();
    }

    private Webhook find(String id, String tenantId) {
        return webhookRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Webhook", id));
    }

    static String sign(String secret, String timestamp, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
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
