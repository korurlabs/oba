package com.korur.oba.apps.kazan.ticket.listener;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.ticket.constant.listener.TicketAgentRoutineListenerLogConstant;
import com.korur.oba.apps.kazan.ticket.event.TicketAssigneesAddedEvent;
import com.korur.oba.connect.routine.core.constant.RoutineConstant;
import com.korur.oba.connect.routine.core.dto.RoutineFireResult;
import com.korur.oba.connect.routine.core.enums.RoutineFireStatus;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.agent.routine.service.AgentRoutineService;

@ExtendWith(MockitoExtension.class)
class TicketAgentRoutineListenerUnitTest {

    private static final String TICKET_KEY = "KZN-311";

    @Mock
    private AgentRoutineService agentRoutineService;
    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private TicketAgentRoutineListener listener;

    private final UUID organizationId = UUID.randomUUID();

    @Test
    void onTicketAssigneesAdded_agentWithRoutine_firesWithTicketKey() {
        UUID agentId = UUID.randomUUID();
        when(agentRoutineService.fire(organizationId, agentId, TICKET_KEY))
                .thenReturn(Optional.of(new RoutineFireResult(RoutineFireStatus.FIRED, 200, "https://claude.ai/code/s")));

        listener.onTicketAssigneesAdded(event(List.of(agentId)));

        verify(agentRoutineService).fire(organizationId, agentId, TICKET_KEY);
        verify(loggingService).info(any(), eq(LogLayer.HANDLER), eq(TicketAgentRoutineListenerLogConstant.ROUTINE_FIRED),
                eq(organizationId), eq(TICKET_KEY), eq(agentId), eq(RoutineFireStatus.FIRED));
    }

    @Test
    void onTicketAssigneesAdded_personOrAgentWithoutRoutine_logsNoFire() {
        UUID personId = UUID.randomUUID();
        when(agentRoutineService.fire(organizationId, personId, TICKET_KEY)).thenReturn(Optional.empty());

        listener.onTicketAssigneesAdded(event(List.of(personId)));

        verify(agentRoutineService).fire(organizationId, personId, TICKET_KEY);
        verify(loggingService, never()).info(any(), any(), eq(TicketAgentRoutineListenerLogConstant.ROUTINE_FIRED),
                any(Object[].class));
    }

    @Test
    void onTicketAssigneesAdded_fireThrows_logsErrorAndFiresTheNextAssignee() {
        UUID failingAgentId = UUID.randomUUID();
        UUID nextAgentId = UUID.randomUUID();
        IllegalStateException failure = new IllegalStateException("vendor unreachable");
        when(agentRoutineService.fire(organizationId, failingAgentId, TICKET_KEY)).thenThrow(failure);
        when(agentRoutineService.fire(organizationId, nextAgentId, TICKET_KEY))
                .thenReturn(Optional.of(new RoutineFireResult(RoutineFireStatus.FAILED, 500, null)));

        assertThatCode(() -> listener.onTicketAssigneesAdded(event(List.of(failingAgentId, nextAgentId))))
                .doesNotThrowAnyException();

        verify(loggingService).error(any(), eq(LogLayer.HANDLER),
                eq(TicketAgentRoutineListenerLogConstant.ROUTINE_FIRE_FAILED), eq(failure),
                eq(organizationId), eq(TICKET_KEY), eq(failingAgentId));
        verify(agentRoutineService).fire(organizationId, nextAgentId, TICKET_KEY);
    }

    @Test
    void onTicketAssigneesAdded_runsAfterCommitOnTheRoutineExecutor() throws NoSuchMethodException {
        Method handler = TicketAgentRoutineListener.class.getMethod("onTicketAssigneesAdded",
                TicketAssigneesAddedEvent.class);

        assertThat(handler.getAnnotation(Async.class).value()).isEqualTo(RoutineConstant.EXECUTOR_BEAN_NAME);
        assertThat(handler.getAnnotation(TransactionalEventListener.class).phase())
                .isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    private TicketAssigneesAddedEvent event(List<UUID> addedUserIds) {
        return new TicketAssigneesAddedEvent(organizationId, UUID.randomUUID(), TICKET_KEY, addedUserIds);
    }
}
