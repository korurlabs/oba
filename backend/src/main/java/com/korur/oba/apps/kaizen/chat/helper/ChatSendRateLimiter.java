package com.korur.oba.apps.kaizen.chat.helper;

// 1. Framework-level
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// 2. Third-party

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.constant.ChatExceptionLogConstant;
import com.korur.oba.apps.kaizen.chat.properties.ChatSendRateProperties;
import com.korur.oba.dojo.exception.internal.business.TooManyRequestsException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Limits how fast one user may send chat messages: at most max sends within
 * a sliding window, 5 in 5 seconds by default.
 *
 * A burst of messages would flood every member's channel and fan out unread
 * updates to every online session, so a client stuck in a loop is refused
 * before anything is saved. The ingress limit counts requests from all users
 * together and would not stop one user. The state is in memory, which holds
 * for the single replica the backend runs on; a restart forgets it, which at
 * worst allows one extra burst.
 */
@Component
public class ChatSendRateLimiter {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ChatSendRateLimiter.class);

    private final ChatSendRateProperties properties;
    private final LoggingService loggingService;
    private final Clock clock;

    /**
     * Send times per user, oldest first. A user's deque holds at most max
     * entries, so the map stays small: one short deque per user who has sent.
     */
    private final Map<UUID, Deque<Instant>> sendTimesByUser = new ConcurrentHashMap<>();

    @Autowired
    public ChatSendRateLimiter(ChatSendRateProperties properties, LoggingService loggingService) {
        this(properties, loggingService, Clock.systemUTC());
    }

    ChatSendRateLimiter(ChatSendRateProperties properties, LoggingService loggingService, Clock clock) {
        this.properties = properties;
        this.loggingService = loggingService;
        this.clock = clock;
    }

    /**
     * Counts one send of the user, or refuses it when the user has already
     * sent max messages within the window. A refused send is not counted, so
     * a client that waits gets through as soon as its oldest send leaves the
     * window.
     *
     * @param userId the sender
     * @throws TooManyRequestsException GEN_API_004 when the user sends too fast
     */
    public void acquire(UUID userId) {
        Instant now = clock.instant();
        Instant windowStart = now.minusMillis(properties.getWindowMs());
        AtomicBoolean allowed = new AtomicBoolean(false);
        // compute runs atomically per key, so concurrent sends of one user
        // cannot both take the last slot.
        sendTimesByUser.compute(userId, (key, sendTimes) -> {
            Deque<Instant> times = sendTimes == null ? new ArrayDeque<>() : sendTimes;
            while (!times.isEmpty() && !times.peekFirst().isAfter(windowStart)) {
                times.pollFirst();
            }
            if (times.size() < properties.getMax()) {
                times.addLast(now);
                allowed.set(true);
            }
            return times.isEmpty() ? null : times;
        });
        if (!allowed.get()) {
            loggingService.warn(LOG_PREFIX, LogLayer.HELPER, ChatExceptionLogConstant.CHAT_SEND_RATE_EXCEEDED, userId);
            throw new TooManyRequestsException();
        }
    }
}
