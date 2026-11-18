package com.korur.oba.connect.realtime.service;

// 1. Framework-level
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.realtime.constant.RealtimeConstant;
import com.korur.oba.connect.realtime.dto.RealtimePrincipal;
import com.korur.oba.connect.realtime.dto.RealtimeSignal;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Sends realtime signals to the sessions subscribed to a topic, or to the sessions of one user
 * in one organization.
 *
 * The bus is a delivery accelerator, not a store: a signal for nobody connected is dropped, and
 * so is one sent while the endpoint is off. Clients resync over REST on every connect, so a lost
 * signal costs freshness, never data.
 *
 * Only {@link RealtimeSignal} payloads are accepted. Each consumer builds its destinations with
 * {@link com.korur.oba.connect.realtime.util.RealtimeDestinationUtil} and has them claimed by
 * its {@link RealtimeDestinationAuthorizer}, so a client can subscribe only where that consumer
 * allows and a signal reaches only those authorized subscribers. Every subscriber of a topic
 * receives the same body, so a signal carries content only where every admitted session may read
 * it, such as a channel topic carrying a message to the members who passed the read check.
 *
 * Signals go through {@link SimpMessagingTemplate} to whatever broker
 * {@link com.korur.oba.connect.realtime.config.RealtimeWebSocketConfig} registers.
 * With the in-memory simple broker they reach the sessions of this process only; a broker relay
 * would carry them to every replica without a change here or in any publisher.
 *
 * A signal that cannot be handed to the broker throws a runtime exception, logged here, so the
 * caller can decide whether to go on with the next recipient. A destination outside the allowed
 * prefix throws {@link IllegalArgumentException}, a programming error.
 */
@Component
@RequiredArgsConstructor
public class RealtimeSignalBus {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(RealtimeSignalBus.class);

    private final SimpMessagingTemplate simpMessagingTemplate;
    private final LoggingService loggingService;

    /**
     * Sends a signal to every session subscribed to a topic.
     *
     * @param destination the topic, starting with "/topic/", such as
     *                    "/topic/organizations/{organizationId}/chat"
     * @param signal      the frame body, serialized to JSON
     */
    public void publishToTopic(String destination, RealtimeSignal signal) {
        if (destination == null || !destination.startsWith(RealtimeConstant.TOPIC_DESTINATION_PREFIX)) {
            throw new IllegalArgumentException(RealtimeConstant.PUBLISH_TOPIC_INVALID.formatted(
                    RealtimeConstant.TOPIC_DESTINATION_PREFIX, destination));
        }
        try {
            simpMessagingTemplate.convertAndSend(destination, signal);
        } catch (MessagingException deliveryFailure) {
            loggingService.error(LOG_PREFIX, LogLayer.HANDLER, RealtimeConstant.PUBLISH_TO_TOPIC_FAILED,
                    deliveryFailure, destination, signal.type());
            throw deliveryFailure;
        }
    }

    /**
     * Sends a signal to one user queue of every session the user holds in that organization,
     * and to no session of theirs in another organization.
     *
     * @param userId         the recipient
     * @param organizationId the organization the signal belongs to
     * @param userQueue      the queue, starting with "/queue/", such as "/queue/chat"; the client
     *                       subscribes to it with the "/user" prefix
     * @param signal         the frame body, serialized to JSON
     */
    public void publishToUser(UUID userId, UUID organizationId, String userQueue, RealtimeSignal signal) {
        if (userQueue == null || !userQueue.startsWith(RealtimeConstant.QUEUE_DESTINATION_PREFIX)) {
            throw new IllegalArgumentException(RealtimeConstant.PUBLISH_USER_QUEUE_INVALID.formatted(
                    RealtimeConstant.QUEUE_DESTINATION_PREFIX, userQueue));
        }
        try {
            simpMessagingTemplate.convertAndSendToUser(
                    RealtimePrincipal.userKey(userId, organizationId), userQueue, signal);
        } catch (MessagingException deliveryFailure) {
            loggingService.error(LOG_PREFIX, LogLayer.HANDLER, RealtimeConstant.PUBLISH_TO_USER_FAILED,
                    deliveryFailure, userId, organizationId, userQueue, signal.type());
            throw deliveryFailure;
        }
    }
}
