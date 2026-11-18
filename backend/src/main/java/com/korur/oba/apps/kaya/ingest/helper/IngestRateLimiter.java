package com.korur.oba.apps.kaya.ingest.helper;

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
import com.korur.oba.apps.kaya.ingest.constant.IngestConstant;

/**
 * The batch budget of each machine token: a fixed one-minute window per token, held in memory. A
 * recorder sends a batch every few seconds at most, so a token over the budget is a runaway
 * sender, and refusing it keeps one machine from filling the session tables. The budget is per
 * backend instance; Traefik's per-IP limit in front of the ingest path bounds the total.
 */
@Component
@RequiredArgsConstructor
public class IngestRateLimiter {

    private final Clock clock;
    private final ConcurrentMap<UUID, Window> windows = new ConcurrentHashMap<>();

    /**
     * Charges one batch to the token.
     *
     * @param tokenId the token that sent the batch
     * @return true when the batch is within the budget, false when the token exhausted it
     */
    public boolean tryCharge(UUID tokenId) {
        Instant now = Instant.now(clock);
        if (windows.size() > IngestConstant.RATE_TABLE_PRUNE_SIZE) {
            windows.values().removeIf(window -> window.expired(now));
        }
        Window charged = windows.compute(tokenId, (id, window) ->
                window == null || window.expired(now) ? new Window(now, 1) : new Window(window.start, window.count + 1));
        return charged.count <= IngestConstant.BATCHES_PER_MINUTE;
    }

    private record Window(Instant start, int count) {

        boolean expired(Instant now) {
            return !now.isBefore(start.plusSeconds(IngestConstant.RATE_WINDOW_SECONDS));
        }
    }
}
