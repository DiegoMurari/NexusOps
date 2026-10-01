package com.nexusops.ticketing.repository;

import com.nexusops.ticketing.domain.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, String> {

    List<Comment> findByTicketIdOrderByCreatedAtAsc(String ticketId);

    Page<Comment> findByTicketId(String ticketId, Pageable pageable);

    List<Comment> findByAuthorId(String authorId);
}