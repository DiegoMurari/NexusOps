package com.nexusops.ticketing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusops.shared.directory.LocationDirectory;
import com.nexusops.shared.directory.UserDirectory;
import com.nexusops.shared.exception.FieldValidationException;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import com.nexusops.ticketing.domain.CatalogTopic;
import com.nexusops.ticketing.domain.QueueMember;
import com.nexusops.ticketing.domain.RoutingRule;
import com.nexusops.ticketing.domain.ServiceQueue;
import com.nexusops.ticketing.domain.Ticket;
import com.nexusops.ticketing.dto.RoutingDecisionDto;
import com.nexusops.ticketing.dto.RoutingRuleDto;
import com.nexusops.ticketing.dto.RoutingRuleRequest;
import com.nexusops.ticketing.dto.RoutingSimulationRequest;
import com.nexusops.ticketing.repository.CatalogAreaRepository;
import com.nexusops.ticketing.repository.CatalogTopicRepository;
import com.nexusops.ticketing.repository.QueueMemberRepository;
import com.nexusops.ticketing.repository.RoutingRuleRepository;
import com.nexusops.ticketing.repository.ServiceQueueRepository;
import com.nexusops.ticketing.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Roteamento configurável (ADR-013, Fase E). Parte dos padrões do tópico (fila e prioridade) e deixa a primeira
 * regra ativa que casar sobrepor fila, prioridade e responsável. O responsável é opcional e independente da
 * fila: regra pode só mandar para a fila e deixar o chamado sem responsável.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RoutingService {

    private static final Pattern ANSWER_KEY = Pattern.compile("^[a-z][a-zA-Z0-9_]{0,39}$");
    private static final int MAX_VALUES = 50;
    private static final int MAX_CONDITIONS = 10;
    private static final List<Ticket.TicketStatus> OPEN = List.of(
        Ticket.TicketStatus.OPEN, Ticket.TicketStatus.IN_PROGRESS, Ticket.TicketStatus.REOPENED,
        Ticket.TicketStatus.WAITING, Ticket.TicketStatus.ON_HOLD);

    /** O que o roteamento precisa saber para decidir. Respostas já validadas quando vêm de um chamado real. */
    public record Context(String topicId, String locationId, String priority, Map<String, Object> answers, String fallbackQueueId) {
    }

    private final RoutingRuleRepository ruleRepository;
    private final CatalogTopicRepository topicRepository;
    private final CatalogAreaRepository areaRepository;
    private final ServiceQueueRepository queueRepository;
    private final QueueMemberRepository memberRepository;
    private final TicketRepository ticketRepository;
    private final QueueService queueService;
    private final UserDirectory userDirectory;
    private final LocationDirectory locationDirectory;
    private final RoutingEngineService engine;
    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------------ CRUD

    @Transactional(readOnly = true)
    public List<RoutingRuleDto> list(String tenantId) {
        return ruleRepository.findByTenantIdOrderByPositionAsc(tenantId).stream().map(this::toDto).toList();
    }

    public RoutingRuleDto create(RoutingRuleRequest request, String by, String tenantId) {
        Map<String, String> errors = new LinkedHashMap<>();
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty()) {
            errors.put("name", "O nome da regra é obrigatório");
        } else if (ruleRepository.existsByTenantIdAndNameIgnoreCase(tenantId, name)) {
            errors.put("name", "Já existe uma regra com o nome " + name);
        }
        List<RoutingRuleDto.Condition> conditions = request.conditions() == null ? List.of() : request.conditions();
        validateConditions(conditions, tenantId, errors);
        validateActions(request.actions(), tenantId, errors);
        throwIfAny(errors);

        RoutingRule rule = RoutingRule.builder()
            .tenantId(tenantId).name(name).description(blankToNull(request.description()))
            .position(ruleRepository.maxPosition(tenantId) + 1)
            .active(request.active() == null || request.active())
            .conditions(serialize(normalize(conditions)))
            .actions(serialize(normalize(request.actions())))
            .createdBy(by).updatedBy(by)
            .build();
        return toDto(ruleRepository.save(rule));
    }

    public RoutingRuleDto update(String id, RoutingRuleRequest request, String by, String tenantId) {
        RoutingRule rule = find(id, tenantId);
        Map<String, String> errors = new LinkedHashMap<>();

        if (request.name() != null && !request.name().isBlank() && !request.name().trim().equals(rule.getName())) {
            String name = request.name().trim();
            if (ruleRepository.existsByTenantIdAndNameIgnoreCaseAndIdNot(tenantId, name, id)) {
                errors.put("name", "Já existe uma regra com o nome " + name);
            } else {
                rule.setName(name);
            }
        }
        if (request.description() != null) {
            rule.setDescription(blankToNull(request.description()));
        }
        if (request.active() != null) {
            rule.setActive(request.active());
        }
        if (request.conditions() != null) {
            validateConditions(request.conditions(), tenantId, errors);
            rule.setConditions(serialize(normalize(request.conditions())));
        }
        if (request.actions() != null) {
            validateActions(request.actions(), tenantId, errors);
            rule.setActions(serialize(normalize(request.actions())));
        }
        throwIfAny(errors);
        rule.setUpdatedBy(by);
        return toDto(ruleRepository.save(rule));
    }

    public void delete(String id, String tenantId) {
        ruleRepository.delete(find(id, tenantId));
    }

    /** Define a ordem de avaliação. Exige exatamente os IDs de regra do tenant, sem repetir nem faltar. */
    public List<RoutingRuleDto> reorder(List<String> ids, String tenantId) {
        List<RoutingRule> all = ruleRepository.findByTenantIdOrderByPositionAsc(tenantId);
        Set<String> expected = new HashSet<>();
        all.forEach(r -> expected.add(r.getId()));
        if (ids.size() != expected.size() || !expected.equals(new HashSet<>(ids))) {
            throw new ValidationException("A nova ordem deve conter todas as regras, cada uma uma vez");
        }
        Map<String, RoutingRule> byId = new LinkedHashMap<>();
        all.forEach(r -> byId.put(r.getId(), r));
        int position = 1;
        for (String id : ids) {
            byId.get(id).setPosition(position++);
        }
        ruleRepository.saveAll(byId.values());
        return list(tenantId);
    }

    // -------------------------------------------------------------- decisão

    /** Simulador: o mesmo caminho do chamado real, com rastro, sem gravar nada. */
    @Transactional(readOnly = true)
    public RoutingDecisionDto simulate(RoutingSimulationRequest request, String tenantId) {
        String priority = request.priority() == null || request.priority().isBlank()
            ? Ticket.Priority.MEDIUM.name() : parsePriority(request.priority());
        if (request.topicId() != null && !request.topicId().isBlank()) {
            topicRepository.findByIdAndTenantId(request.topicId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("CatalogTopic", request.topicId()));
        }
        return decide(new Context(blankToNull(request.topicId()), blankToNull(request.locationId()), priority,
            request.answers(), null), tenantId, true);
    }

    /**
     * Decide fila, prioridade e responsável. Padrões do tópico primeiro; depois a primeira regra ativa que
     * casar sobrepõe o que define. Sem rastro (chamado real), a lista de avaliação vem vazia.
     */
    @Transactional(readOnly = true)
    public RoutingDecisionDto decide(Context ctx, String tenantId, boolean withTrace) {
        List<String> notes = new ArrayList<>();
        CatalogTopic topic = ctx.topicId() == null ? null
            : topicRepository.findByIdAndTenantId(ctx.topicId(), tenantId).orElse(null);

        String queueId = topic == null ? null : topic.getDefaultQueueId();
        String source = queueId == null ? "NONE" : "TOPIC";
        String priority = topic != null && topic.getDefaultPriority() != null ? topic.getDefaultPriority().name() : ctx.priority();
        if (queueId == null && ctx.fallbackQueueId() != null) {
            queueId = ctx.fallbackQueueId();
            source = "MANUAL";
        }

        List<RoutingRuleDto> active = ruleRepository.findByTenantIdAndActiveTrueOrderByPositionAsc(tenantId).stream()
            .map(this::toDto).toList();
        var evaluations = engine.evaluateAll(active, new RoutingEngineService.Context(
            topic == null ? null : topic.getId(), topic == null ? null : topic.getAreaId(), ctx.locationId(), priority, ctx.answers()));
        var winner = engine.firstMatch(evaluations);

        String assigneeEmail = null;
        String assigneeName = null;
        String ruleId = null;
        String ruleName = null;
        if (winner.isPresent()) {
            RoutingRuleDto rule = winner.get().rule();
            RoutingRuleDto.Actions a = rule.actions();
            ruleId = rule.id();
            ruleName = rule.name();
            source = "RULE";
            if (a.queueId() != null) {
                if (isActiveQueue(a.queueId(), tenantId)) {
                    queueId = a.queueId();
                } else {
                    notes.add("A fila da regra \"" + rule.name() + "\" não está mais ativa; mantida a fila anterior");
                }
            }
            if (a.priority() != null) {
                priority = a.priority().trim().toUpperCase();
            }
            UserDirectory.UserRef assignee = resolveAssignee(a, queueId, tenantId, notes);
            if (assignee != null) {
                assigneeEmail = assignee.email();
                assigneeName = assignee.name();
            }
        }

        String queueName = queueId == null ? null
            : queueRepository.findByIdAndTenantId(queueId, tenantId).map(ServiceQueue::getName).orElse(null);
        List<RoutingDecisionDto.RuleTrace> trace = new ArrayList<>();
        if (withTrace) {
            for (var e : evaluations) {
                boolean applied = winner.isPresent() && winner.get().rule().id().equals(e.rule().id());
                trace.add(new RoutingDecisionDto.RuleTrace(e.rule().id(), e.rule().name(), e.matched(), applied, e.conditions()));
            }
        }
        return new RoutingDecisionDto(winner.isPresent() || queueId != null ? source : "NONE", queueId, queueName, priority,
            assigneeEmail, assigneeName, ruleId, ruleName, trace, notes);
    }

    private UserDirectory.UserRef resolveAssignee(RoutingRuleDto.Actions a, String queueId, String tenantId, List<String> notes) {
        RoutingRuleDto.AssigneeStrategy strategy = a.assigneeStrategy() == null ? RoutingRuleDto.AssigneeStrategy.NONE : a.assigneeStrategy();
        switch (strategy) {
            case USER -> {
                var user = userDirectory.findActive(a.assigneeId(), tenantId);
                if (user.isEmpty()) {
                    notes.add("O responsável da regra está inativo ou não existe; o chamado fica sem responsável");
                }
                return user.orElse(null);
            }
            case LEAST_LOADED -> {
                if (queueId == null) {
                    notes.add("Sem fila para escolher o responsável de menor carga");
                    return null;
                }
                List<String> memberIds = memberRepository.findByQueueId(queueId).stream().map(QueueMember::getUserId).toList();
                var candidates = userDirectory.findByIds(memberIds, tenantId).values().stream()
                    .filter(UserDirectory.UserRef::active).toList();
                if (candidates.isEmpty()) {
                    notes.add("A fila não tem membros ativos; o chamado fica sem responsável");
                    return null;
                }
                return candidates.stream()
                    .min(Comparator
                        .comparingLong((UserDirectory.UserRef u) -> ticketRepository.countByTenantIdAndAssigneeIdAndStatusIn(tenantId, u.email(), OPEN))
                        .thenComparing(UserDirectory.UserRef::name, String.CASE_INSENSITIVE_ORDER))
                    .orElse(null);
            }
            default -> {
                return null;
            }
        }
    }

    private boolean isActiveQueue(String queueId, String tenantId) {
        return queueRepository.findByIdAndTenantId(queueId, tenantId).map(ServiceQueue::isActive).orElse(false);
    }

    // ------------------------------------------------------------ validação

    private void validateConditions(List<RoutingRuleDto.Condition> conditions, String tenantId, Map<String, String> errors) {
        if (conditions.size() > MAX_CONDITIONS) {
            errors.put("conditions", "No máximo " + MAX_CONDITIONS + " condições");
            return;
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < conditions.size(); i++) {
            RoutingRuleDto.Condition c = conditions.get(i);
            String at = "conditions[" + i + "]";
            String field = c.field() == null ? "" : c.field().trim();
            boolean answer = field.startsWith(RoutingRuleDto.ANSWER_PREFIX);
            boolean known = Set.of("TOPIC", "AREA", "LOCATION", "PRIORITY").contains(field)
                || (answer && ANSWER_KEY.matcher(field.substring(RoutingRuleDto.ANSWER_PREFIX.length())).matches());
            if (!known) {
                errors.put(at + ".field", "Campo de condição inválido (use TOPIC, AREA, LOCATION, PRIORITY ou ANSWER:chave)");
                continue;
            }
            if (!seen.add(field)) {
                errors.put(at + ".field", "O campo " + field + " já foi usado em outra condição");
                continue;
            }
            List<String> values = c.values().stream().map(String::trim).filter(v -> !v.isEmpty()).distinct().toList();
            if (values.isEmpty() || values.size() > MAX_VALUES) {
                errors.put(at + ".values", "Informe de 1 a " + MAX_VALUES + " valores");
                continue;
            }
            for (String v : values) {
                String problem = checkValue(field, v, tenantId);
                if (problem != null) {
                    errors.put(at + ".values", problem);
                    break;
                }
            }
        }
    }

    private String checkValue(String field, String value, String tenantId) {
        return switch (field) {
            case "TOPIC" -> topicRepository.findByIdAndTenantId(value, tenantId).isPresent() ? null : "Tópico desconhecido: " + value;
            case "AREA" -> areaRepository.findByIdAndTenantId(value, tenantId).isPresent() ? null : "Área desconhecida: " + value;
            case "LOCATION" -> locationDirectory.isActiveInTenant(value, tenantId) ? null : "Localidade inexistente ou inativa: " + value;
            case "PRIORITY" -> {
                try {
                    Ticket.Priority.valueOf(value.toUpperCase());
                    yield null;
                } catch (IllegalArgumentException e) {
                    yield "Prioridade inválida: " + value;
                }
            }
            default -> null; // respostas de formulário aceitam qualquer texto
        };
    }

    private void validateActions(RoutingRuleDto.Actions a, String tenantId, Map<String, String> errors) {
        if (a == null) {
            errors.put("actions", "Defina ao menos uma ação (fila, prioridade ou responsável)");
            return;
        }
        RoutingRuleDto.AssigneeStrategy strategy = a.assigneeStrategy() == null ? RoutingRuleDto.AssigneeStrategy.NONE : a.assigneeStrategy();
        boolean hasQueue = a.queueId() != null && !a.queueId().isBlank();
        boolean hasPriority = a.priority() != null && !a.priority().isBlank();
        if (!hasQueue && !hasPriority && strategy == RoutingRuleDto.AssigneeStrategy.NONE) {
            errors.put("actions", "Defina ao menos uma ação (fila, prioridade ou responsável)");
            return;
        }
        if (hasQueue) {
            try {
                queueService.requireActive(a.queueId(), tenantId);
            } catch (RuntimeException e) {
                errors.put("actions.queueId", "Fila desconhecida ou inativa");
            }
        }
        if (hasPriority) {
            try {
                Ticket.Priority.valueOf(a.priority().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.put("actions.priority", "Prioridade inválida: " + a.priority());
            }
        }
        if (strategy == RoutingRuleDto.AssigneeStrategy.USER
            && (a.assigneeId() == null || userDirectory.findActive(a.assigneeId(), tenantId).isEmpty())) {
            errors.put("actions.assigneeId", "Informe um usuário ativo como responsável");
        }
        if (strategy == RoutingRuleDto.AssigneeStrategy.LEAST_LOADED && !hasQueue) {
            errors.put("actions.assigneeStrategy", "O responsável de menor carga é escolhido entre os membros da fila da regra: defina a fila");
        }
    }

    private static void throwIfAny(Map<String, String> errors) {
        if (!errors.isEmpty()) {
            throw new FieldValidationException("Regra de roteamento inválida", errors);
        }
    }

    private static String parsePriority(String raw) {
        try {
            return Ticket.Priority.valueOf(raw.trim().toUpperCase()).name();
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Prioridade inválida: " + raw);
        }
    }

    private static RoutingRuleDto.Actions normalize(RoutingRuleDto.Actions a) {
        return new RoutingRuleDto.Actions(blankToNull(a.queueId()),
            a.priority() == null || a.priority().isBlank() ? null : a.priority().trim().toUpperCase(),
            a.assigneeStrategy() == null ? RoutingRuleDto.AssigneeStrategy.NONE : a.assigneeStrategy(),
            blankToNull(a.assigneeId()));
    }

    private static List<RoutingRuleDto.Condition> normalize(List<RoutingRuleDto.Condition> conditions) {
        return conditions.stream().map(c -> new RoutingRuleDto.Condition(
            c.field().trim(),
            c.values().stream().map(String::trim).filter(v -> !v.isEmpty()).distinct()
                .map(v -> "PRIORITY".equals(c.field().trim()) ? v.toUpperCase() : v).toList())).toList();
    }

    // -------------------------------------------------------------- helpers

    private RoutingRule find(String id, String tenantId) {
        return ruleRepository.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new ResourceNotFoundException("RoutingRule", id));
    }

    private RoutingRuleDto toDto(RoutingRule r) {
        try {
            List<RoutingRuleDto.Condition> conditions = objectMapper.readValue(r.getConditions(), new TypeReference<>() { });
            RoutingRuleDto.Actions actions = objectMapper.readValue(r.getActions(), RoutingRuleDto.Actions.class);
            return new RoutingRuleDto(r.getId(), r.getName(), r.getDescription(), r.getPosition(), r.isActive(), conditions, actions);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Regra de roteamento corrompida: " + r.getId(), e);
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível serializar a regra", e);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
