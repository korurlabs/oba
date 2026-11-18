package com.korur.oba.connect.realtime.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.connect.realtime.dto.RealtimePrincipal;

/**
 * Access rule of one realtime consumer (chat, tickets) for the destinations it owns.
 *
 * The realtime transport knows nothing about the domains that publish through it, so it cannot
 * decide on its own who may listen to a topic. Each consumer implements this interface as a
 * Spring bean and claims its destinations;
 * {@link com.korur.oba.connect.realtime.helper.RealtimeDestinationAuthorizerRegistry} asks the
 * one claimant of a destination before a SUBSCRIBE or SEND frame passes. The transport depends only
 * on this interface, never on the consumer.
 *
 * Unlike the attachment policies, the default is deny: a destination no authorizer claims is
 * refused, and so is one that two authorizers claim. The registry also applies the rules every
 * destination shares before it asks a consumer: the prefixes, no wildcard patterns, and for
 * /topic/organizations/{organizationId}/... the session's own organization. A consumer therefore
 * decides only its domain rule.
 *
 * The checks run on the inbound channel for every SUBSCRIBE and SEND, and again for every open
 * subscription of a user whose access changed, so they must be quick and must not throw for a
 * missing entity: an unknown or invisible entity is false. A thrown exception drops the frame.
 *
 * A refusal is an ordinary outcome, such as a member who cannot see the board a topic belongs to.
 * The frame is then dropped and the session stays open, because the client cannot know the rule in
 * advance and must not lose its other subscriptions over it.
 */
public interface RealtimeDestinationAuthorizer {

    /**
     * A short, unique name of the consumer, such as "chat". Two authorizers with the same name
     * stop the application at startup.
     *
     * @return the consumer name
     */
    String consumer();

    /**
     * Whether this consumer owns a destination as a client subscribes to it, for example
     * "/topic/organizations/{organizationId}/chat" or "/user/queue/chat".
     *
     * @param destination the SUBSCRIBE destination, already checked for shape and organization
     * @return true when this consumer decides about it
     */
    boolean supportsSubscribe(String destination);

    /**
     * Decides whether the session may receive what is published to a destination this
     * consumer claims.
     *
     * @param principal   the session's user and organization
     * @param destination the SUBSCRIBE destination
     * @return true when the session may subscribe
     */
    boolean authorizeSubscribe(RealtimePrincipal principal, String destination);

    /**
     * Whether this consumer owns a destination a client sends to, under "/app". Consumers that
     * take no client frames keep the default.
     *
     * @param destination the SEND destination, already checked for shape
     * @return true when this consumer decides about it
     */
    default boolean supportsSend(String destination) {
        return false;
    }

    /**
     * Decides whether the session may send to a destination this consumer claims.
     *
     * @param principal   the session's user and organization
     * @param destination the SEND destination
     * @return true when the session may send
     */
    default boolean authorizeSend(RealtimePrincipal principal, String destination) {
        return false;
    }
}
