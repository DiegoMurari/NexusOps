package com.nexusops.ticketing.service;

import com.nexusops.ticketing.domain.*;
import com.nexusops.ticketing.dto.RoutingDecisionDto;
import com.nexusops.ticketing.dto.*;
import com.nexusops.ticketing.event.*;
import com.nexusops.ticketing.mapper.TicketMapper;
import com.nexusops.ticketing.repository.*;
import com.nexusops.ticketing.workflow.AcceptancePolicy;
import com.nexusops.ticketing.workflow.TicketLifecycle;
import com.nexusops.ticketing.workflow.TicketTransitionException;
import com.nexusops.shared.directory.LocationDirectory;
import com.nexusops.sla.service.SlaBreachService;
import com.nexusops.shared.event.TransactionalEventPublisher;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Transactional
public class TicketService {

    private final TicketRepository ticketRepository;
    private final CommentRepository commentRepository;
    private final AttachmentRepository attachmentRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final CategoryRepository categoryRepository;
    private final TicketMapper ticketMapper;
    private final TransactionalEventPublisher eventPublisher;
    private final TicketSlaService ticketSlaService;
    private final SlaBreachService slaBreachService;
    private final AssignmentService assignmentService;
    private final TicketTimelineService timelineService;
    private final TicketCycleService cycleService;
    private final CatalogService catalogService;
    private final QueueService queueService;
    private final FormService formService;
    private final LocationDirectory locationDirectory;
    private final RoutingService routingService;
    private final EvidenceService evidenceService;
    private final TicketAccessService access;

    public TicketDto createTicket(CreateTicketRequest request, String createdBy) {
        // Tópico do catálogo: o sistema deriva fila, prioridade inicial e SLA; o solicitante só escolheu o tópico.
        CatalogTopic topic = null;
        String routedQueueId = null;
        String routedBy = null;
        if (request.getTopicId() != null && !request.getTopicId().isBlank()) {
            topic = catalogService.requireActiveTopic(request.getTopicId(), request.getTenantId());
            if (topic.getDefaultPriority() != null) {
                request.setPriority(topic.getDefaultPriority());
            }
            if (request.getCategoryId() == null || request.getCategoryId().isBlank()) {
                request.setCategoryId(topic.getCategoryId());
            }
            routedQueueId = topic.getDefaultQueueId();
            routedBy = "TOPIC";
        }
        if (routedQueueId == null && request.getQueueId() != null && !request.getQueueId().isBlank()) {
            routedQueueId = queueService.requireActive(request.getQueueId(), request.getTenantId()).getId();
            routedBy = "MANUAL";
        }
        // As respostas do formulário valem contra a versão publicada do tópico; o servidor é a autoridade.
        FormService.ValidatedForm form = FormService.ValidatedForm.NONE_VALUE;
        if (topic != null) {
            form = formService.validateForTicket(topic, request.getFormAnswers(), request.getTenantId());
        } else if (request.getFormAnswers() != null && !request.getFormAnswers().isEmpty()) {
            throw new ValidationException("Respostas de formulário exigem um tópico do catálogo");
        }
        // Política de evidência do formulário (NONE/OPTIONAL/REQUIRED) aplicada antes de gravar qualquer coisa.
        java.util.List<com.nexusops.ticketing.domain.Attachment> stagedEvidence = evidenceService.validateStaged(
            request.getEvidenceIds(), form.evidenceMode(), request.getTenantId(), createdBy);
        String locationId = request.getLocationId() == null || request.getLocationId().isBlank() ? null : request.getLocationId();
        if (locationId != null && !locationDirectory.isActiveInTenant(locationId, request.getTenantId())) {
            throw new ValidationException("Localidade inexistente ou inativa");
        }

        // Regras de roteamento (Fase E): a primeira que casar sobrepõe fila, prioridade e responsável.
        RoutingDecisionDto decision = routingService.decide(new RoutingService.Context(
            topic == null ? null : topic.getId(), locationId,
            request.getPriority() == null ? Ticket.Priority.MEDIUM.name() : request.getPriority().name(),
            form.answers(), routedQueueId), request.getTenantId(), false);
        if (decision.queueId() != null) {
            routedQueueId = decision.queueId();
            routedBy = decision.source();
        }
        if (decision.priority() != null) {
            request.setPriority(Ticket.Priority.valueOf(decision.priority()));
        }
        String routedRuleId = decision.ruleId();
        String routedRuleName = decision.ruleName();
        boolean assignedByRouting = false;
        if ((request.getAssigneeId() == null || request.getAssigneeId().isBlank()) && decision.assigneeId() != null) {
            request.setAssigneeId(decision.assigneeId());
            assignedByRouting = true;
        }

        Ticket ticket;

        switch (request.getTicketType()) {
            case INCIDENT -> {
                Incident incident = Incident.builder()
                    .title(request.getTitle())
                    .description(request.getDescription())
                    .priority(request.getPriority())
                    .urgency(request.getUrgency())
                    .impact(request.getImpact())
                    .tenantId(request.getTenantId())
                    .categoryId(request.getCategoryId())
                    .assigneeId(request.getAssigneeId())
                    .reporterId(request.getReporterId())
                    .groupId(request.getGroupId())
                    .ciReference(request.getCiReference())
                    .tags(request.getTags())
                    .customFields(request.getCustomFields())
                    .createdBy(createdBy)
                    .updatedBy(createdBy)
                    .build();
                ticket = incident;
            }
            case PROBLEM -> {
                Problem problem = Problem.builder()
                    .title(request.getTitle())
                    .description(request.getDescription())
                    .priority(request.getPriority())
                    .tenantId(request.getTenantId())
                    .categoryId(request.getCategoryId())
                    .assigneeId(request.getAssigneeId())
                    .reporterId(request.getReporterId())
                    .groupId(request.getGroupId())
                    .tags(request.getTags())
                    .customFields(request.getCustomFields())
                    .createdBy(createdBy)
                    .updatedBy(createdBy)
                    .build();
                ticket = problem;
            }
            case CHANGE -> {
                Change change = Change.builder()
                    .title(request.getTitle())
                    .description(request.getDescription())
                    .priority(request.getPriority())
                    .tenantId(request.getTenantId())
                    .categoryId(request.getCategoryId())
                    .assigneeId(request.getAssigneeId())
                    .reporterId(request.getReporterId())
                    .groupId(request.getGroupId())
                    .tags(request.getTags())
                    .customFields(request.getCustomFields())
                    .createdBy(createdBy)
                    .updatedBy(createdBy)
                    .build();
                ticket = change;
            }
            default -> throw new ValidationException("Invalid ticket type");
        }

        ticket.setQueueId(routedQueueId);
        ticket.setTopicId(topic == null ? null : topic.getId());
        ticket.setLocationId(locationId);
        ticket.setFormVersionId(form.versionId());
        ticket.setFormAnswers(form.answersJson());

        Ticket saved = ticketRepository.save(ticket);
        // O ticket precisa existir no banco antes dos registros de histórico que o referenciam.
        ticketRepository.flush();

        // O SLA é aplicado antes de abrir o ciclo, que copia a definição e os prazos do chamado.
        ticketSlaService.applyOnCreate(saved);
        cycleService.openFirstCycle(saved);
        Map<String, Object> created = new LinkedHashMap<>();
        created.put("ticketNumber", saved.getTicketNumber());
        created.put("ticketType", request.getTicketType().name());
        created.put("priority", saved.getPriority().name());
        timelineService.append(saved, TicketEventType.CREATED, createdBy, TicketEvent.ActorKind.USER, created);
        evidenceService.claim(saved, stagedEvidence, createdBy);
        if (saved.getQueueId() != null) {
            // Interno: o solicitante não vê a estrutura de filas.
            Map<String, Object> routed = new LinkedHashMap<>();
            routed.put("queueId", saved.getQueueId());
            routed.put("source", routedBy);
            if (routedRuleId != null) {
                routed.put("ruleId", routedRuleId);
                routed.put("ruleName", routedRuleName);
            }
            if (saved.getTopicId() != null) {
                routed.put("topicId", saved.getTopicId());
            }
            timelineService.append(saved, TicketEventType.ROUTED, "system", TicketEvent.ActorKind.SYSTEM, routed);
        }
        if (saved.getAssigneeId() != null) {
            timelineService.append(saved, TicketEventType.ASSIGNED, assignedByRouting ? "system" : createdBy,
                assignedByRouting ? TicketEvent.ActorKind.SYSTEM : TicketEvent.ActorKind.USER,
                assignmentChange(null, saved.getAssigneeId()));
        }

        eventPublisher.publishAfterCommit(new TicketCreatedEvent(
            saved.getId(), saved.getVersion(),
            saved.getTicketNumber(), saved.getTitle(),
            request.getTicketType().name(), saved.getPriority().name(),
            saved.getCategoryId(), saved.getAssigneeId(),
            saved.getReporterId(), saved.getTenantId()
        ));

        return ticketMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public Optional<TicketDto> findById(String id) {
        return ticketRepository.findById(id).map(ticketMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<TicketDto> findByTicketNumber(String ticketNumber) {
        return ticketRepository.findByTicketNumber(ticketNumber).map(ticketMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<TicketDto> findByTenantId(String tenantId) {
        return ticketRepository.findByTenantId(tenantId).stream()
            .map(ticketMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public Page<TicketDto> findByTenantId(String tenantId, Pageable pageable) {
        return ticketRepository.findByTenantId(tenantId, pageable).map(ticketMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<TicketDto> findByTenantIdAndStatus(String tenantId, Ticket.TicketStatus status) {
        return ticketRepository.findByTenantIdAndStatus(tenantId, status).stream()
            .map(ticketMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<TicketDto> findAssignedToUser(String tenantId, String userId) {
        return ticketRepository.findByTenantIdAndAssigneeId(tenantId, userId).stream()
            .map(ticketMapper::toDto)
            .toList();
    }

    public TicketDto updateTicket(String id, UpdateTicketRequest request, String updatedBy) {
        Ticket ticket = ticketRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket", id));
        if (ticket.isClosed()) {
            throw new ValidationException("Closed tickets are read-only");
        }

        String oldAssigneeId = ticket.getAssigneeId();
        Ticket.Priority oldPriority = ticket.getPriority();
        Map<String, Object> changes = new LinkedHashMap<>();

        track(changes, "title", ticket.getTitle(), request.getTitle(), ticket::setTitle);
        track(changes, "description", ticket.getDescription(), request.getDescription(), ticket::setDescription);
        track(changes, "urgency", ticket.getUrgency(), request.getUrgency(), ticket::setUrgency);
        track(changes, "impact", ticket.getImpact(), request.getImpact(), ticket::setImpact);
        track(changes, "categoryId", ticket.getCategoryId(), request.getCategoryId(), ticket::setCategoryId);
        track(changes, "groupId", ticket.getGroupId(), request.getGroupId(), ticket::setGroupId);
        track(changes, "ciReference", ticket.getCiReference(), request.getCiReference(), ticket::setCiReference);
        track(changes, "tags", ticket.getTags(), request.getTags(), ticket::setTags);
        track(changes, "customFields", ticket.getCustomFields(), request.getCustomFields(), ticket::setCustomFields);

        if (request.getPriority() != null) ticket.setPriority(request.getPriority());
        if (request.getAssigneeId() != null) ticket.setAssigneeId(request.getAssigneeId());

        ticket.setUpdatedBy(updatedBy);
        ticket.setUpdatedAt(Instant.now());

        if (!changes.isEmpty()) {
            timelineService.append(ticket, TicketEventType.TICKET_UPDATED, updatedBy,
                TicketEvent.ActorKind.USER, changes);
        }
        if (oldPriority != ticket.getPriority()) {
            Map<String, Object> priority = new LinkedHashMap<>();
            priority.put("from", oldPriority.name());
            priority.put("to", ticket.getPriority().name());
            timelineService.append(ticket, TicketEventType.PRIORITY_CHANGED, updatedBy,
                TicketEvent.ActorKind.USER, priority);
        }
        recordAssignmentChange(ticket, oldAssigneeId, updatedBy);

        return ticketMapper.toDto(ticketRepository.save(ticket));
    }

    public TicketDto transitionTicket(String id, TransitionRequest request, String changedBy) {
        Ticket ticket = ticketRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket", id));

        Ticket.TicketStatus oldStatus = ticket.getStatus();
        Ticket.TicketStatus target = request.getTargetStatus();

        TicketLifecycle.Action action = TicketLifecycle.actionFor(oldStatus, target)
            .orElseThrow(() -> new ValidationException(
                "Invalid status transition from " + oldStatus + " to " + target));
        return performAction(ticket, action, request.getComment(), request.getResolution(), changedBy);
    }

    /**
     * Executa uma ação do ciclo de vida pelo nome, sem inferi-la do status de destino. É o caminho do solicitante
     * (responder, aceitar, contestar): responder e retomar levam ao mesmo status, e só o nome da ação diz quem age.
     */
    public TicketDto performAction(String id, TicketLifecycle.Action action, String comment, String changedBy) {
        Ticket ticket = ticketRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket", id));
        return performAction(ticket, action, comment, null, changedBy);
    }

    private TicketDto performAction(Ticket ticket, TicketLifecycle.Action action, String commentText,
                                    String resolutionText, String changedBy) {
        Ticket.TicketStatus oldStatus = ticket.getStatus();
        TicketLifecycle.Actor actor = actorFor(action, ticket, changedBy);

        Ticket.TicketStatus newStatus;
        try {
            newStatus = TicketLifecycle.apply(oldStatus, action, actor, AcceptancePolicy.manualOnly());
        } catch (TicketTransitionException e) {
            if (e.getReason() == TicketTransitionException.Reason.ACTOR_NOT_PERMITTED) {
                throw new AccessDeniedException(e.getMessage());
            }
            throw new ValidationException(e.getMessage());
        }

        Instant now = Instant.now();
        String comment = trimToNull(commentText);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", oldStatus.name());
        payload.put("to", newStatus.name());
        if (comment != null) {
            payload.put("comment", comment);
        }

        switch (action) {
            case TAKE -> {
                String assignee = ticket.getAssigneeId();
                if (assignee != null && !assignee.equals(changedBy)) {
                    throw new ValidationException("Ticket is assigned to another analyst");
                }
                ticket.setAssigneeId(changedBy);
                payload.put("assigneeId", changedBy);
                if (ticket.getFirstResponseAt() == null) {
                    ticket.setFirstResponseAt(now);
                }
                cycleService.recordFirstResponse(ticket, now);
                timelineService.append(ticket, TicketEventType.TAKEN, changedBy, TicketEvent.ActorKind.USER, payload);
            }
            case HOLD -> {
                ticketSlaService.pause(ticket, now);
                timelineService.append(ticket, TicketEventType.HELD, changedBy, TicketEvent.ActorKind.USER, payload);
            }
            case REQUEST_REQUESTER_INFO -> {
                ticketSlaService.pause(ticket, now);
                timelineService.append(ticket, TicketEventType.INFO_REQUESTED, changedBy,
                    TicketEvent.ActorKind.USER, payload);
            }
            case RESUME -> {
                ticketSlaService.resume(ticket, now);
                timelineService.append(ticket, TicketEventType.RESUMED, changedBy, TicketEvent.ActorKind.USER, payload);
            }
            case REQUESTER_REPLY -> {
                ticketSlaService.resume(ticket, now);
                timelineService.append(ticket, TicketEventType.REQUESTER_REPLIED, changedBy,
                    TicketEvent.ActorKind.USER, payload);
            }
            case RESOLVE -> {
                String solution = trimToNull(resolutionText);
                if (solution == null) {
                    throw new ValidationException("A solution is required to resolve a ticket");
                }
                payload.put("solution", solution);
                cycleService.recordResolved(ticket, now, solution, changedBy);
                ticket.setResolvedAt(now);
                // Resolvido: o relógio para e as violações deste atendimento deixam de estar ativas.
                slaBreachService.resolveByTicket(ticket.getId());
                timelineService.append(ticket, TicketEventType.RESOLVED, changedBy, TicketEvent.ActorKind.USER, payload);
                eventPublisher.publishAfterCommit(new TicketResolvedEvent(
                    ticket.getId(), ticket.getVersion(),
                    ticket.getId(), ticket.getTicketNumber(),
                    solution, changedBy, ticket.getTenantId()
                ));
            }
            case ACCEPT -> {
                cycleService.recordAccepted(ticket, now, changedBy, comment);
                ticket.setClosedAt(now);
                timelineService.append(ticket, TicketEventType.ACCEPTED, changedBy, TicketEvent.ActorKind.USER, payload);
                eventPublisher.publishAfterCommit(new TicketClosedEvent(
                    ticket.getId(), ticket.getVersion(),
                    ticket.getId(), ticket.getTicketNumber(),
                    changedBy, ticket.getTenantId()
                ));
            }
            case CONTEST -> {
                if (comment == null) {
                    throw new ValidationException("A comment is required to contest the solution");
                }
                // O evento de contestação pertence ao ciclo que está sendo encerrado.
                timelineService.append(ticket, TicketEventType.CONTESTED, changedBy, TicketEvent.ActorKind.USER, payload);
                TicketCycle next = cycleService.recordContestedAndReopen(ticket, now, changedBy, comment);

                String previousAssigneeId = ticket.getAssigneeId();
                ticket.setCycleNo(next.getCycleNo());
                ticket.setReopenCount(ticket.getReopenCount() + 1);
                // Volta para a fila operacional; a solução anterior permanece no histórico do ciclo anterior.
                ticket.setAssigneeId(null);
                ticket.setResolvedAt(null);

                Map<String, Object> reopened = new LinkedHashMap<>();
                reopened.put("reason", comment);
                reopened.put("queueId", ticket.getQueueId());
                reopened.put("previousAssigneeId", previousAssigneeId);
                timelineService.append(ticket, TicketEventType.REOPENED, changedBy, TicketEvent.ActorKind.SYSTEM,
                    reopened);
                // SLA por ciclo: o novo ciclo ganha metas próprias a partir da contestação.
                ticketSlaService.restartForNewCycle(ticket, now);
            }
        }

        ticket.setStatus(newStatus);
        ticket.setUpdatedBy(changedBy);
        ticket.setUpdatedAt(now);

        Ticket saved = ticketRepository.save(ticket);

        eventPublisher.publishAfterCommit(new TicketStatusChangedEvent(
            saved.getId(), saved.getVersion(),
            saved.getTicketNumber(), oldStatus.name(), newStatus.name(),
            changedBy, saved.getTenantId()
        ));

        return ticketMapper.toDto(saved);
    }

    /**
     * Aceitar, contestar e responder são do solicitante (quem abriu o chamado). As demais ações são
     * de analista. Quem não é o solicitante e tenta uma ação de solicitante é barrado pela máquina de estados.
     */
    private TicketLifecycle.Actor actorFor(TicketLifecycle.Action action, Ticket ticket, String userId) {
        // O principal autenticado é o e-mail; o chamado guarda o solicitante pelo ID do usuário.
        if (TicketLifecycle.requiresRequester(action) && userId != null
            && (userId.equals(ticket.getReporterId()) || access.isRequesterOf(ticket, ticket.getTenantId(), userId))) {
            return TicketLifecycle.Actor.REQUESTER;
        }
        return TicketLifecycle.Actor.ANALYST;
    }

    public TicketDto assignTicket(String id, AssignRequest request, String assignedBy) {
        Ticket ticket = ticketRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket", id));
        if (ticket.getStatus() == Ticket.TicketStatus.CLOSED || ticket.getStatus() == Ticket.TicketStatus.RESOLVED) {
            throw new ValidationException("A " + ticket.getStatus() + " ticket cannot be assigned");
        }

        String oldAssigneeId = ticket.getAssigneeId();
        ticket.setAssigneeId(request.getAssigneeId());
        if (request.getGroupId() != null) {
            ticket.setGroupId(request.getGroupId());
        }
        ticket.setUpdatedBy(assignedBy);
        ticket.setUpdatedAt(Instant.now());

        recordAssignmentChange(ticket, oldAssigneeId, assignedBy);

        Ticket saved = ticketRepository.save(ticket);

        eventPublisher.publishAfterCommit(new TicketAssignedEvent(
            saved.getId(), saved.getVersion(),
            saved.getTicketNumber(), oldAssigneeId, request.getAssigneeId(),
            assignedBy, saved.getTenantId()
        ));

        return ticketMapper.toDto(saved);
    }

    /**
     * Move o chamado para outra fila (nulo = sem fila). Fila é equipe: o responsável individual não muda,
     * e o SLA do ciclo corrente continua o mesmo.
     */
    public TicketDto changeQueue(String id, ChangeQueueRequest request, String changedBy, String tenantId) {
        Ticket ticket = ticketRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket", id));
        if (!tenantId.equals(ticket.getTenantId())) {
            throw new ResourceNotFoundException("Ticket", id);
        }
        if (ticket.isClosed()) {
            throw new ValidationException("Closed tickets are read-only");
        }
        String target = request.queueId() == null || request.queueId().isBlank()
            ? null : queueService.requireActive(request.queueId(), tenantId).getId();
        String previous = ticket.getQueueId();
        if (Objects.equals(previous, target)) {
            return ticketMapper.toDto(ticket);
        }
        ticket.setQueueId(target);
        ticket.setUpdatedBy(changedBy);
        ticket.setUpdatedAt(Instant.now());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", previous);
        payload.put("to", target);
        if (request.reason() != null && !request.reason().isBlank()) {
            payload.put("reason", request.reason().trim());
        }
        timelineService.append(ticket, TicketEventType.QUEUE_CHANGED, changedBy, TicketEvent.ActorKind.USER, payload);
        return ticketMapper.toDto(ticketRepository.save(ticket));
    }

    /** Chamados guardam o histórico completo e não são removidos. */
    public void deleteTicket(String id) {
        if (!ticketRepository.existsById(id)) {
            throw new ResourceNotFoundException("Ticket", id);
        }
        throw new ValidationException("Tickets keep their full history and cannot be deleted");
    }

    @Transactional(readOnly = true)
    public long countByTenantIdAndStatus(String tenantId, Ticket.TicketStatus status) {
        return ticketRepository.countByTenantIdAndStatus(tenantId, status);
    }

    @Transactional(readOnly = true)
    public long countAssignedToUser(String tenantId, String userId) {
        List<Ticket.TicketStatus> openStatuses = List.of(
            Ticket.TicketStatus.OPEN, Ticket.TicketStatus.IN_PROGRESS,
            Ticket.TicketStatus.WAITING, Ticket.TicketStatus.ON_HOLD
        );
        return ticketRepository.countByTenantIdAndAssigneeIdAndStatusIn(tenantId, userId, openStatuses);
    }

    private void recordAssignmentChange(Ticket ticket, String oldAssigneeId, String actorId) {
        String newAssigneeId = ticket.getAssigneeId();
        if (Objects.equals(oldAssigneeId, newAssigneeId)) {
            return;
        }
        TicketEventType type = newAssigneeId == null ? TicketEventType.UNASSIGNED
            : oldAssigneeId == null ? TicketEventType.ASSIGNED : TicketEventType.REASSIGNED;
        timelineService.append(ticket, type, actorId, TicketEvent.ActorKind.USER,
            assignmentChange(oldAssigneeId, newAssigneeId));
    }

    private static Map<String, Object> assignmentChange(String from, String to) {
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("from", from);
        change.put("to", to);
        return change;
    }

    private <T> void track(Map<String, Object> changes, String field, T current, T incoming, Consumer<T> setter) {
        if (incoming != null && !incoming.equals(current)) {
            Map<String, Object> diff = new LinkedHashMap<>();
            diff.put("from", current == null ? null : current.toString());
            diff.put("to", incoming.toString());
            changes.put(field, diff);
            setter.accept(incoming);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
