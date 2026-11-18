package com.korur.oba.apps.kaizen.chat.service.impl;

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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.dto.request.ChatReadStateRequest;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatUnreadResponse;
import com.korur.oba.apps.kaizen.chat.entity.ChatChannel;
import com.korur.oba.apps.kaizen.chat.entity.ChatMessage;
import com.korur.oba.apps.kaizen.chat.entity.ChatThread;
import com.korur.oba.apps.kaizen.chat.event.ChatReadStateChangedEvent;
import com.korur.oba.apps.kaizen.chat.mock_data.ChatMocks;
import com.korur.oba.apps.kaizen.chat.repository.ChatMessageRepository;
import com.korur.oba.apps.kaizen.chat.repository.ChatReadStateRepository;
import com.korur.oba.apps.kaizen.chat.repository.ChatThreadMemberRepository;
import com.korur.oba.apps.kaizen.chat.repository.ChatThreadRepository;
import com.korur.oba.apps.kaizen.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kaizen.chat.service.ChatReadStateQueryService;
import com.korur.oba.apps.kaizen.chat.validator.ChatValidator;
import com.korur.oba.apps.kaizen.dailyupdate.service.DailyUpdateQueryService;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class ChatReadStateServiceImplUnitTest {

    @Mock
    private ChatReadStateRepository chatReadStateRepository;
    @Mock
    private AppAccessValidator appAccessValidator;
    @Mock
    private DailyUpdateQueryService dailyUpdateQueryService;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatThreadRepository chatThreadRepository;
    @Mock
    private ChatThreadMemberRepository chatThreadMemberRepository;
    @Mock
    private ChatReadStateQueryService chatReadStateQueryService;
    @Mock
    private ChatChannelQueryService chatChannelQueryService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private ChatReadStateServiceImpl service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID channelId = UUID.randomUUID();
    private final User user = ChatMocks.user("Ada", "Lovelace");
    private final User author = ChatMocks.user("Alan", "Turing");
    private final ChatUnreadResponse unread = new ChatUnreadResponse(List.of(), List.of(), 0, 0);

    @BeforeEach
    void setUp() {
        LoggingService loggingService = new LoggingService();
        ChatValidator validator = new ChatValidator(chatMessageRepository, chatThreadRepository,
                dailyUpdateQueryService, null, appAccessValidator, loggingService);
        service = new ChatReadStateServiceImpl(chatReadStateRepository, chatMessageRepository,
                chatThreadMemberRepository, validator,
                chatReadStateQueryService, chatChannelQueryService, applicationEventPublisher, loggingService);
        lenient().when(chatReadStateQueryService.getUnread(user, organizationId)).thenReturn(unread);
        ChatChannel channel = ChatChannel.builder().name("General").organizationId(organizationId).build();
        channel.setId(channelId);
        lenient().when(chatChannelQueryService.verifyChannelAccess(organizationId, user.getId(), channelId))
                .thenReturn(channel);
    }

    private ChatMessage storedMessage(ChatThread thread) {
        ChatMessage message = ChatMocks.message(author, organizationId, "Hello");
        message.setChannelId(channelId);
        message.setThread(thread);
        lenient().when(chatMessageRepository.findByIdWithDeleted(message.getId())).thenReturn(Optional.of(message));
        return message;
    }

    private ChatThread storedThread() {
        ChatThread thread = ChatMocks.thread(organizationId, UUID.randomUUID(), author.getId());
        thread.setChannelId(channelId);
        lenient().when(chatThreadRepository.findByIdAndOrganizationId(thread.getId(), organizationId))
                .thenReturn(Optional.of(thread));
        lenient().when(chatThreadMemberRepository.existsByThread_IdAndUserId(thread.getId(), user.getId()))
                .thenReturn(true);
        return thread;
    }

    @Nested
    class Channel {

        @Test
        void newerMessage_movesPositionPublishesAndReturnsCounts() {
            // Arrange
            ChatMessage message = storedMessage(null);
            when(chatReadStateRepository.upsertChannelReadState(user.getId(), organizationId, message.getChannelId(),
                    message.getId(),
                    message.getCreatedAt())).thenReturn(1);

            // Act
            ChatUnreadResponse result = service.markRead(user, organizationId,
                    new ChatReadStateRequest(channelId, null, message.getId()));

            // Assert
            assertThat(result).isSameAs(unread);
            ArgumentCaptor<ChatReadStateChangedEvent> captor = ArgumentCaptor.forClass(ChatReadStateChangedEvent.class);
            verify(applicationEventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue()).isEqualTo(
                    new ChatReadStateChangedEvent(organizationId, message.getChannelId(), user.getId(), null,
                            message.getId()));
            verify(chatChannelQueryService).verifyChannelAccess(organizationId, user.getId(), channelId);
        }

        @Test
        void olderMessage_leavesPositionAndPublishesNothing() {
            // Arrange
            ChatMessage message = storedMessage(null);
            when(chatReadStateRepository.upsertChannelReadState(user.getId(), organizationId, message.getChannelId(),
                    message.getId(),
                    message.getCreatedAt())).thenReturn(0);

            // Act
            ChatUnreadResponse result = service.markRead(user, organizationId,
                    new ChatReadStateRequest(channelId, null, message.getId()));

            // Assert
            assertThat(result).isSameAs(unread);
            verify(applicationEventPublisher, never()).publishEvent(any());
        }

        @Test
        void messageDeletedMeanwhile_stillMarksItsPlace() {
            // Arrange
            ChatMessage message = storedMessage(null);
            message.setDeleted(true);
            when(chatReadStateRepository.upsertChannelReadState(user.getId(), organizationId, message.getChannelId(),
                    message.getId(),
                    message.getCreatedAt())).thenReturn(1);

            // Act
            service.markRead(user, organizationId, new ChatReadStateRequest(channelId, null, message.getId()));

            // Assert
            verify(applicationEventPublisher).publishEvent(any(ChatReadStateChangedEvent.class));
        }

        @Test
        void threadMessage_isNotInTheChannel() {
            // Arrange
            ChatMessage message = storedMessage(storedThread());

            // Act and Assert
            assertThatThrownBy(() -> service.markRead(user, organizationId,
                    new ChatReadStateRequest(channelId, null, message.getId())))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKaizenError.NOT_CHAT_MESSAGE_FOUND.getCode());
            verifyNoInteractions(chatReadStateRepository);
        }

        @Test
        void messageOfAnotherOrganization_isNotFound() {
            // Arrange
            ChatMessage foreign = ChatMocks.message(author, UUID.randomUUID(), "Elsewhere");
            when(chatMessageRepository.findByIdWithDeleted(foreign.getId())).thenReturn(Optional.of(foreign));

            // Act and Assert
            assertThatThrownBy(() -> service.markRead(user, organizationId,
                    new ChatReadStateRequest(channelId, null, foreign.getId())))
                    .isInstanceOf(NotFoundException.class);
            verifyNoInteractions(chatReadStateRepository);
        }

        @Test
        void unreadableChannel_isRefusedBeforeAnyWrite() {
            // Arrange
            when(chatChannelQueryService.verifyChannelAccess(organizationId, user.getId(), channelId))
                    .thenThrow(new NotFoundException(ExceptionDomainKaizenError.NOT_CHAT_CHANNEL_FOUND));

            // Act and Assert
            assertThatThrownBy(() -> service.markRead(user, organizationId,
                    new ChatReadStateRequest(channelId, null, UUID.randomUUID())))
                    .isInstanceOf(NotFoundException.class);
            verifyNoInteractions(chatReadStateRepository);
        }
    }

    @Nested
    class Thread {

        @Test
        void messageOfTheThread_movesTheThreadPosition() {
            // Arrange
            ChatThread thread = storedThread();
            ChatMessage message = storedMessage(thread);
            when(chatReadStateRepository.upsertThreadReadState(user.getId(), organizationId, message.getChannelId(),
                    thread.getId(),
                    message.getId(), message.getCreatedAt())).thenReturn(1);

            // Act
            service.markRead(user, organizationId, new ChatReadStateRequest(channelId, thread.getId(),
                    message.getId()));

            // Assert
            verify(chatReadStateRepository, never()).upsertChannelReadState(any(), any(), any(), any(), any());
            ArgumentCaptor<ChatReadStateChangedEvent> captor = ArgumentCaptor.forClass(ChatReadStateChangedEvent.class);
            verify(applicationEventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().threadId()).isEqualTo(thread.getId());
        }

        @Test
        void threadTheReaderIsNoMemberOf_writesNoRowAndReturnsTheCounts() {
            // Arrange
            ChatThread thread = storedThread();
            ChatMessage message = storedMessage(thread);
            when(chatThreadMemberRepository.existsByThread_IdAndUserId(thread.getId(), user.getId()))
                    .thenReturn(false);

            // Act
            ChatUnreadResponse result = service.markRead(user, organizationId,
                    new ChatReadStateRequest(channelId, thread.getId(), message.getId()));

            // Assert
            assertThat(result).isSameAs(unread);
            verifyNoInteractions(chatReadStateRepository);
            verify(applicationEventPublisher, never()).publishEvent(any());
        }

        @Test
        void messageOfAnotherThread_isNotFound() {
            // Arrange
            ChatThread thread = storedThread();
            ChatMessage message = storedMessage(storedThread());

            // Act and Assert
            assertThatThrownBy(() -> service.markRead(user, organizationId,
                    new ChatReadStateRequest(channelId, thread.getId(), message.getId())))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKaizenError.NOT_CHAT_MESSAGE_FOUND.getCode());
            verifyNoInteractions(chatReadStateRepository);
        }

        @Test
        void channelMessage_isNotInTheThread() {
            // Arrange
            ChatThread thread = storedThread();
            ChatMessage message = storedMessage(null);

            // Act and Assert
            assertThatThrownBy(() -> service.markRead(user, organizationId,
                    new ChatReadStateRequest(channelId, thread.getId(), message.getId())))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void threadOfAnotherOrganization_isNotFound() {
            // Arrange
            UUID unknownThreadId = UUID.randomUUID();

            // Act and Assert
            assertThatThrownBy(() -> service.markRead(user, organizationId,
                    new ChatReadStateRequest(channelId, unknownThreadId, UUID.randomUUID())))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKaizenError.NOT_CHAT_THREAD_FOUND.getCode());
            verifyNoInteractions(chatReadStateRepository);
        }
    }
}
