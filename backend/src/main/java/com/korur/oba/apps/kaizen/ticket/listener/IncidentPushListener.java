package com.korur.oba.apps.kaizen.ticket.listener;

// 1. Framework-level
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.board.service.BoardMemberQueryService;
import com.korur.oba.apps.kaizen.ticket.constant.listener.IncidentPushListenerLogConstant;
import com.korur.oba.apps.kaizen.ticket.event.IncidentOpenedEvent;
import com.korur.oba.connect.push.constant.PushConstant;
import com.korur.oba.connect.push.dto.PushRequest;
import com.korur.oba.connect.push.service.PushDispatchService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessQueryService;

/**
 * Alerts the members of a board on their phones when an urgent incident opens on it.
 *
 * The recipients are the board's members of any role who are still active members of the
 * board's organization and may open Kaizen there, read when the alert goes out: the alert
 * carries Kaizen content, so a member whose Kaizen access was revoked gets none. The access
 * service reads each member on its own. The member who opened or raised the
 * incident is dropped by the dispatcher, and so is every member who turned urgent incident
 * alerts off. The push goes out as an incident push: time-sensitive on iOS, the urgent incidents
 * channel on Android.
 *
 * The handler runs after the commit, so a rolled-back ticket alerts nobody, and on the push
 * executor, so neither the request nor a monitoring report waits for Expo.
 *
 * A failure is logged at error and not re-thrown: the incident has committed and is visible on
 * the board, in the Incidents tab and in its badge, so there is nothing to roll back and the
 * missing alert is the only loss.
 */
@Component
@RequiredArgsConstructor
public class IncidentPushListener {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(IncidentPushListener.class);

    private final BoardMemberQueryService boardMemberQueryService;
    private final AppAccessQueryService appAccessQueryService;
    private final PushDispatchService pushDispatchService;
    private final LoggingService loggingService;

    /**
     * Sends the incident push to the members of the incident's board.
     *
     * @param event the committed incident
     */
    @Async(PushConstant.EXECUTOR_BEAN_NAME)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentOpened(IncidentOpenedEvent event) {
        try {
            List<UUID> recipients = boardMemberQueryService.getMemberUserIds(List.of(event.boardId()));
            if (recipients.isEmpty()) {
                loggingService.info(LOG_PREFIX, LogLayer.HANDLER,
                        IncidentPushListenerLogConstant.INCIDENT_PUSH_NO_RECIPIENTS,
                        event.ticketKey(), event.boardId());
                return;
            }
            List<UUID> recipientsWithKaizen = recipients.stream()
                    .filter(userId -> appAccessQueryService.hasAppAccess(event.organizationId(), userId,
                            App.KAIZEN))
                    .toList();
            if (recipientsWithKaizen.isEmpty()) {
                loggingService.info(LOG_PREFIX, LogLayer.HANDLER,
                        IncidentPushListenerLogConstant.INCIDENT_PUSH_NO_RECIPIENTS_WITH_KAIZEN,
                        event.ticketKey(), event.boardId(), recipients.size());
                return;
            }

            pushDispatchService.dispatch(PushRequest.incident(
                    event.organizationId(),
                    recipientsWithKaizen,
                    event.actorId(),
                    event.boardName(),
                    event.priority().name(),
                    event.ticketKey(),
                    event.title()));

            loggingService.info(LOG_PREFIX, LogLayer.HANDLER,
                    IncidentPushListenerLogConstant.INCIDENT_PUSH_REQUESTED,
                    event.ticketKey(), event.boardId(), event.priority(), recipientsWithKaizen.size(),
                    recipients.size() - recipientsWithKaizen.size());
        } catch (RuntimeException failure) {
            loggingService.error(LOG_PREFIX, LogLayer.HANDLER,
                    IncidentPushListenerLogConstant.INCIDENT_PUSH_FAILED,
                    failure, event.ticketKey(), event.boardId());
        }
    }
}
