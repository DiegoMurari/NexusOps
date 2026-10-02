package com.nexusops.sla.service;

import com.nexusops.sla.domain.SlaBreach;
import com.nexusops.sla.dto.SlaBreachDto;
import com.nexusops.sla.dto.SlaComplianceDto;
import com.nexusops.sla.mapper.SlaBreachMapper;
import com.nexusops.sla.repository.SlaBreachRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class SlaBreachService {

    private final SlaBreachRepository slaBreachRepository;
    private final SlaBreachMapper slaBreachMapper;

    @Transactional(readOnly = true)
    public Optional<SlaBreachDto> findById(String id) {
        return slaBreachRepository.findById(id).map(slaBreachMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Page<SlaBreachDto> findByTenantId(String tenantId, Pageable pageable) {
        return slaBreachRepository.findByTenantId(tenantId, pageable).map(slaBreachMapper::toDto);
    }

    /**
     * Violações ainda abertas: não resolvidas. Reconhecer ou escalar não tira a violação da lista; só a
     * resolução do chamado (ou resolver a violação) tira.
     */
    @Transactional(readOnly = true)
    public List<SlaBreachDto> findActiveBreaches(String tenantId) {
        return slaBreachRepository.findByTenantIdAndResolvedFalse(tenantId).stream()
            .map(slaBreachMapper::toDto)
            .toList();
    }

    /**
     * Registra uma violação uma única vez por chamado, tipo e prazo estourado (o prazo identifica o ciclo).
     * Devolve verdadeiro quando criou, falso quando já existia.
     */
    public boolean recordBreach(String ticketId, String slaDefinitionId, String tenantId,
                                SlaBreach.BreachType type, Instant dueAt, Integer percentage) {
        if (slaBreachRepository.existsByTicketIdAndBreachTypeAndBreachTime(ticketId, type, dueAt)) {
            return false;
        }
        slaBreachRepository.save(SlaBreach.builder()
            .ticketId(ticketId)
            .slaDefinitionId(slaDefinitionId)
            .tenantId(tenantId)
            .breachType(type)
            .breachTime(dueAt)
            .breachPercentage(percentage)
            .build());
        return true;
    }

    /** O chamado foi resolvido: as violações dele deixam de estar ativas (o histórico permanece). */
    public void resolveByTicket(String ticketId) {
        Instant now = Instant.now();
        for (SlaBreach breach : slaBreachRepository.findByTicketIdAndResolvedFalse(ticketId)) {
            breach.setResolved(true);
            breach.setResolvedAt(now);
            slaBreachRepository.save(breach);
        }
    }

    @Transactional(readOnly = true)
    public List<SlaBreachDto> findByTicketId(String ticketId) {
        return slaBreachRepository.findByTicketId(ticketId).stream()
            .map(slaBreachMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<SlaBreachDto> findByDateRange(String tenantId, Instant start, Instant end) {
        return slaBreachRepository.findByTenantIdAndBreachTimeBetween(tenantId, start, end).stream()
            .map(slaBreachMapper::toDto)
            .toList();
    }

    public SlaBreachDto acknowledgeBreach(String id, String acknowledgedBy) {
        SlaBreach breach = slaBreachRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("SlaBreach", id));

        breach.setAcknowledged(true);
        breach.setAcknowledgedBy(acknowledgedBy);
        breach.setAcknowledgedAt(Instant.now());

        return slaBreachMapper.toDto(slaBreachRepository.save(breach));
    }

    public SlaBreachDto resolveBreach(String id) {
        SlaBreach breach = slaBreachRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("SlaBreach", id));

        breach.setResolved(true);
        breach.setResolvedAt(Instant.now());

        return slaBreachMapper.toDto(slaBreachRepository.save(breach));
    }

    @Transactional(readOnly = true)
    public SlaComplianceDto getComplianceReport(String tenantId, Instant start, Instant end) {
        List<SlaBreach> breaches = slaBreachRepository.findByTenantIdAndBreachTimeBetween(tenantId, start, end);
        
        long totalBreaches = breaches.size();
        long responseBreaches = breaches.stream()
            .filter(b -> b.getBreachType() == SlaBreach.BreachType.RESPONSE)
            .count();
        long resolutionBreaches = breaches.stream()
            .filter(b -> b.getBreachType() == SlaBreach.BreachType.RESOLUTION)
            .count();

        Map<String, Long> breachesByCategory = breaches.stream()
            .collect(Collectors.groupingBy(SlaBreach::getSlaDefinitionId, Collectors.counting()));

        return SlaComplianceDto.builder()
            .tenantId(tenantId)
            .periodStart(start)
            .periodEnd(end)
            .ticketsWithSla(0L) // Would need ticket count
            .responseBreaches(responseBreaches)
            .resolutionBreaches(resolutionBreaches)
            .responseComplianceRate(totalBreaches > 0 ? (100.0 - (responseBreaches * 100.0 / totalBreaches)) : 100.0)
            .resolutionComplianceRate(totalBreaches > 0 ? (100.0 - (resolutionBreaches * 100.0 / totalBreaches)) : 100.0)
            .overallComplianceRate(totalBreaches > 0 ? (100.0 - (totalBreaches * 100.0 / totalBreaches)) : 100.0)
            .breachesByCategory(breachesByCategory)
            .build();
    }

    @Transactional(readOnly = true)
    public long countActiveBreaches(String tenantId) {
        return slaBreachRepository.countByTenantIdAndResolvedFalse(tenantId);
    }
}