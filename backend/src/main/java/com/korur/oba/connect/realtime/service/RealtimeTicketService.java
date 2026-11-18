package com.korur.oba.connect.realtime.service;

// 1. Framework-level
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

// 2. Third-party

// 3. Java SDK
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.realtime.constant.RealtimeConstant;
import com.korur.oba.connect.realtime.dto.RealtimePrincipal;
import com.korur.oba.connect.realtime.dto.RealtimeTicketResponse;
import com.korur.oba.connect.realtime.exception.ExceptionDomainRealtimeError;
import com.korur.oba.connect.realtime.service.RealtimeTicketStore.RealtimeTicketEntry;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.TooManyRequestsException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.util.SecurityUtils;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.event.AppAccessRevokedEvent;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.event.AccessRevokedEvent;

/**
 * Owns the one-time handshake ticket from issue to redemption, so that no unauthenticated
 * WebSocket is ever accepted.
 *
 * A browser cannot send an Authorization header on a WebSocket upgrade. The client therefore
 * first asks for a ticket over REST, where the resource server checks its access token and
 * {@link com.korur.oba.dojo.security.interceptor.RequiredUserAccessInterceptor} checks the user and
 * the membership; the ticket binds the resulting {@link RealtimePrincipal} (user, organization,
 * token expiry) to 32 random bytes. The handshake then presents the ticket
 * as a query parameter and is refused with HTTP 401 unless the ticket is known, unused and
 * unexpired. A ticket opens exactly one socket and lives 30 seconds, because a URL ends up in
 * proxy access logs; its value is never logged here.
 *
 * The principal is handed to
 * {@link com.korur.oba.connect.realtime.interceptor.RealtimeInboundInterceptor} through the
 * socket's session attributes, where the CONNECT frame takes it. When access is revoked, or an app of
 * {@link RealtimeConstant#SESSION_APPS} is lost, the matching unused tickets are dropped after the
 * commit, so a ticket issued just before cannot open a socket afterwards.
 *
 * The {@link RealtimeTicketStore} holds only the state; every rule is here, so a shared store for several replicas
 * changes nothing in this class.
 */
@Component
public class RealtimeTicketService implements HandshakeInterceptor {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(RealtimeTicketService.class);

    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder ticketEncoder = Base64.getUrlEncoder().withoutPadding();

    /**
     * Serializes the count and the save of one issue, so two requests racing for the last free
     * ticket slot of a user cannot both get one. Issuing is rare, so one lock is enough.
     */
    private final Object issueLock = new Object();

    private final RealtimeTicketStore realtimeTicketStore;
    private final LoggingService loggingService;
    private final Clock clock;

    @Autowired
    public RealtimeTicketService(
            RealtimeTicketStore realtimeTicketStore,
            LoggingService loggingService) {
        this(realtimeTicketStore, loggingService, Clock.systemUTC());
    }

    RealtimeTicketService(
            RealtimeTicketStore realtimeTicketStore,
            LoggingService loggingService,
            Clock clock) {
        this.realtimeTicketStore = realtimeTicketStore;
        this.loggingService = loggingService;
        this.clock = clock;
    }

    /**
     * Issues a ticket that opens one realtime socket for the user in the organization.
     *
     * The caller's access to the organization is not checked here: the only caller,
     * {@link com.korur.oba.connect.realtime.controller.RealtimeTicketController#issueTicket}, requires
     * an active GUEST membership through
     * {@link com.korur.oba.dojo.security.annotation.RequiredUserAccess} before this method runs.
     *
     * @param user           the caller, {@link com.korur.oba.shrine.identity.enums.UserStatus#ACTIVE}
     * @param organizationId the organization the socket is bound to
     * @return the ticket and the instant it stops being redeemable
     * @throws ForbiddenException       RTM_SEC_002 when the access token carries no expiry
     * @throws TooManyRequestsException GEN_API_004 when the user already holds the most unused tickets allowed
     */
    public RealtimeTicketResponse issueTicket(User user, UUID organizationId) {
        UUID userId = user.getId();

        Optional<Instant> tokenExpiresAt = SecurityUtils.getTokenExpiresAt();
        if (tokenExpiresAt.isEmpty()) {
            // The session's lifetime is the token's; without an expiry nothing would ever end it.
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, RealtimeConstant.TICKET_REFUSED_NO_TOKEN_EXPIRY,
                    userId, organizationId);
            throw new ForbiddenException(ExceptionDomainRealtimeError.SEC_REALTIME_FORBIDDEN);
        }

        RealtimePrincipal principal = new RealtimePrincipal(userId, organizationId, tokenExpiresAt.get());
        String ticket = newTicket();
        Instant ticketExpiresAt;
        synchronized (issueLock) {
            Instant now = clock.instant();
            int live = realtimeTicketStore.countLive(userId, now);
            if (live >= RealtimeConstant.MAX_LIVE_TICKETS_PER_USER) {
                loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, RealtimeConstant.TICKET_REFUSED_LIMIT,
                        userId, organizationId, live);
                throw new TooManyRequestsException();
            }
            ticketExpiresAt = now.plus(RealtimeConstant.TICKET_TTL);
            realtimeTicketStore.save(ticket, new RealtimeTicketEntry(principal, ticketExpiresAt));
        }

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, RealtimeConstant.TICKET_ISSUED,
                userId, organizationId, ticketExpiresAt, principal.expiresAt());
        return new RealtimeTicketResponse(ticket, ticketExpiresAt);
    }

    /**
     * Redeems the ticket of the handshake. The ticket is taken out of the store before it is
     * checked, so it is used up even when it turns out to be expired.
     *
     * @return true when the ticket opened the socket; false after answering HTTP 401
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String ticket = UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst(RealtimeConstant.TICKET_QUERY_PARAMETER);
        if (ticket == null || ticket.isBlank()) {
            return refuseHandshake(request, response, RealtimeConstant.REASON_TICKET_MISSING);
        }
        Optional<RealtimeTicketEntry> entry = realtimeTicketStore.take(ticket);
        if (entry.isEmpty()) {
            return refuseHandshake(request, response, RealtimeConstant.REASON_TICKET_UNKNOWN);
        }
        // A shared store may not purge on take, so the expiry is checked here as well.
        if (entry.get().isExpiredAt(clock.instant())) {
            return refuseHandshake(request, response, RealtimeConstant.REASON_TICKET_EXPIRED);
        }
        RealtimePrincipal principal = entry.get().principal();
        attributes.put(RealtimeConstant.SESSION_PRINCIPAL_ATTRIBUTE, principal);
        loggingService.observe(LOG_PREFIX, LogLayer.HANDLER, RealtimeConstant.HANDSHAKE_ACCEPTED,
                principal.userId(), principal.organizationId());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // Nothing to do: the principal travels in the session attributes set before the upgrade.
    }

    /**
     * Drops the unused tickets of the user, organization or membership whose access was revoked,
     * once that change has committed, so a change that rolls back drops nothing.
     *
     * @param event the revoked access; a null field means any
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccessRevoked(AccessRevokedEvent event) {
        dropTickets(event.userId(), event.organizationId(), null);
    }

    /**
     * Drops the unused tickets of a user in an organization once a change that took an app from the
     * user has committed, when that app is one of {@link RealtimeConstant#SESSION_APPS}, so a ticket
     * issued just before cannot open a socket that carries that app's signals.
     *
     * @param event the user, the organization and the app the user lost
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppAccessRevoked(AppAccessRevokedEvent event) {
        if (!RealtimeConstant.SESSION_APPS.contains(event.app())) {
            return;
        }
        dropTickets(event.userId(), event.organizationId(), event.app());
    }

    /**
     * Removes the unused tickets that match the user and organization.
     *
     * @param userId         the user, or null for every user of the organization
     * @param organizationId the organization, or null for every organization of the user
     * @param lostApp        the app whose loss drops the tickets, or null when the whole organization access ended
     */
    private void dropTickets(UUID userId, UUID organizationId, App lostApp) {
        try {
            realtimeTicketStore.removeMatching(userId, organizationId);
            loggingService.info(LOG_PREFIX, LogLayer.HANDLER, RealtimeConstant.TICKETS_DROPPED_FOR_REVOKED_ACCESS,
                    userId, organizationId, lostApp);
        } catch (RuntimeException dropFailure) {
            // Not re-thrown: the revoking change has committed and its request succeeded, so
            // there is nothing to roll back. A ticket that stays is redeemable for at most 30
            // seconds, but a socket it opens then stays open until its token expires, so the
            // error log routes the failure to error aggregation.
            loggingService.error(LOG_PREFIX, LogLayer.HANDLER, RealtimeConstant.ACCESS_REVOKED_TICKET_DROP_FAILED,
                    dropFailure, userId, organizationId, lostApp);
        }
    }

    private String newTicket() {
        byte[] bytes = new byte[RealtimeConstant.TICKET_BYTES];
        secureRandom.nextBytes(bytes);
        return ticketEncoder.encodeToString(bytes);
    }

    private boolean refuseHandshake(ServerHttpRequest request, ServerHttpResponse response, String reason) {
        loggingService.warn(LOG_PREFIX, LogLayer.HANDLER, RealtimeConstant.HANDSHAKE_REFUSED,
                reason, request.getRemoteAddress());
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }
}
