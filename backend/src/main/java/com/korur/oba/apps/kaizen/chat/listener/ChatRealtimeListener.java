package com.korur.oba.apps.kaizen.chat.listener;

// 1. Framework-level
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.constant.ChatConstant;
import com.korur.oba.apps.kaizen.chat.constant.ChatExceptionLogConstant;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatMessageResponse;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatUnreadResponse;
import com.korur.oba.apps.kaizen.chat.dto.signal.ChatChannelSignal;
import com.korur.oba.apps.kaizen.chat.dto.signal.ChatMessageSignal;
import com.korur.oba.apps.kaizen.chat.dto.signal.ChatPinSignal;
import com.korur.oba.apps.kaizen.chat.dto.signal.ChatPresenceSignal;
import com.korur.oba.apps.kaizen.chat.dto.signal.ChatReactionSignal;
import com.korur.oba.apps.kaizen.chat.dto.signal.ChatThreadMembershipSignal;
import com.korur.oba.apps.kaizen.chat.dto.signal.ChatThreadSignal;
import com.korur.oba.apps.kaizen.chat.dto.signal.ChatUnreadSignal;
import com.korur.oba.apps.kaizen.chat.entity.ChatChannel;
import com.korur.oba.apps.kaizen.chat.enums.ChatChannelVisibility;
import com.korur.oba.apps.kaizen.chat.event.ChatChannelChangedEvent;
import com.korur.oba.apps.kaizen.chat.event.ChatMessageChangedEvent;
import com.korur.oba.apps.kaizen.chat.event.ChatNotificationsChangedEvent;
import com.korur.oba.apps.kaizen.chat.event.ChatReactionChangedEvent;
import com.korur.oba.apps.kaizen.chat.event.ChatReadStateChangedEvent;
import com.korur.oba.apps.kaizen.chat.event.ChatThreadChangedEvent;
import com.korur.oba.apps.kaizen.chat.repository.ChatChannelMemberRepository;
import com.korur.oba.apps.kaizen.chat.repository.ChatChannelRepository;
import com.korur.oba.apps.kaizen.chat.repository.ChatThreadMemberRepository;
import com.korur.oba.apps.kaizen.chat.service.ChatMessageQueryService;
import com.korur.oba.apps.kaizen.chat.service.ChatReadStateQueryService;
import com.korur.oba.connect.realtime.constant.RealtimeConstant;
import com.korur.oba.connect.realtime.dto.RealtimeSignal;
import com.korur.oba.connect.realtime.event.RealtimePresenceChangedEvent;
import com.korur.oba.connect.realtime.service.RealtimeSessionRegistry;
import com.korur.oba.connect.realtime.service.RealtimeSignalBus;
import com.korur.oba.connect.realtime.util.RealtimeDestinationUtil;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

/**
 * Turns committed chat changes into realtime signals.
 *
 * Messages, pins, reactions and threads go to the topic of their channel,
 * which only the channel's readers may subscribe to. A new or edited message
 * carries its reader-neutral copy, which every subscriber may read; every
 * other signal holds IDs and flags only, and each client reads the message or
 * thread for itself. Presence and the changes of public
 * channels go to the organization topic. Per-user state goes to the user's
 * own queue: the unread counts of the readers a new or deleted message or a
 * changed mention concerns, thread membership changes, and the changes of
 * private channels, which only their members and the organization's owners
 * and admins may know of.
 *
 * Every handler runs after the commit, so a rolled-back write signals nothing,
 * and on the realtime executor, so neither the request thread nor its
 * database connection waits for the fan-out. Unread counts are pushed only to
 * readers of the channel who are online in the organization, with one query
 * for all of them.
 *
 * A failure here is logged at error and not re-thrown: the change has
 * committed and the request succeeded, so there is nothing to roll back. One
 * failing recipient does not stop the others, and every client re-reads its
 * counts and newest messages on each (re)connect and through REST, which is
 * the recovery path for a lost signal.
 */
@Component
@RequiredArgsConstructor
public class ChatRealtimeListener {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ChatRealtimeListener.class);

    private final RealtimeSignalBus realtimeSignalBus;
    private final RealtimeSessionRegistry realtimeSessionRegistry;
    private final ChatReadStateQueryService chatReadStateQueryService;
    private final ChatMessageQueryService chatMessageQueryService;
    private final ChatThreadMemberRepository chatThreadMemberRepository;
    private final ChatChannelRepository chatChannelRepository;
    private final ChatChannelMemberRepository chatChannelMemberRepository;
    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final LoggingService loggingService;

    /**
     * Signals a created, edited, deleted, pinned or unpinned message to the
     * readers of its channel. A created or deleted message also changes the
     * unread counts of the readers it concerns: every online reader for a
     * channel message, the thread's online members who still read the channel
     * for a thread message. A new
     * message leaves its author's counts alone; a deleted one does not, since
     * replies to it stop notifying its author.
     *
     * A created or edited message goes out with its reader-neutral copy when
     * one can be built, so open clients need not read it again, and always
     * with its version while it exists, so a client that must read it itself
     * can still tell whether the copy it holds is current.
     *
     * @param event the committed change
     */
    @Async(RealtimeConstant.EXECUTOR_BEAN_NAME)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageChanged(ChatMessageChangedEvent event) {
        RealtimeSignal signal = switch (event.type()) {
            case CREATED -> messageSignal(ChatConstant.SIGNAL_MESSAGE_CREATED, event);
            case UPDATED -> messageSignal(ChatConstant.SIGNAL_MESSAGE_UPDATED, event);
            case DELETED -> ChatMessageSignal.of(ChatConstant.SIGNAL_MESSAGE_DELETED, event);
            case PINNED -> ChatPinSignal.of(event, true);
            case UNPINNED -> ChatPinSignal.of(event, false);
        };
        publishToChannel(event.organizationId(), event.channelId(), signal, event.type().name(), event.messageId());

        if (event.type() == ChatMessageChangedEvent.Type.CREATED) {
            pushUnread(event.organizationId(), event.channelId(), event.threadId(), event.authorId(),
                    event.messageId());
        } else if (event.type() == ChatMessageChangedEvent.Type.DELETED) {
            pushUnread(event.organizationId(), event.channelId(), event.threadId(), null, event.messageId());
        }
    }

    /**
     * Pushes fresh unread counts after an edit changed whom a message
     * notifies: to every reader the conversation reaches, but the author,
     * when the everyone mention was added or removed, else to the users who
     * gained or lost a mention. Mentions name readers of the channel only, and
     * a user's counts hold only the channels the user reads, so those users
     * need no channel check here.
     *
     * @param event the committed change
     */
    @Async(RealtimeConstant.EXECUTOR_BEAN_NAME)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationsChanged(ChatNotificationsChangedEvent event) {
        if (event.broadcast()) {
            pushUnread(event.organizationId(), event.channelId(), event.threadId(), event.authorId(),
                    event.messageId());
        } else {
            pushUnreadTo(event.organizationId(), event.userIds(), event.threadId(), event.messageId());
        }
    }

    /**
     * Signals a created or changed thread to the readers of its channel, and a joined or
     * left thread to the users concerned. A changed auto-archive window, a
     * status change and a reply that revives an archived thread can each add
     * the thread to or drop it from the unread counts, which hold active
     * threads only, so its online members get fresh unread counts on every
     * update; so does a user who left, whose count loses the thread.
     *
     * @param event the committed change
     */
    @Async(RealtimeConstant.EXECUTOR_BEAN_NAME)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onThreadChanged(ChatThreadChangedEvent event) {
        switch (event.type()) {
            case CREATED -> publishToChannel(event.organizationId(), event.channelId(),
                    ChatThreadSignal.of(ChatConstant.SIGNAL_THREAD_CREATED, event), event.type().name(),
                    event.threadId());
            case UPDATED -> {
                publishToChannel(event.organizationId(), event.channelId(),
                        ChatThreadSignal.of(ChatConstant.SIGNAL_THREAD_UPDATED, event), event.type().name(),
                        event.threadId());
                pushUnread(event.organizationId(), event.channelId(), event.threadId(), null, event.threadId());
            }
            case MEMBERS_JOINED -> publishMembership(event, true);
            case MEMBER_LEFT -> {
                publishMembership(event, false);
                pushUnreadTo(event.organizationId(), event.userIds(), event.threadId(), event.threadId());
            }
        }
    }

    /**
     * Signals an added or removed reaction to the readers of the message's
     * channel.
     *
     * @param event the committed change
     */
    @Async(RealtimeConstant.EXECUTOR_BEAN_NAME)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReactionChanged(ChatReactionChangedEvent event) {
        publishToChannel(event.organizationId(), event.channelId(), ChatReactionSignal.of(event),
                ChatConstant.SIGNAL_REACTION_CHANGED, event.messageId());
    }

    /**
     * Signals a created, changed or deleted channel, or a changed member list,
     * so the clients that list the channel reload their channel list.
     *
     * A public channel's change goes to the organization topic. So does every
     * visibility change: until it, or from it on, every member lists the
     * channel, and a member who no longer may see it must drop it. Any other
     * change of a private channel goes to the user queues of its members, of
     * the users just added or removed, and of the online owners and admins,
     * who list every private channel.
     *
     * A removal, a switch to private and a deletion can end a user's right to
     * read the channel while subscribed to its topic, so the open
     * subscriptions of the users concerned are checked again: the removed
     * users, or for the other two every user online in the organization. A
     * switch to public ends nobody's right and checks nothing.
     *
     * @param event the committed change
     */
    @Async(RealtimeConstant.EXECUTOR_BEAN_NAME)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChannelChanged(ChatChannelChangedEvent event) {
        revalidate(event);

        ChatChannelSignal signal = ChatChannelSignal.of(event);
        if (event.visibility() == ChatChannelVisibility.PUBLIC
                || event.type() == ChatChannelChangedEvent.Type.VISIBILITY_CHANGED) {
            publishToTopic(organizationChatTopic(event.organizationId()), event.organizationId(), signal,
                    ChatConstant.SIGNAL_CHANNEL_CHANGED, event.channelId());
            return;
        }
        Set<UUID> recipients;
        try {
            recipients = privateChannelAudience(event);
        } catch (RuntimeException failure) {
            loggingService.error(LOG_PREFIX, LogLayer.HANDLER,
                    ChatExceptionLogConstant.CHAT_CHANNEL_SIGNAL_PUBLISH_FAILED,
                    failure, event.type(), event.organizationId(), event.channelId());
            return;
        }
        for (UUID userId : recipients) {
            publishToUser(userId, event.organizationId(), signal, ChatConstant.SIGNAL_CHANNEL_CHANGED,
                    event.channelId());
        }
    }

    /**
     * Sends the reader's fresh unread counts to all of the reader's sessions
     * in the organization, so reading on one device clears the badge on the
     * others.
     *
     * @param event the committed read position
     */
    @Async(RealtimeConstant.EXECUTOR_BEAN_NAME)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReadStateChanged(ChatReadStateChangedEvent event) {
        pushUnreadTo(event.organizationId(), List.of(event.userId()), event.threadId(), event.lastReadMessageId());
    }

    /**
     * Publishes a member coming online or going offline in an organization as
     * a PRESENCE signal on that organization's chat topic, where the member
     * list and the mention suggestions read it.
     *
     * Unlike the handlers above it runs synchronously in the session
     * registry's callback, so the online and offline signals of one member
     * keep their order. A failure is not caught here: the registry logs it at
     * error and its presence state stays correct; clients read presence again
     * over REST on their next connect.
     *
     * @param event the first session opened or the last one closed
     */
    @EventListener
    public void onPresenceChanged(RealtimePresenceChangedEvent event) {
        realtimeSignalBus.publishToTopic(organizationChatTopic(event.organizationId()),
                ChatPresenceSignal.of(event.userId(), event.online()));
    }

    // -------------Fan-out-----------//

    /**
     * Builds the signal of a new or edited message. The version comes with
     * the neutral copy when there is one; without it, under the fallback rule
     * or after a failed build, only the version is read.
     */
    private ChatMessageSignal messageSignal(String type, ChatMessageChangedEvent event) {
        ChatMessageResponse message = neutralMessage(type, event);
        Instant version = message != null ? message.version() : messageVersion(type, event);
        return ChatMessageSignal.of(type, event, version, message);
    }

    /**
     * Reads the version of a message sent without its neutral copy; null when
     * it is gone or the read fails. A failure is logged and the signal still
     * goes out without the version, as without the copy.
     */
    private Instant messageVersion(String type, ChatMessageChangedEvent event) {
        try {
            return chatMessageQueryService.findMessageVersion(event.organizationId(), event.messageId())
                    .orElse(null);
        } catch (RuntimeException failure) {
            loggingService.error(LOG_PREFIX, LogLayer.HANDLER, ChatExceptionLogConstant.CHAT_SIGNAL_PAYLOAD_FAILED,
                    failure, type, event.organizationId(), event.messageId());
            return null;
        }
    }

    /**
     * Builds the reader-neutral copy of a new or edited message; null when
     * its copy would differ per reader, when it is gone, or when the build
     * fails. A failure is logged and the signal still goes out without the
     * copy, so clients read the message themselves instead of missing it; it
     * is not retried, since a retry would hold a fan-out thread that other
     * channels' signals wait for.
     */
    private ChatMessageResponse neutralMessage(String type, ChatMessageChangedEvent event) {
        try {
            return chatMessageQueryService.findNeutralMessage(event.organizationId(), event.messageId(),
                    event.clientNonce()).orElse(null);
        } catch (RuntimeException failure) {
            loggingService.error(LOG_PREFIX, LogLayer.HANDLER, ChatExceptionLogConstant.CHAT_SIGNAL_PAYLOAD_FAILED,
                    failure, type, event.organizationId(), event.messageId());
            return null;
        }
    }

    /**
     * Pushes unread counts to the online readers a conversation concerns:
     * every online reader of the channel for the channel itself (threadId
     * null), the online members of the thread who still read the channel
     * otherwise, except the excluded user.
     */
    private void pushUnread(UUID organizationId, UUID channelId, UUID threadId, UUID excludedUserId,
                            UUID sourceId) {
        Set<UUID> audience;
        try {
            audience = onlineReaders(organizationId, channelId);
            if (threadId != null) {
                Set<UUID> threadReaders =
                        new LinkedHashSet<>(chatThreadMemberRepository.findUserIdsByThreadId(threadId));
                threadReaders.retainAll(audience);
                audience = threadReaders;
            }
        } catch (RuntimeException failure) {
            loggingService.error(LOG_PREFIX, LogLayer.HANDLER, ChatExceptionLogConstant.CHAT_UNREAD_FANOUT_FAILED,
                    failure, organizationId, threadId, sourceId);
            return;
        }
        Set<UUID> recipients = new LinkedHashSet<>(audience);
        if (excludedUserId != null) {
            recipients.remove(excludedUserId);
        }
        pushUnreadTo(organizationId, recipients, threadId, sourceId);
    }

    /**
     * Pushes unread counts to those of the given users who are online in the
     * organization, reading all their counts in one query.
     */
    private void pushUnreadTo(UUID organizationId, Collection<UUID> userIds, UUID threadId, UUID sourceId) {
        Map<UUID, ChatUnreadResponse> unreadByUser;
        try {
            Set<UUID> online = realtimeSessionRegistry.onlineUserIds(organizationId);
            Set<UUID> recipients = new LinkedHashSet<>(userIds);
            recipients.retainAll(online);
            if (recipients.isEmpty()) {
                return;
            }
            unreadByUser = chatReadStateQueryService.getUnreadByUsers(organizationId, recipients);
        } catch (RuntimeException failure) {
            loggingService.error(LOG_PREFIX, LogLayer.HANDLER, ChatExceptionLogConstant.CHAT_UNREAD_FANOUT_FAILED,
                    failure, organizationId, threadId, sourceId);
            return;
        }
        unreadByUser.forEach((userId, unread) -> publishToUser(userId, organizationId, ChatUnreadSignal.of(unread),
                ChatConstant.SIGNAL_UNREAD_CHANGED, sourceId));
    }

    /**
     * The online users who can read a channel: everyone online for a public
     * channel, the online members of a private one, nobody for a channel that
     * is gone.
     */
    private Set<UUID> onlineReaders(UUID organizationId, UUID channelId) {
        Optional<ChatChannel> channel = chatChannelRepository.findByIdAndOrganizationId(channelId, organizationId);
        if (channel.isEmpty()) {
            return Set.of();
        }
        Set<UUID> online = realtimeSessionRegistry.onlineUserIds(organizationId);
        if (channel.get().getVisibility() == ChatChannelVisibility.PUBLIC) {
            return online;
        }
        Set<UUID> readers = new LinkedHashSet<>(chatChannelMemberRepository.findUserIdsByChannelId(channelId));
        readers.retainAll(online);
        return readers;
    }

    /**
     * The users told of a private channel's change: its members, the users
     * the change added or removed, and the online owners and admins.
     */
    private Set<UUID> privateChannelAudience(ChatChannelChangedEvent event) {
        Set<UUID> recipients =
                new LinkedHashSet<>(chatChannelMemberRepository.findUserIdsByChannelId(event.channelId()));
        recipients.addAll(event.affectedUserIds());
        Set<UUID> managers = new LinkedHashSet<>(organizationMemberQueryService.findActiveUserIdsByRoles(
                event.organizationId(), Set.of(OrganizationMemberRole.OWNER, OrganizationMemberRole.ADMIN)));
        managers.retainAll(realtimeSessionRegistry.onlineUserIds(event.organizationId()));
        recipients.addAll(managers);
        return recipients;
    }

    /**
     * Checks again the open subscriptions of the users a change may have cut
     * off from the channel, so a removed reader stops receiving its topic.
     */
    private void revalidate(ChatChannelChangedEvent event) {
        Collection<UUID> users = null;
        try {
            switch (event.type()) {
                case MEMBERS_REMOVED -> users = event.affectedUserIds();
                case DELETED -> users = realtimeSessionRegistry.onlineUserIds(event.organizationId());
                case VISIBILITY_CHANGED -> {
                    if (event.visibility() == ChatChannelVisibility.PRIVATE) {
                        users = realtimeSessionRegistry.onlineUserIds(event.organizationId());
                    }
                }
                default -> {
                    // Creations, renames and additions end nobody's right to read.
                }
            }
            if (users != null && !users.isEmpty()) {
                realtimeSessionRegistry.revalidateSubscriptions(users);
            }
        } catch (RuntimeException failure) {
            loggingService.error(LOG_PREFIX, LogLayer.HANDLER,
                    ChatExceptionLogConstant.CHAT_CHANNEL_REVALIDATION_FAILED,
                    failure, event.organizationId(), event.channelId(), users);
        }
    }

    private void publishMembership(ChatThreadChangedEvent event, boolean member) {
        for (UUID userId : event.userIds()) {
            publishToUser(userId, event.organizationId(), ChatThreadMembershipSignal.of(event.threadId(), member),
                    ChatConstant.SIGNAL_THREAD_MEMBERSHIP_CHANGED, event.threadId());
        }
    }

    private static String organizationChatTopic(UUID organizationId) {
        return RealtimeDestinationUtil.organizationTopic(organizationId, ChatConstant.CHAT_TOPIC_SUFFIX);
    }

    private void publishToChannel(UUID organizationId, UUID channelId, RealtimeSignal signal, String type,
                                  UUID sourceId) {
        publishToTopic(RealtimeDestinationUtil.organizationTopic(organizationId,
                ChatConstant.CHAT_CHANNEL_TOPIC_SUFFIX_PREFIX + channelId), organizationId, signal, type, sourceId);
    }

    private void publishToTopic(String topic, UUID organizationId, RealtimeSignal signal, String type,
                                UUID sourceId) {
        try {
            realtimeSignalBus.publishToTopic(topic, signal);
        } catch (RuntimeException failure) {
            loggingService.error(LOG_PREFIX, LogLayer.HANDLER, ChatExceptionLogConstant.CHAT_SIGNAL_PUBLISH_FAILED,
                    failure, type, organizationId, sourceId);
        }
    }

    private void publishToUser(UUID userId, UUID organizationId, RealtimeSignal signal, String type, UUID sourceId) {
        try {
            realtimeSignalBus.publishToUser(userId, organizationId, ChatConstant.CHAT_USER_QUEUE, signal);
        } catch (RuntimeException failure) {
            loggingService.error(LOG_PREFIX, LogLayer.HANDLER,
                    ChatExceptionLogConstant.CHAT_USER_SIGNAL_PUBLISH_FAILED, failure, type, userId, organizationId,
                    sourceId);
        }
    }
}
