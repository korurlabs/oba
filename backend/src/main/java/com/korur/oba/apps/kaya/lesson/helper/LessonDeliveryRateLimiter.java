package com.korur.oba.apps.kaya.lesson.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

// 4. Internal
import com.korur.oba.apps.kaya.lesson.constant.LessonConstant;

/**
 * The lesson budgets: of the machine tokens, reads per token and added lessons per token; and of the
 * tokens and the web app together, reads per user, reads per organization and added lessons per user,
 * each a fixed one-minute window per key, held in memory. Every page of the
 * list and every search makes PostgreSQL pass over all lessons that apply to the checkout, and
 * every added lesson makes each later read longer, so a client that reads or adds without pause
 * could keep the connection pool busy for every tenant. Each budget is far above what the CLI
 * needs; the numbers and their reasons are {@link LessonConstant#DELIVERY_READS_PER_MINUTE},
 * {@link LessonConstant#DELIVERY_USER_READS_PER_MINUTE},
 * {@link LessonConstant#DELIVERY_ORGANIZATION_READS_PER_MINUTE},
 * {@link LessonConstant#DELIVERY_PROPOSALS_PER_MINUTE} and {@link LessonConstant#USER_LESSONS_PER_MINUTE}.
 *
 * Like the batch budget of {@link com.korur.oba.apps.kaya.ingest.helper.IngestRateLimiter}, every
 * table is per backend instance, with no shared store: each instance grants the whole budget, and
 * a restart forgets it, which at worst allows one extra minute.
 */
@Component
@RequiredArgsConstructor
public class LessonDeliveryRateLimiter {

    private final Clock clock;
    private final Budget tokenReads = new Budget(LessonConstant.DELIVERY_READS_PER_MINUTE);
    private final Budget userReads = new Budget(LessonConstant.DELIVERY_USER_READS_PER_MINUTE);
    private final Budget organizationReads = new Budget(LessonConstant.DELIVERY_ORGANIZATION_READS_PER_MINUTE);
    private final Budget proposals = new Budget(LessonConstant.DELIVERY_PROPOSALS_PER_MINUTE);
    private final Budget userLessons = new Budget(LessonConstant.USER_LESSONS_PER_MINUTE);

    /**
     * Charges one read, a page of the list or a search, to the token.
     *
     * @param tokenId the token that sent the read
     * @return empty when the read is within the budget; when the token exhausted it, how long until
     *         its window ends and the budget is whole again
     */
    public Optional<Duration> tryChargeRead(UUID tokenId) {
        return tokenReads.tryCharge(tokenId, Instant.now(clock));
    }

    /**
     * Charges one read to the user, of one of their tokens or of the web app, whatever organization
     * it reads.
     *
     * @param userId the user who sent the read
     * @return empty when the read is within the budget; when the user exhausted it, how long until
     *         their window ends
     */
    public Optional<Duration> tryChargeUserRead(UUID userId) {
        return userReads.tryCharge(userId, Instant.now(clock));
    }

    /**
     * Charges one read to the organization it reads, of a token or of the web app.
     *
     * @param organizationId the organization the read is of
     * @return empty when the read is within the budget; when the organization exhausted it, how long
     *         until its window ends
     */
    public Optional<Duration> tryChargeOrganizationRead(UUID organizationId) {
        return organizationReads.tryCharge(organizationId, Instant.now(clock));
    }

    /**
     * Charges one added lesson to the token.
     *
     * @param tokenId the token that sent the lesson
     * @return empty when the lesson is within the budget; when the token exhausted it, how long
     *         until its window ends
     */
    public Optional<Duration> tryChargeProposal(UUID tokenId) {
        return proposals.tryCharge(tokenId, Instant.now(clock));
    }

    /**
     * Charges one lesson a member adds, in the web app or with one of their tokens, to the member,
     * whatever organization it is for.
     *
     * @param userId the member who sent the lesson
     * @return empty when the lesson is within the budget; when the member exhausted it, how long
     *         until their window ends
     */
    public Optional<Duration> tryChargeUserLesson(UUID userId) {
        return userLessons.tryCharge(userId, Instant.now(clock));
    }

    /** The windows the table of token reads holds, expired ones included until they are pruned. */
    int tokenReadWindows() {
        return tokenReads.windows.size();
    }

    /** One budget: a window per key, in a table of its own. */
    private static final class Budget {

        private final int perMinute;
        private final ConcurrentMap<UUID, Window> windows = new ConcurrentHashMap<>();
        private final AtomicReference<Instant> nextPrune = new AtomicReference<>(Instant.MIN);

        private Budget(int perMinute) {
            this.perMinute = perMinute;
        }

        private Optional<Duration> tryCharge(UUID key, Instant now) {
            pruneWhenDue(now);
            Window charged = windows.compute(key, (id, window) ->
                    window == null || window.expired(now) ? new Window(now, 1) : new Window(window.start, window.count + 1));
            return charged.count <= perMinute ? Optional.empty() : Optional.of(charged.remaining(now));
        }

        /**
         * Drops the expired windows once the table holds more than
         * {@link LessonConstant#DELIVERY_RATE_TABLE_PRUNE_SIZE}, at most once per window. Every request
         * is charged, refused ones too, so a table of that many live keys would otherwise be scanned
         * on every charge. Between two prunes the table holds at most the keys charged in the last
         * two windows, or that many keys.
         */
        private void pruneWhenDue(Instant now) {
            Instant due = nextPrune.get();
            if (windows.size() > LessonConstant.DELIVERY_RATE_TABLE_PRUNE_SIZE && !now.isBefore(due)
                    && nextPrune.compareAndSet(due, now.plusSeconds(LessonConstant.DELIVERY_RATE_WINDOW_SECONDS))) {
                windows.values().removeIf(window -> window.expired(now));
            }
        }
    }

    private record Window(Instant start, int count) {

        boolean expired(Instant now) {
            return !now.isBefore(end());
        }

        /** The time until the window ends and its key starts a fresh one. */
        Duration remaining(Instant now) {
            return Duration.between(now, end());
        }

        private Instant end() {
            return start.plusSeconds(LessonConstant.DELIVERY_RATE_WINDOW_SECONDS);
        }
    }
}
