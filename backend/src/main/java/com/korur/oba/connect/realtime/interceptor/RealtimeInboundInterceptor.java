package com.korur.oba.connect.realtime.interceptor;

// 1. Framework-level
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.security.Principal;
import java.time.Instant;
import java.util.Map;

// 4. Internal
import com.korur.oba.connect.realtime.constant.RealtimeConstant;
import com.korur.oba.connect.realtime.dto.RealtimeFrameDecision;
import com.korur.oba.connect.realtime.dto.RealtimePrincipal;
import com.korur.oba.connect.realtime.exception.ExceptionDomainRealtimeError;
import com.korur.oba.connect.realtime.helper.RealtimeDestinationAuthorizerRegistry;
import com.korur.oba.connect.realtime.properties.RealtimeProperties;
import com.korur.oba.connect.realtime.service.RealtimeSessionRegistry;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Authenticates and confines every realtime session on the client inbound channel.
 *
 * A STOMP frame is not an HTTP request, so neither the resource server nor
 * {@link com.korur.oba.dojo.security.interceptor.RequiredUserAccessInterceptor} ever sees it.
 * The access check is made once, when the client asks for a handshake ticket over REST;
 * {@link com.korur.oba.connect.realtime.service.RealtimeTicketService} redeems that ticket on
 * the handshake and leaves its {@link RealtimePrincipal} in the socket's session attributes. This
 * interceptor applies the rules from there on:
 *
 * CONNECT takes the principal out of the session attributes and makes it the session user, keyed
 * to that user and organization; its headers are ignored. A CONNECT without one is refused with
 * RTM_SEC_001: the principal is taken only once, so the session user can never be replaced, even
 * if a repeated CONNECT got past Spring's STOMP handler, which already refuses one.
 *
 * After CONNECT every SUBSCRIBE and SEND is decided by
 * {@link RealtimeDestinationAuthorizerRegistry}, deny by default: the shared destination rules
 * first, then the one consumer that claims the destination. A SUBSCRIBE that carries a selector
 * header is refused, because a selector filters on the broker and is not authorized per session.
 * {@link RealtimeSessionRegistry} bounds the SEND rate and the open subscriptions of one session.
 * Any frame after the token behind the session has expired is refused.
 *
 * A frame is handled by its verdict. {@link RealtimeFrameDecision.Verdict#REFUSE} throws, which
 * ends the session (below). {@link RealtimeFrameDecision.Verdict#DROP} returns null, so the frame
 * goes nowhere and the session stays open: a consumer's refusal, a failing consumer check, a
 * limit reached. The client is not answered; it relies on its REST reads.
 *
 * A refusal throws a {@link MessageDeliveryException} whose message is the error code alone.
 * Spring's STOMP handler turns it into an ERROR frame with that code as the message header and
 * closes the session, which is the contract the clients key their reconnect behaviour on:
 * RTM_SEC_001 means reconnect with a new ticket, RTM_SEC_002 means this session is not allowed.
 */
@Component
@RequiredArgsConstructor
public class RealtimeInboundInterceptor implements ChannelInterceptor {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(RealtimeInboundInterceptor.class);

    private final RealtimeDestinationAuthorizerRegistry realtimeDestinationAuthorizerRegistry;
    private final RealtimeSessionRegistry realtimeSessionRegistry;
    private final RealtimeProperties realtimeProperties;
    private final LoggingService loggingService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            // Heartbeats and broker-internal messages carry no STOMP command and nothing to check.
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
            authenticateConnect(message, accessor);
            return message;
        }
        if (command == StompCommand.SUBSCRIBE) {
            RealtimePrincipal principal = requireLiveSession(message, accessor);
            return apply(message, accessor, principal, decideSubscribe(accessor, principal));
        }
        if (command == StompCommand.SEND) {
            RealtimePrincipal principal = requireLiveSession(message, accessor);
            return apply(message, accessor, principal, decideSend(accessor, principal));
        }
        if (command == StompCommand.UNSUBSCRIBE || command == StompCommand.DISCONNECT) {
            // Both only take access away, so they pass even after the token expired: a client
            // must always be able to leave a destination or the session cleanly.
            return message;
        }
        // Every other command is refused. MESSAGE in particular is a broker-to-client frame: sent
        // by a client, the simple broker would deliver it to every subscriber of its destination,
        // past every SEND rule. ACK, NACK and the transaction frames have no use with the simple
        // broker, and the client sends none of them.
        throw rejectFrame(message, accessor, accessor.getUser(),
                ExceptionDomainRealtimeError.SEC_REALTIME_FORBIDDEN, RealtimeConstant.REASON_COMMAND_NOT_ALLOWED);
    }

    /**
     * The limit is checked before the authorizers, so a session past its cap causes no
     * consumer query.
     *
     * A subscription id the session already holds is refused. The broker keeps the first
     * subscription of an id and ignores the second, so a reused id would leave the session
     * receiving a destination that the session registry no longer tracks and that a later access
     * change could never end.
     */
    private RealtimeFrameDecision decideSubscribe(StompHeaderAccessor accessor, RealtimePrincipal principal) {
        if (accessor.getFirstNativeHeader(RealtimeConstant.SELECTOR_HEADER) != null) {
            return RealtimeFrameDecision.refuse(RealtimeConstant.REASON_SELECTOR);
        }
        if (realtimeSessionRegistry.holdsSubscription(accessor.getSessionId(), accessor.getSubscriptionId())) {
            return RealtimeFrameDecision.refuse(RealtimeConstant.REASON_SUBSCRIPTION_ID_REUSED);
        }
        if (!realtimeSessionRegistry.allowsSubscription(accessor.getSessionId())) {
            return RealtimeFrameDecision.drop(RealtimeConstant.REASON_SUBSCRIPTION_CAP);
        }
        return realtimeDestinationAuthorizerRegistry.authorizeSubscribe(principal, accessor.getDestination());
    }

    /**
     * The rate is checked before the authorizers, so a flood causes no consumer query.
     */
    private RealtimeFrameDecision decideSend(StompHeaderAccessor accessor, RealtimePrincipal principal) {
        if (!realtimeSessionRegistry.tryAcquireSend(accessor.getSessionId(), RealtimeConstant.SEND_BUCKET_INBOUND,
                realtimeProperties.getInboundSendMax(), realtimeProperties.getInboundSendWindowMs())) {
            return RealtimeFrameDecision.drop(RealtimeConstant.REASON_SEND_RATE);
        }
        return realtimeDestinationAuthorizerRegistry.authorizeSend(principal, accessor.getDestination());
    }

    private Message<?> apply(Message<?> message, StompHeaderAccessor accessor, RealtimePrincipal principal,
                             RealtimeFrameDecision decision) {
        if (decision.verdict() == RealtimeFrameDecision.Verdict.REFUSE) {
            throw rejectFrame(message, accessor, principal,
                    ExceptionDomainRealtimeError.SEC_REALTIME_FORBIDDEN, decision.reason());
        }
        if (decision.verdict() == RealtimeFrameDecision.Verdict.DROP) {
            loggingService.warn(LOG_PREFIX, LogLayer.HANDLER, RealtimeConstant.FRAME_DROPPED,
                    decision.reason(), accessor.getCommand(), accessor.getDestination(),
                    accessor.getSessionId(), principal.getName());
            return null;
        }
        return message;
    }

    /**
     * Binds the session to the principal its handshake ticket carried. The principal is removed
     * from the session attributes as it is taken, so any later CONNECT on the same socket finds
     * none and is refused.
     */
    private void authenticateConnect(Message<?> message, StompHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        Object handshakePrincipal = sessionAttributes == null
                ? null
                : sessionAttributes.remove(RealtimeConstant.SESSION_PRINCIPAL_ATTRIBUTE);
        if (!(handshakePrincipal instanceof RealtimePrincipal principal)) {
            throw rejectConnect(message, sessionId,
                    ExceptionDomainRealtimeError.SEC_REALTIME_UNAUTHENTICATED, RealtimeConstant.REASON_NO_HANDSHAKE_PRINCIPAL);
        }
        if (principal.isExpiredAt(Instant.now())) {
            throw rejectConnect(message, sessionId,
                    ExceptionDomainRealtimeError.SEC_REALTIME_UNAUTHENTICATED, RealtimeConstant.REASON_SESSION_EXPIRED);
        }
        accessor.setUser(principal);
        loggingService.info(LOG_PREFIX, LogLayer.HANDLER, RealtimeConstant.CONNECT_ACCEPTED,
                sessionId, principal.userId(), principal.organizationId(), principal.expiresAt());
    }

    private RealtimePrincipal requireLiveSession(Message<?> message, StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (!(user instanceof RealtimePrincipal principal)) {
            throw rejectFrame(message, accessor, user,
                    ExceptionDomainRealtimeError.SEC_REALTIME_UNAUTHENTICATED, RealtimeConstant.REASON_NO_SESSION_USER);
        }
        if (principal.isExpiredAt(Instant.now())) {
            throw rejectFrame(message, accessor, principal,
                    ExceptionDomainRealtimeError.SEC_REALTIME_UNAUTHENTICATED, RealtimeConstant.REASON_SESSION_EXPIRED);
        }
        return principal;
    }

    private MessageDeliveryException rejectConnect(
            Message<?> message, String sessionId, ExceptionDomainRealtimeError error, String reason) {
        loggingService.warn(LOG_PREFIX, LogLayer.HANDLER, RealtimeConstant.CONNECT_REJECTED,
                error.getCode(), reason, sessionId);
        return new MessageDeliveryException(message, error.getCode());
    }

    private MessageDeliveryException rejectFrame(
            Message<?> message, StompHeaderAccessor accessor, Principal user,
            ExceptionDomainRealtimeError error, String reason) {
        String userName = user == null ? null : user.getName();
        loggingService.warn(LOG_PREFIX, LogLayer.HANDLER, RealtimeConstant.FRAME_REJECTED,
                error.getCode(), reason, accessor.getCommand(), accessor.getDestination(),
                accessor.getSessionId(), userName);
        return new MessageDeliveryException(message, error.getCode());
    }
}
