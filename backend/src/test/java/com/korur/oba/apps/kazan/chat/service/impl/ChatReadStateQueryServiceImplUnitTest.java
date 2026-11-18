package com.korur.oba.apps.kazan.chat.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.ChatConstant;
import com.korur.oba.apps.kazan.chat.dto.projection.ChatUnreadCountProjection;
import com.korur.oba.apps.kazan.chat.dto.response.ChatConversationUnreadResponse;
import com.korur.oba.apps.kazan.chat.dto.response.ChatUnreadResponse;
import com.korur.oba.apps.kazan.chat.mapper.ChatReadStateMapperImpl;
import com.korur.oba.apps.kazan.chat.mock_data.ChatMocks;
import com.korur.oba.apps.kazan.chat.repository.ChatMessageRepository;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class ChatReadStateQueryServiceImplUnitTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    private ChatReadStateQueryServiceImpl service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID channelId = UUID.randomUUID();
    private final User user = ChatMocks.user("Ada", "Lovelace");

    @BeforeEach
    void setUp() {
        service = new ChatReadStateQueryServiceImpl(chatMessageRepository, new ChatReadStateMapperImpl());
    }

    @Nested
    class GetUnread {

        @Test
        void channelAndThreads_areSplitAndAddedUp() {
            // Arrange
            UUID lastReadId = UUID.randomUUID();
            Instant lastReadAt = Instant.parse("2026-10-20T10:42:00Z");
            UUID threadA = UUID.randomUUID();
            UUID threadB = UUID.randomUUID();
            when(chatMessageRepository.findUnreadCounts(user.getId(), organizationId)).thenReturn(List.of(
                    new UnreadRow(user.getId(), channelId, null, 4, 1, lastReadId, lastReadAt),
                    new UnreadRow(user.getId(), channelId, threadA, 2, 2, null, null),
                    new UnreadRow(user.getId(), channelId, threadB, 1, 0, null, null)));

            // Act
            ChatUnreadResponse unread = service.getUnread(user, organizationId);

            // Assert
            assertThat(unread.channels()).containsExactly(
                    new ChatConversationUnreadResponse(channelId, channelId, 4, 1, lastReadId, lastReadAt));
            assertThat(unread.threads()).containsExactly(
                    new ChatConversationUnreadResponse(threadA, channelId, 2, 2, null, null),
                    new ChatConversationUnreadResponse(threadB, channelId, 1, 0, null, null));
            assertThat(unread.total()).isEqualTo(7);
            assertThat(unread.mentionTotal()).isEqualTo(3);
        }

        @Test
        void cappedCounts_arePassedOnAndAddedUp() {
            // Arrange
            UUID threadId = UUID.randomUUID();
            when(chatMessageRepository.findUnreadCounts(user.getId(), organizationId)).thenReturn(List.of(
                    new UnreadRow(user.getId(), channelId, null, ChatConstant.MAX_UNREAD_COUNT, 0, null, null),
                    new UnreadRow(user.getId(), channelId, threadId, ChatConstant.MAX_UNREAD_COUNT, 0, null, null)));

            // Act
            ChatUnreadResponse unread = service.getUnread(user, organizationId);

            // Assert
            assertThat(unread.channels().getFirst().unread()).isEqualTo(ChatConstant.MAX_UNREAD_COUNT);
            assertThat(unread.total()).isEqualTo(2 * ChatConstant.MAX_UNREAD_COUNT);
        }

        @Test
        void cappedMentionCounts_addUpBeyondTheCap() {
            // Arrange
            UUID threadId = UUID.randomUUID();
            when(chatMessageRepository.findUnreadCounts(user.getId(), organizationId)).thenReturn(List.of(
                    new UnreadRow(user.getId(), channelId, null, ChatConstant.MAX_UNREAD_COUNT,
                            ChatConstant.MAX_UNREAD_COUNT,
                            null, null),
                    new UnreadRow(user.getId(), channelId, threadId, ChatConstant.MAX_UNREAD_COUNT, 7, null, null)));

            // Act
            ChatUnreadResponse unread = service.getUnread(user, organizationId);

            // Assert
            assertThat(unread.channels().getFirst().mentionCount()).isEqualTo(ChatConstant.MAX_UNREAD_COUNT);
            assertThat(unread.threads()).containsExactly(new ChatConversationUnreadResponse(threadId, channelId,
                    ChatConstant.MAX_UNREAD_COUNT, 7, null, null));
            assertThat(unread.mentionTotal()).isEqualTo(ChatConstant.MAX_UNREAD_COUNT + 7);
        }

        @Test
        void missingMentionCount_isZero() {
            // Arrange
            UUID threadId = UUID.randomUUID();
            when(chatMessageRepository.findUnreadCounts(user.getId(), organizationId)).thenReturn(List.of(
                    new UnreadRow(user.getId(), channelId, null, 2, null, null, null),
                    new UnreadRow(user.getId(), channelId, threadId, 1, null, null, null)));

            // Act
            ChatUnreadResponse unread = service.getUnread(user, organizationId);

            // Assert
            assertThat(unread.channels().getFirst().mentionCount()).isZero();
            assertThat(unread.threads()).containsExactly(
                    new ChatConversationUnreadResponse(threadId, channelId, 1, 0, null, null));
            assertThat(unread.mentionTotal()).isZero();
        }

        @Test
        void noRows_isZeroWithoutConversations() {
            // Arrange
            when(chatMessageRepository.findUnreadCounts(user.getId(), organizationId)).thenReturn(List.of());

            // Act
            ChatUnreadResponse unread = service.getUnread(user, organizationId);

            // Assert
            assertThat(unread.channels()).isEmpty();
            assertThat(unread.threads()).isEmpty();
            assertThat(unread.total()).isZero();
            assertThat(unread.mentionTotal()).isZero();
        }
    }

    @Nested
    class GetUnreadByUsers {

        @Test
        void rowsOfSeveralUsers_areGroupedPerUser() {
            // Arrange
            UUID otherId = UUID.randomUUID();
            UUID absentId = UUID.randomUUID();
            UUID threadId = UUID.randomUUID();
            when(chatMessageRepository.findUnreadCountsByUsers(organizationId,
                    Set.of(user.getId(), otherId, absentId))).thenReturn(List.of(
                    new UnreadRow(user.getId(), channelId, null, 3, 0, null, null),
                    new UnreadRow(otherId, channelId, null, 2, 2, null, null),
                    new UnreadRow(user.getId(), channelId, threadId, 5, 1, null, null)));

            // Act
            Map<UUID, ChatUnreadResponse> unread = service.getUnreadByUsers(organizationId,
                    List.of(user.getId(), otherId, absentId));

            // Assert
            assertThat(unread).containsOnlyKeys(user.getId(), otherId);
            assertThat(unread.get(user.getId()).total()).isEqualTo(8);
            assertThat(unread.get(user.getId()).threads()).containsExactly(
                    new ChatConversationUnreadResponse(threadId, channelId, 5, 1, null, null));
            assertThat(unread.get(user.getId()).mentionTotal()).isEqualTo(1);
            assertThat(unread.get(otherId).total()).isEqualTo(2);
            assertThat(unread.get(otherId).mentionTotal()).isEqualTo(2);
        }

        @Test
        void noUsers_readsNothing() {
            // Act
            Map<UUID, ChatUnreadResponse> unread = service.getUnreadByUsers(organizationId, List.of());

            // Assert
            assertThat(unread).isEmpty();
            verifyNoInteractions(chatMessageRepository);
        }
    }

    private record UnreadRow(UUID getUserId, UUID getChannelId, UUID getThreadId, Integer getUnread,
                             Integer getMentionCount, UUID getLastReadMessageId, Instant getLastReadAt)
            implements ChatUnreadCountProjection {
    }
}
