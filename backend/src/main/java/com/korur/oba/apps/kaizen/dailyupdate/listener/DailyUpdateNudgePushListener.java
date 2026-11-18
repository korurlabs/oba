package com.korur.oba.apps.kaizen.dailyupdate.listener;

// 1. Framework-level
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaizen.dailyupdate.constant.DailyUpdateConstant;
import com.korur.oba.apps.kaizen.dailyupdate.constant.DailyUpdateExceptionLogConstant;
import com.korur.oba.apps.kaizen.dailyupdate.constant.listener.DailyUpdateListenerLogConstant;
import com.korur.oba.apps.kaizen.dailyupdate.event.DailyUpdateNudgedEvent;
import com.korur.oba.connect.push.constant.PushConstant;
import com.korur.oba.connect.push.dto.PushRequest;
import com.korur.oba.connect.push.service.PushDispatchService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessQueryService;

/**
 * Sends the push of a committed nudge to the recipient's phones.
 *
 * It runs after the commit, so a nudge refused by the once-a-day key sends
 * nothing, and on the push executor, so the request that saved the nudge
 * never waits for Expo. The push carries the posting deadline in Istanbul
 * time, the rule the push module does not know.
 *
 * The recipient had Kaizen in the organization when the nudge was saved;
 * access is read again when the push goes out, so a grant revoked in between
 * stops the reminder, which is Kaizen content.
 */
@Component
@RequiredArgsConstructor
public class DailyUpdateNudgePushListener {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(DailyUpdateNudgePushListener.class);

    private final AppAccessQueryService appAccessQueryService;
    private final PushDispatchService pushDispatchService;
    private final LoggingService loggingService;

    /**
     * Dispatches the nudge push, unless the recipient may no longer open
     * Kaizen in the organization. The dispatch logs and records its own
     * delivery failures; any other failure, such as a database error while
     * reading the recipient's devices, is logged here at error level with the
     * exception, because the nudge is already committed and an async listener
     * has no caller to rethrow to. The nudge stays sent: the recipient sees
     * the reminder missing, and the sender cannot nudge again that day.
     *
     * @param event the committed nudge
     */
    @Async(PushConstant.EXECUTOR_BEAN_NAME)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNudged(DailyUpdateNudgedEvent event) {
        try {
            if (!appAccessQueryService.hasAppAccess(event.organizationId(), event.recipientId(), App.KAIZEN)) {
                loggingService.info(LOG_PREFIX, LogLayer.HANDLER,
                        DailyUpdateListenerLogConstant.DAILY_UPDATE_NUDGE_PUSH_SKIPPED_WITHOUT_KAIZEN,
                        event.nudgeId(), event.recipientId());
                return;
            }
            pushDispatchService.dispatch(PushRequest.nudge(event.organizationId(), event.recipientId(),
                    event.senderId(), event.senderName(),
                    DailyUpdateConstant.POSTING_DEADLINE.format(DailyUpdateConstant.NUDGE_DEADLINE_FORMAT)));
            loggingService.debug(LOG_PREFIX, LogLayer.HANDLER,
                    DailyUpdateListenerLogConstant.DAILY_UPDATE_NUDGE_PUSH_DISPATCHED, event.nudgeId(),
                    event.recipientId());
        } catch (RuntimeException failure) {
            loggingService.error(LOG_PREFIX, LogLayer.HANDLER,
                    DailyUpdateExceptionLogConstant.DAILY_UPDATE_NUDGE_PUSH_FAILED, failure, event.organizationId(),
                    event.nudgeId(), event.recipientId());
        }
    }
}
