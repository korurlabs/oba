package com.korur.oba.apps.kazan.chat.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.dto.request.ChatReactionRequest;
import com.korur.oba.apps.kazan.chat.dto.response.ChatReactionSummaryResponse;
import com.korur.oba.apps.kazan.chat.entity.ChatMessage;
import com.korur.oba.apps.kazan.chat.entity.ChatThread;
import com.korur.oba.apps.kazan.chat.event.ChatReactionChangedEvent;
import com.korur.oba.apps.kazan.chat.mapper.ChatMessageMapperImpl;
import com.korur.oba.apps.kazan.chat.mock_data.ChatMocks;
import com.korur.oba.apps.kazan.chat.repository.ChatMessageRepository;
import com.korur.oba.apps.kazan.chat.repository.ChatReactionRepository;
import com.korur.oba.apps.kazan.chat.repository.ChatThreadRepository;
import com.korur.oba.apps.kazan.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kazan.chat.validator.ChatValidator;
import com.korur.oba.apps.kazan.dailyupdate.service.DailyUpdateQueryService;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.ticket.mapper.TicketEmbeddedMapperImpl;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class ChatReactionServiceImplUnitTest {

    private static final String THUMBS_UP = "👍";

    @Mock
    private ChatReactionRepository chatReactionRepository;
    @Mock
    private AppAccessValidator appAccessValidator;
    @Mock
    private DailyUpdateQueryService dailyUpdateQueryService;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatThreadRepository chatThreadRepository;
    @Mock
    private ChatChannelQueryService chatChannelQueryService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private ChatReactionServiceImpl service;

    private final UUID organizationId = UUID.randomUUID();
    private final User user = ChatMocks.user("Ada", "Lovelace");
    private ChatMessage message;

    @BeforeEach
    void setUp() {
        LoggingService loggingService = new LoggingService();
        ChatValidator validator = new ChatValidator(chatMessageRepository, chatThreadRepository,
                dailyUpdateQueryService, null, appAccessValidator, loggingService);
        service = new ChatReactionServiceImpl(chatReactionRepository, chatMessageRepository, validator,
                new ChatMessageMapperImpl(new TicketEmbeddedMapperImpl()), chatChannelQueryService,
                applicationEventPublisher, loggingService);

        message = ChatMocks.message(ChatMocks.user("Alan", "Turing"), organizationId, "Release is out");
        lenient().when(chatMessageRepository.findByIdAndOrganizationId(message.getId(), organizationId))
                .thenReturn(Optional.of(message));
        lenient().when(chatMessageRepository.findReactionSummaries(List.of(message.getId()), user.getId()))
                .thenReturn(List.of(ChatMocks.reaction(message.getId(), THUMBS_UP, user.getId())));
    }

    private ChatReactionChangedEvent publishedEvent() {
        ArgumentCaptor<ChatReactionChangedEvent> captor = ArgumentCaptor.forClass(ChatReactionChangedEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    @Nested
    class AddReaction {

        @Test
        void newReaction_isSavedPublishedAndSummarised() {
            // Arrange
            ChatThread thread = ChatMocks.thread(organizationId, UUID.randomUUID(), user.getId());
            message.setThread(thread);
            when(chatReactionRepository.countDistinctEmojiByMessageId(message.getId())).thenReturn(3L);
            when(chatReactionRepository.insertIfAbsent(message.getId(), user.getId(), THUMBS_UP)).thenReturn(1);

            // Act
            List<ChatReactionSummaryResponse> summaries = service.addReaction(user, organizationId,
                    message.getId(), new ChatReactionRequest(THUMBS_UP));

            // Assert
            verify(chatReactionRepository).insertIfAbsent(message.getId(), user.getId(), THUMBS_UP);
            assertThat(summaries).extracting(ChatReactionSummaryResponse::emoji).containsExactly(THUMBS_UP);
            ChatReactionChangedEvent event = publishedEvent();
            assertThat(event.added()).isTrue();
            assertThat(event.threadId()).isEqualTo(thread.getId());
            assertThat(event.organizationId()).isEqualTo(organizationId);
        }

        @Test
        void existingReaction_returnsSummaryWithoutChange() {
            // Arrange
            when(chatReactionRepository.existsByMessageIdAndUserIdAndEmoji(message.getId(), user.getId(), THUMBS_UP))
                    .thenReturn(true);

            // Act
            List<ChatReactionSummaryResponse> summaries = service.addReaction(user, organizationId,
                    message.getId(), new ChatReactionRequest(THUMBS_UP));

            // Assert
            assertThat(summaries).hasSize(1);
            verify(chatReactionRepository, never()).insertIfAbsent(any(), any(), anyString());
            verify(applicationEventPublisher, never()).publishEvent(any());
        }

        @Test
        void reactionAddedConcurrently_returnsSummaryWithoutEvent() {
            // Arrange
            when(chatReactionRepository.insertIfAbsent(message.getId(), user.getId(), THUMBS_UP)).thenReturn(0);

            // Act
            List<ChatReactionSummaryResponse> summaries = service.addReaction(user, organizationId,
                    message.getId(), new ChatReactionRequest(THUMBS_UP));

            // Assert
            assertThat(summaries).extracting(ChatReactionSummaryResponse::emoji).containsExactly(THUMBS_UP);
            verify(applicationEventPublisher, never()).publishEvent(any());
        }

        @Test
        void twentyFirstDistinctEmoji_isRefused() {
            // Arrange
            when(chatReactionRepository.countDistinctEmojiByMessageId(message.getId())).thenReturn(20L);

            // Act and Assert
            assertThatThrownBy(() -> service.addReaction(user, organizationId, message.getId(),
                    new ChatReactionRequest(THUMBS_UP)))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_CHAT_REACTION_INVALID.getCode());
            verify(chatReactionRepository, never()).insertIfAbsent(any(), any(), anyString());
        }

        @Test
        void emojiAlreadyOnFullMessage_fits() {
            // Arrange
            when(chatReactionRepository.existsByMessageIdAndEmoji(message.getId(), THUMBS_UP)).thenReturn(true);
            when(chatReactionRepository.countDistinctEmojiByMessageId(message.getId())).thenReturn(20L);

            // Act
            service.addReaction(user, organizationId, message.getId(), new ChatReactionRequest(THUMBS_UP));

            // Assert
            verify(chatReactionRepository).insertIfAbsent(message.getId(), user.getId(), THUMBS_UP);
        }

        @Test
        void twoGraphemes_areRefused() {
            // Act and Assert
            assertThatThrownBy(() -> service.addReaction(user, organizationId, message.getId(),
                    new ChatReactionRequest(THUMBS_UP + THUMBS_UP)))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_CHAT_REACTION_INVALID.getCode());
            verify(chatReactionRepository, never()).insertIfAbsent(any(), any(), anyString());
        }

        @Test
        void messageOfAnotherOrganization_isNotFound() {
            // Arrange
            UUID messageId = UUID.randomUUID();
            when(chatMessageRepository.findByIdAndOrganizationId(messageId, organizationId))
                    .thenReturn(Optional.empty());

            // Act and Assert
            assertThatThrownBy(() -> service.addReaction(user, organizationId, messageId,
                    new ChatReactionRequest(THUMBS_UP)))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void unreadableChannel_isRefusedBeforeAnyInsert() {
            // Arrange
            when(chatChannelQueryService.verifyChannelAccess(organizationId, user.getId(), message.getChannelId()))
                    .thenThrow(new NotFoundException(ExceptionDomainKazanError.NOT_CHAT_CHANNEL_FOUND));

            // Act and Assert
            assertThatThrownBy(() -> service.addReaction(user, organizationId, message.getId(),
                    new ChatReactionRequest(THUMBS_UP)))
                    .isInstanceOf(NotFoundException.class);
            verify(chatReactionRepository, never()).insertIfAbsent(any(), any(), anyString());
        }
    }

    @Nested
    class RemoveReaction {

        @Test
        void existingReaction_isRemovedAndPublished() {
            // Arrange
            when(chatReactionRepository.deleteByMessageIdAndUserIdAndEmoji(message.getId(), user.getId(), THUMBS_UP))
                    .thenReturn(1);

            // Act
            service.removeReaction(user, organizationId, message.getId(), THUMBS_UP);

            // Assert
            ChatReactionChangedEvent event = publishedEvent();
            assertThat(event.added()).isFalse();
            assertThat(event.emoji()).isEqualTo(THUMBS_UP);
            assertThat(event.threadId()).isNull();
        }

        @Test
        void absentReaction_changesNothing() {
            // Arrange
            when(chatReactionRepository.deleteByMessageIdAndUserIdAndEmoji(any(), any(), anyString())).thenReturn(0);

            // Act
            List<ChatReactionSummaryResponse> summaries = service.removeReaction(user, organizationId,
                    message.getId(), THUMBS_UP);

            // Assert
            assertThat(summaries).hasSize(1);
            verify(applicationEventPublisher, never()).publishEvent(any());
        }
    }
}
