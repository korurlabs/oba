package com.korur.oba.connect.realtime.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.realtime.dto.RealtimePrincipal;

/**
 * Holds the unused handshake tickets between their issue and their one redemption.
 *
 * The store keeps state only; {@link RealtimeTicketService} owns every rule (who may get a
 * ticket, how long it lives, how many a user may hold). A ticket is redeemed on whichever replica
 * the handshake reaches, so a deployment with more than one replica needs a store that every
 * replica shares and whose {@link #take} is atomic; {@link InMemoryRealtimeTicketStore} serves
 * one replica.
 */
public interface RealtimeTicketStore {

    /**
     * Stores a newly issued ticket.
     *
     * @param ticket the ticket value
     * @param entry  the session principal it opens and when it stops being redeemable
     */
    void save(String ticket, RealtimeTicketEntry entry);

    /**
     * Removes a ticket and returns what it held, so a ticket is redeemed at most once even when
     * two handshakes present it at the same moment.
     *
     * @param ticket the ticket value
     * @return the entry, or empty when the ticket is unknown, already taken or purged as expired
     */
    Optional<RealtimeTicketEntry> take(String ticket);

    /**
     * Counts the tickets of a user that are still redeemable.
     *
     * @param userId the user
     * @param now    the instant to compare the ticket expiry against
     * @return the number of unused tickets of the user that expire after now
     */
    int countLive(UUID userId, Instant now);

    /**
     * Drops the unused tickets of a user, of an organization, or of one membership, after that
     * access was revoked. A null field means any.
     *
     * @param userId         the user, or null for every user
     * @param organizationId the organization, or null for every organization
     */
    void removeMatching(UUID userId, UUID organizationId);

    /**
     * One unused ticket.
     *
     * @param principal       the session principal the handshake receives
     * @param ticketExpiresAt the instant after which the ticket no longer opens a socket
     */
    record RealtimeTicketEntry(RealtimePrincipal principal, Instant ticketExpiresAt) {

        /**
         * Whether the ticket can no longer be redeemed.
         *
         * @param now the instant to compare against
         * @return true when the ticket expired at or before now
         */
        public boolean isExpiredAt(Instant now) {
            return !ticketExpiresAt.isAfter(now);
        }
    }
}
