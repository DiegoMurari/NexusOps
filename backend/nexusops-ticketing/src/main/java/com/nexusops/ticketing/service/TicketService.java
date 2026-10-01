package com.nexusops.ticketing.service;

import com.nexusops.ticketing.domain.*;
import com.nexusops.ticketing.dto.*;
import com.nexusops.ticketing.event.*;
import com.nexusops.ticketing.mapper.TicketMapper;
import com.nexusops.ticketing.repository.*;
import com.nexusops.sla.service.SlaCalculationService;
import com.nexusops.shared.event.TransactionalEventPublisher;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

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
    private final SlaCalculationService slaCalculationService;
    private final AssignmentService assignmentService;
    private final WorkflowService workflowService;

    public TicketDto createTicket(CreateTicketRequest request, String createdBy) {
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

        Ticket saved = ticketRepository.save(ticket);

        eventPublisher.publishAfterCommit(new TicketCreatedEvent(
            saved.getId(), saved.getVersion(),
            saved.getTicketNumber(), saved.getTitle(),
            request.getTicketType().name(), saved.getPriority().name(),
            saved.getCategoryId(), saved.getAssigneeId(),
            saved.getReporterId(), saved.getTenantId()
        ));

        if (saved.getSlaDefinitionId() != null) {
            slaCalculationService.startSlaTimer(saved.getId());
        }

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

        if (request.getTitle() != null) ticket.setTitle(request.getTitle());
        if (request.getDescription() != null) ticket.setDescription(request.getDescription());
        if (request.getPriority() != null) ticket.setPriority(request.getPriority());
        if (request.getUrgency() != null) ticket.setUrgency(request.getUrgency());
        if (request.getImpact() != null) ticket.setImpact(request.getImpact());
        if (request.getCategoryId() != null) ticket.setCategoryId(request.getCategoryId());
        if (request.getAssigneeId() != null) ticket.setAssigneeId(request.getAssigneeId());
        if (request.getGroupId() != null) ticket.setGroupId(request.getGroupId());
        if (request.getCiReference() != null) ticket.setCiReference(request.getCiReference());
        if (request.getTags() != null) ticket.setTags(request.getTags());
        if (request.getCustomFields() != null) ticket.setCustomFields(request.getCustomFields());

        ticket.setUpdatedBy(updatedBy);
        ticket.setUpdatedAt(Instant.now());

        return ticketMapper.toDto(ticketRepository.save(ticket));
    }

    public TicketDto transitionTicket(String id, TransitionRequest request, String changedBy) {
        Ticket ticket = ticketRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket", id));

        Ticket.TicketStatus oldStatus = ticket.getStatus();
        
        if (!isValidTransition(oldStatus, request.getTargetStatus())) {
            throw new ValidationException("Invalid status transition from " + oldStatus + " to " + request.getTargetStatus());
        }

        ticket.setStatus(request.getTargetStatus());
        ticket.setUpdatedBy(changedBy);
        ticket.setUpdatedAt(Instant.now());

        if (request.getTargetStatus() == Ticket.TicketStatus.RESOLVED) {
            ticket.setResolvedAt(Instant.now());
            if (request.getResolution() != null) {
                ticket.setDescription(ticket.getDescription() + "\n\nResolution: " + request.getResolution());
            }
            eventPublisher.publishAfterCommit(new TicketResolvedEvent(
                ticket.getId(), ticket.getVersion(),
                ticket.getId(), ticket.getTicketNumber(),
                request.getResolution(), changedBy, ticket.getTenantId()
            ));
        } else if (request.getTargetStatus() == Ticket.TicketStatus.CLOSED) {
            ticket.setClosedAt(Instant.now());
            eventPublisher.publishAfterCommit(new TicketClosedEvent(
                ticket.getId(), ticket.getVersion(),
                ticket.getId(), ticket.getTicketNumber(),
                changedBy, ticket.getTenantId()
            ));
        } else if (request.getTargetStatus() == Ticket.TicketStatus.IN_PROGRESS && oldStatus == Ticket.TicketStatus.OPEN) {
            // First response
            if (ticket.getFirstResponseAt() == null) {
                ticket.setFirstResponseAt(Instant.now());
            }
        }

        Ticket saved = ticketRepository.save(ticket);

        eventPublisher.publishAfterCommit(new TicketStatusChangedEvent(
            saved.getId(), saved.getVersion(),
            saved.getTicketNumber(), oldStatus.name(), request.getTargetStatus().name(),
            changedBy, saved.getTenantId()
        ));

        return ticketMapper.toDto(saved);
    }

    private boolean isValidTransition(Ticket.TicketStatus from, Ticket.TicketStatus to) {
        return switch (from) {
            case OPEN -> to == Ticket.TicketStatus.IN_PROGRESS || to == Ticket.TicketStatus.ON_HOLD;
            case IN_PROGRESS -> to == Ticket.TicketStatus.WAITING || to == Ticket.TicketStatus.ON_HOLD 
                || to == Ticket.TicketStatus.RESOLVED;
            case WAITING -> to == Ticket.TicketStatus.IN_PROGRESS || to == Ticket.TicketStatus.ON_HOLD;
            case ON_HOLD -> to == Ticket.TicketStatus.IN_PROGRESS || to == Ticket.TicketStatus.WAITING;
            case RESOLVED -> to == Ticket.TicketStatus.CLOSED || to == Ticket.TicketStatus.REOPENED;
            case CLOSED -> to == Ticket.TicketStatus.REOPENED;
            case REOPENED -> to == Ticket.TicketStatus.IN_PROGRESS || to == Ticket.TicketStatus.ON_HOLD;
        };
    }

    public TicketDto assignTicket(String id, AssignRequest request, String assignedBy) {
        Ticket ticket = ticketRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket", id));

        String oldAssigneeId = ticket.getAssigneeId();
        ticket.setAssigneeId(request.getAssigneeId());
        if (request.getGroupId() != null) {
            ticket.setGroupId(request.getGroupId());
        }
        ticket.setUpdatedBy(assignedBy);
        ticket.setUpdatedAt(Instant.now());

        Ticket saved = ticketRepository.save(ticket);

        eventPublisher.publishAfterCommit(new TicketAssignedEvent(
            saved.getId(), saved.getVersion(),
            saved.getTicketNumber(), oldAssigneeId, request.getAssigneeId(),
            assignedBy, saved.getTenantId()
        ));

        return ticketMapper.toDto(saved);
    }

    public void deleteTicket(String id) {
        Ticket ticket = ticketRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket", id));
        ticketRepository.delete(ticket);
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
}