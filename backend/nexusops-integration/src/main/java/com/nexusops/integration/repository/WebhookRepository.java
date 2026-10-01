package com.nexusops.integration.repository;

import com.nexusops.integration.domain.Webhook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WebhookRepository extends JpaRepository<Webhook, String> {

    Optional<Webhook> findByIdAndTenantId(String id, String tenantId);

    List<Webhook> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}
