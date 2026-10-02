package com.nexusops.ticketing.workflow;

import com.nexusops.ticketing.domain.Ticket.TicketStatus;
import com.nexusops.ticketing.workflow.TicketLifecycle.Action;
import com.nexusops.ticketing.workflow.TicketLifecycle.Actor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketLifecycleTest {

    private static final AcceptancePolicy MANUAL = AcceptancePolicy.manualOnly();

    private static TicketStatus apply(TicketStatus from, Action action, Actor actor) {
        return TicketLifecycle.apply(from, action, actor, MANUAL);
    }

    @Test
    @DisplayName("fluxo feliz: ABERTO → EM ANDAMENTO → RESOLVIDO → FECHADO")
    void happyPath() {
        TicketStatus s = TicketStatus.OPEN;
        s = apply(s, Action.TAKE, Actor.ANALYST);
        assertThat(s).isEqualTo(TicketStatus.IN_PROGRESS);
        s = apply(s, Action.RESOLVE, Actor.ANALYST);
        assertThat(s).isEqualTo(TicketStatus.RESOLVED);
        s = apply(s, Action.ACCEPT, Actor.REQUESTER);
        assertThat(s).isEqualTo(TicketStatus.CLOSED);
    }

    @Test
    @DisplayName("contestação: RESOLVIDO → REABERTO → EM ANDAMENTO")
    void contestPath() {
        TicketStatus s = apply(TicketStatus.RESOLVED, Action.CONTEST, Actor.REQUESTER);
        assertThat(s).isEqualTo(TicketStatus.REOPENED);
        assertThat(apply(s, Action.TAKE, Actor.ANALYST)).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("analista não fecha o chamado: só o solicitante aceita")
    void analystCannotClose() {
        assertThatThrownBy(() -> apply(TicketStatus.RESOLVED, Action.ACCEPT, Actor.ANALYST))
            .isInstanceOfSatisfying(TicketTransitionException.class,
                e -> assertThat(e.getReason()).isEqualTo(TicketTransitionException.Reason.ACTOR_NOT_PERMITTED));
    }

    @Test
    @DisplayName("analista não contesta a própria solução")
    void analystCannotContest() {
        assertThatThrownBy(() -> apply(TicketStatus.RESOLVED, Action.CONTEST, Actor.ANALYST))
            .isInstanceOf(TicketTransitionException.class);
    }

    @Test
    @DisplayName("sem auto-aceite na v1: o SISTEMA não fecha o chamado")
    void systemCannotAutoAcceptInV1() {
        assertThatThrownBy(() -> apply(TicketStatus.RESOLVED, Action.ACCEPT, Actor.SYSTEM))
            .isInstanceOfSatisfying(TicketTransitionException.class,
                e -> assertThat(e.getReason()).isEqualTo(TicketTransitionException.Reason.ACTOR_NOT_PERMITTED));
    }

    @Test
    @DisplayName("aceite por SISTEMA só existe quando a política o habilita (reservado para o futuro)")
    void systemAcceptOnlyWhenPolicyEnables() {
        TicketStatus s = TicketLifecycle.apply(TicketStatus.RESOLVED, Action.ACCEPT, Actor.SYSTEM,
            new AcceptancePolicy(true));
        assertThat(s).isEqualTo(TicketStatus.CLOSED);
    }

    @Test
    @DisplayName("não é possível resolver sem estar em andamento")
    void cannotResolveFromOpen() {
        assertThatThrownBy(() -> apply(TicketStatus.OPEN, Action.RESOLVE, Actor.ANALYST))
            .isInstanceOfSatisfying(TicketTransitionException.class,
                e -> assertThat(e.getReason()).isEqualTo(TicketTransitionException.Reason.NOT_ALLOWED));
    }

    @Test
    @DisplayName("FECHADO é terminal: não reabre, não contesta, não aceita de novo")
    void closedIsTerminal() {
        for (Action action : Action.values()) {
            for (Actor actor : Actor.values()) {
                assertThatThrownBy(() -> apply(TicketStatus.CLOSED, action, actor))
                    .as("%s by %s from CLOSED", action, actor)
                    .isInstanceOf(TicketTransitionException.class);
            }
        }
        assertThat(TicketLifecycle.actionFor(TicketStatus.CLOSED, TicketStatus.REOPENED)).isEmpty();
    }

    @Test
    @DisplayName("só contesta enquanto aguarda validação")
    void contestOnlyWhileAwaitingValidation() {
        for (TicketStatus from : TicketStatus.values()) {
            if (from == TicketStatus.RESOLVED) {
                continue;
            }
            assertThatThrownBy(() -> apply(from, Action.CONTEST, Actor.REQUESTER))
                .as("contest from %s", from)
                .isInstanceOf(TicketTransitionException.class);
        }
    }

    @Test
    @DisplayName("espera e retomada")
    void holdAndResume() {
        assertThat(apply(TicketStatus.IN_PROGRESS, Action.HOLD, Actor.ANALYST)).isEqualTo(TicketStatus.ON_HOLD);
        assertThat(apply(TicketStatus.ON_HOLD, Action.RESUME, Actor.ANALYST)).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(apply(TicketStatus.IN_PROGRESS, Action.REQUEST_REQUESTER_INFO, Actor.ANALYST))
            .isEqualTo(TicketStatus.WAITING);
        assertThat(apply(TicketStatus.WAITING, Action.REQUESTER_REPLY, Actor.REQUESTER))
            .isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("actionFor mapeia pares de status para a ação correta")
    void actionForMapping() {
        assertThat(TicketLifecycle.actionFor(TicketStatus.OPEN, TicketStatus.IN_PROGRESS)).contains(Action.TAKE);
        assertThat(TicketLifecycle.actionFor(TicketStatus.REOPENED, TicketStatus.IN_PROGRESS)).contains(Action.TAKE);
        assertThat(TicketLifecycle.actionFor(TicketStatus.ON_HOLD, TicketStatus.IN_PROGRESS)).contains(Action.RESUME);
        assertThat(TicketLifecycle.actionFor(TicketStatus.WAITING, TicketStatus.IN_PROGRESS)).contains(Action.RESUME);
        assertThat(TicketLifecycle.actionFor(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED)).contains(Action.RESOLVE);
        assertThat(TicketLifecycle.actionFor(TicketStatus.RESOLVED, TicketStatus.CLOSED)).contains(Action.ACCEPT);
        assertThat(TicketLifecycle.actionFor(TicketStatus.RESOLVED, TicketStatus.REOPENED)).contains(Action.CONTEST);
        assertThat(TicketLifecycle.actionFor(TicketStatus.OPEN, TicketStatus.CLOSED)).isEmpty();
        assertThat(TicketLifecycle.actionFor(TicketStatus.OPEN, TicketStatus.RESOLVED)).isEmpty();
    }

    @Test
    @DisplayName("ações exclusivas do solicitante")
    void requesterOnlyActions() {
        assertThat(TicketLifecycle.requiresRequester(Action.ACCEPT)).isTrue();
        assertThat(TicketLifecycle.requiresRequester(Action.CONTEST)).isTrue();
        assertThat(TicketLifecycle.requiresRequester(Action.RESOLVE)).isFalse();
    }
}
