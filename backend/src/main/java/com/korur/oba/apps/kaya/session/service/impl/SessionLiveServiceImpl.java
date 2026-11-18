package com.korur.oba.apps.kaya.session.service.impl;

// 1. Framework-level
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// 2. Third-party

// 3. Java SDK
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// 4. Internal
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.session.constant.SessionConstant;
import com.korur.oba.apps.kaya.session.constant.service.SessionServiceLogConstant;
import com.korur.oba.apps.kaya.session.dto.response.SessionUpdatedStreamResponse;
import com.korur.oba.apps.kaya.session.event.SessionUpdatedEvent;
import com.korur.oba.apps.kaya.session.mapper.SessionMapper;
import com.korur.oba.apps.kaya.session.service.SessionLiveService;

/**
 * Holds the open session streams of this instance by organization. The streams live in memory, so
 * with several instances a client sees the changes made through the instance it is connected
 * to; the client's periodic list refresh covers the rest.
 */
@Service
public class SessionLiveServiceImpl implements SessionLiveService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(SessionLiveServiceImpl.class);

    private final Map<UUID, Set<SseEmitter>> emittersByOrganization = new ConcurrentHashMap<>();
    private final SessionMapper sessionMapper;
    private final LoggingService loggingService;
    private final Clock clock;
    private final Duration streamTimeout;

    public SessionLiveServiceImpl(SessionMapper sessionMapper, LoggingService loggingService, Clock clock,
                               @Value("${kaya.session.stream.timeout:PT1H}") Duration streamTimeout) {
        this.sessionMapper = sessionMapper;
        this.loggingService = loggingService;
        this.clock = clock;
        this.streamTimeout = streamTimeout;
    }

    @Override
    public SseEmitter subscribe(User user, UUID organizationId) {
        SseEmitter emitter = new SseEmitter(streamTimeout.toMillis());
        Set<SseEmitter> emitters = emittersByOrganization.computeIfAbsent(organizationId, id -> ConcurrentHashMap.newKeySet());
        emitters.add(emitter);
        emitter.onCompletion(() -> remove(organizationId, emitter));
        // Completing on timeout ends the response normally; left alone, the timeout would be
        // dispatched as an AsyncRequestTimeoutException although a client reopening is routine.
        emitter.onTimeout(() -> {
            remove(organizationId, emitter);
            emitter.complete();
        });
        emitter.onError(error -> remove(organizationId, emitter));
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, SessionServiceLogConstant.STREAM_SUBSCRIBED,
                user.getId(), organizationId, emitters.size());

        send(organizationId, emitter, SessionConstant.STREAM_EVENT_HEARTBEAT, heartbeat());
        return emitter;
    }

    /**
     * Runs after the commit and off the committing thread, so a slow or dead client never holds
     * up an ingest batch or a member's request.
     */
    @Override
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSessionUpdated(SessionUpdatedEvent event) {
        Set<SseEmitter> emitters = emittersByOrganization.get(event.organizationId());
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, SessionServiceLogConstant.STREAM_PUBLISHING,
                event.sessionId(), event.organizationId(), emitters.size());
        SessionUpdatedStreamResponse data = sessionMapper.toUpdatedStreamResponse(event.sessionId(), event.lastEventAt());
        for (SseEmitter emitter : emitters) {
            send(event.organizationId(), emitter, SessionConstant.STREAM_EVENT_SESSION_UPDATED, data);
        }
    }

    /**
     * Keeps idle streams open through proxies that close a silent connection, and finds the
     * streams whose client has gone away.
     */
    @Scheduled(fixedRateString = "${kaya.session.stream.heartbeat:PT25S}", initialDelayString = "${kaya.session.stream.heartbeat:PT25S}")
    public void sendHeartbeats() {
        Map<String, Object> heartbeat = heartbeat();
        for (Map.Entry<UUID, Set<SseEmitter>> entry : emittersByOrganization.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                send(entry.getKey(), emitter, SessionConstant.STREAM_EVENT_HEARTBEAT, heartbeat);
            }
        }
    }

    /**
     * Ends every open stream when the application shuts down. A stream stays open for up to an
     * hour, so graceful shutdown would otherwise wait out its whole grace period for them; the
     * clients reconnect to another instance.
     */
    @EventListener(ContextClosedEvent.class)
    public void completeAll() {
        int closed = 0;
        for (Map.Entry<UUID, Set<SseEmitter>> entry : emittersByOrganization.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                remove(entry.getKey(), emitter);
                try {
                    emitter.complete();
                } catch (IllegalStateException e) {
                    // rule-10-no-op -- reason: the container refuses to complete a stream whose
                    // async request has already errored; that stream is over and dropped, and the
                    // loop must still reach the other open streams so shutdown does not wait on them.
                    loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, SessionServiceLogConstant.STREAM_COMPLETE_FAILED,
                            entry.getKey());
                }
                closed++;
            }
        }
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, SessionServiceLogConstant.STREAMS_COMPLETED_ON_SHUTDOWN, closed);
    }

    private void send(UUID organizationId, SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException | IllegalStateException e) {
            // A failed send means the client has gone or the stream has completed; the stream is
            // dropped so later events skip it, and the client opens a new one when it comes back.
            // The stream is not completed here: after an I/O error the container ends the request
            // itself and refuses a completion from this thread, and that refusal escaping would stop
            // the caller's loop before it reaches the organization's other streams.
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, SessionServiceLogConstant.STREAM_SEND_FAILED,
                    eventName, organizationId);
            remove(organizationId, emitter);
        }
    }

    private Map<String, Object> heartbeat() {
        return Map.of(SessionConstant.STREAM_FIELD_AT, clock.instant());
    }

    private void remove(UUID organizationId, SseEmitter emitter) {
        Set<SseEmitter> emitters = emittersByOrganization.get(organizationId);
        if (emitters != null && emitters.remove(emitter)) {
            emittersByOrganization.computeIfPresent(organizationId, (id, open) -> open.isEmpty() ? null : open);
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, SessionServiceLogConstant.STREAM_CLOSED,
                    organizationId, emitters.size());
        }
    }
}
