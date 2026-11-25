package com.korur.oba.apps.kazan.ticket.listener;

// 1. Framework-level
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.ticket.constant.listener.TicketAgentRoutineListenerLogConstant;
import com.korur.oba.apps.kazan.ticket.event.TicketAssigneesAddedEvent;
import com.korur.oba.connect.routine.core.constant.RoutineConstant;
import com.korur.oba.connect.routine.core.dto.RoutineFireResult;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.agent.routine.service.AgentRoutineService;

/**
 * Fires the routine of each agent a ticket change assigned, with the ticket key as the routine's
 * only input, so the agent's hosted session starts on the ticket.
 *
 * It lives in Kazan, which owns the ticket and the event, so the agent and connector code stays free
 * of tickets. {@link AgentRoutineService#fire} answers empty for a person, an API agent, a disabled
 * agent and an agent without a routine, so every added assignee is passed on without a lookup here.
 *
 * The handler runs after the commit, so a rolled-back assignment fires nothing, and on the routine
 * executor, so the request never waits for the vendor. The fire records its own result on the
 * agent's routine, which the Agents page shows. A failure is logged at error and not re-thrown: the
 * assignment has committed and stays, the routine's daily schedule picks the ticket up, and one
 * agent's failure does not stop the next agent's fire.
 */
@Component
@RequiredArgsConstructor
public class TicketAgentRoutineListener {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(TicketAgentRoutineListener.class);

    private final AgentRoutineService agentRoutineService;
    private final LoggingService loggingService;

    /**
     * Fires the routine of every agent among the added assignees.
     *
     * @param event the committed assignment
     */
    @Async(RoutineConstant.EXECUTOR_BEAN_NAME)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTicketAssigneesAdded(TicketAssigneesAddedEvent event) {
        for (UUID userId : event.addedUserIds()) {
            fire(event, userId);
        }
    }

    private void fire(TicketAssigneesAddedEvent event, UUID userId) {
        try {
            Optional<RoutineFireResult> result = agentRoutineService.fire(
                    event.organizationId(), userId, event.ticketKey());
            if (result.isPresent()) {
                loggingService.info(LOG_PREFIX, LogLayer.HANDLER, TicketAgentRoutineListenerLogConstant.ROUTINE_FIRED,
                        event.organizationId(), event.ticketKey(), userId, result.get().status());
            }
        } catch (RuntimeException failure) {
            loggingService.error(LOG_PREFIX, LogLayer.HANDLER, TicketAgentRoutineListenerLogConstant.ROUTINE_FIRE_FAILED,
                    failure, event.organizationId(), event.ticketKey(), userId);
        }
    }
}
