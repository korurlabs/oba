package com.korur.oba.apps.kazan.chat.listener;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.ChatConstant;
import com.korur.oba.apps.kazan.chat.constant.ChatExceptionLogConstant;
import com.korur.oba.apps.kazan.chat.dto.response.ChatMessageResponse;
import com.korur.oba.apps.kazan.chat.dto.response.ChatUnreadResponse;
import com.korur.oba.apps.kazan.chat.dto.signal.ChatChannelSignal;
import com.korur.oba.apps.kazan.chat.dto.signal.ChatMessageSignal;
import com.korur.oba.apps.kazan.chat.dto.signal.ChatPinSignal;
import com.korur.oba.apps.kazan.chat.dto.signal.ChatPresenceSignal;
import com.korur.oba.apps.kazan.chat.dto.signal.ChatReactionSignal;
import com.korur.oba.apps.kazan.chat.dto.signal.ChatThreadMembershipSignal;
import com.korur.oba.apps.kazan.chat.dto.signal.ChatThreadSignal;
import com.korur.oba.apps.kazan.chat.dto.signal.ChatUnreadSignal;
import com.korur.oba.apps.kazan.chat.entity.ChatChannel;
import com.korur.oba.apps.kazan.chat.enums.ChatChannelVisibility;
import com.korur.oba.apps.kazan.chat.event.ChatChannelChangedEvent;
import com.korur.oba.apps.kazan.chat.event.ChatMessageChangedEvent;
import com.korur.oba.apps.kazan.chat.event.ChatNotificationsChangedEvent;
import com.korur.oba.apps.kazan.chat.event.ChatReactionChangedEvent;
import com.korur.oba.apps.kazan.chat.event.ChatReadStateChangedEvent;
import com.korur.oba.apps.kazan.chat.event.ChatThreadChangedEvent;
import com.korur.oba.apps.kazan.chat.repository.ChatChannelMemberRepository;
import com.korur.oba.apps.kazan.chat.repository.ChatChannelRepository;
import com.korur.oba.apps.kazan.chat.repository.ChatThreadMemberRepository;
import com.korur.oba.apps.kazan.chat.service.ChatMessageQueryService;
import com.korur.oba.apps.kazan.chat.service.ChatReadStateQueryService;
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

@ExtendWith(MockitoExtension.class)
class ChatRealtimeListenerUnitTest {

    @Mock
    private RealtimeSignalBus realtimeSignalBus;
    @Mock
    private RealtimeSessionRegistry realtimeSessionRegistry;
    @Mock
    private ChatReadStateQueryService chatReadStateQueryService;
    @Mock
    private ChatMessageQueryService chatMessageQueryService;
    @Mock
    private ChatThreadMemberRepository chatThreadMemberRepository;
    @Mock
    private ChatChannelRepository chatChannelRepository;
    @Mock
    private ChatChannelMemberRepository chatChannelMemberRepository;
    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;

    private ChatRealtimeListener listener;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();
    private final UUID readerId = UUID.randomUUID();
    private final UUID offlineId = UUID.randomUUID();
    private final UUID messageId = UUID.randomUUID();
    private final UUID threadId = UUID.randomUUID();
    private final UUID channelId = UUID.randomUUID();
    private final Instant createdAt = Instant.parse("2026-10-20T07:30:00Z");
    private final String chatTopic =
            RealtimeDestinationUtil.organizationTopic(organizationId, ChatConstant.CHAT_TOPIC_SUFFIX);
    private final String channelTopic = RealtimeDestinationUtil.organizationTopic(organizationId,
            ChatConstant.CHAT_CHANNEL_TOPIC_SUFFIX_PREFIX + channelId);

    @BeforeEach
    void setUp() {
        listener = new ChatRealtimeListener(realtimeSignalBus, realtimeSessionRegistry, chatReadStateQueryService,
                chatMessageQueryService, chatThreadMemberRepository, chatChannelRepository, chatChannelMemberRepository,
                organizationMemberQueryService, new LoggingService());
    }

    private void channelIs(ChatChannelVisibility visibility) {
        ChatChannel channel = ChatChannel.builder()
                .id(channelId)
                .organizationId(organizationId)
                .name("General")
                .visibility(visibility)
                .build();
        when(chatChannelRepository.findByIdAndOrganizationId(channelId, organizationId))
                .thenReturn(Optional.of(channel));
    }

    private ChatMessageChangedEvent messageEvent(ChatMessageChangedEvent.Type type, UUID inThread) {
        return new ChatMessageChangedEvent(type, organizationId, channelId, messageId, inThread, authorId, createdAt,
                "nonce-1");
    }

    private static ChatUnreadResponse unread(int total) {
        return new ChatUnreadResponse(List.of(), List.of(), total, 0);
    }

    private RealtimeSignal channelSignal() {
        ArgumentCaptor<RealtimeSignal> captor = ArgumentCaptor.forClass(RealtimeSignal.class);
        verify(realtimeSignalBus).publishToTopic(eq(channelTopic), captor.capture());
        verify(realtimeSignalBus, never()).publishToTopic(eq(chatTopic), any());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private Collection<UUID> unreadQueryUsers() {
        ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(chatReadStateQueryService).getUnreadByUsers(eq(organizationId), captor.capture());
        return captor.getValue();
    }

    @Nested
    class MessageChanged {

        @Test
        void createdChannelMessage_signalsAndPushesUnreadToOnlineMembersButTheAuthor() {
            // Arrange
            channelIs(ChatChannelVisibility.PUBLIC);
            when(realtimeSessionRegistry.onlineUserIds(organizationId)).thenReturn(Set.of(authorId, readerId));
            ChatUnreadResponse readerUnread = unread(3);
            when(chatReadStateQueryService.getUnreadByUsers(eq(organizationId), anyCollection()))
                    .thenReturn(Map.of(readerId, readerUnread));

            // Act
            listener.onMessageChanged(messageEvent(ChatMessageChangedEvent.Type.CREATED, null));

            // Assert
            assertThat(channelSignal()).isEqualTo(new ChatMessageSignal(
                    ChatConstant.SIGNAL_MESSAGE_CREATED, messageId, null, authorId, createdAt, "nonce-1", null, null));
            assertThat(unreadQueryUsers()).containsExactly(readerId);
            verify(realtimeSignalBus).publishToUser(readerId, organizationId, ChatConstant.CHAT_USER_QUEUE,
                    new ChatUnreadSignal(ChatConstant.SIGNAL_UNREAD_CHANGED, readerUnread));
            verify(realtimeSignalBus, never()).publishToUser(eq(authorId), any(), any(), any());
        }

        @Test
        void createdThreadMessage_pushesUnreadOnlyToOnlineThreadMembers() {
            // Arrange
            channelIs(ChatChannelVisibility.PUBLIC);
            UUID bystanderId = UUID.randomUUID();
            when(chatThreadMemberRepository.findUserIdsByThreadId(threadId))
                    .thenReturn(List.of(authorId, readerId, offlineId));
            when(realtimeSessionRegistry.onlineUserIds(organizationId))
                    .thenReturn(Set.of(authorId, readerId, bystanderId));
            when(chatReadStateQueryService.getUnreadByUsers(eq(organizationId), anyCollection()))
                    .thenReturn(Map.of(readerId, unread(1)));

            // Act
            listener.onMessageChanged(messageEvent(ChatMessageChangedEvent.Type.CREATED, threadId));

            // Assert
            assertThat(unreadQueryUsers()).containsExactly(readerId);
            verify(realtimeSignalBus).publishToUser(eq(readerId), eq(organizationId),
                    eq(ChatConstant.CHAT_USER_QUEUE), any());
        }

        @Test
        void deletedMessage_recountsUnreadAuthorIncluded() {
            // Arrange
            channelIs(ChatChannelVisibility.PUBLIC);
            when(realtimeSessionRegistry.onlineUserIds(organizationId)).thenReturn(Set.of(authorId, readerId));
            when(chatReadStateQueryService.getUnreadByUsers(eq(organizationId), anyCollection()))
                    .thenReturn(Map.of(authorId, unread(0), readerId, unread(0)));

            // Act
            listener.onMessageChanged(messageEvent(ChatMessageChangedEvent.Type.DELETED, null));

            // Assert
            assertThat(channelSignal()).extracting("type").isEqualTo(ChatConstant.SIGNAL_MESSAGE_DELETED);
            assertThat(unreadQueryUsers()).containsExactlyInAnyOrder(authorId, readerId);
            verify(realtimeSignalBus).publishToUser(eq(readerId), eq(organizationId),
                    eq(ChatConstant.CHAT_USER_QUEUE), any());
            verify(realtimeSignalBus).publishToUser(eq(authorId), eq(organizationId),
                    eq(ChatConstant.CHAT_USER_QUEUE), any());
        }

        @Test
        void editedMessage_signalsWithoutRecount() {
            // Act
            listener.onMessageChanged(messageEvent(ChatMessageChangedEvent.Type.UPDATED, threadId));

            // Assert
            assertThat(channelSignal()).extracting("type").isEqualTo(ChatConstant.SIGNAL_MESSAGE_UPDATED);
            verifyNoInteractions(chatReadStateQueryService, realtimeSessionRegistry, chatThreadMemberRepository);
        }

        @Test
        void editedMessage_carriesNeutralCopyAndItsVersion() {
            // Arrange
            Instant version = Instant.parse("2026-10-20T07:45:00Z");
            ChatMessageResponse neutral = mock(ChatMessageResponse.class);
            when(neutral.version()).thenReturn(version);
            when(chatMessageQueryService.findNeutralMessage(organizationId, messageId, "nonce-1"))
                    .thenReturn(Optional.of(neutral));

            // Act
            listener.onMessageChanged(messageEvent(ChatMessageChangedEvent.Type.UPDATED, null));

            // Assert
            assertThat(channelSignal()).isEqualTo(new ChatMessageSignal(ChatConstant.SIGNAL_MESSAGE_UPDATED,
                    messageId, null, authorId, createdAt, "nonce-1", version, neutral));
        }

        @Test
        void createdMessage_carriesNeutralCopyAndItsVersion() {
            // Arrange
            channelIs(ChatChannelVisibility.PUBLIC);
            Instant version = Instant.parse("2026-10-20T07:31:00Z");
            ChatMessageResponse neutral = mock(ChatMessageResponse.class);
            when(neutral.version()).thenReturn(version);
            when(chatMessageQueryService.findNeutralMessage(organizationId, messageId, "nonce-1"))
                    .thenReturn(Optional.of(neutral));

            // Act
            listener.onMessageChanged(messageEvent(ChatMessageChangedEvent.Type.CREATED, null));

            // Assert
            assertThat(channelSignal()).isEqualTo(new ChatMessageSignal(ChatConstant.SIGNAL_MESSAGE_CREATED,
                    messageId, null, authorId, createdAt, "nonce-1", version, neutral));
        }

        @Test
        void payloadBuildFails_isLoggedAtErrorOnceAndNotRetried() {
            // Arrange
            LoggingService loggingService = spy(new LoggingService());
            ChatRealtimeListener failingListener = new ChatRealtimeListener(realtimeSignalBus,
                    realtimeSessionRegistry, chatReadStateQueryService, chatMessageQueryService,
                    chatThreadMemberRepository, chatChannelRepository, chatChannelMemberRepository,
                    organizationMemberQueryService, loggingService);
            when(chatMessageQueryService.findNeutralMessage(organizationId, messageId, "nonce-1"))
                    .thenThrow(new IllegalStateException("storage down"));

            // Act
            failingListener.onMessageChanged(messageEvent(ChatMessageChangedEvent.Type.UPDATED, null));

            // Assert
            verify(chatMessageQueryService, times(1)).findNeutralMessage(organizationId, messageId, "nonce-1");
            verify(loggingService, times(1)).error(any(LogPrefix.class), eq(LogLayer.HANDLER),
                    eq(ChatExceptionLogConstant.CHAT_SIGNAL_PAYLOAD_FAILED), any(IllegalStateException.class),
                    eq(ChatConstant.SIGNAL_MESSAGE_UPDATED), eq(organizationId), eq(messageId));
            assertThat(channelSignal()).extracting("message", "version").containsExactly(null, null);
        }

        @Test
        void messageNeedsPerReaderRead_signalsWithoutCopyButWithVersion() {
            // Arrange
            Instant version = Instant.parse("2026-10-20T07:50:00Z");
            when(chatMessageQueryService.findNeutralMessage(organizationId, messageId, "nonce-1"))
                    .thenReturn(Optional.empty());
            when(chatMessageQueryService.findMessageVersion(organizationId, messageId))
                    .thenReturn(Optional.of(version));

            // Act
            listener.onMessageChanged(messageEvent(ChatMessageChangedEvent.Type.UPDATED, null));

            // Assert
            assertThat(channelSignal()).extracting("message", "version").containsExactly(null, version);
        }

        @Test
        void createdMessageUnderFallbackRule_carriesVersion() {
            // Arrange
            channelIs(ChatChannelVisibility.PUBLIC);
            Instant version = Instant.parse("2026-10-20T07:31:00Z");
            when(chatMessageQueryService.findNeutralMessage(organizationId, messageId, "nonce-1"))
                    .thenReturn(Optional.empty());
            when(chatMessageQueryService.findMessageVersion(organizationId, messageId))
                    .thenReturn(Optional.of(version));

            // Act
            listener.onMessageChanged(messageEvent(ChatMessageChangedEvent.Type.CREATED, null));

            // Assert
            assertThat(channelSignal()).isEqualTo(new ChatMessageSignal(ChatConstant.SIGNAL_MESSAGE_CREATED,
                    messageId, null, authorId, createdAt, "nonce-1", version, null));
        }

        @Test
        void messageGone_signalsWithoutCopyOrVersion() {
            // Arrange
            when(chatMessageQueryService.findNeutralMessage(organizationId, messageId, "nonce-1"))
                    .thenReturn(Optional.empty());
            when(chatMessageQueryService.findMessageVersion(organizationId, messageId))
                    .thenReturn(Optional.empty());

            // Act
            listener.onMessageChanged(messageEvent(ChatMessageChangedEvent.Type.UPDATED, null));

            // Assert
            assertThat(channelSignal()).extracting("message", "version").containsExactly(null, null);
        }

        @Test
        void payloadBuildFails_stillSignalsWithoutCopyButWithVersion() {
            // Arrange
            Instant version = Instant.parse("2026-10-20T07:50:00Z");
            when(chatMessageQueryService.findNeutralMessage(organizationId, messageId, "nonce-1"))
                    .thenThrow(new IllegalStateException("storage down"));
            when(chatMessageQueryService.findMessageVersion(organizationId, messageId))
                    .thenReturn(Optional.of(version));

            // Act
            listener.onMessageChanged(messageEvent(ChatMessageChangedEvent.Type.UPDATED, null));

            // Assert
            assertThat(channelSignal()).isEqualTo(new ChatMessageSignal(ChatConstant.SIGNAL_MESSAGE_UPDATED,
                    messageId, null, authorId, createdAt, "nonce-1", version, null));
        }

        @Test
        void versionReadFails_isLoggedAndSignalStillGoesOut() {
            // Arrange
            LoggingService loggingService = spy(new LoggingService());
            ChatRealtimeListener failingListener = new ChatRealtimeListener(realtimeSignalBus,
                    realtimeSessionRegistry, chatReadStateQueryService, chatMessageQueryService,
                    chatThreadMemberRepository, chatChannelRepository, chatChannelMemberRepository,
                    organizationMemberQueryService, loggingService);
            when(chatMessageQueryService.findNeutralMessage(organizationId, messageId, "nonce-1"))
                    .thenReturn(Optional.empty());
            when(chatMessageQueryService.findMessageVersion(organizationId, messageId))
                    .thenThrow(new IllegalStateException("database down"));

            // Act
            failingListener.onMessageChanged(messageEvent(ChatMessageChangedEvent.Type.UPDATED, null));

            // Assert
            verify(loggingService, times(1)).error(any(LogPrefix.class), eq(LogLayer.HANDLER),
                    eq(ChatExceptionLogConstant.CHAT_SIGNAL_PAYLOAD_FAILED), any(IllegalStateException.class),
                    eq(ChatConstant.SIGNAL_MESSAGE_UPDATED), eq(organizationId), eq(messageId));
            assertThat(channelSignal()).extracting("message", "version").containsExactly(null, null);
        }

        @Test
        void deletedAndPinnedMessages_buildNoCopy() {
            // Act
            listener.onMessageChanged(messageEvent(ChatMessageChangedEvent.Type.DELETED, null));
            listener.onMessageChanged(messageEvent(ChatMessageChangedEvent.Type.PINNED, null));

            // Assert
            verifyNoInteractions(chatMessageQueryService);
        }

        @Test
        void pinnedAndUnpinned_arePinSignals() {
            // Act
            listener.onMessageChanged(messageEvent(ChatMessageChangedEvent.Type.PINNED, null));
            listener.onMessageChanged(messageEvent(ChatMessageChangedEvent.Type.UNPINNED, null));

            // Assert
            verify(realtimeSignalBus).publishToTopic(channelTopic,
                    new ChatPinSignal(ChatConstant.SIGNAL_PIN_CHANGED, messageId, null, true));
            verify(realtimeSignalBus).publishToTopic(channelTopic,
                    new ChatPinSignal(ChatConstant.SIGNAL_PIN_CHANGED, messageId, null, false));
            verifyNoInteractions(chatReadStateQueryService);
        }

        @Test
        void nobodyElseOnline_readsNoCounts() {
            // Arrange
            channelIs(ChatChannelVisibility.PUBLIC);
            when(realtimeSessionRegistry.onlineUserIds(organizationId)).thenReturn(Set.of(authorId));

            // Act
            listener.onMessageChanged(messageEvent(ChatMessageChangedEvent.Type.CREATED, null));

            // Assert
            verifyNoInteractions(chatReadStateQueryService);
            verify(realtimeSignalBus, never()).publishToUser(any(), any(), any(), any());
        }

        @Test
        void failingChannelSignal_isLoggedAndUnreadStillPushed() {
            // Arrange
            channelIs(ChatChannelVisibility.PUBLIC);
            doThrow(new IllegalStateException("broker down")).when(realtimeSignalBus)
                    .publishToTopic(eq(channelTopic), any());
            when(realtimeSessionRegistry.onlineUserIds(organizationId)).thenReturn(Set.of(readerId));
            when(chatReadStateQueryService.getUnreadByUsers(eq(organizationId), anyCollection()))
                    .thenReturn(Map.of(readerId, unread(1)));

            // Act and Assert
            assertThatCode(() -> listener.onMessageChanged(messageEvent(ChatMessageChangedEvent.Type.CREATED, null)))
                    .doesNotThrowAnyException();
            verify(realtimeSignalBus).publishToUser(eq(readerId), eq(organizationId),
                    eq(ChatConstant.CHAT_USER_QUEUE), any());
        }

        @Test
        void failingPushToOneUser_doesNotStopTheOthers() {
            // Arrange
            channelIs(ChatChannelVisibility.PUBLIC);
            UUID secondId = UUID.randomUUID();
            when(realtimeSessionRegistry.onlineUserIds(organizationId)).thenReturn(Set.of(readerId, secondId));
            Map<UUID, ChatUnreadResponse> counts = new LinkedHashMap<>();
            counts.put(readerId, unread(1));
            counts.put(secondId, unread(2));
            when(chatReadStateQueryService.getUnreadByUsers(eq(organizationId), anyCollection())).thenReturn(counts);
            doThrow(new IllegalStateException("session gone")).when(realtimeSignalBus)
                    .publishToUser(eq(readerId), eq(organizationId), eq(ChatConstant.CHAT_USER_QUEUE), any());

            // Act and Assert
            assertThatCode(() -> listener.onMessageChanged(messageEvent(ChatMessageChangedEvent.Type.CREATED, null)))
                    .doesNotThrowAnyException();
            verify(realtimeSignalBus).publishToUser(eq(secondId), eq(organizationId),
                    eq(ChatConstant.CHAT_USER_QUEUE), any());
        }

        @Test
        void failingCountQuery_isLoggedAndSwallowed() {
            // Arrange
            channelIs(ChatChannelVisibility.PUBLIC);
            when(realtimeSessionRegistry.onlineUserIds(organizationId)).thenReturn(Set.of(readerId));
            when(chatReadStateQueryService.getUnreadByUsers(eq(organizationId), anyCollection()))
                    .thenThrow(new IllegalStateException("pool exhausted"));

            // Act and Assert
            assertThatCode(() -> listener.onMessageChanged(messageEvent(ChatMessageChangedEvent.Type.CREATED, null)))
                    .doesNotThrowAnyException();
            verify(realtimeSignalBus, never()).publishToUser(any(), any(), any(), any());
        }

        @Test
        void failingThreadMemberQuery_isLoggedAndSwallowed() {
            // Arrange
            when(chatThreadMemberRepository.findUserIdsByThreadId(threadId))
                    .thenThrow(new IllegalStateException("pool exhausted"));

            // Act and Assert
            assertThatCode(() -> listener.onMessageChanged(
                    messageEvent(ChatMessageChangedEvent.Type.CREATED, threadId))).doesNotThrowAnyException();
            verifyNoInteractions(chatReadStateQueryService);
        }
    }

    @Nested
    class NotificationsChanged {

        @Test
        void changedMentions_pushUnreadOnlyToTheOnlineUsersConcerned() {
            // Arrange
            UUID bystanderId = UUID.randomUUID();
            when(realtimeSessionRegistry.onlineUserIds(organizationId)).thenReturn(Set.of(readerId, bystanderId));
            ChatUnreadResponse readerUnread = unread(2);
            when(chatReadStateQueryService.getUnreadByUsers(eq(organizationId), anyCollection()))
                    .thenReturn(Map.of(readerId, readerUnread));

            // Act
            listener.onNotificationsChanged(new ChatNotificationsChangedEvent(organizationId, channelId, threadId,
                    messageId,
                    authorId, List.of(readerId, offlineId), false));

            // Assert
            assertThat(unreadQueryUsers()).containsExactly(readerId);
            verify(realtimeSignalBus).publishToUser(readerId, organizationId, ChatConstant.CHAT_USER_QUEUE,
                    new ChatUnreadSignal(ChatConstant.SIGNAL_UNREAD_CHANGED, readerUnread));
            verify(realtimeSignalBus, never()).publishToTopic(any(), any());
            verifyNoInteractions(chatThreadMemberRepository);
        }

        @Test
        void everyoneToggledInThread_pushesUnreadToOnlineThreadMembersButTheAuthor() {
            // Arrange
            channelIs(ChatChannelVisibility.PUBLIC);
            when(chatThreadMemberRepository.findUserIdsByThreadId(threadId))
                    .thenReturn(List.of(authorId, readerId, offlineId));
            when(realtimeSessionRegistry.onlineUserIds(organizationId)).thenReturn(Set.of(authorId, readerId));
            when(chatReadStateQueryService.getUnreadByUsers(eq(organizationId), anyCollection()))
                    .thenReturn(Map.of(readerId, unread(1)));

            // Act
            listener.onNotificationsChanged(new ChatNotificationsChangedEvent(organizationId, channelId, threadId,
                    messageId,
                    authorId, List.of(), true));

            // Assert
            assertThat(unreadQueryUsers()).containsExactly(readerId);
            verify(realtimeSignalBus, never()).publishToUser(eq(authorId), any(), any(), any());
        }

        @Test
        void everyoneToggledInChannel_pushesUnreadToOnlineMembersButTheAuthor() {
            // Arrange
            channelIs(ChatChannelVisibility.PUBLIC);
            when(realtimeSessionRegistry.onlineUserIds(organizationId)).thenReturn(Set.of(authorId, readerId));
            when(chatReadStateQueryService.getUnreadByUsers(eq(organizationId), anyCollection()))
                    .thenReturn(Map.of(readerId, unread(1)));

            // Act
            listener.onNotificationsChanged(new ChatNotificationsChangedEvent(organizationId, channelId, null,
                    messageId,
                    authorId, List.of(), true));

            // Assert
            assertThat(unreadQueryUsers()).containsExactly(readerId);
            verifyNoInteractions(chatThreadMemberRepository);
        }

        @Test
        void failingCountQuery_isLoggedAndSwallowed() {
            // Arrange
            when(realtimeSessionRegistry.onlineUserIds(organizationId)).thenReturn(Set.of(readerId));
            when(chatReadStateQueryService.getUnreadByUsers(eq(organizationId), anyCollection()))
                    .thenThrow(new IllegalStateException("database down"));

            // Act and Assert
            assertThatCode(() -> listener.onNotificationsChanged(new ChatNotificationsChangedEvent(organizationId,
                    channelId,
                    null, messageId, authorId, List.of(readerId), false))).doesNotThrowAnyException();
            verify(realtimeSignalBus, never()).publishToUser(any(), any(), any(), any());
        }
    }

    @Nested
    class ThreadChanged {

        private final UUID rootId = UUID.randomUUID();

        private ChatThreadChangedEvent threadEvent(ChatThreadChangedEvent.Type type, List<UUID> userIds) {
            return new ChatThreadChangedEvent(type, organizationId, channelId, threadId, rootId, userIds);
        }

        @Test
        void created_isThreadSignalOnly() {
            // Act
            listener.onThreadChanged(threadEvent(ChatThreadChangedEvent.Type.CREATED, List.of(authorId)));

            // Assert
            assertThat(channelSignal()).isEqualTo(new ChatThreadSignal(
                    ChatConstant.SIGNAL_THREAD_CREATED, threadId, rootId));
            verify(realtimeSignalBus, never()).publishToUser(any(), any(), any(), any());
        }

        @Test
        void updated_signalsAndRecountsOnlineThreadMembers() {
            // Arrange
            channelIs(ChatChannelVisibility.PUBLIC);
            when(chatThreadMemberRepository.findUserIdsByThreadId(threadId)).thenReturn(List.of(readerId, offlineId));
            when(realtimeSessionRegistry.onlineUserIds(organizationId)).thenReturn(Set.of(readerId));
            when(chatReadStateQueryService.getUnreadByUsers(eq(organizationId), anyCollection()))
                    .thenReturn(Map.of(readerId, unread(0)));

            // Act
            listener.onThreadChanged(threadEvent(ChatThreadChangedEvent.Type.UPDATED, List.of()));

            // Assert
            assertThat(channelSignal()).extracting("type").isEqualTo(ChatConstant.SIGNAL_THREAD_UPDATED);
            assertThat(unreadQueryUsers()).containsExactly(readerId);
        }

        @Test
        void membersJoined_tellsEachJoinedUser() {
            // Act
            listener.onThreadChanged(threadEvent(ChatThreadChangedEvent.Type.MEMBERS_JOINED,
                    List.of(authorId, readerId)));

            // Assert
            ChatThreadMembershipSignal joined = new ChatThreadMembershipSignal(
                    ChatConstant.SIGNAL_THREAD_MEMBERSHIP_CHANGED, threadId, true);
            verify(realtimeSignalBus).publishToUser(authorId, organizationId, ChatConstant.CHAT_USER_QUEUE, joined);
            verify(realtimeSignalBus).publishToUser(readerId, organizationId, ChatConstant.CHAT_USER_QUEUE, joined);
            verify(realtimeSignalBus, never()).publishToTopic(any(), any());
            verifyNoInteractions(chatReadStateQueryService);
        }

        @Test
        void memberLeft_tellsTheUserAndRecountsWhenOnline() {
            // Arrange
            when(realtimeSessionRegistry.onlineUserIds(organizationId)).thenReturn(Set.of(readerId));
            ChatUnreadResponse readerUnread = unread(2);
            when(chatReadStateQueryService.getUnreadByUsers(eq(organizationId), anyCollection()))
                    .thenReturn(Map.of(readerId, readerUnread));

            // Act
            listener.onThreadChanged(threadEvent(ChatThreadChangedEvent.Type.MEMBER_LEFT, List.of(readerId)));

            // Assert
            verify(realtimeSignalBus).publishToUser(readerId, organizationId, ChatConstant.CHAT_USER_QUEUE,
                    new ChatThreadMembershipSignal(ChatConstant.SIGNAL_THREAD_MEMBERSHIP_CHANGED,
                            threadId, false));
            verify(realtimeSignalBus).publishToUser(readerId, organizationId, ChatConstant.CHAT_USER_QUEUE,
                    new ChatUnreadSignal(ChatConstant.SIGNAL_UNREAD_CHANGED, readerUnread));
        }
    }

    @Test
    void reactionChanged_isReactionSignal() {
        // Act
        listener.onReactionChanged(new ChatReactionChangedEvent(organizationId, channelId, messageId, threadId, "👍",
                readerId,
                true));

        // Assert
        assertThat(channelSignal()).isEqualTo(new ChatReactionSignal(
                ChatConstant.SIGNAL_REACTION_CHANGED, messageId, threadId, "👍", readerId, true));
    }

    @Nested
    class ReadStateChanged {

        @Test
        void onlineReader_getsFreshCounts() {
            // Arrange
            when(realtimeSessionRegistry.onlineUserIds(organizationId)).thenReturn(Set.of(readerId, authorId));
            ChatUnreadResponse readerUnread = unread(0);
            when(chatReadStateQueryService.getUnreadByUsers(eq(organizationId), anyCollection()))
                    .thenReturn(Map.of(readerId, readerUnread));

            // Act
            listener.onReadStateChanged(new ChatReadStateChangedEvent(organizationId, channelId, readerId, null,
                    messageId));

            // Assert
            assertThat(unreadQueryUsers()).containsExactly(readerId);
            verify(realtimeSignalBus).publishToUser(readerId, organizationId, ChatConstant.CHAT_USER_QUEUE,
                    new ChatUnreadSignal(ChatConstant.SIGNAL_UNREAD_CHANGED, readerUnread));
        }

        @Test
        void offlineReader_readsNoCounts() {
            // Arrange
            when(realtimeSessionRegistry.onlineUserIds(organizationId)).thenReturn(Set.of(authorId));

            // Act
            listener.onReadStateChanged(new ChatReadStateChangedEvent(organizationId, channelId, readerId, threadId,
                    messageId));

            // Assert
            verifyNoInteractions(chatReadStateQueryService, realtimeSignalBus);
        }
    }

    @Nested
    class ChannelReaders {

        @Test
        void createdMessageInPrivateChannel_pushesUnreadOnlyToOnlineMembers() {
            // Arrange
            UUID outsiderId = UUID.randomUUID();
            channelIs(ChatChannelVisibility.PRIVATE);
            when(chatChannelMemberRepository.findUserIdsByChannelId(channelId))
                    .thenReturn(List.of(authorId, readerId, offlineId));
            when(realtimeSessionRegistry.onlineUserIds(organizationId))
                    .thenReturn(Set.of(authorId, readerId, outsiderId));
            when(chatReadStateQueryService.getUnreadByUsers(eq(organizationId), anyCollection()))
                    .thenReturn(Map.of(readerId, unread(1)));

            // Act
            listener.onMessageChanged(messageEvent(ChatMessageChangedEvent.Type.CREATED, null));

            // Assert
            assertThat(channelSignal()).extracting("type").isEqualTo(ChatConstant.SIGNAL_MESSAGE_CREATED);
            assertThat(unreadQueryUsers()).containsExactly(readerId);
            verify(realtimeSignalBus, never()).publishToUser(eq(outsiderId), any(), any(), any());
        }

        @Test
        void threadMessageInPrivateChannel_skipsThreadMembersNoLongerOnTheMemberList() {
            // Arrange
            UUID formerMemberId = UUID.randomUUID();
            channelIs(ChatChannelVisibility.PRIVATE);
            when(chatChannelMemberRepository.findUserIdsByChannelId(channelId)).thenReturn(List.of(authorId, readerId));
            when(chatThreadMemberRepository.findUserIdsByThreadId(threadId))
                    .thenReturn(List.of(authorId, readerId, formerMemberId));
            when(realtimeSessionRegistry.onlineUserIds(organizationId))
                    .thenReturn(Set.of(authorId, readerId, formerMemberId));
            when(chatReadStateQueryService.getUnreadByUsers(eq(organizationId), anyCollection()))
                    .thenReturn(Map.of(readerId, unread(1)));

            // Act
            listener.onMessageChanged(messageEvent(ChatMessageChangedEvent.Type.CREATED, threadId));

            // Assert
            assertThat(unreadQueryUsers()).containsExactly(readerId);
        }

        @Test
        void messageOfAChannelThatIsGone_pushesNoUnread() {
            // Arrange
            when(chatChannelRepository.findByIdAndOrganizationId(channelId, organizationId))
                    .thenReturn(Optional.empty());

            // Act
            listener.onMessageChanged(messageEvent(ChatMessageChangedEvent.Type.DELETED, null));

            // Assert
            assertThat(channelSignal()).extracting("type").isEqualTo(ChatConstant.SIGNAL_MESSAGE_DELETED);
            verifyNoInteractions(chatReadStateQueryService);
            verify(realtimeSignalBus, never()).publishToUser(any(), any(), any(), any());
        }

        @Test
        void failingChannelLookup_isLoggedAndSwallowed() {
            // Arrange
            when(chatChannelRepository.findByIdAndOrganizationId(channelId, organizationId))
                    .thenThrow(new IllegalStateException("pool exhausted"));

            // Act and Assert
            assertThatCode(() -> listener.onMessageChanged(messageEvent(ChatMessageChangedEvent.Type.CREATED, null)))
                    .doesNotThrowAnyException();
            verifyNoInteractions(chatReadStateQueryService);
        }
    }

    @Nested
    class ChannelChanged {

        private final UUID memberId = UUID.randomUUID();
        private final UUID removedId = UUID.randomUUID();
        private final UUID onlineAdminId = UUID.randomUUID();
        private final UUID offlineAdminId = UUID.randomUUID();
        private final ChatChannelSignal signal =
                new ChatChannelSignal(ChatConstant.SIGNAL_CHANNEL_CHANGED, channelId);

        private ChatChannelChangedEvent channelEvent(ChatChannelChangedEvent.Type type,
                                                     ChatChannelVisibility visibility, List<UUID> affected) {
            return new ChatChannelChangedEvent(type, organizationId, channelId, visibility, affected);
        }

        private void privateAudience(Set<UUID> online) {
            when(chatChannelMemberRepository.findUserIdsByChannelId(channelId)).thenReturn(List.of(memberId));
            when(organizationMemberQueryService.findActiveUserIdsByRoles(organizationId,
                    Set.of(OrganizationMemberRole.OWNER, OrganizationMemberRole.ADMIN)))
                    .thenReturn(Set.of(onlineAdminId, offlineAdminId));
            when(realtimeSessionRegistry.onlineUserIds(organizationId)).thenReturn(online);
        }

        @Test
        void publicChannelUpdated_goesToTheOrganizationTopicOnly() {
            // Act
            listener.onChannelChanged(channelEvent(ChatChannelChangedEvent.Type.UPDATED,
                    ChatChannelVisibility.PUBLIC, List.of()));

            // Assert
            verify(realtimeSignalBus).publishToTopic(chatTopic, signal);
            verify(realtimeSignalBus, never()).publishToUser(any(), any(), any(), any());
            verifyNoInteractions(realtimeSessionRegistry, chatChannelMemberRepository,
                    organizationMemberQueryService);
        }

        @Test
        void privateChannelUpdated_goesToMembersAndOnlineOwnersAndAdmins() {
            // Arrange
            privateAudience(Set.of(memberId, onlineAdminId));

            // Act
            listener.onChannelChanged(channelEvent(ChatChannelChangedEvent.Type.UPDATED,
                    ChatChannelVisibility.PRIVATE, List.of()));

            // Assert
            verify(realtimeSignalBus).publishToUser(memberId, organizationId, ChatConstant.CHAT_USER_QUEUE, signal);
            verify(realtimeSignalBus).publishToUser(onlineAdminId, organizationId, ChatConstant.CHAT_USER_QUEUE,
                    signal);
            verify(realtimeSignalBus, never()).publishToUser(eq(offlineAdminId), any(), any(), any());
            verify(realtimeSignalBus, never()).publishToTopic(any(), any());
            verify(realtimeSessionRegistry, never()).revalidateSubscriptions(anyCollection());
        }

        @Test
        void privateMembersAdded_alsoTellsTheAddedUsers() {
            // Arrange
            UUID addedId = UUID.randomUUID();
            privateAudience(Set.of());

            // Act
            listener.onChannelChanged(channelEvent(ChatChannelChangedEvent.Type.MEMBERS_ADDED,
                    ChatChannelVisibility.PRIVATE, List.of(addedId)));

            // Assert
            verify(realtimeSignalBus).publishToUser(memberId, organizationId, ChatConstant.CHAT_USER_QUEUE, signal);
            verify(realtimeSignalBus).publishToUser(addedId, organizationId, ChatConstant.CHAT_USER_QUEUE, signal);
            verify(realtimeSessionRegistry, never()).revalidateSubscriptions(anyCollection());
        }

        @Test
        void privateMembersRemoved_tellsTheRemovedUsersAndRevalidatesOnlyThem() {
            // Arrange
            privateAudience(Set.of(onlineAdminId));

            // Act
            listener.onChannelChanged(channelEvent(ChatChannelChangedEvent.Type.MEMBERS_REMOVED,
                    ChatChannelVisibility.PRIVATE, List.of(removedId)));

            // Assert
            verify(realtimeSessionRegistry).revalidateSubscriptions(List.of(removedId));
            verify(realtimeSignalBus).publishToUser(removedId, organizationId, ChatConstant.CHAT_USER_QUEUE, signal);
            verify(realtimeSignalBus).publishToUser(memberId, organizationId, ChatConstant.CHAT_USER_QUEUE, signal);
            verify(realtimeSignalBus).publishToUser(onlineAdminId, organizationId, ChatConstant.CHAT_USER_QUEUE,
                    signal);
            verify(realtimeSignalBus, never()).publishToTopic(any(), any());
        }

        @Test
        void privateChannelDeleted_revalidatesEveryOnlineUserAndTellsTheAudience() {
            // Arrange
            Set<UUID> online = Set.of(memberId, onlineAdminId, readerId);
            privateAudience(online);

            // Act
            listener.onChannelChanged(channelEvent(ChatChannelChangedEvent.Type.DELETED,
                    ChatChannelVisibility.PRIVATE, List.of()));

            // Assert
            verify(realtimeSessionRegistry).revalidateSubscriptions(online);
            verify(realtimeSignalBus).publishToUser(memberId, organizationId, ChatConstant.CHAT_USER_QUEUE, signal);
            verify(realtimeSignalBus).publishToUser(onlineAdminId, organizationId, ChatConstant.CHAT_USER_QUEUE,
                    signal);
            verify(realtimeSignalBus, never()).publishToUser(eq(readerId), any(), any(), any());
            verify(realtimeSignalBus, never()).publishToTopic(any(), any());
        }

        @Test
        void publicChannelDeleted_goesToTheOrganizationAndRevalidatesEveryOnlineUser() {
            // Arrange
            Set<UUID> online = Set.of(readerId);
            when(realtimeSessionRegistry.onlineUserIds(organizationId)).thenReturn(online);

            // Act
            listener.onChannelChanged(channelEvent(ChatChannelChangedEvent.Type.DELETED,
                    ChatChannelVisibility.PUBLIC, List.of()));

            // Assert
            verify(realtimeSessionRegistry).revalidateSubscriptions(online);
            verify(realtimeSignalBus).publishToTopic(chatTopic, signal);
        }

        @Test
        void switchedToPrivate_goesToTheOrganizationAndRevalidatesEveryOnlineUser() {
            // Arrange
            Set<UUID> online = Set.of(memberId, readerId);
            when(realtimeSessionRegistry.onlineUserIds(organizationId)).thenReturn(online);

            // Act
            listener.onChannelChanged(channelEvent(ChatChannelChangedEvent.Type.VISIBILITY_CHANGED,
                    ChatChannelVisibility.PRIVATE, List.of(memberId)));

            // Assert
            verify(realtimeSessionRegistry).revalidateSubscriptions(online);
            verify(realtimeSignalBus).publishToTopic(chatTopic, signal);
            verify(realtimeSignalBus, never()).publishToUser(any(), any(), any(), any());
        }

        @Test
        void switchedToPublic_goesToTheOrganizationWithoutRevalidation() {
            // Act
            listener.onChannelChanged(channelEvent(ChatChannelChangedEvent.Type.VISIBILITY_CHANGED,
                    ChatChannelVisibility.PUBLIC, List.of(memberId)));

            // Assert
            verify(realtimeSignalBus).publishToTopic(chatTopic, signal);
            verifyNoInteractions(realtimeSessionRegistry);
        }

        @Test
        void failingAudienceQuery_isLoggedAndSwallowed() {
            // Arrange
            when(chatChannelMemberRepository.findUserIdsByChannelId(channelId))
                    .thenThrow(new IllegalStateException("pool exhausted"));

            // Act and Assert
            assertThatCode(() -> listener.onChannelChanged(channelEvent(ChatChannelChangedEvent.Type.UPDATED,
                    ChatChannelVisibility.PRIVATE, List.of()))).doesNotThrowAnyException();
            verify(realtimeSignalBus, never()).publishToUser(any(), any(), any(), any());
        }

        @Test
        void failingRevalidation_isLoggedAndTheSignalStillSent() {
            // Arrange
            privateAudience(Set.of());
            doThrow(new IllegalStateException("broker down")).when(realtimeSessionRegistry)
                    .revalidateSubscriptions(anyCollection());

            // Act and Assert
            assertThatCode(() -> listener.onChannelChanged(channelEvent(ChatChannelChangedEvent.Type.MEMBERS_REMOVED,
                    ChatChannelVisibility.PRIVATE, List.of(removedId)))).doesNotThrowAnyException();
            verify(realtimeSignalBus).publishToUser(removedId, organizationId, ChatConstant.CHAT_USER_QUEUE, signal);
        }
    }

    @Test
    void presenceChanged_staysOnTheOrganizationTopic() {
        // Act
        listener.onPresenceChanged(new RealtimePresenceChangedEvent(organizationId, readerId, true));

        // Assert
        verify(realtimeSignalBus).publishToTopic(chatTopic, new ChatPresenceSignal(ChatConstant.SIGNAL_PRESENCE,
                readerId, true));
    }
}
