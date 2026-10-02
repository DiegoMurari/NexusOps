package com.nexusops.ticketing.service;

import com.nexusops.shared.directory.UserDirectory;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import com.nexusops.ticketing.domain.CatalogArea;
import com.nexusops.ticketing.domain.CatalogTopic;
import com.nexusops.ticketing.domain.Ticket;
import com.nexusops.ticketing.dto.CreateCommentRequest;
import com.nexusops.ticketing.dto.CreateTicketRequest;
import com.nexusops.ticketing.dto.FormDefinitionDto;
import com.nexusops.ticketing.dto.PortalDto;
import com.nexusops.ticketing.dto.TicketDto;
import com.nexusops.ticketing.dto.TicketEventDto;
import com.nexusops.ticketing.repository.CatalogAreaRepository;
import com.nexusops.ticketing.repository.CatalogTopicRepository;
import com.nexusops.ticketing.repository.TicketRepository;
import com.nexusops.ticketing.workflow.TicketLifecycle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Portal do Solicitante (ADR-013). Tudo aqui é escopado a "chamados que eu abri" e devolve só o que o solicitante
 * pode ver: etapa em linguagem dele, histórico público sem estrutura interna, evidências públicas.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PortalService {

    /** Eventos que o solicitante vê, e a chave do payload que carrega o texto de cada um. */
    private static final Map<String, String> VISIBLE_EVENTS = Map.ofEntries(
        Map.entry("CREATED", ""), Map.entry("TAKEN", ""),
        Map.entry("INFO_REQUESTED", "comment"), Map.entry("REQUESTER_REPLIED", "comment"),
        Map.entry("COMMENT_ADDED", "content"),
        Map.entry("EVIDENCE_ADDED", "fileName"), Map.entry("EVIDENCE_REMOVED", "fileName"),
        Map.entry("RESOLVED", "solution"), Map.entry("ACCEPTED", "comment"), Map.entry("AUTO_ACCEPTED", "comment"),
        Map.entry("CONTESTED", "comment"), Map.entry("REOPENED", "reason"));

    private record Me(String id, String name, String email) {
    }

    private final TicketRepository ticketRepository;
    private final TicketService ticketService;
    private final CommentService commentService;
    private final TicketTimelineService timelineService;
    private final TicketCycleService cycleService;
    private final EvidenceService evidenceService;
    private final FormService formService;
    private final CatalogTopicRepository topicRepository;
    private final CatalogAreaRepository areaRepository;
    private final UserDirectory userDirectory;

    // --------------------------------------------------------------- leitura

    /** Meus chamados: os que pedem a minha atenção primeiro, depois os mais recentes. */
    @Transactional(readOnly = true)
    public List<PortalDto.Ticket> list(String principal, String tenantId) {
        Me me = me(principal, tenantId);
        List<Ticket> mine = ticketRepository.findByTenantIdAndReporterId(tenantId, me.id());
        Map<String, CatalogTopic> topics = new HashMap<>();
        Map<String, CatalogArea> areas = new HashMap<>();
        Map<String, String> assignees = new HashMap<>();
        return mine.stream()
            .map(t -> toTicket(t, tenantId, topics, areas, assignees))
            .sorted(Comparator.comparing(PortalDto.Ticket::needsAttention).reversed()
                .thenComparing(PortalDto.Ticket::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
    }

    @Transactional(readOnly = true)
    public PortalDto.Detail detail(String id, String principal, String tenantId) {
        Me me = me(principal, tenantId);
        Ticket ticket = own(id, me, tenantId);
        return toDetail(ticket, principal, tenantId);
    }

    // ---------------------------------------------------------------- escrita

    /** Abre um pedido pelo Portal: o solicitante escolhe o tópico e responde; o servidor deriva todo o resto. */
    public PortalDto.Detail create(PortalDto.CreateRequest request, String principal, String tenantId) {
        Me me = me(principal, tenantId);
        CreateTicketRequest create = CreateTicketRequest.builder()
            .title(request.title().trim())
            .description(request.description())
            .priority(Ticket.Priority.MEDIUM) // o tópico e as regras decidem; o solicitante não escolhe
            .tenantId(tenantId)
            .reporterId(me.id())
            .topicId(request.topicId())
            .locationId(request.locationId())
            .formAnswers(request.formAnswers())
            .evidenceIds(request.evidenceIds())
            .ticketType(CreateTicketRequest.TicketType.INCIDENT)
            .build();
        TicketDto created = ticketService.createTicket(create, principal);
        return toDetail(own(created.getId(), me, tenantId), principal, tenantId);
    }

    /** Responde à equipe. Se a equipe tinha pedido informação, a resposta devolve o chamado ao atendimento. */
    public PortalDto.Detail reply(String id, String message, String principal, String tenantId) {
        Me me = me(principal, tenantId);
        Ticket ticket = own(id, me, tenantId);
        if (ticket.getStatus() == Ticket.TicketStatus.CLOSED) {
            throw new ValidationException("Este chamado está encerrado. Abra um novo pedido.");
        }
        if (ticket.getStatus() == Ticket.TicketStatus.RESOLVED) {
            throw new ValidationException("Há uma solução aguardando a sua validação: aceite ou conteste.");
        }
        if (ticket.getStatus() == Ticket.TicketStatus.WAITING) {
            ticketService.performAction(id, TicketLifecycle.Action.REQUESTER_REPLY, message, principal);
        } else {
            CreateCommentRequest comment = new CreateCommentRequest();
            comment.setContent(message.trim());
            comment.setPublicComment(true);
            commentService.addComment(id, comment, principal);
        }
        return toDetail(own(id, me, tenantId), principal, tenantId);
    }

    public PortalDto.Detail accept(String id, String comment, String principal, String tenantId) {
        Me me = me(principal, tenantId);
        own(id, me, tenantId);
        ticketService.performAction(id, TicketLifecycle.Action.ACCEPT, comment, principal);
        return toDetail(own(id, me, tenantId), principal, tenantId);
    }

    public PortalDto.Detail contest(String id, String comment, String principal, String tenantId) {
        Me me = me(principal, tenantId);
        own(id, me, tenantId);
        if (comment == null || comment.isBlank()) {
            throw new ValidationException("Explique o que não foi resolvido para contestar a solução.");
        }
        ticketService.performAction(id, TicketLifecycle.Action.CONTEST, comment, principal);
        return toDetail(own(id, me, tenantId), principal, tenantId);
    }

    // ---------------------------------------------------------------- mapeamento

    static String stage(Ticket.TicketStatus status) {
        return switch (status) {
            case OPEN -> "RECEIVED";
            case IN_PROGRESS, ON_HOLD, REOPENED -> "IN_PROGRESS";
            case WAITING -> "WAITING_YOU";
            case RESOLVED -> "AWAITING_VALIDATION";
            case CLOSED -> "CLOSED";
        };
    }

    static boolean needsAttention(String stage) {
        return "WAITING_YOU".equals(stage) || "AWAITING_VALIDATION".equals(stage);
    }

    private PortalDto.Ticket toTicket(Ticket t, String tenantId, Map<String, CatalogTopic> topics,
                                      Map<String, CatalogArea> areas, Map<String, String> assignees) {
        CatalogTopic topic = t.getTopicId() == null ? null
            : topics.computeIfAbsent(t.getTopicId(), k -> topicRepository.findByIdAndTenantId(k, tenantId).orElse(null));
        CatalogArea area = topic == null ? null
            : areas.computeIfAbsent(topic.getAreaId(), k -> areaRepository.findByIdAndTenantId(k, tenantId).orElse(null));
        String assigneeName = t.getAssigneeId() == null ? null
            : assignees.computeIfAbsent(t.getAssigneeId(), k ->
                userDirectory.findActiveByEmail(k, tenantId).map(UserDirectory.UserRef::name).orElse(""));
        String stage = stage(t.getStatus());
        return new PortalDto.Ticket(t.getId(), t.getTicketNumber(), t.getTitle(), stage, needsAttention(stage),
            area == null ? null : area.getName(), topic == null ? null : topic.getName(),
            assigneeName == null || assigneeName.isBlank() ? null : assigneeName, t.getCreatedAt(), t.getUpdatedAt());
    }

    private PortalDto.Detail toDetail(Ticket ticket, String principal, String tenantId) {
        PortalDto.Ticket head = toTicket(ticket, tenantId, new HashMap<>(), new HashMap<>(), new HashMap<>());

        List<PortalDto.Event> timeline = new ArrayList<>();
        for (TicketEventDto e : timelineService.timeline(ticket.getId(), false)) {
            String key = VISIBLE_EVENTS.get(e.type());
            if (key == null) {
                continue;
            }
            Object raw = key.isEmpty() || e.payload() == null ? null : e.payload().get(key);
            String text = raw instanceof String s && !s.isBlank() ? s : null;
            String actor = "SYSTEM".equals(e.actorKind()) ? "SYSTEM"
                : principal.equals(e.actorId()) ? "REQUESTER" : "TEAM";
            timeline.add(new PortalDto.Event(e.id(), e.type(), text, actor, e.cycleNo(), e.occurredAt()));
        }

        String pendingSolution = null;
        if ("AWAITING_VALIDATION".equals(head.stage())) {
            pendingSolution = cycleService.cycles(ticket.getId()).cycles().stream()
                .filter(c -> c.cycleNo() == ticket.getCycleNo() && c.resolution() != null)
                .map(c -> c.resolution().solutionText())
                .findFirst().orElse(null);
        }
        boolean canReply = ticket.getStatus() != Ticket.TicketStatus.CLOSED && ticket.getStatus() != Ticket.TicketStatus.RESOLVED;

        return new PortalDto.Detail(head, ticket.getDescription(), formService.answerLines(ticket), pendingSolution, canReply, timeline,
            evidenceService.list(ticket.getId(), tenantId, principal));
    }

    // ---------------------------------------------------------------- identidade

    private Me me(String principal, String tenantId) {
        UserDirectory.UserRef u = userDirectory.findActiveByEmail(principal, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("User", principal));
        return new Me(u.id(), u.name(), u.email());
    }

    /** O chamado precisa ser meu e do meu tenant; senão, finge que não existe. */
    private Ticket own(String id, Me me, String tenantId) {
        return ticketRepository.findById(id)
            .filter(t -> tenantId.equals(t.getTenantId()) && me.id().equals(t.getReporterId()))
            .orElseThrow(() -> new ResourceNotFoundException("Ticket", id));
    }
}
