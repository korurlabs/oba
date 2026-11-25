package com.korur.oba.apps.kaya.lesson.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// 4. Internal
import com.korur.oba.apps.kaya.lesson.constant.LessonConstant;

/**
 * The lesson budgets of the machine tokens: reads per token, reads per user, reads per organization
 * and added lessons per token, each a fixed one-minute window per key, held in memory. Every page of the
 * list and every search makes PostgreSQL pass over all lessons that apply to the checkout, and
 * every added lesson makes each later read longer, so a client that reads or adds without pause
 * could keep the connection pool busy for every tenant. Each budget is far above what the CLI
 * needs; the numbers and their reasons are {@link LessonConstant#DELIVERY_READS_PER_MINUTE},
 * {@link LessonConstant#DELIVERY_USER_READS_PER_MINUTE},
 * {@link LessonConstant#DELIVERY_ORGANIZATION_READS_PER_MINUTE} and
 * {@link LessonConstant#DELIVERY_PROPOSALS_PER_MINUTE}.
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

    /**
     * Charges one read, a page of the list or a search, to the token.
     *
     * @param tokenId the token that sent the read
     * @return true when the read is within the budget, false when the token exhausted it
     */
    public boolean tryChargeRead(UUID tokenId) {
        return tokenReads.tryCharge(tokenId, Instant.now(clock));
    }

    /**
     * Charges one read to the user of the token, whatever organization the token belongs to.
     *
     * @param userId the user of the token that sent the read
     * @return true when the read is within the budget, false when the user exhausted it
     */
    public boolean tryChargeUserRead(UUID userId) {
        return userReads.tryCharge(userId, Instant.now(clock));
    }

    /**
     * Charges one read to the organization of the token.
     *
     * @param organizationId the organization of the token that sent the read
     * @return true when the read is within the budget, false when the organization exhausted it
     */
    public boolean tryChargeOrganizationRead(UUID organizationId) {
        return organizationReads.tryCharge(organizationId, Instant.now(clock));
    }

    /**
     * Charges one added lesson to the token.
     *
     * @param tokenId the token that sent the lesson
     * @return true when the lesson is within the budget, false when the token exhausted it
     */
    public boolean tryChargeProposal(UUID tokenId) {
        return proposals.tryCharge(tokenId, Instant.now(clock));
    }

    /** One budget: a window per key, in a table of its own. */
    private static final class Budget {

        private final int perMinute;
        private final ConcurrentMap<UUID, Window> windows = new ConcurrentHashMap<>();

        private Budget(int perMinute) {
            this.perMinute = perMinute;
        }

        private boolean tryCharge(UUID key, Instant now) {
            if (windows.size() > LessonConstant.DELIVERY_RATE_TABLE_PRUNE_SIZE) {
                windows.values().removeIf(window -> window.expired(now));
            }
            Window charged = windows.compute(key, (id, window) ->
                    window == null || window.expired(now) ? new Window(now, 1) : new Window(window.start, window.count + 1));
            return charged.count <= perMinute;
        }
    }

    private record Window(Instant start, int count) {

        boolean expired(Instant now) {
            return !now.isBefore(start.plusSeconds(LessonConstant.DELIVERY_RATE_WINDOW_SECONDS));
        }
    }
}
