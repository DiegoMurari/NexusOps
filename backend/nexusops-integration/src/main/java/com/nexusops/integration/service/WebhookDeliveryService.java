package com.nexusops.integration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusops.integration.domain.IntegrationLog;
import com.nexusops.integration.domain.Webhook;
import com.nexusops.integration.domain.WebhookDelivery;
import com.nexusops.integration.dto.WebhookDeliveryDto;
import com.nexusops.integration.mapper.WebhookDeliveryMapper;
import com.nexusops.integration.repository.WebhookDeliveryRepository;
import com.nexusops.integration.repository.WebhookRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Entrega automática dos eventos do sistema aos webhooks, com fila no banco (outbox), reentrega com espera
 * crescente e desistência depois de {@value #MAX_ATTEMPTS} tentativas. Idempotente por (webhook, evento).
 * O envio passa sempre pelo {@link SafeHttpSender} (https, IP público, sem redirecionamento).
 */
@Service
@RequiredArgsConstructor
public class WebhookDeliveryService {

    static final int MAX_ATTEMPTS = 6;
    static final int BATCH_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;
    /** Quanto tempo uma entrega reservada fica "em envio" antes de outra instância poder pegá-la de novo. */
    static final Duration LEASE = Duration.ofMinutes(2);
    /** Espera depois da 1ª, 2ª... falha; a 6ª falha encerra. */
    static final List<Duration> BACKOFF = List.of(
        Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(10), Duration.ofHours(1), Duration.ofHours(6));
    static final Duration DELIVERY_RETENTION = Duration.ofDays(30);
    static final Duration LOG_RETENTION = Duration.ofDays(90);

    private final WebhookRepository webhookRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final IntegrationLogService logService;
    private final SafeHttpSender sender;
    private final ObjectMapper objectMapper;

    /** Enfileira o evento para cada webhook ativo do tenant que o assina. Devolve quantas entregas criou. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int enqueue(String tenantId, String eventType, String eventId, Instant occurredAt, Map<String, Object> data) {
        int created = 0;
        for (Webhook webhook : webhookRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)) {
            if (webhook.getStatus() != Webhook.WebhookStatus.ACTIVE || !webhook.getEvents().contains(eventType)) {
                continue;
            }
            if (deliveryRepository.existsByWebhookIdAndEventId(webhook.getId(), eventId)) {
                continue;
            }
            String deliveryId = UUID.randomUUID().toString();
            deliveryRepository.save(WebhookDelivery.builder()
                .id(deliveryId)
                .tenantId(tenantId)
                .webhookId(webhook.getId())
                .eventType(eventType)
                .eventId(eventId)
                .payload(payload(deliveryId, eventType, occurredAt, data))
                .nextAttemptAt(Instant.now())
                .build());
            created++;
        }
        return created;
    }

    /** Reserva as entregas vencidas para este worker: marca "em envio" com prazo, e só então solta a linha. */
    @Transactional
    public List<WebhookDelivery> claimDue(Instant now) {
        List<WebhookDelivery> due = deliveryRepository.lockDue(now, BATCH_SIZE);
        for (WebhookDelivery delivery : due) {
            delivery.setStatus(WebhookDelivery.Status.SENDING);
            delivery.setNextAttemptAt(now.plus(LEASE));
        }
        return due;
    }

    /** Uma tentativa de envio. Fora de transação: a chamada de rede não segura conexão do banco. */
    public void deliver(WebhookDelivery delivery) {
        Webhook webhook = webhookRepository.findByIdAndTenantId(delivery.getWebhookId(), delivery.getTenantId())
            .orElse(null);
        if (webhook == null || webhook.getStatus() != Webhook.WebhookStatus.ACTIVE) {
            delivery.setStatus(WebhookDelivery.Status.CANCELLED);
            delivery.setLastError(webhook == null ? "WEBHOOK_REMOVED" : "WEBHOOK_DISABLED");
            deliveryRepository.save(delivery);
            return;
        }

        int attempt = delivery.getAttempts() + 1;
        Instant sentAt = Instant.now();
        String timestamp = String.valueOf(sentAt.getEpochSecond());
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-NexusOps-Event", delivery.getEventType());
        headers.put("X-NexusOps-Delivery", delivery.getId());
        headers.put("X-NexusOps-Timestamp", timestamp);
        if (webhook.getSecret() != null && !webhook.getSecret().isBlank()) {
            headers.put("X-NexusOps-Signature",
                "sha256=" + WebhookService.sign(webhook.getSecret(), timestamp, delivery.getPayload()));
        }

        SafeHttpSender.Result result = sender.post(webhook.getTargetUrl(), delivery.getPayload(), headers,
            webhook.getTimeoutSeconds());
        boolean ok = result.delivered();
        Instant finishedAt = Instant.now();

        delivery.setAttempts(attempt);
        delivery.setLastHttpStatus(result.httpStatus());
        delivery.setLastError(ok ? null : (result.responded() ? "HTTP_" + result.httpStatus() : result.failure()));
        boolean gaveUp = false;
        if (ok) {
            delivery.setStatus(WebhookDelivery.Status.DELIVERED);
            delivery.setDeliveredAt(finishedAt);
        } else if (isPermanent(result) || attempt >= MAX_ATTEMPTS) {
            delivery.setStatus(WebhookDelivery.Status.FAILED);
            gaveUp = true;
        } else {
            delivery.setStatus(WebhookDelivery.Status.PENDING);
            delivery.setNextAttemptAt(finishedAt.plus(BACKOFF.get(attempt - 1)));
        }
        deliveryRepository.save(delivery);

        IntegrationLog.Outcome outcome = ok ? IntegrationLog.Outcome.SUCCESS : IntegrationLog.Outcome.FAILURE;
        webhookRepository.recordDelivery(webhook.getId(), webhook.getTenantId(), finishedAt, outcome.name(),
            result.httpStatus(), delivery.getLastError());
        logService.record(webhook.getTenantId(), IntegrationLog.Kind.WEBHOOK, webhook.getId(), webhook.getName(),
            IntegrationLog.Event.DELIVERY, outcome, result.httpStatus(), result.durationMs(),
            delivery.getEventType() + " · tentativa " + attempt + " · " + result.summary() + (gaveUp ? " · desistiu" : ""),
            "system");
    }

    /** Recoloca na fila uma entrega que desistiu, para o worker tentar de novo do zero. */
    @Transactional
    public WebhookDeliveryDto retry(String id, String tenantId) {
        WebhookDelivery delivery = deliveryRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("WebhookDelivery", id));
        if (delivery.getStatus() != WebhookDelivery.Status.FAILED) {
            throw new ValidationException("Only failed deliveries can be retried");
        }
        delivery.setStatus(WebhookDelivery.Status.PENDING);
        delivery.setAttempts(0);
        delivery.setNextAttemptAt(Instant.now());
        delivery.setLastError(null);
        return WebhookDeliveryMapper.toDto(deliveryRepository.save(delivery));
    }

    @Transactional(readOnly = true)
    public Page<WebhookDeliveryDto> list(String tenantId, String webhookId, WebhookDelivery.Status status,
                                         int page, int size) {
        Specification<WebhookDelivery> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (webhookId != null && !webhookId.isBlank()) {
                predicates.add(cb.equal(root.get("webhookId"), webhookId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
            Sort.by(Sort.Direction.DESC, "createdAt"));
        return deliveryRepository.findAll(spec, pageable).map(WebhookDeliveryMapper::toDto);
    }

    @Transactional(readOnly = true)
    public long count(String tenantId, WebhookDelivery.Status status) {
        return deliveryRepository.countByTenantIdAndStatus(tenantId, status);
    }

    /** Limpeza periódica: entregas concluídas e logs antigos. Entregas pendentes nunca são apagadas. */
    public void purge(Instant now) {
        deliveryRepository.deleteFinishedBefore(now.minus(DELIVERY_RETENTION));
        logService.purgeOlderThan(now.minus(LOG_RETENTION));
    }

    /** Erro do destino que não vai mudar sozinho (4xx, exceto timeout e limite de taxa) ou endereço proibido. */
    static boolean isPermanent(SafeHttpSender.Result result) {
        if (!result.responded()) {
            return "INVALID_URL".equals(result.failure()) || "BLOCKED_ADDRESS".equals(result.failure());
        }
        int status = result.httpStatus();
        return status >= 400 && status < 500 && status != 408 && status != 429;
    }

    private String payload(String deliveryId, String eventType, Instant occurredAt, Map<String, Object> data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", deliveryId);
        body.put("event", eventType);
        body.put("occurredAt", occurredAt.toString());
        body.put("data", data);
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize webhook payload", e);
        }
    }
}
