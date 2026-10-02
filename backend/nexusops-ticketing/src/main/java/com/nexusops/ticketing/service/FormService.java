package com.nexusops.ticketing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import com.nexusops.ticketing.domain.CatalogTopic;
import com.nexusops.ticketing.domain.Ticket;
import com.nexusops.ticketing.domain.TopicFormVersion;
import com.nexusops.ticketing.dto.FormDefinitionDto;
import com.nexusops.ticketing.dto.PortalDto;
import com.nexusops.ticketing.dto.TicketFormDto;
import com.nexusops.ticketing.dto.TopicFormVersionDto;
import com.nexusops.ticketing.repository.CatalogTopicRepository;
import com.nexusops.ticketing.repository.TicketRepository;
import com.nexusops.ticketing.repository.TopicFormVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Versões do formulário de cada tópico (ADR-013, Fase D). Rascunho é editável; publicar congela a versão e
 * arquiva a anterior. O chamado guarda a versão exata com que foi aberto, então publicar uma versão nova nunca
 * muda chamados já abertos.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class FormService {

    /** Resultado de validar as respostas de um chamado novo. Sem formulário publicado, os dois são nulos. */
    public record ValidatedForm(String versionId, String answersJson, Map<String, Object> answers,
                                FormDefinitionDto.EvidenceMode evidenceMode) {
        /** Sem formulário publicado não há política de evidência: ela é opcional. */
        public static final ValidatedForm NONE_VALUE = new ValidatedForm(null, null, Map.of(), FormDefinitionDto.EvidenceMode.OPTIONAL);
    }

    private final TopicFormVersionRepository versionRepository;
    private final CatalogTopicRepository topicRepository;
    private final TicketRepository ticketRepository;
    private final FormValidationService validation;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<TopicFormVersionDto> list(String topicId, String tenantId) {
        requireTopic(topicId, tenantId);
        return versionRepository.findByTopicIdAndTenantIdOrderByVersionDesc(topicId, tenantId).stream()
            .map(this::toDto)
            .toList();
    }

    /** Abre um rascunho a partir da definição enviada, da versão publicada ou vazio, nessa ordem. */
    public TopicFormVersionDto createDraft(String topicId, FormDefinitionDto initial, String by, String tenantId) {
        requireTopic(topicId, tenantId);
        if (versionRepository.findByTopicIdAndTenantIdAndStatus(topicId, tenantId, TopicFormVersion.Status.DRAFT).isPresent()) {
            throw new ValidationException("Já existe um rascunho para este tópico; edite-o ou descarte-o");
        }
        FormDefinitionDto base = initial != null ? initial
            : findPublished(topicId, tenantId).map(v -> parse(v.getDefinition())).orElse(FormDefinitionDto.empty());
        validation.validateDefinition(base);

        TopicFormVersion draft = TopicFormVersion.builder()
            .tenantId(tenantId).topicId(topicId)
            .version(versionRepository.maxVersion(topicId) + 1)
            .status(TopicFormVersion.Status.DRAFT)
            .definition(serialize(base))
            .createdBy(by)
            .build();
        return toDto(versionRepository.save(draft));
    }

    public TopicFormVersionDto saveDraft(String versionId, FormDefinitionDto definition, String by, String tenantId) {
        TopicFormVersion version = find(versionId, tenantId);
        if (version.getStatus() != TopicFormVersion.Status.DRAFT) {
            throw new ValidationException("Só o rascunho pode ser editado; crie um novo rascunho a partir da versão publicada");
        }
        validation.validateDefinition(definition);
        version.setDefinition(serialize(definition));
        return toDto(versionRepository.save(version));
    }

    /** Publica o rascunho: a versão publicada anterior vira arquivada. */
    public TopicFormVersionDto publish(String versionId, String by, String tenantId) {
        TopicFormVersion version = find(versionId, tenantId);
        if (version.getStatus() != TopicFormVersion.Status.DRAFT) {
            throw new ValidationException("Só um rascunho pode ser publicado");
        }
        validation.validateDefinition(parse(version.getDefinition()));

        findPublished(version.getTopicId(), tenantId).ifPresent(previous -> {
            previous.setStatus(TopicFormVersion.Status.ARCHIVED);
            // O índice único de "uma publicada por tópico" exige que o arquivamento chegue antes.
            versionRepository.saveAndFlush(previous);
        });
        version.setStatus(TopicFormVersion.Status.PUBLISHED);
        version.setPublishedAt(Instant.now());
        version.setPublishedBy(by);
        return toDto(versionRepository.save(version));
    }

    public void discardDraft(String versionId, String tenantId) {
        TopicFormVersion version = find(versionId, tenantId);
        if (version.getStatus() != TopicFormVersion.Status.DRAFT) {
            throw new ValidationException("Só um rascunho pode ser descartado");
        }
        versionRepository.delete(version);
    }

    @Transactional(readOnly = true)
    public Optional<TopicFormVersion> findPublished(String topicId, String tenantId) {
        return versionRepository.findByTopicIdAndTenantIdAndStatus(topicId, tenantId, TopicFormVersion.Status.PUBLISHED);
    }

    /** O formulário que o Portal desenha para o tópico: a versão publicada, ou vazio se não houver. */
    @Transactional(readOnly = true)
    public FormDefinitionDto portalForm(String topicId, String tenantId) {
        CatalogTopic topic = requireTopic(topicId, tenantId);
        if (!topic.isActive()) {
            throw new ValidationException("O tópico " + topic.getName() + " não está disponível");
        }
        return findPublished(topicId, tenantId).map(v -> parse(v.getDefinition())).orElse(FormDefinitionDto.empty());
    }

    /**
     * Valida as respostas contra a versão publicada do tópico e devolve o que o chamado deve guardar.
     * Respostas sem formulário publicado são recusadas: não há contra o que validá-las.
     */
    @Transactional(readOnly = true)
    public ValidatedForm validateForTicket(CatalogTopic topic, Map<String, Object> answers, String tenantId) {
        Optional<TopicFormVersion> published = findPublished(topic.getId(), tenantId);
        if (published.isEmpty()) {
            if (answers != null && !answers.isEmpty()) {
                throw new ValidationException("O tópico " + topic.getName() + " não tem formulário publicado");
            }
            return ValidatedForm.NONE_VALUE;
        }
        FormDefinitionDto definition = parse(published.get().getDefinition());
        Map<String, Object> validated = validation.validateAnswers(definition, answers, tenantId);
        return new ValidatedForm(published.get().getId(), serialize(validated), validated, definition.evidence().mode());
    }

    /** Idem, a partir do ID do chamado (do tenant do chamador). */
    @Transactional(readOnly = true)
    public Optional<TicketFormDto> ticketForm(String ticketId, String tenantId) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .filter(t -> tenantId.equals(t.getTenantId()))
            .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
        return ticketForm(ticket);
    }

    /** O formulário com que o chamado foi aberto (versão exata + respostas), se tiver. */
    @Transactional(readOnly = true)
    public Optional<TicketFormDto> ticketForm(Ticket ticket) {
        if (ticket.getFormVersionId() == null) {
            return Optional.empty();
        }
        return versionRepository.findById(ticket.getFormVersionId()).map(v -> new TicketFormDto(
            v.getId(), v.getVersion(), parse(v.getDefinition()), parseAnswers(ticket.getFormAnswers())));
    }

    /** As respostas do chamado com o rótulo de cada campo (os campos ocultos nunca foram gravados). */
    @Transactional(readOnly = true)
    public List<PortalDto.Answer> answerLines(Ticket ticket) {
        Optional<TicketFormDto> form = ticketForm(ticket);
        if (form.isEmpty()) {
            return List.of();
        }
        List<PortalDto.Answer> lines = new java.util.ArrayList<>();
        Map<String, Object> given = form.get().answers();
        for (FormDefinitionDto.Field f : form.get().definition().fields()) {
            Object v = given.get(f.key());
            if (v == null || f.type() == FormDefinitionDto.FieldType.LOCATION) {
                continue;
            }
            lines.add(new PortalDto.Answer(f.label(), display(f, v)));
        }
        return lines;
    }

    private static String display(FormDefinitionDto.Field f, Object v) {
        if (v instanceof Boolean b) {
            return b ? "Sim" : "Não";
        }
        if (v instanceof Iterable<?> items) {
            List<String> labels = new java.util.ArrayList<>();
            items.forEach(i -> labels.add(optionLabel(f, String.valueOf(i))));
            return String.join(", ", labels);
        }
        return optionLabel(f, String.valueOf(v));
    }

    private static String optionLabel(FormDefinitionDto.Field f, String value) {
        return f.options().stream().filter(o -> value.equals(o.value())).map(FormDefinitionDto.Option::label)
            .findFirst().orElse(value);
    }

    // ----------------------------------------------------------------- helpers

    private CatalogTopic requireTopic(String topicId, String tenantId) {
        return topicRepository.findByIdAndTenantId(topicId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("CatalogTopic", topicId));
    }

    private TopicFormVersion find(String id, String tenantId) {
        return versionRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("FormVersion", id));
    }

    private TopicFormVersionDto toDto(TopicFormVersion v) {
        return new TopicFormVersionDto(v.getId(), v.getTopicId(), v.getVersion(), v.getStatus().name(),
            parse(v.getDefinition()), v.getCreatedAt(), v.getCreatedBy(), v.getUpdatedAt(), v.getPublishedAt(),
            v.getPublishedBy());
    }

    private FormDefinitionDto parse(String json) {
        try {
            return objectMapper.readValue(json, FormDefinitionDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Definição de formulário corrompida", e);
        }
    }

    private Map<String, Object> parseAnswers(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Respostas do formulário corrompidas", e);
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível serializar o formulário", e);
        }
    }
}
