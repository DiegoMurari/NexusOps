package com.nexusops.ticketing.dto;

import java.time.Instant;

/**
 * Evidência anexada. {@code subjectType} diz a que ponto do histórico ela pertence (TICKET, COMMENT ou EVENT) e
 * {@code subjectId} o comentário ou evento. {@code ticketId} é nulo enquanto estiver só preparada.
 */
public record EvidenceDto(
    String id,
    String ticketId,
    String fileName,
    long fileSize,
    String mimeType,
    String subjectType,
    String subjectId,
    boolean internal,
    String uploaderId,
    String scanStatus,
    Instant createdAt
) {
}
