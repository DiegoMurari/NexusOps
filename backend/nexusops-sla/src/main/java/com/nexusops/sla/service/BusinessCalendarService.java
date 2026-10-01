package com.nexusops.sla.service;

import com.nexusops.sla.domain.BusinessCalendar;
import com.nexusops.sla.dto.BusinessCalendarDto;
import com.nexusops.sla.mapper.BusinessCalendarMapper;
import com.nexusops.sla.repository.BusinessCalendarRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class BusinessCalendarService {

    private final BusinessCalendarRepository businessCalendarRepository;
    private final BusinessCalendarMapper businessCalendarMapper;

    public BusinessCalendarDto createCalendar(BusinessCalendar calendar, String createdBy) {
        if (calendar.isDefaultCalendar()) {
            Optional<BusinessCalendar> existingDefault = businessCalendarRepository
                .findByTenantIdAndDefaultCalendarTrue(calendar.getTenantId());
            if (existingDefault.isPresent()) {
                throw new ValidationException("A default calendar already exists for this tenant");
            }
        }

        calendar.setCreatedBy(createdBy);
        calendar.setUpdatedBy(createdBy);

        return businessCalendarMapper.toDto(businessCalendarRepository.save(calendar));
    }

    @Transactional(readOnly = true)
    public Optional<BusinessCalendarDto> findById(String id) {
        return businessCalendarRepository.findById(id).map(businessCalendarMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<BusinessCalendarDto> findByTenantId(String tenantId) {
        return businessCalendarRepository.findByTenantId(tenantId).stream()
            .map(businessCalendarMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public Optional<BusinessCalendarDto> findDefaultByTenantId(String tenantId) {
        return businessCalendarRepository.findByTenantIdAndDefaultCalendarTrue(tenantId)
            .map(businessCalendarMapper::toDto);
    }

    public BusinessCalendarDto updateCalendar(String id, BusinessCalendar calendar, String updatedBy) {
        BusinessCalendar existing = businessCalendarRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("BusinessCalendar", id));

        if (calendar.isDefaultCalendar() && !existing.isDefaultCalendar()) {
            Optional<BusinessCalendar> otherDefault = businessCalendarRepository
                .findByTenantIdAndDefaultCalendarTrue(existing.getTenantId());
            if (otherDefault.isPresent() && !otherDefault.get().getId().equals(id)) {
                throw new ValidationException("A default calendar already exists for this tenant");
            }
        }

        existing.setName(calendar.getName());
        existing.setDescription(calendar.getDescription());
        existing.setTimezone(calendar.getTimezone());
        existing.setDefaultCalendar(calendar.isDefaultCalendar());
        existing.setBusinessHours(calendar.getBusinessHours());
        existing.setHolidays(calendar.getHolidays());
        existing.setExceptions(calendar.getExceptions());
        existing.setUpdatedBy(updatedBy);
        existing.setUpdatedAt(Instant.now());

        return businessCalendarMapper.toDto(businessCalendarRepository.save(existing));
    }

    public void deleteCalendar(String id) {
        BusinessCalendar calendar = businessCalendarRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("BusinessCalendar", id));
        
        if (calendar.isDefaultCalendar()) {
            throw new ValidationException("Cannot delete the default calendar");
        }
        
        businessCalendarRepository.delete(calendar);
    }
}