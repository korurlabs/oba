package com.korur.oba.connect.realtime.properties;

// 1. Framework-level
import org.springframework.boot.context.properties.ConfigurationProperties;

// 2. Third-party
import lombok.Getter;
import lombok.Setter;

// 3. Java SDK

// 4. Internal
import com.korur.oba.connect.realtime.constant.RealtimeConstant;

/**
 * Configuration properties for the realtime transport.
 * Bound from application.properties with prefix "oba.realtime".
 *
 * Every default here is the production value, so an environment that sets nothing gets a working
 * endpoint.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = RealtimeConstant.PROPERTIES_PREFIX)
public class RealtimeProperties {

    /**
     * Whether the STOMP endpoint accepts handshakes. False turns the live channel off without a
     * deploy of the clients: they keep working over REST, and signals published meanwhile reach
     * no session.
     */
    private boolean enabled = true;

    /**
     * The message broker behind the endpoint. "simple" is the in-memory broker of this process,
     * the only one built; it reaches only the sessions of this replica.
     */
    private String broker = RealtimeConstant.BROKER_SIMPLE;

    /**
     * STOMP heartbeat in both directions, in milliseconds. Below the idle timeouts of Cloudflare
     * and Traefik in front of the API, so an idle but healthy socket is never cut by the proxy.
     */
    private long heartbeatMs = 25_000L;

    /**
     * Interval of the sweep that closes sessions whose access token has expired, in milliseconds.
     * Read by the scheduled sweep,
     * {@link com.korur.oba.connect.realtime.service.RealtimeSessionRegistry#closeExpiredSessions},
     * through its placeholder, which carries the same default.
     */
    private long expirySweepMs = 30_000L;

    /**
     * Most SEND frames one session may send within {@link #inboundSendWindowMs}; the rest are
     * dropped.
     * Every SEND fans out to other sessions, so one client in a loop must not flood the others.
     */
    private int inboundSendMax = 20;

    /** Length of the sliding window of {@link #inboundSendMax}, in milliseconds. */
    private long inboundSendWindowMs = 10_000L;

    /**
     * Most subscriptions one session may hold at once; a SUBSCRIBE past it is dropped. Bounds the
     * memory and the authorizer queries a single session can cause.
     */
    private int maxSubscriptionsPerSession = 50;
}
