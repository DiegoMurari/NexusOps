package com.nexusops.notification.repository;

import com.nexusops.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {

    Page<Notification> findByRecipientIdAndTenantIdOrderByCreatedAtDesc(String recipientId, String tenantId, Pageable pageable);

    List<Notification> findByRecipientIdAndTenantIdAndReadFalse(String recipientId, String tenantId);

    long countByRecipientIdAndTenantIdAndReadFalse(String recipientId, String tenantId);

    Optional<Notification> findByIdAndRecipientId(String id, String recipientId);
}
