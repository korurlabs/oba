package com.korur.oba.apps.kaizen.chat.helper;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.properties.ChatSendRateProperties;
import com.korur.oba.dojo.exception.internal.business.TooManyRequestsException;
import com.korur.oba.dojo.logging.service.LoggingService;

class ChatSendRateLimiterUnitTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-20T10:00:00Z"));

    private ChatSendRateLimiter limiter;

    @BeforeEach
    void setUp() {
        ChatSendRateProperties properties = new ChatSendRateProperties();
        properties.setMax(5);
        properties.setWindowMs(5_000L);
        limiter = new ChatSendRateLimiter(properties, new LoggingService(), clock);
    }

    private void sendTimes(UUID userId, int times) {
        for (int index = 0; index < times; index++) {
            limiter.acquire(userId);
        }
    }

    @Test
    void fiveSendsInWindow_pass() {
        UUID userId = UUID.randomUUID();

        assertThatCode(() -> sendTimes(userId, 5)).doesNotThrowAnyException();
    }

    @Test
    void sixthSendInWindow_throwsTooManyRequests() {
        UUID userId = UUID.randomUUID();
        sendTimes(userId, 5);

        assertThatThrownBy(() -> limiter.acquire(userId)).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void otherUser_hasOwnWindow() {
        UUID userId = UUID.randomUUID();
        sendTimes(userId, 5);

        assertThatCode(() -> limiter.acquire(UUID.randomUUID())).doesNotThrowAnyException();
    }

    @Test
    void oldestSendLeavesWindow_nextSendPasses() {
        // Arrange
        UUID userId = UUID.randomUUID();
        limiter.acquire(userId);
        clock.advanceMillis(1_000L);
        sendTimes(userId, 4);

        // Act: exactly one window after the first send, it no longer counts
        clock.advanceMillis(4_000L);

        // Assert
        assertThatCode(() -> limiter.acquire(userId)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.acquire(userId)).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void refusedSend_isNotCounted() {
        // Arrange
        UUID userId = UUID.randomUUID();
        sendTimes(userId, 5);
        clock.advanceMillis(4_999L);
        assertThatThrownBy(() -> limiter.acquire(userId)).isInstanceOf(TooManyRequestsException.class);

        // Act
        clock.advanceMillis(1L);

        // Assert: the window frees all five slots, the refused send took none
        assertThatCode(() -> sendTimes(userId, 5)).doesNotThrowAnyException();
    }

    /**
     * A clock the test moves forward by hand.
     */
    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant start) {
            this.now = start;
        }

        void advanceMillis(long millis) {
            now = now.plusMillis(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
