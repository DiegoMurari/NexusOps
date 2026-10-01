package com.nexusops.sla.service;

import com.nexusops.sla.domain.SlaDefinition;
import com.nexusops.sla.dto.CreateSlaDefinitionRequest;
import com.nexusops.sla.dto.SlaDefinitionDto;
import com.nexusops.sla.dto.UpdateSlaDefinitionRequest;
import com.nexusops.sla.event.SlaDefinitionChangedEvent;
import com.nexusops.sla.mapper.SlaDefinitionMapper;
import com.nexusops.sla.repository.SlaDefinitionRepository;
import com.nexusops.shared.event.TransactionalEventPublisher;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class SlaDefinitionService {

    private final SlaDefinitionRepository slaDefinitionRepository;
    private final SlaDefinitionMapper slaDefinitionMapper;
    private final TransactionalEventPublisher eventPublisher;

    public SlaDefinitionDto createDefinition(CreateSlaDefinitionRequest request, String createdBy) {
        if (slaDefinitionRepository.findByTenantIdAndName(request.getTenantId(), request.getName()).isPresent()) {
            throw new ValidationException("SLA definition with name " + request.getName() + " already exists in tenant");
        }

        SlaDefinition definition = slaDefinitionMapper.toEntity(request);
        definition.setCreatedBy(createdBy);
        definition.setUpdatedBy(createdBy);

        SlaDefinition saved = slaDefinitionRepository.save(definition);

        eventPublisher.publishAfterCommit(new SlaDefinitionChangedEvent(
            saved.getId(), saved.getVersion(),
            saved.getName(), saved.getTenantId(),
            saved.isActive(), createdBy
        ));

        return slaDefinitionMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "sla-definitions", key = "#id")
    public Optional<SlaDefinitionDto> findById(String id) {
        return slaDefinitionRepository.findById(id).map(slaDefinitionMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<SlaDefinitionDto> findByTenantId(String tenantId) {
        return slaDefinitionRepository.findByTenantId(tenantId).stream()
            .map(slaDefinitionMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<SlaDefinitionDto> findActiveByTenantId(String tenantId) {
        return slaDefinitionRepository.findByTenantIdAndActiveTrue(tenantId).stream()
            .map(slaDefinitionMapper::toDto)
            .toList();
    }

    public SlaDefinitionDto updateDefinition(String id, UpdateSlaDefinitionRequest request, String updatedBy) {
        SlaDefinition definition = slaDefinitionRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("SlaDefinition", id));

        boolean changed = false;

        if (request.getName() != null && !request.getName().equals(definition.getName())) {
            definition.setName(request.getName());
            definition.setVersion(definition.getVersion() + 1);
            changed = true;
        }
        if (request.getDescription() != null) {
            definition.setDescription(request.getDescription());
            changed = true;
        }
        if (request.getActive() != null && request.getActive() != definition.isActive()) {
            definition.setActive(request.getActive());
            changed = true;
        }
        if (request.getAppliesToType() != null) {
            definition.setAppliesToType(request.getAppliesToType());
            changed = true;
        }
        if (request.getAppliesToCategory() != null) {
            definition.setAppliesToCategory(request.getAppliesToCategory());
            changed = true;
        }
        if (request.getAppliesToPriority() != null) {
            definition.setAppliesToPriority(request.getAppliesToPriority());
            changed = true;
        }
        if (request.getAppliesToCustomerTier() != null) {
            definition.setAppliesToCustomerTier(request.getAppliesToCustomerTier());
            changed = true;
        }
        if (request.getResponseTimeMinutes() != null) {
            definition.setResponseTimeMinutes(request.getResponseTimeMinutes());
            changed = true;
        }
        if (request.getResolutionTimeMinutes() != null) {
            definition.setResolutionTimeMinutes(request.getResolutionTimeMinutes());
            changed = true;
        }
        if (request.getBusinessCalendarId() != null) {
            definition.setBusinessCalendarId(request.getBusinessCalendarId());
            changed = true;
        }
        if (request.getPauseOnHold() != null) {
            definition.setPauseOnHold(request.getPauseOnHold());
            changed = true;
        }
        if (request.getStopOnFirstResponse() != null) {
            definition.setStopOnFirstResponse(request.getStopOnFirstResponse());
            changed = true;
        }
        if (request.getBreachWarning80() != null) {
            definition.setBreachWarning80(request.getBreachWarning80());
            changed = true;
        }
        if (request.getBreachWarning90() != null) {
            definition.setBreachWarning90(request.getBreachWarning90());
            changed = true;
        }

        if (changed) {
            definition.setUpdatedBy(updatedBy);
            definition.setUpdatedAt(Instant.now());
            SlaDefinition saved = slaDefinitionRepository.save(definition);

            eventPublisher.publishAfterCommit(new SlaDefinitionChangedEvent(
                saved.getId(), saved.getVersion(),
                saved.getName(), saved.getTenantId(),
                saved.isActive(), updatedBy
            ));

            return slaDefinitionMapper.toDto(saved);
        }

        return slaDefinitionMapper.toDto(definition);
    }

    @CacheEvict(value = "sla-definitions", key = "#id")
    public void deleteDefinition(String id) {
        SlaDefinition definition = slaDefinitionRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("SlaDefinition", id));
        slaDefinitionRepository.delete(definition);
    }
}