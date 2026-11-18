package com.korur.oba.connect.realtime.service;

// 1. Framework-level
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// 2. Third-party

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// 4. Internal
import com.korur.oba.connect.realtime.constant.RealtimeConstant;
import com.korur.oba.connect.realtime.dto.RealtimePrincipal;

/**
 * The {@link RealtimeTicketStore} of a single replica: a map in this process, used with the
 * in-memory simple broker, which serves one replica too.
 *
 * Expired tickets are purged on every call, so an issued ticket that is never redeemed occupies
 * memory for at most its lifetime plus the time until the next ticket request or handshake.
 */
@Component
@ConditionalOnProperty(name = RealtimeConstant.BROKER_PROPERTY, havingValue = RealtimeConstant.BROKER_SIMPLE,
        matchIfMissing = true)
public class InMemoryRealtimeTicketStore implements RealtimeTicketStore {

    private final Map<String, RealtimeTicketEntry> tickets = new ConcurrentHashMap<>();
    private final Clock clock;

    @Autowired
    public InMemoryRealtimeTicketStore() {
        this(Clock.systemUTC());
    }

    InMemoryRealtimeTicketStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void save(String ticket, RealtimeTicketEntry entry) {
        purgeExpired();
        tickets.put(ticket, entry);
    }

    @Override
    public Optional<RealtimeTicketEntry> take(String ticket) {
        purgeExpired();
        // remove is atomic per key, so of two handshakes racing on one ticket only one gets it.
        return Optional.ofNullable(tickets.remove(ticket));
    }

    @Override
    public int countLive(UUID userId, Instant now) {
        purgeExpired();
        return (int) tickets.values().stream()
                .filter((RealtimeTicketEntry entry) -> entry.principal().userId().equals(userId)
                        && !entry.isExpiredAt(now))
                .count();
    }

    @Override
    public void removeMatching(UUID userId, UUID organizationId) {
        purgeExpired();
        tickets.values().removeIf((RealtimeTicketEntry entry) -> {
            RealtimePrincipal principal = entry.principal();
            return (userId == null || userId.equals(principal.userId()))
                    && (organizationId == null || organizationId.equals(principal.organizationId()));
        });
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        tickets.values().removeIf((RealtimeTicketEntry entry) -> entry.isExpiredAt(now));
    }
}
