package com.korur.oba.apps.kaizen.chat.service.impl;

// 1. Framework-level
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.constant.ChatConstant;
import com.korur.oba.apps.kaizen.chat.constant.ChatExceptionLogConstant;
import com.korur.oba.apps.kaizen.chat.constant.service.ChatServiceLogConstant;
import com.korur.oba.apps.kaizen.chat.dto.request.ChatReadStateRequest;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatUnreadResponse;
import com.korur.oba.apps.kaizen.chat.entity.ChatChannel;
import com.korur.oba.apps.kaizen.chat.entity.ChatMessage;
import com.korur.oba.apps.kaizen.chat.entity.ChatThread;
import com.korur.oba.apps.kaizen.chat.event.ChatReadStateChangedEvent;
import com.korur.oba.apps.kaizen.chat.repository.ChatMessageRepository;
import com.korur.oba.apps.kaizen.chat.repository.ChatReadStateRepository;
import com.korur.oba.apps.kaizen.chat.repository.ChatThreadMemberRepository;
import com.korur.oba.apps.kaizen.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kaizen.chat.service.ChatReadStateQueryService;
import com.korur.oba.apps.kaizen.chat.service.ChatReadStateService;
import com.korur.oba.apps.kaizen.chat.validator.ChatValidator;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Implementation of read position writes.
 *
 * The position is written with one native upsert that only moves forward, so
 * two tabs marking read at once never create a second row or move it back.
 * The upsert bypasses the persistence context: no {@link com.korur.oba.apps.kaizen.chat.entity.ChatReadState} entity is
 * loaded in this transaction, and the counts returned afterwards come from a
 * native query, so nothing stale is read back. The request's channel is
 * checked first, and the thread and the message must belong to it.
 */
@Service
@RequiredArgsConstructor
public class ChatReadStateServiceImpl implements ChatReadStateService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ChatReadStateServiceImpl.class);

    private final ChatReadStateRepository chatReadStateRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatThreadMemberRepository chatThreadMemberRepository;
    private final ChatValidator chatValidator;
    private final ChatReadStateQueryService chatReadStateQueryService;
    private final ChatChannelQueryService chatChannelQueryService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public ChatUnreadResponse markRead(User user, UUID organizationId, ChatReadStateRequest request) {
        ChatChannel channel = chatChannelQueryService.verifyChannelAccess(organizationId, user.getId(),
                request.channelId());
        ChatThread thread = request.threadId() == null
                ? null
                : chatValidator.ensureThreadExists(organizationId, request.threadId());
        if (thread != null) {
            chatValidator.ensureThreadInChannel(thread, channel.getId());
        }
        ChatMessage message = findMessageInConversation(organizationId, channel.getId(), thread,
                request.lastReadMessageId());
        // A thread counts as unread only for its members, so a reader who
        // opens a thread they never joined gets no read position for it:
        // storing one would start tracking a thread they do not follow.
        if (thread != null && !chatThreadMemberRepository.existsByThread_IdAndUserId(thread.getId(), user.getId())) {
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE,
                    ChatServiceLogConstant.CHAT_READ_STATE_SKIPPED_NOT_MEMBER, user.getId(), organizationId,
                    thread.getId());
            return chatReadStateQueryService.getUnread(user, organizationId);
        }

        int moved = thread == null
                ? chatReadStateRepository.upsertChannelReadState(user.getId(), organizationId, message.getChannelId(),
                        message.getId(), message.getCreatedAt())
                : chatReadStateRepository.upsertThreadReadState(user.getId(), organizationId, message.getChannelId(),
                        thread.getId(), message.getId(), message.getCreatedAt());
        // A reviewer added to a pull request share by an edit is notified
        // through an entry older than the position, so opening the channel
        // must clear that notification even when no newer message moves it.
        boolean touched = moved == 0 && thread == null
                && chatReadStateRepository.touchChannelReadStateForShareMentions(user.getId(),
                        message.getChannelId()) > 0;
        if (moved > 0 || touched) {
            applicationEventPublisher.publishEvent(
                    new ChatReadStateChangedEvent(organizationId, message.getChannelId(), user.getId(),
                            request.threadId(), message.getId()));
        }
        if (moved > 0) {
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ChatServiceLogConstant.CHAT_READ_STATE_ADVANCED,
                    user.getId(), organizationId, request.threadId(), message.getId());
        }

        return chatReadStateQueryService.getUnread(user, organizationId);
    }

    /**
     * Reads the message the reader has seen up to. A message deleted since
     * the client loaded it still marks its place, so it is read with the
     * deleted rows. It must belong to the conversation being marked: the
     * channel itself for no thread, else that thread of the channel; anything
     * else, a message of another organization or channel included, is
     * reported as not found.
     */
    private ChatMessage findMessageInConversation(UUID organizationId, UUID channelId, ChatThread thread,
                                                  UUID messageId) {
        UUID threadId = thread == null ? null : thread.getId();
        return chatMessageRepository.findByIdWithDeleted(messageId)
                .filter(message -> organizationId.equals(message.getOrganizationId()))
                .filter(message -> channelId.equals(message.getChannelId()))
                .filter(message -> Objects.equals(threadId,
                        message.getThread() == null ? null : message.getThread().getId()))
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, ChatExceptionLogConstant.CHAT_MESSAGE_NOT_FOUND,
                            messageId, organizationId);
                    return new NotFoundException(ExceptionDomainKaizenError.NOT_CHAT_MESSAGE_FOUND,
                            Map.of(ChatConstant.FIELD_MESSAGE_ID, messageId));
                });
    }
}
