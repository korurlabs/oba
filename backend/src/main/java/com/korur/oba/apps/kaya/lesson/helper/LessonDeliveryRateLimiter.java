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
 * The lesson read budget of each machine token: a fixed one-minute window per token, held in
 * memory. Every page of the list and every search makes PostgreSQL pass over all lessons that
 * apply to the checkout, and the token's organization decides how many those are, so a token that
 * reads without pause could keep the connection pool busy for every tenant. The budget is far
 * above what the CLI reads, see {@link LessonConstant#DELIVERY_READS_PER_MINUTE}, and it is per
 * backend instance: a restart forgets it, which at worst allows one extra minute of reads.
 */
@Component
@RequiredArgsConstructor
public class LessonDeliveryRateLimiter {

    private final Clock clock;
    private final ConcurrentMap<UUID, Window> windows = new ConcurrentHashMap<>();

    /**
     * Charges one read to the token.
     *
     * @param tokenId the token that sent the read
     * @return true when the read is within the budget, false when the token exhausted it
     */
    public boolean tryCharge(UUID tokenId) {
        Instant now = Instant.now(clock);
        if (windows.size() > LessonConstant.DELIVERY_RATE_TABLE_PRUNE_SIZE) {
            windows.values().removeIf(window -> window.expired(now));
        }
        Window charged = windows.compute(tokenId, (id, window) ->
                window == null || window.expired(now) ? new Window(now, 1) : new Window(window.start, window.count + 1));
        return charged.count <= LessonConstant.DELIVERY_READS_PER_MINUTE;
    }

    private record Window(Instant start, int count) {

        boolean expired(Instant now) {
            return !now.isBefore(start.plusSeconds(LessonConstant.DELIVERY_RATE_WINDOW_SECONDS));
        }
    }
}
