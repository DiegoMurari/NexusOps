package com.nexusops.ticketing.service;

import com.nexusops.ticketing.domain.TimeEntry;
import com.nexusops.ticketing.dto.TimeEntryDto;
import com.nexusops.ticketing.dto.CreateTimeEntryRequest;
import com.nexusops.ticketing.mapper.TimeEntryMapper;
import com.nexusops.ticketing.repository.TimeEntryRepository;
import com.nexusops.ticketing.repository.TicketRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TimeTrackingService {

    private final TimeEntryRepository timeEntryRepository;
    private final TicketRepository ticketRepository;
    private final TimeEntryMapper timeEntryMapper;

    public TimeEntryDto addTimeEntry(String ticketId, CreateTimeEntryRequest request) {
        ticketRepository.findById(ticketId)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        TimeEntry timeEntry = timeEntryMapper.toEntity(request);
        timeEntry.setTicketId(ticketId);
        timeEntry.setTenantId(ticketRepository.findById(ticketId).get().getTenantId());

        return timeEntryMapper.toDto(timeEntryRepository.save(timeEntry));
    }

    @Transactional(readOnly = true)
    public List<TimeEntryDto> getTimeEntriesByTicketId(String ticketId) {
        return timeEntryRepository.findByTicketId(ticketId).stream()
            .map(timeEntryMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public Integer getTotalDurationByTicketId(String ticketId) {
        return timeEntryRepository.getTotalDurationByTicketId(ticketId);
    }

    @Transactional(readOnly = true)
    public List<TimeEntryDto> getTimeEntriesByUserId(String userId) {
        return timeEntryRepository.findByUserId(userId).stream()
            .map(timeEntryMapper::toDto)
            .toList();
    }

    public void deleteTimeEntry(String id) {
        timeEntryRepository.deleteById(id);
    }
}