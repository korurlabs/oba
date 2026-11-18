package com.korur.oba.connect.realtime.service;

// 1. Framework-level
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

// 2. Third-party

// 3. Java SDK
import java.io.IOException;
import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

// 4. Internal
import com.korur.oba.connect.realtime.constant.RealtimeConstant;
import com.korur.oba.connect.realtime.dto.RealtimeFrameDecision;
import com.korur.oba.connect.realtime.dto.RealtimePrincipal;
import com.korur.oba.connect.realtime.event.RealtimePresenceChangedEvent;
import com.korur.oba.connect.realtime.exception.ExceptionDomainRealtimeError;
import com.korur.oba.connect.realtime.helper.RealtimeDestinationAuthorizerRegistry;
import com.korur.oba.connect.realtime.properties.RealtimeProperties;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.event.AppAccessRevokedEvent;
import com.korur.oba.shrine.organization.event.AccessRevokedEvent;

/**
 * Keeps the open realtime sessions of this process, so the process can tell who is online in an
 * organization and end sessions itself: when the access token behind a session expires, and when
 * organization access is revoked for a membership, a user or a whole organization.
 *
 * A STOMP session id equals the id of its WebSocket session, so the WebSocket session registered
 * by the decorator is found again from the CONNECTED event, which is when the
 * {@link RealtimePrincipal}, and with it the user, organization and token expiry, becomes known. A session that never
 * completes CONNECT carries no principal, counts for nobody's presence and is closed by the
 * transport's time-to-first-message limit.
 *
 * Presence is per (organization, user): a member is online while at least one of their sessions
 * in that organization is open. Only the transitions publish a
 * {@link RealtimePresenceChangedEvent}, the first session opening and the last one closing, so a second tab changes nothing for the others.
 * The consumers that show presence turn the event into their own signal.
 *
 * Each session's subscriptions are tracked as the client named them, so they can be checked
 * again when some users' access changed: a subscription the authorizers no longer allow is ended
 * on the broker while the session stays open. The count also bounds how many subscriptions one
 * session may hold.
 *
 * The registry also limits what one session may ask of the server, whatever consumer it talks to.
 * A SEND fans out to other sessions and a SUBSCRIBE may run a consumer's database check, so a
 * client stuck in a loop would otherwise load every other session. Each send bucket is a sliding
 * window per session, kept with the session and forgotten when it closes; frames past a limit
 * are dropped, not refused, so the session keeps working once the client slows down.
 *
 * Presence is kept in this registry's own map rather than read from Spring's
 * {@link org.springframework.messaging.simp.user.SimpUserRegistry}.
 * Both agree once the transport has finished a connect or a close, but the presence transitions
 * must be decided atomically: the close callback runs before Spring's registry drops the session,
 * so two tabs closing at the same moment would each still see the other in Spring's registry, and
 * neither would announce the member offline.
 *
 * The access check is made once, when the handshake ticket is issued. {@link AccessRevokedEvent},
 * and {@link AppAccessRevokedEvent} for an app of {@link RealtimeConstant#SESSION_APPS}, close the
 * affected sessions as soon as a revocation commits; the expiry sweep bounds everything
 * else the check covered to one access token lifetime. An expired session is sent an ERROR frame
 * with RTM_SEC_001 before it is closed, the code a client also gets for an expired token on
 * CONNECT, so it reconnects with a fresh token instead of treating it as an outage.
 */
@Component
public class RealtimeSessionRegistry implements WebSocketHandlerDecoratorFactory {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(RealtimeSessionRegistry.class);

    private static final byte[] EMPTY_PAYLOAD = new byte[0];

    private final Map<String, TrackedSession> sessions = new ConcurrentHashMap<>();

    /** Open session ids per organization and user; guarded by {@link #presenceLock}. */
    private final Map<UUID, Map<UUID, Set<String>>> presence = new HashMap<>();
    private final Object presenceLock = new Object();

    private final ObjectProvider<MessageChannel> clientOutboundChannel;
    private final ObjectProvider<MessageChannel> brokerChannel;
    private final RealtimeDestinationAuthorizerRegistry realtimeDestinationAuthorizerRegistry;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final RealtimeProperties realtimeProperties;
    private final LoggingService loggingService;
    private final Clock clock;

    /**
     * The outbound and broker channels are resolved lazily: both are built by the message broker
     * configuration, which itself needs this decorator while it is being built.
     */
    @Autowired
    public RealtimeSessionRegistry(
            @Qualifier(RealtimeConstant.CLIENT_OUTBOUND_CHANNEL_BEAN_NAME) ObjectProvider<MessageChannel> clientOutboundChannel,
            @Qualifier(RealtimeConstant.BROKER_CHANNEL_BEAN_NAME) ObjectProvider<MessageChannel> brokerChannel,
            RealtimeDestinationAuthorizerRegistry realtimeDestinationAuthorizerRegistry,
            ApplicationEventPublisher applicationEventPublisher,
            RealtimeProperties realtimeProperties,
            LoggingService loggingService) {
        this(clientOutboundChannel, brokerChannel, realtimeDestinationAuthorizerRegistry, applicationEventPublisher,
                realtimeProperties, loggingService, Clock.systemUTC());
    }

    RealtimeSessionRegistry(
            ObjectProvider<MessageChannel> clientOutboundChannel,
            ObjectProvider<MessageChannel> brokerChannel,
            RealtimeDestinationAuthorizerRegistry realtimeDestinationAuthorizerRegistry,
            ApplicationEventPublisher applicationEventPublisher,
            RealtimeProperties realtimeProperties,
            LoggingService loggingService,
            Clock clock) {
        this.clientOutboundChannel = clientOutboundChannel;
        this.brokerChannel = brokerChannel;
        this.realtimeDestinationAuthorizerRegistry = realtimeDestinationAuthorizerRegistry;
        this.applicationEventPublisher = applicationEventPublisher;
        this.realtimeProperties = realtimeProperties;
        this.loggingService = loggingService;
        this.clock = clock;
    }

    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sessions.put(session.getId(),
                        new TrackedSession(session, null, false, new ConcurrentHashMap<>(), new ConcurrentHashMap<>()));
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                try {
                    onConnectionClosed(session.getId());
                } finally {
                    super.afterConnectionClosed(session, closeStatus);
                }
            }
        };
    }

    /**
     * Records the principal of a session once CONNECT has been accepted, and publishes the
     * member's presence when this is their first session in the organization.
     *
     * @param event the CONNECTED event, whose user is the {@link RealtimePrincipal} set on CONNECT
     */
    @EventListener
    public void onSessionConnected(SessionConnectedEvent event) {
        String sessionId = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
        Principal user = event.getUser();
        if (sessionId == null || !(user instanceof RealtimePrincipal principal)) {
            return;
        }
        TrackedSession tracked = sessions.computeIfPresent(sessionId,
                (String id, TrackedSession current) -> current.withPrincipal(principal));
        if (tracked == null) {
            // The connection closed before its CONNECTED event arrived; nothing is online.
            return;
        }
        boolean firstSession = addPresence(principal, sessionId);
        if (!sessions.containsKey(sessionId)) {
            // The connection closed after the lookup above; its close found no presence entry
            // yet, so the one just added is undone here instead of staying online forever.
            removePresence(principal, sessionId);
            return;
        }
        if (firstSession) {
            publishPresence(principal, true);
        }
    }

    /**
     * Records a subscription the inbound channel let through. Published by the STOMP handler
     * only for frames that passed
     * {@link com.korur.oba.connect.realtime.interceptor.RealtimeInboundInterceptor}, and before
     * the session's next frame is read, so the count the subscription cap reads is current.
     *
     * The first destination of an id is kept, as the broker keeps it, so this registry never
     * tracks a destination other than the one the broker delivers. The interceptor already
     * refuses a reused id.
     *
     * @param event the SUBSCRIBE of a session, with the destination as the client named it
     */
    @EventListener
    public void onSessionSubscribe(SessionSubscribeEvent event) {
        MessageHeaders headers = event.getMessage().getHeaders();
        String subscriptionId = SimpMessageHeaderAccessor.getSubscriptionId(headers);
        String destination = SimpMessageHeaderAccessor.getDestination(headers);
        TrackedSession tracked = trackedSession(headers);
        if (tracked != null && subscriptionId != null && destination != null) {
            tracked.subscriptions().putIfAbsent(subscriptionId, destination);
        }
    }

    /**
     * Forgets a subscription the client ended.
     *
     * @param event the UNSUBSCRIBE of a session
     */
    @EventListener
    public void onSessionUnsubscribe(SessionUnsubscribeEvent event) {
        MessageHeaders headers = event.getMessage().getHeaders();
        String subscriptionId = SimpMessageHeaderAccessor.getSubscriptionId(headers);
        TrackedSession tracked = trackedSession(headers);
        if (tracked != null && subscriptionId != null) {
            tracked.subscriptions().remove(subscriptionId);
        }
    }

    /**
     * Whether a session already holds a subscription with this id.
     *
     * @param sessionId      the STOMP session
     * @param subscriptionId the id a SUBSCRIBE frame names
     * @return true when the session holds that id; false for an unknown session or a missing id
     */
    public boolean holdsSubscription(String sessionId, String subscriptionId) {
        TrackedSession tracked = sessionId == null ? null : sessions.get(sessionId);
        return tracked != null && subscriptionId != null && tracked.subscriptions().containsKey(subscriptionId);
    }

    /**
     * The number of subscriptions a session holds.
     *
     * @param sessionId the STOMP session
     * @return the count; 0 for an unknown session
     */
    public int subscriptionCount(String sessionId) {
        TrackedSession tracked = sessionId == null ? null : sessions.get(sessionId);
        return tracked == null ? 0 : tracked.subscriptions().size();
    }

    /**
     * Whether the session may open one more subscription.
     *
     * @param sessionId the STOMP session
     * @return true while it holds fewer than the maxSubscriptionsPerSession of
     *         {@link RealtimeProperties}
     */
    public boolean allowsSubscription(String sessionId) {
        return subscriptionCount(sessionId) < realtimeProperties.getMaxSubscriptionsPerSession();
    }

    /**
     * Counts one frame of the session in a send bucket, or refuses it when the session already
     * sent max frames in that bucket within the last windowMs. A refused frame is not counted, so
     * a client that waits gets through as soon as its oldest frame leaves the window: with max 1,
     * the next frame passes once now is at least the last accepted frame plus windowMs.
     *
     * Each bucket is its own window, so a consumer can throttle one kind of frame more tightly
     * than the shared limit on every SEND. An unknown session is refused, because a frame is
     * only read from a session the transport has registered.
     *
     * @param sessionId the STOMP session
     * @param bucket    the window to count in, such as {@link RealtimeConstant#SEND_BUCKET_INBOUND}
     * @param max       the most frames the window holds
     * @param windowMs  the length of the sliding window, in milliseconds
     * @return true when the frame may go on
     */
    public boolean tryAcquireSend(String sessionId, String bucket, int max, long windowMs) {
        TrackedSession tracked = sessionId == null ? null : sessions.get(sessionId);
        if (tracked == null) {
            return false;
        }
        Instant now = clock.instant();
        Instant windowStart = now.minusMillis(windowMs);
        AtomicBoolean allowed = new AtomicBoolean(false);
        // compute runs atomically per bucket, so two frames racing on one session cannot both
        // take the last slot.
        tracked.sendWindows().compute(bucket, (String key, Deque<Instant> sendTimes) -> {
            Deque<Instant> times = sendTimes == null ? new ArrayDeque<>() : sendTimes;
            while (!times.isEmpty() && !times.peekFirst().isAfter(windowStart)) {
                times.pollFirst();
            }
            if (times.size() < max) {
                times.addLast(now);
                allowed.set(true);
            }
            return times.isEmpty() ? null : times;
        });
        return allowed.get();
    }

    /**
     * Checks every open subscription of the given users again and ends the ones the destination
     * authorizers no longer allow. The sessions stay open and keep their other subscriptions.
     *
     * The subscription is removed on the broker itself, with an UNSUBSCRIBE sent to the broker
     * channel, because the client cannot be trusted to drop it. The client is not told; it learns
     * of lost access from the REST reads it makes. When that removal fails, the session is closed
     * with RTM_SEC_001 instead, so the client reconnects and every subscription is authorized
     * again on SUBSCRIBE.
     *
     * @param userIds the users whose access changed
     * @return the number of subscriptions ended
     */
    public int revalidateSubscriptions(Collection<UUID> userIds) {
        Set<UUID> users = Set.copyOf(userIds);
        int revoked = 0;
        for (TrackedSession tracked : List.copyOf(sessions.values())) {
            RealtimePrincipal principal = tracked.principal();
            if (principal == null || tracked.closing() || !users.contains(principal.userId())) {
                continue;
            }
            for (Map.Entry<String, String> subscription : List.copyOf(tracked.subscriptions().entrySet())) {
                RealtimeFrameDecision decision =
                        realtimeDestinationAuthorizerRegistry.authorizeSubscribe(principal, subscription.getValue());
                if (decision.passes()) {
                    continue;
                }
                if (!revoke(tracked, principal, subscription.getKey(), subscription.getValue(), decision.reason())) {
                    break;
                }
                revoked++;
            }
        }
        loggingService.info(LOG_PREFIX, LogLayer.HANDLER, RealtimeConstant.SUBSCRIPTIONS_REVALIDATED,
                users.size(), revoked);
        return revoked;
    }

    /**
     * The members of an organization with at least one open session on this process.
     *
     * @param organizationId the organization
     * @return the online user ids, a snapshot
     */
    public Set<UUID> onlineUserIds(UUID organizationId) {
        synchronized (presenceLock) {
            Map<UUID, Set<String>> users = presence.get(organizationId);
            return users == null ? Set.of() : Set.copyOf(users.keySet());
        }
    }

    /**
     * Ends every open session whose organization access was revoked, once that change has
     * committed, so a change that rolls back closes nothing. Each session gets an ERROR frame
     * with RTM_SEC_002 first, so the client shows the organization error instead of reconnecting.
     *
     * Runs on the committing thread, so the sessions are closed before the revoking request
     * returns. A socket that has passed the handshake but not yet sent CONNECT is matched by the
     * principal its ticket left in the session attributes, so it cannot CONNECT afterwards.
     *
     * @param event the revoked access; a null field means any
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccessRevoked(AccessRevokedEvent event) {
        closeRevokedSessions(event, null);
    }

    /**
     * Ends the open sessions of a user in an organization once a change that took an app from the
     * user has committed, when that app is one of {@link RealtimeConstant#SESSION_APPS}. The user
     * stays a member, but the sessions carry that app's signals, which the user may no longer read.
     * The loss of any other app leaves the sessions open.
     *
     * @param event the user, the organization and the app the user lost
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppAccessRevoked(AppAccessRevokedEvent event) {
        if (!RealtimeConstant.SESSION_APPS.contains(event.app())) {
            return;
        }
        closeRevokedSessions(AccessRevokedEvent.member(event.userId(), event.organizationId()), event.app());
    }

    /**
     * Sends RTM_SEC_002 to every open session the scope matches and closes it.
     *
     * @param scope   the (user, organization) pairs whose sessions end; a null field means any
     * @param lostApp the app whose loss ends the sessions, or null when the whole organization access ended
     */
    private void closeRevokedSessions(AccessRevokedEvent scope, App lostApp) {
        try {
            int closed = 0;
            for (TrackedSession tracked : List.copyOf(sessions.values())) {
                RealtimePrincipal principal = sessionPrincipal(tracked);
                if (principal != null
                        && scope.matches(principal.userId(), principal.organizationId())
                        && markClosing(tracked)) {
                    sendErrorAndClose(tracked.session(), ExceptionDomainRealtimeError.SEC_REALTIME_FORBIDDEN);
                    closed++;
                }
            }
            loggingService.info(LOG_PREFIX, LogLayer.HANDLER, RealtimeConstant.SESSIONS_CLOSED_FOR_REVOKED_ACCESS,
                    scope.userId(), scope.organizationId(), lostApp, closed);
        } catch (RuntimeException closeFailure) {
            // Not re-thrown: the revoking change has committed and the caller's request
            // succeeded, so there is nothing to roll back. The expiry sweep closes the sessions
            // within one token lifetime, and every REST read already refuses the user; the error
            // log routes the failure to error aggregation.
            loggingService.error(LOG_PREFIX, LogLayer.HANDLER, RealtimeConstant.ACCESS_REVOKED_CLOSE_FAILED,
                    closeFailure, scope.userId(), scope.organizationId(), lostApp);
        }
    }

    /**
     * Ends every session whose access token has expired.
     */
    @Scheduled(fixedDelayString = RealtimeConstant.EXPIRY_SWEEP_MS_PLACEHOLDER)
    public void closeExpiredSessions() {
        Instant now = clock.instant();
        for (TrackedSession tracked : List.copyOf(sessions.values())) {
            RealtimePrincipal principal = tracked.principal();
            if (principal != null && principal.isExpiredAt(now) && markClosing(tracked)) {
                loggingService.info(LOG_PREFIX, LogLayer.SCHEDULER, RealtimeConstant.SESSION_EXPIRED_CLOSING,
                        tracked.session().getId(), principal.getName(), principal.expiresAt());
                sendErrorAndClose(tracked.session(), ExceptionDomainRealtimeError.SEC_REALTIME_UNAUTHENTICATED);
            }
        }
    }

    /**
     * The number of open sessions this process tracks, authenticated or not.
     *
     * @return the session count
     */
    public int openSessionCount() {
        return sessions.size();
    }

    private void onConnectionClosed(String sessionId) {
        TrackedSession tracked = sessions.remove(sessionId);
        if (tracked == null || tracked.principal() == null) {
            return;
        }
        if (removePresence(tracked.principal(), sessionId)) {
            publishPresence(tracked.principal(), false);
        }
    }

    /**
     * Flags a session as being closed, so a second sweep or a revocation racing with the first
     * does not send a second ERROR frame while the close is in flight. The session stays tracked
     * until the transport reports it closed, which is where presence is updated.
     *
     * @return true when this call flagged it, false when it was already closing or is gone
     */
    private boolean markClosing(TrackedSession tracked) {
        String sessionId = tracked.session().getId();
        AtomicBoolean flagged = new AtomicBoolean(false);
        sessions.computeIfPresent(sessionId, (String id, TrackedSession current) -> {
            if (current.closing()) {
                return current;
            }
            flagged.set(true);
            return current.asClosing();
        });
        return flagged.get();
    }

    private boolean addPresence(RealtimePrincipal principal, String sessionId) {
        synchronized (presenceLock) {
            Set<String> userSessions = presence
                    .computeIfAbsent(principal.organizationId(), (UUID organizationId) -> new HashMap<>())
                    .computeIfAbsent(principal.userId(), (UUID userId) -> new HashSet<>());
            boolean first = userSessions.isEmpty();
            userSessions.add(sessionId);
            return first;
        }
    }

    private boolean removePresence(RealtimePrincipal principal, String sessionId) {
        synchronized (presenceLock) {
            Map<UUID, Set<String>> users = presence.get(principal.organizationId());
            if (users == null) {
                return false;
            }
            Set<String> userSessions = users.get(principal.userId());
            if (userSessions == null || !userSessions.remove(sessionId)) {
                return false;
            }
            if (!userSessions.isEmpty()) {
                return false;
            }
            users.remove(principal.userId());
            if (users.isEmpty()) {
                presence.remove(principal.organizationId());
            }
            return true;
        }
    }

    private void publishPresence(RealtimePrincipal principal, boolean online) {
        try {
            applicationEventPublisher.publishEvent(
                    new RealtimePresenceChangedEvent(principal.organizationId(), principal.userId(), online));
        } catch (RuntimeException publishFailure) {
            // Not re-thrown: this runs inside the transport's connect and close callbacks, and a
            // failure there would break the session bookkeeping. The registry state is already
            // correct, the member list REST read shows the presence, and the clients resync on
            // their next connect; the error log routes the failure to error aggregation.
            loggingService.error(LOG_PREFIX, LogLayer.HANDLER, RealtimeConstant.PRESENCE_PUBLISH_FAILED,
                    publishFailure, principal.userId(), principal.organizationId(), online);
        }
    }

    /**
     * Ends one subscription on the broker.
     *
     * @return true when it was ended, false when the session was closed instead
     */
    private boolean revoke(TrackedSession tracked, RealtimePrincipal principal, String subscriptionId,
                           String destination, String reason) {
        String sessionId = tracked.session().getId();
        boolean sent;
        try {
            sent = brokerChannel.getObject().send(unsubscribeMessage(sessionId, subscriptionId, principal));
        } catch (RuntimeException revokeFailure) {
            loggingService.error(LOG_PREFIX, LogLayer.HANDLER, RealtimeConstant.SUBSCRIPTION_REVOKE_FAILED,
                    revokeFailure, destination, sessionId, principal.getName());
            sent = false;
        }
        if (!sent) {
            if (markClosing(tracked)) {
                sendErrorAndClose(tracked.session(), ExceptionDomainRealtimeError.SEC_REALTIME_UNAUTHENTICATED);
            }
            return false;
        }
        tracked.subscriptions().remove(subscriptionId);
        loggingService.info(LOG_PREFIX, LogLayer.HANDLER, RealtimeConstant.SUBSCRIPTION_REVOKED,
                reason, destination, sessionId, principal.getName());
        return true;
    }

    private Message<byte[]> unsubscribeMessage(String sessionId, String subscriptionId, RealtimePrincipal principal) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.UNSUBSCRIBE);
        accessor.setSessionId(sessionId);
        accessor.setSubscriptionId(subscriptionId);
        accessor.setUser(principal);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(EMPTY_PAYLOAD, accessor.getMessageHeaders());
    }

    /**
     * The principal of a session: the one set on CONNECT, or before CONNECT the one the
     * handshake ticket left in the session attributes; null for neither.
     */
    private RealtimePrincipal sessionPrincipal(TrackedSession tracked) {
        if (tracked.principal() != null) {
            return tracked.principal();
        }
        Object handshakePrincipal = tracked.session().getAttributes().get(RealtimeConstant.SESSION_PRINCIPAL_ATTRIBUTE);
        return handshakePrincipal instanceof RealtimePrincipal principal ? principal : null;
    }

    private TrackedSession trackedSession(MessageHeaders headers) {
        String sessionId = SimpMessageHeaderAccessor.getSessionId(headers);
        return sessionId == null ? null : sessions.get(sessionId);
    }

    private void sendErrorAndClose(WebSocketSession session, ExceptionDomainRealtimeError error) {
        try {
            // Sent through the outbound channel so it is serialized with the broker's own frames;
            // the STOMP handler closes the session after writing an ERROR frame.
            clientOutboundChannel.getObject().send(errorFrame(session.getId(), error));
        } catch (RuntimeException sendFailure) {
            loggingService.warn(LOG_PREFIX, LogLayer.HANDLER, RealtimeConstant.SESSION_ERROR_NOTICE_FAILED,
                    session.getId(), error.getCode(), sendFailure);
            closeDirectly(session);
        }
    }

    private Message<byte[]> errorFrame(String sessionId, ExceptionDomainRealtimeError error) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(error.getCode());
        accessor.setSessionId(sessionId);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(EMPTY_PAYLOAD, accessor.getMessageHeaders());
    }

    private void closeDirectly(WebSocketSession session) {
        try {
            session.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException | RuntimeException closeFailure) {
            // The client is gone or going either way; logged at error with its cause so a
            // transport that refuses to close sessions shows up in error aggregation.
            loggingService.error(LOG_PREFIX, LogLayer.HANDLER, RealtimeConstant.SESSION_CLOSE_FAILED,
                    closeFailure, session.getId());
        }
    }

    /**
     * One open session; principal stays null until CONNECT is accepted, closing turns true once
     * an ERROR frame or a close has been sent. subscriptions maps each subscription id to its
     * destination as the client named it; sendWindows maps each send bucket to the times of its
     * accepted frames, oldest first. Both maps are shared by every copy of the record.
     */
    private record TrackedSession(WebSocketSession session, RealtimePrincipal principal, boolean closing,
                                  Map<String, String> subscriptions, Map<String, Deque<Instant>> sendWindows) {

        TrackedSession withPrincipal(RealtimePrincipal connected) {
            return new TrackedSession(session, connected, closing, subscriptions, sendWindows);
        }

        TrackedSession asClosing() {
            return new TrackedSession(session, principal, true, subscriptions, sendWindows);
        }
    }
}
