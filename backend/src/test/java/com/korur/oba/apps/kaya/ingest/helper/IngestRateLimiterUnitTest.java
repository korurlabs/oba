package com.korur.oba.apps.kaya.ingest.helper;

// 1. Framework-level
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.ingest.constant.IngestConstant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngestRateLimiterUnitTest {

    /** A clock the test moves by hand. */
    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-10-20T09:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final IngestRateLimiter limiter = new IngestRateLimiter(clock);

    private void chargeBudget(UUID tokenId) {
        for (int charge = 0; charge < IngestConstant.BATCHES_PER_MINUTE; charge++) {
            assertTrue(limiter.tryCharge(tokenId), "charge " + (charge + 1));
        }
    }

    @Test
    void tryCharge_withinTheBudget_allowsEveryBatch() {
        chargeBudget(UUID.randomUUID());
    }

    @Test
    void tryCharge_overTheBudget_refusesTheNextBatch() {
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

        clock.advance(Duration.ofSeconds(IngestConstant.RATE_WINDOW_SECONDS - 1));

        assertFalse(limiter.tryCharge(tokenId));
    }

    @Test
    void tryCharge_afterTheWindowEnds_startsAFreshBudget() {
        UUID tokenId = UUID.randomUUID();
        chargeBudget(tokenId);
        assertFalse(limiter.tryCharge(tokenId));

        clock.advance(Duration.ofSeconds(IngestConstant.RATE_WINDOW_SECONDS));

        chargeBudget(tokenId);
        assertFalse(limiter.tryCharge(tokenId));
    }
}
