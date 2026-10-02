package com.nexusops.ticketing.service;

import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import com.nexusops.sla.service.SlaDefinitionService;
import com.nexusops.ticketing.domain.CatalogArea;
import com.nexusops.ticketing.domain.CatalogTopic;
import com.nexusops.ticketing.domain.Ticket;
import com.nexusops.ticketing.dto.CatalogAreaDto;
import com.nexusops.ticketing.dto.CatalogAreaRequest;
import com.nexusops.ticketing.dto.CatalogTopicDto;
import com.nexusops.ticketing.dto.CatalogTopicRequest;
import com.nexusops.ticketing.dto.PortalCatalogDto;
import com.nexusops.ticketing.repository.CatalogAreaRepository;
import com.nexusops.ticketing.repository.CatalogTopicRepository;
import com.nexusops.ticketing.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Catálogo de serviços (ADR-013, Fase C). A área é o rótulo que o Portal mostra; o tópico é o que o
 * solicitante escolhe e carrega os padrões internos (fila, prioridade inicial, SLA) que ele nunca vê.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CatalogService {

    private final CatalogAreaRepository areaRepository;
    private final CatalogTopicRepository topicRepository;
    private final CategoryRepository categoryRepository;
    private final QueueService queueService;
    private final SlaDefinitionService slaDefinitionService;

    // ------------------------------------------------------------------ áreas

    public CatalogAreaDto createArea(CatalogAreaRequest request, String by, String tenantId) {
        String name = required(request.name(), "O nome da área é obrigatório");
        if (areaRepository.existsByTenantIdAndNameIgnoreCase(tenantId, name)) {
            throw new ValidationException("Já existe uma área com o nome " + name);
        }
        CatalogArea area = CatalogArea.builder()
            .tenantId(tenantId).name(name)
            .description(blankToNull(request.description()))
            .icon(blankToNull(request.icon()))
            .sortOrder(request.sortOrder() == null ? 0 : request.sortOrder())
            .active(request.active() == null || request.active())
            .createdBy(by).updatedBy(by)
            .build();
        return toDto(areaRepository.save(area), 0);
    }

    public CatalogAreaDto updateArea(String id, CatalogAreaRequest request, String by, String tenantId) {
        CatalogArea area = findArea(id, tenantId);
        if (request.name() != null && !request.name().isBlank() && !request.name().trim().equals(area.getName())) {
            String name = request.name().trim();
            if (areaRepository.existsByTenantIdAndNameIgnoreCaseAndIdNot(tenantId, name, id)) {
                throw new ValidationException("Já existe uma área com o nome " + name);
            }
            area.setName(name);
        }
        if (request.description() != null) {
            area.setDescription(blankToNull(request.description()));
        }
        if (request.icon() != null) {
            area.setIcon(blankToNull(request.icon()));
        }
        if (request.sortOrder() != null) {
            area.setSortOrder(request.sortOrder());
        }
        if (request.active() != null) {
            area.setActive(request.active());
        }
        area.setUpdatedBy(by);
        CatalogArea saved = areaRepository.save(area);
        return toDto(saved, topicRepository.findByAreaIdAndTenantIdOrderBySortOrderAscNameAsc(id, tenantId).size());
    }

    @Transactional(readOnly = true)
    public List<CatalogAreaDto> listAreas(String tenantId) {
        Map<String, Long> counts = new HashMap<>();
        topicRepository.findByTenantIdOrderBySortOrderAscNameAsc(tenantId)
            .forEach(t -> counts.merge(t.getAreaId(), 1L, Long::sum));
        return areaRepository.findByTenantIdOrderBySortOrderAscNameAsc(tenantId).stream()
            .map(a -> toDto(a, counts.getOrDefault(a.getId(), 0L)))
            .toList();
    }

    public void deleteArea(String id, String tenantId) {
        CatalogArea area = findArea(id, tenantId);
        if (topicRepository.existsByAreaId(id)) {
            throw new ValidationException("A área tem tópicos; mova ou remova os tópicos, ou desative a área");
        }
        areaRepository.delete(area);
    }

    // ----------------------------------------------------------------- tópicos

    public CatalogTopicDto createTopic(CatalogTopicRequest request, String by, String tenantId) {
        String areaId = required(request.areaId(), "A área do tópico é obrigatória");
        findArea(areaId, tenantId);
        String name = required(request.name(), "O nome do tópico é obrigatório");
        if (topicRepository.existsByAreaIdAndNameIgnoreCase(areaId, name)) {
            throw new ValidationException("Já existe um tópico com o nome " + name + " nesta área");
        }
        CatalogTopic topic = CatalogTopic.builder()
            .tenantId(tenantId).areaId(areaId).name(name)
            .description(blankToNull(request.description()))
            .defaultQueueId(validQueue(request.defaultQueueId(), tenantId))
            .defaultPriority(parsePriority(request.defaultPriority()))
            .slaDefinitionId(validSla(request.slaDefinitionId(), tenantId))
            .categoryId(validCategory(request.categoryId(), tenantId))
            .sortOrder(request.sortOrder() == null ? 0 : request.sortOrder())
            .active(request.active() == null || request.active())
            .createdBy(by).updatedBy(by)
            .build();
        return toDto(topicRepository.save(topic));
    }

    public CatalogTopicDto updateTopic(String id, CatalogTopicRequest request, String by, String tenantId) {
        CatalogTopic topic = findTopic(id, tenantId);
        if (request.areaId() != null && !request.areaId().isBlank() && !request.areaId().equals(topic.getAreaId())) {
            findArea(request.areaId(), tenantId);
            topic.setAreaId(request.areaId());
        }
        if (request.name() != null && !request.name().isBlank()) {
            topic.setName(request.name().trim());
        }
        if (topicRepository.existsByAreaIdAndNameIgnoreCaseAndIdNot(topic.getAreaId(), topic.getName(), id)) {
            throw new ValidationException("Já existe um tópico com o nome " + topic.getName() + " nesta área");
        }
        if (request.description() != null) {
            topic.setDescription(blankToNull(request.description()));
        }
        if (request.defaultQueueId() != null) {
            topic.setDefaultQueueId(validQueue(request.defaultQueueId(), tenantId));
        }
        if (request.defaultPriority() != null) {
            topic.setDefaultPriority(parsePriority(request.defaultPriority()));
        }
        if (request.slaDefinitionId() != null) {
            topic.setSlaDefinitionId(validSla(request.slaDefinitionId(), tenantId));
        }
        if (request.categoryId() != null) {
            topic.setCategoryId(validCategory(request.categoryId(), tenantId));
        }
        if (request.sortOrder() != null) {
            topic.setSortOrder(request.sortOrder());
        }
        if (request.active() != null) {
            topic.setActive(request.active());
        }
        topic.setUpdatedBy(by);
        return toDto(topicRepository.save(topic));
    }

    @Transactional(readOnly = true)
    public List<CatalogTopicDto> listTopics(String tenantId, String areaId) {
        List<CatalogTopic> topics = areaId == null || areaId.isBlank()
            ? topicRepository.findByTenantIdOrderBySortOrderAscNameAsc(tenantId)
            : topicRepository.findByAreaIdAndTenantIdOrderBySortOrderAscNameAsc(areaId, tenantId);
        return topics.stream().map(this::toDto).toList();
    }

    /** Excluir só vale para tópico sem uso; chamados guardam o tópico por ID. Prefira desativar. */
    public void deleteTopic(String id, String tenantId) {
        topicRepository.delete(findTopic(id, tenantId));
    }

    /** Tópico ativo (e de área ativa) do tenant, para abrir um chamado. */
    @Transactional(readOnly = true)
    public CatalogTopic requireActiveTopic(String topicId, String tenantId) {
        CatalogTopic topic = findTopic(topicId, tenantId);
        if (!topic.isActive() || !findArea(topic.getAreaId(), tenantId).isActive()) {
            throw new ValidationException("O tópico " + topic.getName() + " não está disponível");
        }
        return topic;
    }

    // ------------------------------------------------------------------ portal

    /** O que o solicitante vê: áreas ativas com seus tópicos ativos, sem fila, prioridade ou SLA. */
    @Transactional(readOnly = true)
    public PortalCatalogDto portal(String tenantId) {
        Map<String, List<PortalCatalogDto.Topic>> byArea = new HashMap<>();
        for (CatalogTopic t : topicRepository.findByTenantIdAndActiveTrueOrderBySortOrderAscNameAsc(tenantId)) {
            byArea.computeIfAbsent(t.getAreaId(), k -> new ArrayList<>())
                .add(new PortalCatalogDto.Topic(t.getId(), t.getName(), t.getDescription()));
        }
        List<PortalCatalogDto.Area> areas = areaRepository
            .findByTenantIdAndActiveTrueOrderBySortOrderAscNameAsc(tenantId).stream()
            .filter(a -> byArea.containsKey(a.getId()))
            .map(a -> new PortalCatalogDto.Area(a.getId(), a.getName(), a.getDescription(), a.getIcon(), byArea.get(a.getId())))
            .toList();
        return new PortalCatalogDto(areas);
    }

    // ----------------------------------------------------------------- helpers

    private CatalogArea findArea(String id, String tenantId) {
        return areaRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("CatalogArea", id));
    }

    private CatalogTopic findTopic(String id, String tenantId) {
        return topicRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("CatalogTopic", id));
    }

    private String validQueue(String queueId, String tenantId) {
        if (queueId == null || queueId.isBlank()) {
            return null;
        }
        return queueService.requireActive(queueId, tenantId).getId();
    }

    private String validSla(String slaId, String tenantId) {
        if (slaId == null || slaId.isBlank()) {
            return null;
        }
        return slaDefinitionService.findById(slaId, tenantId)
            .orElseThrow(() -> new ValidationException("Definição de SLA desconhecida: " + slaId))
            .getId();
    }

    private String validCategory(String categoryId, String tenantId) {
        if (categoryId == null || categoryId.isBlank()) {
            return null;
        }
        return categoryRepository.findById(categoryId)
            .filter(c -> tenantId.equals(c.getTenantId()))
            .orElseThrow(() -> new ValidationException("Categoria desconhecida: " + categoryId))
            .getId();
    }

    private static Ticket.Priority parsePriority(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Ticket.Priority.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Prioridade inválida: " + raw);
        }
    }

    private CatalogAreaDto toDto(CatalogArea a, long topics) {
        return new CatalogAreaDto(a.getId(), a.getName(), a.getDescription(), a.getIcon(), a.getSortOrder(),
            a.isActive(), topics);
    }

    private CatalogTopicDto toDto(CatalogTopic t) {
        return new CatalogTopicDto(t.getId(), t.getAreaId(), t.getName(), t.getDescription(), t.getDefaultQueueId(),
            t.getDefaultPriority() == null ? null : t.getDefaultPriority().name(), t.getSlaDefinitionId(),
            t.getCategoryId(), t.getSortOrder(), t.isActive());
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(message);
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
