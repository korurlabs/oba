package com.korur.oba.apps.kazan.chat.service.impl;

// 1. Framework-level
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.service.ChatServiceLogConstant;
import com.korur.oba.apps.kazan.chat.dto.request.ChatReactionRequest;
import com.korur.oba.apps.kazan.chat.dto.response.ChatReactionSummaryResponse;
import com.korur.oba.apps.kazan.chat.entity.ChatMessage;
import com.korur.oba.apps.kazan.chat.event.ChatReactionChangedEvent;
import com.korur.oba.apps.kazan.chat.mapper.ChatMessageMapper;
import com.korur.oba.apps.kazan.chat.repository.ChatMessageRepository;
import com.korur.oba.apps.kazan.chat.repository.ChatReactionRepository;
import com.korur.oba.apps.kazan.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kazan.chat.service.ChatReactionService;
import com.korur.oba.apps.kazan.chat.validator.ChatValidator;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Implementation of chat reaction write operations.
 *
 * A reaction is its own row, outside the message's lifecycle, so reacting
 * never touches the message and never collides with an edit of it.
 */
@Service
@RequiredArgsConstructor
public class ChatReactionServiceImpl implements ChatReactionService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ChatReactionServiceImpl.class);

    private final ChatReactionRepository chatReactionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatValidator chatValidator;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatChannelQueryService chatChannelQueryService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public List<ChatReactionSummaryResponse> addReaction(User user, UUID organizationId, UUID messageId,
                                                         ChatReactionRequest request) {
        ChatMessage message = chatValidator.ensureMessageExists(organizationId, messageId);
        chatChannelQueryService.verifyChannelAccess(organizationId, user.getId(), message.getChannelId());
        String emoji = request.emoji();
        chatValidator.ensureEmojiValid(messageId, emoji);

        if (chatReactionRepository.existsByMessageIdAndUserIdAndEmoji(messageId, user.getId(), emoji)) {
            return loadSummaries(messageId, user);
        }
        chatValidator.ensureReactionLimitNotReached(messageId,
                chatReactionRepository.existsByMessageIdAndEmoji(messageId, emoji),
                chatReactionRepository.countDistinctEmojiByMessageId(messageId));
        // A concurrent add of the same reaction may have inserted the row since
        // the check above; the insert then changes nothing and nothing is
        // published, so the reaction is announced once.
        if (chatReactionRepository.insertIfAbsent(messageId, user.getId(), emoji) == 0) {
            return loadSummaries(messageId, user);
        }

        publishReactionEvent(message, emoji, user, true);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ChatServiceLogConstant.CHAT_REACTION_ADDED,
                messageId, user.getId(), emoji);

        return loadSummaries(messageId, user);
    }

    @Override
    @Transactional
    public List<ChatReactionSummaryResponse> removeReaction(User user, UUID organizationId, UUID messageId,
                                                            String emoji) {
        ChatMessage message = chatValidator.ensureMessageExists(organizationId, messageId);
        chatChannelQueryService.verifyChannelAccess(organizationId, user.getId(), message.getChannelId());

        if (chatReactionRepository.deleteByMessageIdAndUserIdAndEmoji(messageId, user.getId(), emoji) > 0) {
            publishReactionEvent(message, emoji, user, false);
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ChatServiceLogConstant.CHAT_REACTION_REMOVED,
                    messageId, user.getId(), emoji);
        }

        return loadSummaries(messageId, user);
    }

    private List<ChatReactionSummaryResponse> loadSummaries(UUID messageId, User user) {
        return chatMessageRepository.findReactionSummaries(List.of(messageId), user.getId())
                .stream()
                .map(chatMessageMapper::toReactionSummaryResponse)
                .toList();
    }

    private void publishReactionEvent(ChatMessage message, String emoji, User user, boolean added) {
        applicationEventPublisher.publishEvent(new ChatReactionChangedEvent(
                message.getOrganizationId(),
                message.getChannelId(),
                message.getId(),
                message.getThread() == null ? null : message.getThread().getId(),
                emoji,
                user.getId(),
                added));
    }
}
