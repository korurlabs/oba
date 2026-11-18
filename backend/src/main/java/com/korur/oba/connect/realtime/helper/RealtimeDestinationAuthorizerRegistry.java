package com.korur.oba.connect.realtime.helper;

// 1. Framework-level
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.stereotype.Component;

// 2. Third-party

// 3. Java SDK
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

// 4. Internal
import com.korur.oba.connect.realtime.constant.RealtimeConstant;
import com.korur.oba.connect.realtime.dto.RealtimeFrameDecision;
import com.korur.oba.connect.realtime.dto.RealtimePrincipal;
import com.korur.oba.connect.realtime.service.RealtimeDestinationAuthorizer;
import com.korur.oba.connect.realtime.util.RealtimeDestinationUtil;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Decides every SUBSCRIBE and SEND destination of a realtime session, deny by default.
 *
 * The rules every destination shares come first, so no consumer can weaken them:
 *
 * - a destination is present, at most {@link RealtimeConstant#MAX_DESTINATION_LENGTH} long and
 *   holds none of * ? { } or
 *   "..", because the simple broker reads a subscription with a wildcard as a pattern that would
 *   match other organizations' topics;
 * - SUBSCRIBE names a destination under /topic/ or /user/queue/ (a raw /queue/ would reach other
 *   users' queues), SEND one under /app/;
 * - every topic is scoped to one organization, /topic/organizations/{organizationId}/..., and
 *   names the session's own organization in canonical form. A topic outside that prefix has no
 *   tenant, so any session of any organization could receive it; it is refused even when a
 *   consumer would claim it.
 *
 * A frame that breaks one of these is {@link RealtimeFrameDecision.Verdict#REFUSE}: no correct
 * client sends it. Then exactly one {@link RealtimeDestinationAuthorizer} must claim the
 * destination; none or several is {@link RealtimeFrameDecision.Verdict#REFUSE} as well, the
 * second because two rules for one destination are a server fault that must not resolve to
 * whichever answers first. The claimant's refusal, or an exception it throws, is
 * {@link RealtimeFrameDecision.Verdict#DROP}: the frame is discarded and the session keeps its
 * other subscriptions.
 */
@Component
public class RealtimeDestinationAuthorizerRegistry {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(RealtimeDestinationAuthorizerRegistry.class);

    private final List<RealtimeDestinationAuthorizer> authorizers;
    private final LoggingService loggingService;

    /**
     * Registers every consumer's authorizer and stops the application when two share a
     * consumer name, which would mean one consumer registered twice.
     *
     * @param authorizers    every {@link RealtimeDestinationAuthorizer} bean
     * @param loggingService the logging service
     */
    public RealtimeDestinationAuthorizerRegistry(
            List<RealtimeDestinationAuthorizer> authorizers, LoggingService loggingService) {
        Set<String> consumers = new HashSet<>();
        for (RealtimeDestinationAuthorizer authorizer : authorizers) {
            if (!consumers.add(authorizer.consumer())) {
                throw new IllegalStateException(
                        RealtimeConstant.AUTHORIZER_CONSUMER_DUPLICATE.formatted(authorizer.consumer()));
            }
        }
        this.authorizers = List.copyOf(authorizers);
        this.loggingService = loggingService;
        loggingService.info(LOG_PREFIX, LogLayer.CONFIG, RealtimeConstant.AUTHORIZERS_REGISTERED, consumers);
    }

    /**
     * Decides a SUBSCRIBE destination. Also used to re-check an open subscription after the
     * user's access changed.
     *
     * @param principal   the session's user and organization
     * @param destination the destination as the client subscribed to it
     * @return the {@link RealtimeFrameDecision}, with its reason unless it passes
     */
    public RealtimeFrameDecision authorizeSubscribe(RealtimePrincipal principal, String destination) {
        Optional<RealtimeFrameDecision> shapeRefusal = checkShape(destination);
        if (shapeRefusal.isPresent()) {
            return shapeRefusal.get();
        }
        if (!destination.startsWith(RealtimeConstant.TOPIC_DESTINATION_PREFIX)
                && !destination.startsWith(RealtimeConstant.USER_QUEUE_DESTINATION_PREFIX)) {
            return RealtimeFrameDecision.refuse(RealtimeConstant.REASON_DESTINATION_PREFIX);
        }
        if (destination.startsWith(RealtimeConstant.TOPIC_DESTINATION_PREFIX)) {
            // Empty for a topic outside /topic/organizations/ as well, so such a topic is refused.
            Optional<UUID> organizationId = RealtimeDestinationUtil.organizationOf(destination);
            if (organizationId.isEmpty() || !organizationId.get().equals(principal.organizationId())) {
                return RealtimeFrameDecision.refuse(RealtimeConstant.REASON_DESTINATION_ORGANIZATION);
            }
        }
        return askClaimant(principal, destination, StompCommand.SUBSCRIBE,
                (RealtimeDestinationAuthorizer authorizer) -> authorizer.supportsSubscribe(destination),
                RealtimeDestinationAuthorizer::authorizeSubscribe);
    }

    /**
     * Decides a SEND destination.
     *
     * @param principal   the session's user and organization
     * @param destination the destination the client sends to
     * @return the {@link RealtimeFrameDecision}, with its reason unless it passes
     */
    public RealtimeFrameDecision authorizeSend(RealtimePrincipal principal, String destination) {
        Optional<RealtimeFrameDecision> shapeRefusal = checkShape(destination);
        if (shapeRefusal.isPresent()) {
            return shapeRefusal.get();
        }
        if (!destination.startsWith(RealtimeConstant.SEND_DESTINATION_PREFIX)) {
            return RealtimeFrameDecision.refuse(RealtimeConstant.REASON_DESTINATION_PREFIX);
        }
        return askClaimant(principal, destination, StompCommand.SEND,
                (RealtimeDestinationAuthorizer authorizer) -> authorizer.supportsSend(destination),
                RealtimeDestinationAuthorizer::authorizeSend);
    }

    private Optional<RealtimeFrameDecision> checkShape(String destination) {
        if (destination == null || destination.isBlank()) {
            return Optional.of(RealtimeFrameDecision.refuse(RealtimeConstant.REASON_DESTINATION_BLANK));
        }
        if (destination.length() > RealtimeConstant.MAX_DESTINATION_LENGTH) {
            return Optional.of(RealtimeFrameDecision.refuse(RealtimeConstant.REASON_DESTINATION_TOO_LONG));
        }
        for (String forbidden : RealtimeConstant.FORBIDDEN_DESTINATION_SEQUENCES) {
            if (destination.contains(forbidden)) {
                return Optional.of(RealtimeFrameDecision.refuse(RealtimeConstant.REASON_DESTINATION_PATTERN));
            }
        }
        return Optional.empty();
    }

    private RealtimeFrameDecision askClaimant(
            RealtimePrincipal principal, String destination, StompCommand command,
            Predicate<RealtimeDestinationAuthorizer> claims,
            AuthorizerCheck check) {

        List<RealtimeDestinationAuthorizer> claimants = authorizers.stream().filter(claims).toList();
        if (claimants.isEmpty()) {
            return RealtimeFrameDecision.refuse(RealtimeConstant.REASON_DESTINATION_UNCLAIMED);
        }
        if (claimants.size() > 1) {
            loggingService.error(LOG_PREFIX, LogLayer.HANDLER, RealtimeConstant.AUTHORIZER_CONTESTED,
                    claimants.stream().map(RealtimeDestinationAuthorizer::consumer).toList(), command, destination);
            return RealtimeFrameDecision.refuse(RealtimeConstant.REASON_DESTINATION_CONTESTED);
        }
        RealtimeDestinationAuthorizer claimant = claimants.get(0);
        boolean allowed;
        try {
            allowed = check.decide(claimant, principal, destination);
        } catch (RuntimeException failure) {
            // Not re-thrown: an exception here would end the whole session with an ERROR frame
            // over one destination. The frame is dropped instead, which grants nothing, and the
            // error log routes the fault to error aggregation.
            loggingService.error(LOG_PREFIX, LogLayer.HANDLER, RealtimeConstant.AUTHORIZER_FAILED, failure,
                    claimant.consumer(), command, destination, principal.getName());
            return RealtimeFrameDecision.drop(RealtimeConstant.REASON_CONSUMER_FAILED);
        }
        return allowed
                ? RealtimeFrameDecision.pass()
                : RealtimeFrameDecision.drop(RealtimeConstant.REASON_CONSUMER_DENIED);
    }

    /**
     * One of the two decision methods of an authorizer, subscribe or send.
     */
    @FunctionalInterface
    private interface AuthorizerCheck {
        boolean decide(
                RealtimeDestinationAuthorizer authorizer, RealtimePrincipal principal, String destination);
    }
}
