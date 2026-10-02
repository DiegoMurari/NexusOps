package com.nexusops.sla.service;

import com.nexusops.sla.domain.SlaDefinition;
import com.nexusops.sla.dto.CreateSlaDefinitionRequest;
import com.nexusops.sla.dto.SlaDefinitionDto;
import com.nexusops.sla.dto.UpdateSlaDefinitionRequest;
import com.nexusops.sla.event.SlaDefinitionChangedEvent;
import com.nexusops.sla.mapper.SlaDefinitionMapper;
import com.nexusops.sla.repository.BusinessCalendarRepository;
import com.nexusops.sla.repository.SlaDefinitionRepository;
import com.nexusops.shared.event.TransactionalEventPublisher;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Definições de SLA. Tudo é por tenant (o tenant vem do chamador, nunca do pedido). Editar uma definição
 * muda as metas dos chamados que ainda vão ser avaliados; os prazos já gravados em ciclos em andamento não
 * são reescritos (o SLA é medido por ciclo e nunca sobrescrito).
 *
 * <p>Os critérios de aplicação (tipo, categoria, prioridade, perfil) são opcionais; vazio = "qualquer".
 * Num PATCH, nulo mantém o valor e texto em branco limpa o critério.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SlaDefinitionService {

    private final SlaDefinitionRepository slaDefinitionRepository;
    private final BusinessCalendarRepository businessCalendarRepository;
    private final SlaDefinitionMapper slaDefinitionMapper;
    private final TransactionalEventPublisher eventPublisher;

    public SlaDefinitionDto createDefinition(CreateSlaDefinitionRequest request, String createdBy, String tenantId) {
        if (slaDefinitionRepository.findByTenantIdAndName(tenantId, request.getName()).isPresent()) {
            throw new ValidationException("SLA definition with name " + request.getName() + " already exists in tenant");
        }
        validateTargets(request.getResponseTimeMinutes(), request.getResolutionTimeMinutes());
        requireCalendarInTenant(request.getBusinessCalendarId(), tenantId);

        SlaDefinition definition = slaDefinitionMapper.toEntity(request);
        definition.setTenantId(tenantId);
        definition.setAppliesToType(blankToNull(request.getAppliesToType()));
        definition.setAppliesToCategory(blankToNull(request.getAppliesToCategory()));
        definition.setAppliesToPriority(blankToNull(request.getAppliesToPriority()));
        definition.setAppliesToCustomerTier(blankToNull(request.getAppliesToCustomerTier()));
        definition.setBusinessCalendarId(blankToNull(request.getBusinessCalendarId()));
        definition.setVersion(1);
        definition.setCreatedBy(createdBy);
        definition.setUpdatedBy(createdBy);

        SlaDefinition saved = slaDefinitionRepository.save(definition);
        publishChanged(saved, createdBy);
        return slaDefinitionMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public Optional<SlaDefinitionDto> findById(String id, String tenantId) {
        return slaDefinitionRepository.findById(id)
            .filter(d -> tenantId.equals(d.getTenantId()))
            .map(slaDefinitionMapper::toDto);
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

    public SlaDefinitionDto updateDefinition(String id, String tenantId, UpdateSlaDefinitionRequest request, String updatedBy) {
        SlaDefinition definition = findInTenant(id, tenantId);

        boolean changed = false;
        // O que muda a regra de negócio (critérios, metas, calendário, pausas) conta como nova revisão.
        boolean material = false;

        if (request.getName() != null && !request.getName().isBlank() && !request.getName().equals(definition.getName())) {
            slaDefinitionRepository.findByTenantIdAndName(tenantId, request.getName())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new ValidationException("SLA definition with name " + request.getName() + " already exists in tenant");
                });
            definition.setName(request.getName());
            changed = true;
        }
        if (request.getDescription() != null && !request.getDescription().equals(nz(definition.getDescription()))) {
            definition.setDescription(blankToNull(request.getDescription()));
            changed = true;
        }
        if (request.getActive() != null && request.getActive() != definition.isActive()) {
            definition.setActive(request.getActive());
            changed = true;
            material = true;
        }

        String type = criterion(request.getAppliesToType(), definition.getAppliesToType());
        String category = criterion(request.getAppliesToCategory(), definition.getAppliesToCategory());
        String priority = criterion(request.getAppliesToPriority(), definition.getAppliesToPriority());
        String tier = criterion(request.getAppliesToCustomerTier(), definition.getAppliesToCustomerTier());
        if (!Objects.equals(type, definition.getAppliesToType()) || !Objects.equals(category, definition.getAppliesToCategory())
            || !Objects.equals(priority, definition.getAppliesToPriority()) || !Objects.equals(tier, definition.getAppliesToCustomerTier())) {
            definition.setAppliesToType(type);
            definition.setAppliesToCategory(category);
            definition.setAppliesToPriority(priority);
            definition.setAppliesToCustomerTier(tier);
            changed = true;
            material = true;
        }

        Integer response = request.getResponseTimeMinutes() != null ? request.getResponseTimeMinutes() : definition.getResponseTimeMinutes();
        Integer resolution = request.getResolutionTimeMinutes() != null ? request.getResolutionTimeMinutes() : definition.getResolutionTimeMinutes();
        validateTargets(response, resolution);
        if (!Objects.equals(response, definition.getResponseTimeMinutes()) || !Objects.equals(resolution, definition.getResolutionTimeMinutes())) {
            definition.setResponseTimeMinutes(response);
            definition.setResolutionTimeMinutes(resolution);
            changed = true;
            material = true;
        }

        if (request.getBusinessCalendarId() != null) {
            String calendarId = blankToNull(request.getBusinessCalendarId());
            requireCalendarInTenant(calendarId, tenantId);
            if (!Objects.equals(calendarId, definition.getBusinessCalendarId())) {
                definition.setBusinessCalendarId(calendarId);
                changed = true;
                material = true;
            }
        }
        if (request.getPauseOnHold() != null && request.getPauseOnHold() != definition.isPauseOnHold()) {
            definition.setPauseOnHold(request.getPauseOnHold());
            changed = true;
            material = true;
        }
        if (request.getStopOnFirstResponse() != null && request.getStopOnFirstResponse() != definition.isStopOnFirstResponse()) {
            definition.setStopOnFirstResponse(request.getStopOnFirstResponse());
            changed = true;
            material = true;
        }
        if (request.getBreachWarning80() != null && request.getBreachWarning80() != definition.isBreachWarning80()) {
            definition.setBreachWarning80(request.getBreachWarning80());
            changed = true;
        }
        if (request.getBreachWarning90() != null && request.getBreachWarning90() != definition.isBreachWarning90()) {
            definition.setBreachWarning90(request.getBreachWarning90());
            changed = true;
        }

        if (!changed) {
            return slaDefinitionMapper.toDto(definition);
        }

        if (material) {
            definition.setVersion(definition.getVersion() + 1);
        }
        definition.setUpdatedBy(updatedBy);
        definition.setUpdatedAt(Instant.now());
        SlaDefinition saved = slaDefinitionRepository.save(definition);
        publishChanged(saved, updatedBy);
        return slaDefinitionMapper.toDto(saved);
    }

    public void deleteDefinition(String id, String tenantId) {
        slaDefinitionRepository.delete(findInTenant(id, tenantId));
    }

    private SlaDefinition findInTenant(String id, String tenantId) {
        return slaDefinitionRepository.findById(id)
            .filter(d -> tenantId.equals(d.getTenantId()))
            .orElseThrow(() -> new ResourceNotFoundException("SlaDefinition", id));
    }

    private void publishChanged(SlaDefinition saved, String by) {
        eventPublisher.publishAfterCommit(new SlaDefinitionChangedEvent(
            saved.getId(), saved.getVersion(), saved.getName(), saved.getTenantId(), saved.isActive(), by));
    }

    private void requireCalendarInTenant(String calendarId, String tenantId) {
        if (calendarId == null || calendarId.isBlank()) {
            return;
        }
        businessCalendarRepository.findById(calendarId)
            .filter(c -> tenantId.equals(c.getTenantId()))
            .orElseThrow(() -> new ValidationException("Unknown business calendar: " + calendarId));
    }

    /** Pelo menos uma meta, ambas positivas, e a resolução não pode ser menor que a resposta. */
    private static void validateTargets(Integer response, Integer resolution) {
        if (response == null && resolution == null) {
            throw new ValidationException("Define at least a response or a resolution time");
        }
        if ((response != null && response <= 0) || (resolution != null && resolution <= 0)) {
            throw new ValidationException("SLA times must be greater than zero minutes");
        }
        if (response != null && resolution != null && resolution < response) {
            throw new ValidationException("Resolution time cannot be shorter than response time");
        }
    }

    /** Nulo mantém o valor atual; em branco limpa; senão substitui. */
    private static String criterion(String requested, String current) {
        if (requested == null) {
            return current;
        }
        return blankToNull(requested);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
