package com.nexusops.integration.delivery;

import com.nexusops.integration.domain.WebhookDelivery;
import com.nexusops.integration.service.WebhookDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Tarefa agendada que envia as entregas vencidas e faz a limpeza diária. Desligue com
 * {@code nexusops.integration.delivery.enabled=false}. Uma entrega que o worker não conseguir concluir (queda
 * no meio) volta sozinha à fila quando a reserva expira.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "nexusops.integration.delivery.enabled", havingValue = "true", matchIfMissing = true)
public class WebhookDeliveryWorker {

    private final WebhookDeliveryService deliveryService;

    @Scheduled(fixedDelayString = "${nexusops.integration.delivery.poll-ms:5000}")
    public void run() {
        try {
            for (WebhookDelivery delivery : deliveryService.claimDue(Instant.now())) {
                try {
                    deliveryService.deliver(delivery);
                } catch (RuntimeException e) {
                    // fica "em envio" e volta à fila quando a reserva vencer
                    log.warn("webhook delivery {} could not be completed: {}", delivery.getId(), e.getClass().getSimpleName());
                }
            }
        } catch (RuntimeException e) {
            log.warn("webhook delivery worker cycle failed: {}", e.getClass().getSimpleName());
        }
    }

    @Scheduled(cron = "0 17 3 * * *")
    public void purge() {
        try {
            deliveryService.purge(Instant.now());
        } catch (RuntimeException e) {
            log.warn("webhook delivery purge failed: {}", e.getClass().getSimpleName());
        }
    }
}
