package com.korur.oba.apps.kaya.lesson.helper;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
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
            assertTrue(limiter.tryChargeRead(tokenId), "charge " + (charge + 1));
        }
    }

    @Nested
    class TokenReads {

        @Test
        void tryChargeRead_withinTheBudget_allowsEveryRead() {
            chargeBudget(UUID.randomUUID());
        }

        @Test
        void tryChargeRead_overTheBudget_refusesTheNextRead() {
            UUID tokenId = UUID.randomUUID();
            chargeBudget(tokenId);

            assertFalse(limiter.tryChargeRead(tokenId));
            assertFalse(limiter.tryChargeRead(tokenId));
        }

        @Test
        void tryChargeRead_budgetOfOneToken_doesNotCountForAnother() {
            UUID busy = UUID.randomUUID();
            chargeBudget(busy);
            assertFalse(limiter.tryChargeRead(busy));

            assertTrue(limiter.tryChargeRead(UUID.randomUUID()));
        }

        @Test
        void tryChargeRead_justBeforeTheWindowEnds_stillRefuses() {
            UUID tokenId = UUID.randomUUID();
            chargeBudget(tokenId);

            clock.advance(Duration.ofSeconds(LessonConstant.DELIVERY_RATE_WINDOW_SECONDS - 1));

            assertFalse(limiter.tryChargeRead(tokenId));
        }

        @Test
        void tryChargeRead_afterTheWindowEnds_startsAFreshBudget() {
            UUID tokenId = UUID.randomUUID();
            chargeBudget(tokenId);
            assertFalse(limiter.tryChargeRead(tokenId));

            clock.advance(Duration.ofSeconds(LessonConstant.DELIVERY_RATE_WINDOW_SECONDS));

            chargeBudget(tokenId);
            assertFalse(limiter.tryChargeRead(tokenId));
        }
    }

    @Nested
    class OrganizationReads {

        private void chargeOrganizationBudget(UUID organizationId) {
            for (int charge = 0; charge < LessonConstant.DELIVERY_ORGANIZATION_READS_PER_MINUTE; charge++) {
                assertTrue(limiter.tryChargeOrganizationRead(organizationId), "charge " + (charge + 1));
            }
        }

        @Test
        void organizationBudget_coversTenMachinesThatEachReadEveryPageWithTwoRestarts() {
            // One read of the CLI: at most 100 pages, started again at most twice.
            int teamReads = 10 * 100 * 3;

            assertTrue(teamReads <= LessonConstant.DELIVERY_ORGANIZATION_READS_PER_MINUTE);
            assertTrue(LessonConstant.DELIVERY_ORGANIZATION_READS_PER_MINUTE > LessonConstant.DELIVERY_READS_PER_MINUTE);
        }

        @Test
        void tryChargeOrganizationRead_overTheBudget_refusesThatOrganizationOnly() {
            UUID busy = UUID.randomUUID();
            chargeOrganizationBudget(busy);

            assertFalse(limiter.tryChargeOrganizationRead(busy));
            assertTrue(limiter.tryChargeOrganizationRead(UUID.randomUUID()));
        }

        @Test
        void tryChargeOrganizationRead_afterTheWindowEnds_startsAFreshBudget() {
            UUID organizationId = UUID.randomUUID();
            chargeOrganizationBudget(organizationId);
            assertFalse(limiter.tryChargeOrganizationRead(organizationId));

            clock.advance(Duration.ofSeconds(LessonConstant.DELIVERY_RATE_WINDOW_SECONDS));

            assertTrue(limiter.tryChargeOrganizationRead(organizationId));
        }

        @Test
        void tryChargeOrganizationRead_keyedLikeAToken_sharesNothingWithTheTokenReads() {
            // Organizations and tokens are both UUIDs; the budgets keep separate tables.
            UUID id = UUID.randomUUID();
            chargeBudget(id);
            assertFalse(limiter.tryChargeRead(id));

            assertTrue(limiter.tryChargeOrganizationRead(id));
        }
    }

    @Nested
    class UserReads {

        private void chargeUserBudget(UUID userId) {
            for (int charge = 0; charge < LessonConstant.DELIVERY_USER_READS_PER_MINUTE; charge++) {
                assertTrue(limiter.tryChargeUserRead(userId), "charge " + (charge + 1));
            }
        }

        @Test
        void userBudget_coversFourMachinesThatEachReadEveryPageWithTwoRestarts() {
            assertTrue(LessonConstant.DELIVERY_USER_READS_PER_MINUTE >= 4 * 3 * 100);
            assertTrue(LessonConstant.DELIVERY_USER_READS_PER_MINUTE < LessonConstant.DELIVERY_ORGANIZATION_READS_PER_MINUTE);
        }

        @Test
        void tryChargeUserRead_overTheBudget_refusesThatUserOnly() {
            UUID busy = UUID.randomUUID();
            chargeUserBudget(busy);

            assertFalse(limiter.tryChargeUserRead(busy));
            assertTrue(limiter.tryChargeUserRead(UUID.randomUUID()));
        }

        @Test
        void tryChargeUserRead_overTheBudget_holdsWhateverOrganizationTheTokenBelongsTo() {
            // The reads of one person through the tokens of many organizations share one budget: the
            // key is the user, so a fresh organization and a fresh token start no fresh budget.
            UUID userId = UUID.randomUUID();
            chargeUserBudget(userId);
            UUID freshOrganization = UUID.randomUUID();
            UUID freshToken = UUID.randomUUID();

            assertTrue(limiter.tryChargeRead(freshToken));
            assertFalse(limiter.tryChargeUserRead(userId));
            assertTrue(limiter.tryChargeOrganizationRead(freshOrganization));
        }

        @Test
        void tryChargeUserRead_afterTheWindowEnds_startsAFreshBudget() {
            UUID userId = UUID.randomUUID();
            chargeUserBudget(userId);
            assertFalse(limiter.tryChargeUserRead(userId));

            clock.advance(Duration.ofSeconds(LessonConstant.DELIVERY_RATE_WINDOW_SECONDS));

            assertTrue(limiter.tryChargeUserRead(userId));
        }

        @Test
        void tryChargeUserRead_keyedLikeAToken_sharesNothingWithTheOtherBudgets() {
            UUID key = UUID.randomUUID();
            chargeUserBudget(key);

            assertTrue(limiter.tryChargeRead(key));
            assertTrue(limiter.tryChargeOrganizationRead(key));
        }
    }

    @Nested
    class Proposals {

        private void chargeProposalBudget(UUID tokenId) {
            for (int charge = 0; charge < LessonConstant.DELIVERY_PROPOSALS_PER_MINUTE; charge++) {
                assertTrue(limiter.tryChargeProposal(tokenId), "charge " + (charge + 1));
            }
        }

        @Test
        void proposalBudget_isFarBelowTheReadBudget() {
            assertTrue(LessonConstant.DELIVERY_PROPOSALS_PER_MINUTE * 10 <= LessonConstant.DELIVERY_READS_PER_MINUTE);
        }

        @Test
        void tryChargeProposal_overTheBudget_refusesTheNextLessonOfThatTokenOnly() {
            UUID tokenId = UUID.randomUUID();
            chargeProposalBudget(tokenId);

            assertFalse(limiter.tryChargeProposal(tokenId));
            assertTrue(limiter.tryChargeProposal(UUID.randomUUID()));
        }

        @Test
        void tryChargeProposal_overTheBudget_leavesTheReadsOfTheTokenAlone() {
            UUID tokenId = UUID.randomUUID();
            chargeProposalBudget(tokenId);
            assertFalse(limiter.tryChargeProposal(tokenId));

            chargeBudget(tokenId);
        }

        @Test
        void tryChargeProposal_afterTheWindowEnds_startsAFreshBudget() {
            UUID tokenId = UUID.randomUUID();
            chargeProposalBudget(tokenId);
            assertFalse(limiter.tryChargeProposal(tokenId));

            clock.advance(Duration.ofSeconds(LessonConstant.DELIVERY_RATE_WINDOW_SECONDS));

            chargeProposalBudget(tokenId);
            assertFalse(limiter.tryChargeProposal(tokenId));
        }
    }

    @Nested
    class Pruning {

        private void chargeFreshTokens(int tokens) {
            for (int token = 0; token < tokens; token++) {
                assertTrue(limiter.tryChargeRead(UUID.randomUUID()));
            }
        }

        @Test
        void tryCharge_tableOverItsPruneSize_dropsExpiredWindowsAtMostOncePerWindow() {
            long halfWindow = LessonConstant.DELIVERY_RATE_WINDOW_SECONDS / 2;
            int half = LessonConstant.DELIVERY_RATE_TABLE_PRUNE_SIZE / 2 + 1;
            chargeFreshTokens(half);
            clock.advance(Duration.ofSeconds(halfWindow));
            // The table outgrows its prune size here; nothing has expired yet.
            chargeFreshTokens(half);
            assertEquals(2 * half, limiter.tokenReadWindows());

            clock.advance(Duration.ofSeconds(halfWindow));
            chargeFreshTokens(1);
            // The first half has expired, but the table was scanned less than a window ago.
            assertEquals(2 * half + 1, limiter.tokenReadWindows());

            clock.advance(Duration.ofSeconds(halfWindow));
            chargeFreshTokens(1);
            // A window after the last scan both halves are dropped; the two latest windows stay.
            assertEquals(2, limiter.tokenReadWindows());
        }
    }
}
