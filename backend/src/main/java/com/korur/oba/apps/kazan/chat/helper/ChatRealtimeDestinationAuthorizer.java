package com.korur.oba.apps.kazan.chat.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.ChatConstant;
import com.korur.oba.apps.kazan.chat.service.ChatChannelQueryService;
import com.korur.oba.connect.realtime.dto.RealtimePrincipal;
import com.korur.oba.connect.realtime.service.RealtimeDestinationAuthorizer;
import com.korur.oba.connect.realtime.util.RealtimeDestinationUtil;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessQueryService;

/**
 * The realtime destinations of chat and who may use them.
 *
 * Chat claims the organization topic "/topic/organizations/{organizationId}/chat",
 * every channel topic "/topic/organizations/{organizationId}/chat/channels/{channelId}",
 * the user queue "/user/queue/chat" and the typing destination "/app/chat/typing".
 *
 * Every chat destination, subscribed to or sent to, first needs Kazan in the
 * session's organization. The realtime CONNECT checks only the membership, not
 * the app, so without this check a member whose Kazan access was never granted
 * or was revoked would still receive the organization's chat.
 *
 * The organization topic carries presence and the changes of public channels,
 * which every member of the organization with Kazan, GUEST included, may see;
 * the realtime CONNECT already required that membership, and the registry has
 * checked that the topic names the session's own organization. The user queue
 * only ever reaches the session's own user. Both need no check beyond the app.
 *
 * A channel topic carries the messages of one channel, so it is allowed only
 * to a user who can read that channel: any member for a public channel, the
 * member list for a private one. The registry asks again, through the same
 * check, when a user loses that right while subscribed.
 */
@Component
@RequiredArgsConstructor
public class ChatRealtimeDestinationAuthorizer implements RealtimeDestinationAuthorizer {

    private final ChatChannelQueryService chatChannelQueryService;
    private final AppAccessQueryService appAccessQueryService;

    @Override
    public String consumer() {
        return ChatConstant.CHAT_REALTIME_CONSUMER;
    }

    @Override
    public boolean supportsSubscribe(String destination) {
        return ChatConstant.CHAT_USER_QUEUE_SUBSCRIPTION.equals(destination)
                || RealtimeDestinationUtil.parseOrganizationTopic(destination, ChatConstant.CHAT_TOPIC_SUFFIX).isPresent()
                || channelTopicRemainder(destination).isPresent();
    }

    /**
     * Refuses every chat destination when the session's user has no Kazan in
     * the session's organization. Otherwise allows the organization topic and
     * the user queue, and a channel topic when its channel ID is canonical and
     * the user can read the channel. A channel topic of another organization
     * than the session's is refused here too, so the check does not rest on the
     * registry alone.
     */
    @Override
    public boolean authorizeSubscribe(RealtimePrincipal principal, String destination) {
        if (!hasKazan(principal)) {
            return false;
        }
        Optional<String> channelRemainder = channelTopicRemainder(destination);
        if (channelRemainder.isEmpty()) {
            return true;
        }
        Optional<UUID> organizationId = RealtimeDestinationUtil.organizationOf(destination);
        Optional<UUID> channelId = parseCanonical(channelRemainder.get());
        if (organizationId.isEmpty() || channelId.isEmpty()
                || !organizationId.get().equals(principal.organizationId())) {
            return false;
        }
        return chatChannelQueryService.canRead(organizationId.get(), principal.userId(), channelId.get());
    }

    @Override
    public boolean supportsSend(String destination) {
        return ChatConstant.CHAT_TYPING_DESTINATION.equals(destination);
    }

    /**
     * Allows the typing destination only to a user with Kazan in the
     * session's organization; the organization role and the channel rules are
     * checked by the typing receiver.
     */
    @Override
    public boolean authorizeSend(RealtimePrincipal principal, String destination) {
        return hasKazan(principal);
    }

    /**
     * Whether the session's user may open Kazan in the session's organization.
     * Reads the grants on each call, so a revoke takes effect on the next frame.
     */
    private boolean hasKazan(RealtimePrincipal principal) {
        return appAccessQueryService.hasAppAccess(principal.organizationId(), principal.userId(), App.KAZAN);
    }

    /**
     * The part after "/chat/channels/" of a channel topic, whatever its form,
     * so chat claims and then refuses a malformed channel ID instead of
     * leaving the destination unclaimed.
     */
    private static Optional<String> channelTopicRemainder(String destination) {
        Optional<UUID> organizationId = RealtimeDestinationUtil.organizationOf(destination);
        if (organizationId.isEmpty()) {
            return Optional.empty();
        }
        String prefix = RealtimeDestinationUtil.organizationTopic(organizationId.get(),
                ChatConstant.CHAT_CHANNEL_TOPIC_SUFFIX_PREFIX);
        return destination.startsWith(prefix) ? Optional.of(destination.substring(prefix.length())) : Optional.empty();
    }

    /**
     * Reads a channel ID only in the form {@link UUID#toString()} writes, the form the
     * listener publishes to; another spelling of the same channel would be a
     * broker topic that never receives a signal.
     */
    private static Optional<UUID> parseCanonical(String raw) {
        try {
            UUID parsed = UUID.fromString(raw);
            return parsed.toString().equals(raw) ? Optional.of(parsed) : Optional.empty();
        } catch (IllegalArgumentException malformed) {
            // rule-10-no-op -- reason: a malformed channel ID is an expected client input; the empty
            // result refuses the SUBSCRIBE, and the registry logs the refusal.
            return Optional.empty();
        }
    }
}
