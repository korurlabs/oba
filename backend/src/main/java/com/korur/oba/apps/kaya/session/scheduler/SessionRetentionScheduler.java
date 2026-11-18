package com.korur.oba.apps.kaya.session.scheduler;

// 1. Framework-level
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;

// 4. Internal
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.session.constant.service.SessionServiceLogConstant;
import com.korur.oba.apps.kaya.session.service.SessionService;
import com.korur.oba.dojo.time.BusinessCalendar;

/**
 * Deletes the sessions each organization no longer keeps, once a day. Retention counts from a
 * session's last event, so a session still being recorded is never deleted.
 */
@Component
@RequiredArgsConstructor
public class SessionRetentionScheduler {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(SessionRetentionScheduler.class);

    private final SessionService sessionService;
    private final LoggingService loggingService;
    private final Clock clock;

    @Scheduled(cron = "${kaya.session.retention.cron:0 30 3 * * *}", zone = BusinessCalendar.SCHEDULER_ZONE_ID)
    public void deleteExpiredSessions() {
        loggingService.info(LOG_PREFIX, LogLayer.SCHEDULER, SessionServiceLogConstant.RETENTION_STARTED);
        try {
            int deleted = sessionService.deleteExpiredSessions(clock.instant());
            loggingService.info(LOG_PREFIX, LogLayer.SCHEDULER, SessionServiceLogConstant.RETENTION_COMPLETED, deleted);
        } catch (RuntimeException e) {
            // rule-10-no-op -- reason: logged as an error for the alerting on error logs; a scheduled
            // job has no caller to rethrow to, and the next daily run deletes the same sessions.
            loggingService.error(LOG_PREFIX, LogLayer.SCHEDULER, SessionServiceLogConstant.RETENTION_FAILED, e);
        }
    }
}
