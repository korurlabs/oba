package com.korur.oba.apps.kaya.lesson.helper;

// 1. Framework-level
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.lesson.constant.LessonConstant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LessonDeliveryRateLimiterUnitTest {

    /** A clock the test moves by hand. */
    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-11-25T09:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
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

    private final MutableClock clock = new MutableClock();
    private final LessonDeliveryRateLimiter limiter = new LessonDeliveryRateLimiter(clock);

    private void chargeBudget(UUID tokenId) {
        for (int charge = 0; charge < LessonConstant.DELIVERY_READS_PER_MINUTE; charge++) {
            assertTrue(limiter.tryCharge(tokenId), "charge " + (charge + 1));
        }
    }

    @Test
    void tryCharge_withinTheBudget_allowsEveryRead() {
        chargeBudget(UUID.randomUUID());
    }

    @Test
    void tryCharge_overTheBudget_refusesTheNextRead() {
        UUID tokenId = UUID.randomUUID();
        chargeBudget(tokenId);

        assertFalse(limiter.tryCharge(tokenId));
        assertFalse(limiter.tryCharge(tokenId));
    }

    @Test
    void tryCharge_budgetOfOneToken_doesNotCountForAnother() {
        UUID busy = UUID.randomUUID();
        chargeBudget(busy);
        assertFalse(limiter.tryCharge(busy));

        assertTrue(limiter.tryCharge(UUID.randomUUID()));
    }

    @Test
    void tryCharge_justBeforeTheWindowEnds_stillRefuses() {
        UUID tokenId = UUID.randomUUID();
        chargeBudget(tokenId);

        clock.advance(Duration.ofSeconds(LessonConstant.DELIVERY_RATE_WINDOW_SECONDS - 1));

        assertFalse(limiter.tryCharge(tokenId));
    }

    @Test
    void tryCharge_afterTheWindowEnds_startsAFreshBudget() {
        UUID tokenId = UUID.randomUUID();
        chargeBudget(tokenId);
        assertFalse(limiter.tryCharge(tokenId));

        clock.advance(Duration.ofSeconds(LessonConstant.DELIVERY_RATE_WINDOW_SECONDS));

        chargeBudget(tokenId);
        assertFalse(limiter.tryCharge(tokenId));
    }
}
