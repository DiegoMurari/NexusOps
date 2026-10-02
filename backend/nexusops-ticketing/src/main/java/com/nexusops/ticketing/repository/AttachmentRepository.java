package com.nexusops.ticketing.repository;

import com.nexusops.ticketing.domain.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, String> {

    List<Attachment> findByTicketId(String ticketId);

    List<Attachment> findByUploaderId(String uploaderId);

    List<Attachment> findByTicketIdOrderByCreatedAtAsc(String ticketId);

    java.util.Optional<Attachment> findByIdAndTenantId(String id, String tenantId);

    /** Anexos preparados (sem chamado) criados antes do limite: ninguém os confirmou. */
    List<Attachment> findByTicketIdIsNullAndCreatedAtBefore(java.time.Instant before);
}