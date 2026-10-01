package com.nexusops.ticketing.controller;

import com.nexusops.ticketing.dto.CommentDto;
import com.nexusops.ticketing.dto.CreateCommentRequest;
import com.nexusops.ticketing.service.CommentService;
import com.nexusops.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tickets/{ticketId}/comments")
@RequiredArgsConstructor
@Tag(name = "Comments", description = "Ticket comment endpoints")
public class CommentController {

    private final CommentService commentService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @PreAuthorize("hasPermission('TICKET', 'UPDATE')")
    @Operation(summary = "Add comment to ticket")
    public ResponseEntity<CommentDto> addComment(
            @PathVariable String ticketId,
            @Valid @RequestBody CreateCommentRequest request) {
        String authorId = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(commentService.addComment(ticketId, request, authorId));
    }

    @GetMapping
    @PreAuthorize("hasPermission('TICKET', 'READ')")
    @Operation(summary = "List comments for ticket")
    public ResponseEntity<Page<CommentDto>> listComments(
            @PathVariable String ticketId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(commentService.getCommentsByTicketId(ticketId, pageable));
    }

    @GetMapping("/all")
    @PreAuthorize("hasPermission('TICKET', 'READ')")
    @Operation(summary = "List all comments for ticket (no pagination)")
    public ResponseEntity<List<CommentDto>> listAllComments(@PathVariable String ticketId) {
        return ResponseEntity.ok(commentService.getCommentsByTicketId(ticketId));
    }

    @DeleteMapping("/{commentId}")
    @PreAuthorize("hasPermission('TICKET', 'DELETE')")
    @Operation(summary = "Delete comment")
    public ResponseEntity<Void> deleteComment(
            @PathVariable String ticketId,
            @PathVariable String commentId) {
        commentService.deleteComment(commentId);
        return ResponseEntity.ok().build();
    }
}