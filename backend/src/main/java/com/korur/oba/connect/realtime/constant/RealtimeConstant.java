package com.korur.oba.connect.realtime.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Duration;
import java.util.List;
import java.util.Set;

// 4. Internal
import com.korur.oba.dojo.security.constant.SecurityConstant;
import com.korur.oba.shrine.access.enums.App;

/**
 * Destination rules, prefixes, configuration keys, refusal reasons and log messages of the
 * realtime transport: the handshake ticket, the STOMP endpoint, the CONNECT and frame checks, the
 * destination authorizer registry, the session registry with its inbound limits, and the signal
 * bus. The destinations of a consumer, such as chat, belong to that consumer.
 *
 * No log message here takes a token, a ticket or a frame payload as a parameter. A payload may
 * carry message content, and the transport does not know what it holds, so it never logs one.
 */
public final class RealtimeConstant {

    private RealtimeConstant() {
    }

    // --- Endpoint and destinations ---
    /** Defined beside the public paths of the HTTP security, which must let the handshake through. */
    public static final String ENDPOINT_PATH = SecurityConstant.REALTIME_ENDPOINT_PATH;
    public static final String BROKER_TOPIC_PREFIX = "/topic";
    public static final String BROKER_QUEUE_PREFIX = "/queue";
    public static final String APPLICATION_DESTINATION_PREFIX = "/app";
    public static final String USER_DESTINATION_PREFIX = "/user";

    /** The prefixes a client may SUBSCRIBE under; everything else, a raw /queue among them, is refused. */
    public static final String TOPIC_DESTINATION_PREFIX = BROKER_TOPIC_PREFIX + "/";
    public static final String USER_QUEUE_DESTINATION_PREFIX = USER_DESTINATION_PREFIX + BROKER_QUEUE_PREFIX + "/";

    /**
     * The prefix a per-user destination passed to
     * {@link org.springframework.messaging.simp.SimpMessagingTemplate#convertAndSendToUser} starts with.
     */
    public static final String QUEUE_DESTINATION_PREFIX = BROKER_QUEUE_PREFIX + "/";

    /** The prefix a client may SEND under. */
    public static final String SEND_DESTINATION_PREFIX = APPLICATION_DESTINATION_PREFIX + "/";

    /**
     * Topics scoped to one organization, spelled as this prefix, the organization id and a
     * consumer suffix such as "/chat". Only the session's own organization may be subscribed.
     */
    public static final String ORGANIZATION_TOPIC_PREFIX = "/topic/organizations/";
    public static final String DESTINATION_SEPARATOR = "/";

    /**
     * Characters and sequences no client destination may hold. The simple broker reads *, ? and
     * {} in a subscription as a pattern, so one subscription could match every organization's
     * topic; .. is refused so a destination can never step out of its prefix.
     */
    public static final List<String> FORBIDDEN_DESTINATION_SEQUENCES = List.of("*", "?", "{", "}", "..");

    /** Longest destination a client may name; real destinations stay well below it. */
    public static final int MAX_DESTINATION_LENGTH = 256;

    /** STOMP header of a broker-side message filter, refused because it is not authorized per session. */
    public static final String SELECTOR_HEADER = "selector";

    // --- Handshake ticket ---
    public static final String TICKETS_PATH = "/realtime/tickets";
    /** The handshake query parameter that carries the ticket. */
    public static final String TICKET_QUERY_PARAMETER = "ticket";
    /** Where the handshake leaves the ticket's principal for the CONNECT frame of the same socket. */
    public static final String SESSION_PRINCIPAL_ATTRIBUTE = "realtimePrincipal";
    /** Long enough for a client to open the socket right after the request, short enough that a leaked URL is useless. */
    public static final Duration TICKET_TTL = Duration.ofSeconds(30);
    /** 256 bits of {@link java.security.SecureRandom} output, so a ticket cannot be guessed within its lifetime. */
    public static final int TICKET_BYTES = 32;
    /** A client needs one ticket per connect; more than a few unused ones means a loop or abuse. */
    public static final int MAX_LIVE_TICKETS_PER_USER = 5;

    // --- Access ---
    /**
     * The apps whose signals travel over the realtime transport. A user who loses one of them in an
     * organization has every open session and unused ticket there ended, because a session is bound to
     * the organization, not to one app. Losing any other app leaves the sessions open. An app that starts
     * sending realtime signals is added here, and nowhere else.
     */
    public static final Set<App> SESSION_APPS = Set.of(App.KAIZEN);

    // --- Configuration ---
    public static final String PROPERTIES_PREFIX = "oba.realtime";
    public static final String BROKER_PROPERTY = PROPERTIES_PREFIX + ".broker";
    /** The only broker built today: the in-memory simple broker of this process. */
    public static final String BROKER_SIMPLE = "simple";
    public static final String BROKER_UNSUPPORTED =
            "oba.realtime.broker='%s' is not supported; the only broker built is '" + BROKER_SIMPLE + "'.";
    public static final String EXPIRY_SWEEP_MS_PLACEHOLDER = "${oba.realtime.expiry-sweep-ms:30000}";
    public static final String HEARTBEAT_THREAD_PREFIX = "realtime-heartbeat-";
    public static final String EXECUTOR_BEAN_NAME = "realtimeExecutor";
    public static final String EXECUTOR_THREAD_PREFIX = "realtime-";
    public static final String CLIENT_OUTBOUND_CHANNEL_BEAN_NAME = "clientOutboundChannel";
    public static final String BROKER_CHANNEL_BEAN_NAME = "brokerChannel";

    // --- Refusal reasons of the handshake, logged with the HTTP 401 ---
    public static final String REASON_TICKET_MISSING = "ticket missing";
    public static final String REASON_TICKET_UNKNOWN = "ticket unknown, already used or purged as expired";
    public static final String REASON_TICKET_EXPIRED = "ticket expired";

    // --- Refusal reasons of the CONNECT and frame checks, logged beside the error code ---
    public static final String REASON_NO_HANDSHAKE_PRINCIPAL = "no ticket principal on the socket; no ticket, or a repeated CONNECT";
    public static final String REASON_NO_SESSION_USER = "frame before an authenticated CONNECT";
    public static final String REASON_SESSION_EXPIRED = "session token expired";
    public static final String REASON_DESTINATION_BLANK = "destination missing";
    public static final String REASON_DESTINATION_TOO_LONG = "destination too long";
    public static final String REASON_DESTINATION_PATTERN = "destination holds a wildcard or ..";
    public static final String REASON_DESTINATION_PREFIX = "destination outside the allowed prefixes";
    public static final String REASON_DESTINATION_ORGANIZATION = "topic not scoped to the session's organization or not canonical";
    public static final String REASON_DESTINATION_UNCLAIMED = "no consumer claims the destination";
    public static final String REASON_DESTINATION_CONTESTED = "more than one consumer claims the destination";
    public static final String REASON_SELECTOR = "selector header not allowed";
    public static final String REASON_COMMAND_NOT_ALLOWED = "STOMP command not allowed from a client";
    public static final String REASON_SUBSCRIPTION_ID_REUSED = "subscription id already held by the session";

    // --- Drop reasons, logged with the dropped frame; the session stays open ---
    public static final String REASON_CONSUMER_DENIED = "consumer denied the destination";
    public static final String REASON_CONSUMER_FAILED = "consumer check failed";
    public static final String REASON_SUBSCRIPTION_CAP = "subscription cap of the session reached";
    public static final String REASON_SEND_RATE = "send rate of the session exceeded";

    // --- Send buckets of RealtimeSessionRegistry.tryAcquireSend, one sliding window each per session ---
    /** Every SEND frame of a session, whatever its destination. */
    public static final String SEND_BUCKET_INBOUND = "inbound";

    // --- RealtimeTicketController ---
    public static final String RESPONSE_TICKET_ISSUED = "Realtime ticket issued successfully";
    public static final String TICKET_ISSUE_REQUEST = "Request to issue a realtime ticket. organizationId={}";
    public static final String TICKET_ISSUE_COMPLETED = "Realtime ticket issued. organizationId={}";

    // --- RealtimeTicketService ---
    public static final String TICKET_ISSUED =
            "Realtime ticket issued. userId={}, organizationId={}, ticketExpiresAt={}, tokenExpiresAt={}";
    public static final String TICKET_REFUSED_NO_TOKEN_EXPIRY =
            "Realtime ticket refused: the access token carries no expiry. userId={}, organizationId={}";
    public static final String TICKET_REFUSED_LIMIT =
            "Realtime ticket refused: the user holds the most unused tickets allowed. userId={}, organizationId={}, live={}";
    public static final String HANDSHAKE_REFUSED =
            "Realtime handshake refused. reason={}, remoteAddress={}";
    public static final String HANDSHAKE_ACCEPTED =
            "Realtime handshake accepted. userId={}, organizationId={}";
    /** lostApp is null when the whole organization access ended, and names the app when only that app was lost. */
    public static final String TICKETS_DROPPED_FOR_REVOKED_ACCESS =
            "Dropped the unused realtime tickets after an access revocation. userId={}, organizationId={}, lostApp={}";
    public static final String ACCESS_REVOKED_TICKET_DROP_FAILED =
            "Could not drop the unused realtime tickets after an access revocation; they expire within their lifetime. userId={}, organizationId={}, lostApp={}";

    // --- RealtimeInboundInterceptor ---
    public static final String CONNECT_REJECTED =
            "Realtime CONNECT rejected. code={}, reason={}, sessionId={}";
    public static final String FRAME_REJECTED =
            "Realtime frame rejected. code={}, reason={}, command={}, destination={}, sessionId={}, user={}";
    public static final String CONNECT_ACCEPTED =
            "Realtime session authenticated. sessionId={}, userId={}, organizationId={}, expiresAt={}";
    public static final String FRAME_DROPPED =
            "Realtime frame dropped. reason={}, command={}, destination={}, sessionId={}, user={}";

    // --- RealtimeDestinationAuthorizerRegistry ---
    public static final String AUTHORIZERS_REGISTERED =
            "Realtime destination authorizers registered. consumers={}";
    public static final String AUTHORIZER_CONSUMER_DUPLICATE =
            "Two realtime destination authorizers share the consumer name '%s'; each consumer must register one.";
    public static final String AUTHORIZER_FAILED =
            "Realtime destination authorizer failed; the frame is dropped. consumer={}, command={}, destination={}, user={}";
    public static final String AUTHORIZER_CONTESTED =
            "More than one realtime destination authorizer claims a destination; it is refused. consumers={}, command={}, destination={}";

    // --- RealtimeSessionRegistry ---
    public static final String SESSION_EXPIRED_CLOSING =
            "Closing realtime session whose access token has expired. sessionId={}, user={}, expiresAt={}";
    /** lostApp is null when the whole organization access ended, and names the app when only that app was lost. */
    public static final String SESSIONS_CLOSED_FOR_REVOKED_ACCESS =
            "Closed the realtime sessions whose access was revoked. userId={}, organizationId={}, lostApp={}, sessions={}";
    public static final String SESSION_ERROR_NOTICE_FAILED =
            "Could not send the ERROR frame; closing the realtime session directly. sessionId={}, code={}";
    public static final String SESSION_CLOSE_FAILED =
            "Could not close realtime session. sessionId={}";
    public static final String PRESENCE_PUBLISH_FAILED =
            "Could not publish a presence change; clients see it at their next resync. userId={}, organizationId={}, online={}";
    public static final String SUBSCRIPTION_REVOKED =
            "Realtime subscription revoked after an access change. reason={}, destination={}, sessionId={}, user={}";
    public static final String SUBSCRIPTION_REVOKE_FAILED =
            "Could not revoke a realtime subscription; closing the session so it reconnects and subscribes again. destination={}, sessionId={}, user={}";
    public static final String SUBSCRIPTIONS_REVALIDATED =
            "Realtime subscriptions revalidated after an access change. users={}, revoked={}";
    public static final String ENDPOINT_DISABLED =
            "Realtime endpoint not registered (oba.realtime.enabled=false); clients fall back to REST reads.";

    public static final String ACCESS_REVOKED_CLOSE_FAILED =
            "Could not close the realtime sessions whose access was revoked; they close when their token expires. userId={}, organizationId={}, lostApp={}";

    // --- RealtimeSignalBus ---
    public static final String PUBLISH_TO_TOPIC_FAILED =
            "Realtime signal not delivered to the topic. destination={}, signalType={}";
    public static final String PUBLISH_TO_USER_FAILED =
            "Realtime signal not delivered to the user queue. userId={}, organizationId={}, userQueue={}, signalType={}";
    public static final String PUBLISH_TOPIC_INVALID =
            "A realtime signal may be published only to a destination under %s: %s";
    public static final String PUBLISH_USER_QUEUE_INVALID =
            "A realtime user signal may be published only to a queue under %s: %s";
}
