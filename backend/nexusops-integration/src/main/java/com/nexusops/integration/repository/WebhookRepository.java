package com.nexusops.integration.repository;

import com.nexusops.integration.domain.Webhook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface WebhookRepository extends JpaRepository<Webhook, String> {

    Optional<Webhook> findByIdAndTenantId(String id, String tenantId);

    List<Webhook> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    /**
     * Grava só o resultado da última entrega, sem passar pela entidade: o teste roda fora de transação (faz
     * chamada de rede) e não deve brigar por versão com uma edição simultânea feita pela tela.
     */
    @Transactional
    @Modifying
    @Query("update Webhook w set w.lastDeliveryAt = :at, w.lastDeliveryStatus = :status, "
        + "w.lastDeliveryHttpStatus = :http, w.lastError = :error where w.id = :id and w.tenantId = :tenantId")
    int recordDelivery(@Param("id") String id, @Param("tenantId") String tenantId, @Param("at") Instant at,
                       @Param("status") String status, @Param("http") Integer httpStatus,
                       @Param("error") String error);
}
