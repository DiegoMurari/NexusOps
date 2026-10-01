package com.nexusops.ticketing.dto;

import com.nexusops.ticketing.domain.Comment;
import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentDto {

    private String id;
    private String ticketId;
    private String tenantId;
    private String authorId;
    private String content;
    private String contentHtml;
    private boolean publicComment;
    private String mentions;
    private Instant createdAt;
    private Instant updatedAt;
}