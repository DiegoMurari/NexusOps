package com.nexusops.ticketing.repository;

import com.nexusops.ticketing.domain.TimeEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface TimeEntryRepository extends JpaRepository<TimeEntry, String> {

    List<TimeEntry> findByTicketId(String ticketId);

    List<TimeEntry> findByUserId(String userId);

    @Query("SELECT SUM(t.durationMinutes) FROM TimeEntry t WHERE t.ticketId = :ticketId")
    Integer getTotalDurationByTicketId(@Param("ticketId") String ticketId);

    @Query("SELECT SUM(t.durationMinutes) FROM TimeEntry t WHERE t.userId = :userId AND t.startTime BETWEEN :start AND :end")
    Integer getTotalDurationByUserIdAndDateRange(@Param("userId") String userId, @Param("start") Instant start, @Param("end") Instant end);
}