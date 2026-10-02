package com.nexusops.ticketing.workflow;

import com.nexusops.ticketing.domain.Ticket.TicketStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Máquina de estados do atendimento (ADR-013). Pura: sem Spring, sem banco, sem relógio.
 *
 * <p>Fluxo obrigatório: ABERTO → EM ANDAMENTO → RESOLVIDO → FECHADO, e RESOLVIDO → REABERTO → EM ANDAMENTO.
 * RESOLVIDO significa que o técnico apresentou a solução; FECHADO significa que o solicitante aceitou.
 */
public final class TicketLifecycle {

    public enum Action {
        TAKE, HOLD, REQUEST_REQUESTER_INFO, RESUME, REQUESTER_REPLY, RESOLVE, ACCEPT, CONTEST
    }

    public enum Actor {
        ANALYST, REQUESTER, SYSTEM
    }

    private record Rule(Set<TicketStatus> from, TicketStatus to, Set<Actor> actors) {
    }

    private static final Map<Action, Rule> RULES = new EnumMap<>(Action.class);

    static {
        RULES.put(Action.TAKE, new Rule(
            EnumSet.of(TicketStatus.OPEN, TicketStatus.REOPENED), TicketStatus.IN_PROGRESS,
            EnumSet.of(Actor.ANALYST)));
        RULES.put(Action.HOLD, new Rule(
            EnumSet.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS, TicketStatus.WAITING, TicketStatus.REOPENED),
            TicketStatus.ON_HOLD, EnumSet.of(Actor.ANALYST)));
        RULES.put(Action.REQUEST_REQUESTER_INFO, new Rule(
            EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.ON_HOLD), TicketStatus.WAITING,
            EnumSet.of(Actor.ANALYST)));
        RULES.put(Action.RESUME, new Rule(
            EnumSet.of(TicketStatus.WAITING, TicketStatus.ON_HOLD), TicketStatus.IN_PROGRESS,
            EnumSet.of(Actor.ANALYST)));
        RULES.put(Action.REQUESTER_REPLY, new Rule(
            EnumSet.of(TicketStatus.WAITING), TicketStatus.IN_PROGRESS,
            EnumSet.of(Actor.REQUESTER, Actor.SYSTEM)));
        RULES.put(Action.RESOLVE, new Rule(
            EnumSet.of(TicketStatus.IN_PROGRESS), TicketStatus.RESOLVED,
            EnumSet.of(Actor.ANALYST)));
        // Somente o solicitante fecha. O aceite por SISTEMA só existirá quando a política o habilitar.
        RULES.put(Action.ACCEPT, new Rule(
            EnumSet.of(TicketStatus.RESOLVED), TicketStatus.CLOSED,
            EnumSet.of(Actor.REQUESTER)));
        // Contestar só é possível enquanto aguarda validação (RESOLVIDO). FECHADO é terminal na v1.
        RULES.put(Action.CONTEST, new Rule(
            EnumSet.of(TicketStatus.RESOLVED), TicketStatus.REOPENED,
            EnumSet.of(Actor.REQUESTER)));
    }

    private TicketLifecycle() {
    }

    /** Aplica a ação e devolve o novo status, ou lança {@link TicketTransitionException}. */
    public static TicketStatus apply(TicketStatus from, Action action, Actor actor, AcceptancePolicy policy) {
        Rule rule = RULES.get(action);
        if (!rule.from().contains(from)) {
            throw new TicketTransitionException(TicketTransitionException.Reason.NOT_ALLOWED,
                "Action " + action + " is not allowed from status " + from);
        }
        boolean actorAllowed = rule.actors().contains(actor)
            || (action == Action.ACCEPT && actor == Actor.SYSTEM && policy.autoAcceptEnabled());
        if (!actorAllowed) {
            throw new TicketTransitionException(TicketTransitionException.Reason.ACTOR_NOT_PERMITTED,
                "Actor " + actor + " cannot perform " + action);
        }
        return rule.to();
    }

    /** Descobre qual ação leva de um status a outro. Vazio quando não existe caminho direto. */
    public static Optional<Action> actionFor(TicketStatus from, TicketStatus to) {
        Optional<Action> match = Optional.empty();
        for (Map.Entry<Action, Rule> entry : RULES.entrySet()) {
            Rule rule = entry.getValue();
            if (rule.to() == to && rule.from().contains(from)) {
                // RESUME e REQUESTER_REPLY compartilham origem e destino: RESUME é a ação do analista.
                if (match.isEmpty() || entry.getKey() == Action.RESUME) {
                    match = Optional.of(entry.getKey());
                }
            }
        }
        return match;
    }

    /** Ações que só o solicitante pode executar. */
    public static boolean requiresRequester(Action action) {
        return action == Action.ACCEPT || action == Action.CONTEST || action == Action.REQUESTER_REPLY;
    }
}
