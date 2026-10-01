package com.nexusops.ticketing.service;

import com.nexusops.ticketing.domain.Comment;
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
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CommentService {

    private final CommentRepository commentRepository;
    private final TicketRepository ticketRepository;
    private final CommentMapper commentMapper;
    private final TransactionalEventPublisher eventPublisher;

    public CommentDto addComment(String ticketId, CreateCommentRequest request, String authorId) {
        ticketRepository.findById(ticketId)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        Comment comment = commentMapper.toEntity(request);
        comment.setTicketId(ticketId);
        comment.setTenantId(ticketRepository.findById(ticketId).get().getTenantId());
        comment.setAuthorId(authorId);
        comment.setCreatedAt(Instant.now());
        comment.setUpdatedAt(Instant.now());

        Comment saved = commentRepository.save(comment);

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

    public void deleteComment(String id) {
        commentRepository.deleteById(id);
    }
}