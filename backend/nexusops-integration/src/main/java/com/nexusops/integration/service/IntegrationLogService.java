package com.nexusops.integration.service;

import com.nexusops.integration.domain.IntegrationLog;
import com.nexusops.integration.dto.IntegrationLogDto;
import com.nexusops.integration.mapper.IntegrationLogMapper;
import com.nexusops.integration.repository.IntegrationLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Grava e consulta o histórico de atividade das integrações, sempre dentro do tenant do chamador. */
@Service
@RequiredArgsConstructor
public class IntegrationLogService {

    static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_MESSAGE = 500;

    private final IntegrationLogRepository logRepository;

    @Transactional
    public void record(String tenantId, IntegrationLog.Kind kind, String integrationId, String integrationName,
                       IntegrationLog.Event event, IntegrationLog.Outcome outcome,
                       Integer httpStatus, Long durationMs, String message, String actor) {
        logRepository.save(IntegrationLog.builder()
            .tenantId(tenantId)
            .integrationKind(kind)
            .integrationId(integrationId)
            .integrationName(integrationName)
            .event(event)
            .outcome(outcome)
            .httpStatus(httpStatus)
            .durationMs(durationMs)
            .message(truncate(message))
            .actor(actor)
            .build());
    }

    /** Atalho para as mudanças de configuração, que não têm HTTP nem duração. */
    @Transactional
    public void recordChange(String tenantId, IntegrationLog.Kind kind, String integrationId, String integrationName,
                             IntegrationLog.Event event, String actor) {
        record(tenantId, kind, integrationId, integrationName, event, IntegrationLog.Outcome.SUCCESS,
            null, null, null, actor);
    }

    @Transactional(readOnly = true)
    public Page<IntegrationLogDto> list(String tenantId, IntegrationLog.Kind kind, String integrationId,
                                        IntegrationLog.Outcome outcome, int page, int size) {
        Specification<IntegrationLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (kind != null) {
                predicates.add(cb.equal(root.get("integrationKind"), kind));
            }
            if (integrationId != null && !integrationId.isBlank()) {
                predicates.add(cb.equal(root.get("integrationId"), integrationId));
            }
            if (outcome != null) {
                predicates.add(cb.equal(root.get("outcome"), outcome));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
            Sort.by(Sort.Direction.DESC, "createdAt"));
        return logRepository.findAll(spec, pageable).map(IntegrationLogMapper::toDto);
    }

    /** Retenção do histórico: apaga o que for mais antigo que {@code before}. */
    public int purgeOlderThan(java.time.Instant before) {
        return logRepository.deleteOlderThan(before);
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_MESSAGE ? message : message.substring(0, MAX_MESSAGE);
    }
}
