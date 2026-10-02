package com.nexusops.ticketing.service;

import com.nexusops.shared.exception.ValidationException;
import com.nexusops.ticketing.domain.Comment;
import com.nexusops.ticketing.domain.Ticket;
import com.nexusops.ticketing.domain.TicketEvent;
import com.nexusops.ticketing.domain.TicketEventType;
import com.nexusops.ticketing.dto.CommentDto;
import com.nexusops.ticketing.dto.CreateCommentRequest;
import com.nexusops.ticketing.event.CommentAddedEvent;
import com.nexusops.ticketing.mapper.CommentMapper;
import com.nexusops.ticketing.repository.CommentRepository;
import com.nexusops.ticketing.repository.TicketRepository;
import com.nexusops.shared.event.TransactionalEventPublisher;
import com.nexusops.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class CommentService {

    private final CommentRepository commentRepository;
    private final TicketRepository ticketRepository;
    private final CommentMapper commentMapper;
    private final TransactionalEventPublisher eventPublisher;
    private final TicketTimelineService timelineService;

    public CommentDto addComment(String ticketId, CreateCommentRequest request, String authorId) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        Comment comment = commentMapper.toEntity(request);
        comment.setTicketId(ticketId);
        comment.setTenantId(ticketRepository.findById(ticketId).get().getTenantId());
        comment.setAuthorId(authorId);
        comment.setCreatedAt(Instant.now());
        comment.setUpdatedAt(Instant.now());

        Comment saved = commentRepository.save(comment);

        // O comentário é copiado para a timeline imutável: o histórico não depende da tabela de comentários.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("commentId", saved.getId());
        payload.put("public", request.isPublicComment());
        payload.put("content", saved.getContent());
        timelineService.append(ticket,
            request.isPublicComment() ? TicketEventType.COMMENT_ADDED : TicketEventType.INTERNAL_NOTE_ADDED,
            authorId, TicketEvent.ActorKind.USER, payload);

        eventPublisher.publishAfterCommit(new CommentAddedEvent(
            saved.getId(), saved.getVersion(),
            ticketId, ticketRepository.findById(ticketId).get().getTicketNumber(),
            authorId, request.isPublicComment(), comment.getTenantId()
        ));

        return commentMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<CommentDto> getCommentsByTicketId(String ticketId) {
        return commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
            .map(commentMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public Page<CommentDto> getCommentsByTicketId(String ticketId, Pageable pageable) {
        return commentRepository.findByTicketId(ticketId, pageable).map(commentMapper::toDto);
    }

    /** Comentários fazem parte do histórico do chamado e não são removidos. */
    public void deleteComment(String id) {
        if (!commentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Comment", id);
        }
        throw new ValidationException("Comments are part of the ticket history and cannot be deleted");
    }
}