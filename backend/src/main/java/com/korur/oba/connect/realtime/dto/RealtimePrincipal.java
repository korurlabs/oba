package com.korur.oba.connect.realtime.dto;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * The user of one realtime session: a Village user bound to one organization until the access
 * token that requested the session's handshake ticket expires.
 *
 * The principal is built by {@link com.korur.oba.connect.realtime.service.RealtimeTicketService#issueTicket}
 * when the client asks for a ticket over REST, after the resource server checked the access token
 * and {@link com.korur.oba.dojo.security.interceptor.RequiredUserAccessInterceptor} checked the
 * membership in the organization. The handshake redeems the ticket and the CONNECT frame takes the
 * principal from the session attributes; neither carries a token nor an organization of its own.
 *
 * The name is the user key "{userId}:{organizationId}", which is what a sender passes to
 * {@link org.springframework.messaging.simp.SimpMessagingTemplate#convertAndSendToUser}. Keying on
 * the pair rather than on the user alone is the tenant boundary of the socket: a user with two
 * tabs on two organizations holds one session per organization, and a frame addressed to one
 * organization never reaches the other.
 *
 * @param userId         the Village user id of the caller that requested the ticket
 * @param organizationId the X-Organization-ID of the ticket request, checked for membership
 * @param expiresAt      the expiry of the access token that requested the ticket
 */
public record RealtimePrincipal(UUID userId, UUID organizationId, Instant expiresAt) implements Principal {

    private static final String USER_KEY_SEPARATOR = ":";

    /**
     * Builds the user key that frames for this user in this organization are addressed to.
     *
     * @param userId         the recipient's user id
     * @param organizationId the organization the frame belongs to
     * @return the user key, "{userId}:{organizationId}"
     */
    public static String userKey(UUID userId, UUID organizationId) {
        return userId + USER_KEY_SEPARATOR + organizationId;
    }

    @Override
    public String getName() {
        return userKey(userId, organizationId);
    }

    /**
     * True once the access token that requested the session's ticket has expired, from which point the session
     * may no longer act and is closed.
     *
     * @param now the instant to compare against
     * @return true when the token has expired at now
     */
    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
