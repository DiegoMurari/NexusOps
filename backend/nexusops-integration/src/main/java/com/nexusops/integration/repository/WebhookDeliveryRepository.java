package com.nexusops.integration.repository;

import com.nexusops.integration.domain.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface WebhookDeliveryRepository
    extends JpaRepository<WebhookDelivery, String>, JpaSpecificationExecutor<WebhookDelivery> {

    Optional<WebhookDelivery> findByIdAndTenantId(String id, String tenantId);

    boolean existsByWebhookIdAndEventId(String webhookId, String eventId);

    long countByTenantIdAndStatus(String tenantId, WebhookDelivery.Status status);

    /**
     * Reserva as entregas vencidas. {@code SKIP LOCKED} permite várias instâncias do worker sem pegarem a
     * mesma linha; o chamador precisa estar numa transação e marcar a reserva antes de soltá-la.
     */
    @Query(value = "select * from integration.webhook_deliveries "
        + "where status in ('PENDING', 'SENDING') and next_attempt_at <= :now "
        + "order by next_attempt_at limit :limit for update skip locked", nativeQuery = true)
    List<WebhookDelivery> lockDue(@Param("now") Instant now, @Param("limit") int limit);

    /** Limpeza: entregas já concluídas (ou desistidas) há muito tempo. Pendentes nunca são apagadas. */
    default int deleteFinishedBefore(Instant before) {
        return deleteByStatusInAndCreatedAtBefore(List.of(WebhookDelivery.Status.DELIVERED,
            WebhookDelivery.Status.FAILED, WebhookDelivery.Status.CANCELLED), before);
    }

    @Transactional
    @Modifying
    @Query("delete from WebhookDelivery d where d.status in :statuses and d.createdAt < :before")
    int deleteByStatusInAndCreatedAtBefore(@Param("statuses") List<WebhookDelivery.Status> statuses,
                                           @Param("before") Instant before);
}
