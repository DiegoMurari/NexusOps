package com.nexusops.ticketing.service;

import com.nexusops.shared.directory.UserDirectory;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import com.nexusops.ticketing.domain.CatalogArea;
import com.nexusops.ticketing.domain.CatalogTopic;
import com.nexusops.ticketing.domain.Ticket;
import com.nexusops.ticketing.dto.ConsoleDto;
import com.nexusops.ticketing.dto.QueueDto;
import com.nexusops.ticketing.dto.TicketDto;
import com.nexusops.ticketing.mapper.TicketMapper;
import com.nexusops.ticketing.repository.CatalogAreaRepository;
import com.nexusops.ticketing.repository.CatalogTopicRepository;
import com.nexusops.ticketing.repository.ServiceQueueRepository;
import com.nexusops.ticketing.repository.TicketRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Console do analista (ADR-013, Fase H): a operação vista por fila e por responsável. Fila é equipe e responsável
 * é pessoa, então "minhas filas", "meus" e "sem responsável" são recortes diferentes e independentes.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsoleService {

    /** Recortes da lista. */
    public enum Scope { MINE, MY_QUEUES, UNASSIGNED, ALL }

    private static final List<Ticket.TicketStatus> OPEN = List.of(
        Ticket.TicketStatus.OPEN, Ticket.TicketStatus.IN_PROGRESS, Ticket.TicketStatus.REOPENED,
        Ticket.TicketStatus.WAITING, Ticket.TicketStatus.ON_HOLD);

    private final TicketRepository ticketRepository;
    private final TicketMapper ticketMapper;
    private final QueueService queueService;
    private final ServiceQueueRepository queueRepository;
    private final CatalogTopicRepository topicRepository;
    private final CatalogAreaRepository areaRepository;
    private final FormService formService;
    private final UserDirectory userDirectory;

    /**
     * A lista do analista. Sem status explícito, mostra o que está em aberto (o fechado só aparece pedindo, ou em
     * ALL); a busca olha título e número.
     */
    public Page<TicketDto> queueView(String tenantId, String principal, Scope scope, String queueId,
                                     Ticket.TicketStatus status, String q, Pageable pageable) {
        Specification<Ticket> spec = base(tenantId, principal, scope, status == null);
        if (queueId != null && !queueId.isBlank()) {
            spec = spec.and((r, cq, cb) -> cb.equal(r.get("queueId"), queueId));
        }
        if (status != null) {
            spec = spec.and((r, cq, cb) -> cb.equal(r.get("status"), status));
        }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((r, cq, cb) -> cb.or(
                cb.like(cb.lower(r.get("title")), like), cb.like(cb.lower(r.get("ticketNumber")), like)));
        }
        return ticketRepository.findAll(spec, pageable).map(ticketMapper::toDto);
    }

    /** Quanto há em aberto em cada recorte. */
    public ConsoleDto.Counts counts(String tenantId, String principal) {
        return new ConsoleDto.Counts(
            ticketRepository.count(base(tenantId, principal, Scope.MINE, true)),
            ticketRepository.count(base(tenantId, principal, Scope.MY_QUEUES, true)),
            ticketRepository.count(base(tenantId, principal, Scope.UNASSIGNED, true)),
            ticketRepository.count(base(tenantId, principal, Scope.ALL, true)));
    }

    /** De onde veio o chamado e como chegou: nomes resolvidos mais as respostas do formulário. */
    public ConsoleDto.Context context(String ticketId, String tenantId) {
        Ticket t = ticketRepository.findById(ticketId)
            .filter(x -> tenantId.equals(x.getTenantId()))
            .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
        String queueName = t.getQueueId() == null ? null
            : queueRepository.findByIdAndTenantId(t.getQueueId(), tenantId).map(q -> q.getName()).orElse(null);
        CatalogTopic topic = t.getTopicId() == null ? null
            : topicRepository.findByIdAndTenantId(t.getTopicId(), tenantId).orElse(null);
        CatalogArea area = topic == null ? null : areaRepository.findByIdAndTenantId(topic.getAreaId(), tenantId).orElse(null);
        String reporter = t.getReporterId() == null ? null
            : userDirectory.findByIds(Set.of(t.getReporterId()), tenantId).values().stream()
                .findFirst().map(UserDirectory.UserRef::name).orElse(null);
        String assignee = t.getAssigneeId() == null ? null
            : userDirectory.findActiveByEmail(t.getAssigneeId(), tenantId).map(UserDirectory.UserRef::name).orElse(null);
        return new ConsoleDto.Context(t.getQueueId(), queueName, topic == null ? null : topic.getName(),
            area == null ? null : area.getName(), reporter, assignee, formService.answerLines(t));
    }

    /** Valida o recorte vindo da URL. */
    public static Scope parseScope(String raw) {
        if (raw == null || raw.isBlank()) {
            return Scope.ALL;
        }
        try {
            return Scope.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Recorte inválido: use MINE, MY_QUEUES, UNASSIGNED ou ALL");
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Tenant + recorte; sem status explícito só o que está em aberto (o fechado se pede por status). */
    private Specification<Ticket> base(String tenantId, String principal, Scope scope, boolean openOnly) {
        List<String> myQueues = scope == Scope.MY_QUEUES
            ? queueService.mineByEmail(principal, tenantId).stream().map(QueueDto::id).toList() : List.of();
        return (r, cq, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(r.get("tenantId"), tenantId));
            switch (scope) {
                case MINE -> p.add(cb.equal(r.get("assigneeId"), principal));
                case MY_QUEUES -> p.add(myQueues.isEmpty() ? cb.disjunction() : r.get("queueId").in(myQueues));
                case UNASSIGNED -> p.add(cb.isNull(r.get("assigneeId")));
                case ALL -> { }
            }
            if (openOnly) {
                p.add(r.get("status").in(OPEN));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }
}
