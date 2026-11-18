package com.korur.oba.connect.push.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// 4. Internal
import com.korur.oba.connect.push.constant.PushConstant;
import com.korur.oba.connect.push.constant.helper.PushTicketTrackerLogConstant;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Keeps, in memory, the Expo tickets whose receipt has not been read yet, each with the token it was
 * sent to, so a receipt that reports DeviceNotRegistered can remove that device.
 *
 * The store is lost on restart; that only delays the cleanup of a dead token until its next push,
 * whose ticket reports it again. It holds at most a fixed number of tickets, so an Expo outage
 * cannot grow it without bound.
 */
@Component
@RequiredArgsConstructor
public class PushTicketTracker {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(PushTicketTracker.class);

    private final LoggingService loggingService;

    private final ConcurrentMap<String, PendingTicket> pending = new ConcurrentHashMap<>();

    /**
     * Remembers sent tickets until their receipt is read.
     *
     * @param ticketTokens the token each ticket id was sent to
     * @param sentAt       when the push was sent
     */
    public void track(Map<String, String> ticketTokens, Instant sentAt) {
        int dropped = 0;
        for (Map.Entry<String, String> entry : ticketTokens.entrySet()) {
            if (pending.size() >= PushConstant.RECEIPT_TRACKING_LIMIT) {
                dropped++;
                continue;
            }
            pending.put(entry.getKey(), new PendingTicket(entry.getValue(), sentAt));
        }
        if (dropped > 0) {
            loggingService.warn(LOG_PREFIX, LogLayer.HELPER, PushTicketTrackerLogConstant.TRACKING_LIMIT_REACHED,
                    PushConstant.RECEIPT_TRACKING_LIMIT, dropped);
        }
    }

    /**
     * Returns the tickets old enough for Expo to have a receipt.
     *
     * @param now the current instant
     * @return the token by ticket id, oldest tickets not guaranteed first
     */
    public Map<String, String> due(Instant now) {
        Instant dueBefore = now.minus(PushConstant.RECEIPT_DELAY);
        Map<String, String> due = new LinkedHashMap<>();
        pending.forEach((ticketId, ticket) -> {
            if (!ticket.sentAt().isAfter(dueBefore)) {
                due.put(ticketId, ticket.token());
            }
        });
        return due;
    }

    /**
     * Forgets tickets whose receipt was read.
     *
     * @param ticketIds the ticket ids
     */
    public void forget(Collection<String> ticketIds) {
        ticketIds.forEach(pending::remove);
    }

    /**
     * Forgets tickets older than Expo keeps receipts, whose receipt can no longer be read.
     *
     * @param now the current instant
     * @return the number of tickets forgotten
     */
    public int expire(Instant now) {
        Instant keptAfter = now.minus(PushConstant.RECEIPT_RETENTION);
        int before = pending.size();
        pending.values().removeIf(ticket -> ticket.sentAt().isBefore(keptAfter));
        return Math.max(0, before - pending.size());
    }

    private record PendingTicket(String token, Instant sentAt) {

        /**
         * Keeps the token out of every log line that prints this record.
         */
        @Override
        public String toString() {
            return "PendingTicket[sentAt=" + sentAt + "]";
        }
    }
}
