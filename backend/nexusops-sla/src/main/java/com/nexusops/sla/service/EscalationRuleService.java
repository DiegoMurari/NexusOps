package com.nexusops.sla.service;

import com.nexusops.sla.domain.EscalationRule;
import com.nexusops.sla.dto.EscalationRuleDto;
import com.nexusops.sla.mapper.EscalationRuleMapper;
import com.nexusops.sla.repository.EscalationRuleRepository;
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
public class EscalationRuleService {

    private final EscalationRuleRepository escalationRuleRepository;
    private final EscalationRuleMapper escalationRuleMapper;

    public EscalationRuleDto createRule(EscalationRule rule, String createdBy) {
        if (escalationRuleRepository.findByTenantIdAndName(rule.getTenantId(), rule.getName()).isPresent()) {
            throw new ValidationException("Escalation rule with name " + rule.getName() + " already exists in tenant");
        }

        rule.setCreatedBy(createdBy);
        rule.setUpdatedBy(createdBy);

        return escalationRuleMapper.toDto(escalationRuleRepository.save(rule));
    }

    @Transactional(readOnly = true)
    public Optional<EscalationRuleDto> findById(String id) {
        return escalationRuleRepository.findById(id).map(escalationRuleMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<EscalationRuleDto> findByTenantId(String tenantId) {
        return escalationRuleRepository.findByTenantId(tenantId).stream()
            .map(escalationRuleMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<EscalationRuleDto> findActiveByTenantId(String tenantId) {
        return escalationRuleRepository.findByTenantIdAndActiveTrue(tenantId).stream()
            .map(escalationRuleMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<EscalationRuleDto> findBySlaDefinitionId(String slaDefinitionId) {
        return escalationRuleRepository.findBySlaDefinitionId(slaDefinitionId).stream()
            .map(escalationRuleMapper::toDto)
            .toList();
    }

    public EscalationRuleDto updateRule(String id, EscalationRule rule, String updatedBy) {
        EscalationRule existing = escalationRuleRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("EscalationRule", id));

        existing.setName(rule.getName());
        existing.setDescription(rule.getDescription());
        existing.setActive(rule.isActive());
        existing.setTriggerType(rule.getTriggerType());
        existing.setTriggerValue(rule.getTriggerValue());
        existing.setTriggerPercentage(rule.getTriggerPercentage());
        existing.setSlaDefinitionId(rule.getSlaDefinitionId());
        existing.setActions(rule.getActions());
        existing.setUpdatedBy(updatedBy);
        existing.setUpdatedAt(Instant.now());

        return escalationRuleMapper.toDto(escalationRuleRepository.save(existing));
    }

    public void deleteRule(String id) {
        EscalationRule rule = escalationRuleRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("EscalationRule", id));
        escalationRuleRepository.delete(rule);
    }
}