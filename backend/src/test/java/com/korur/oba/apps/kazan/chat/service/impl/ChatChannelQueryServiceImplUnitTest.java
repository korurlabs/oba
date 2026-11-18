package com.korur.oba.apps.kazan.chat.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.dto.projection.ChatChannelMemberCountProjection;
import com.korur.oba.apps.kazan.chat.dto.response.ChatChannelResponse;
import com.korur.oba.apps.kazan.chat.entity.ChatChannel;
import com.korur.oba.apps.kazan.chat.entity.ChatMessage;
import com.korur.oba.apps.kazan.chat.enums.ChatChannelPurpose;
import com.korur.oba.apps.kazan.chat.enums.ChatChannelVisibility;
import com.korur.oba.apps.kazan.chat.mapper.ChatChannelMapperImpl;
import com.korur.oba.apps.kazan.chat.mock_data.ChatMocks;
import com.korur.oba.apps.kazan.chat.repository.ChatChannelMemberRepository;
import com.korur.oba.apps.kazan.chat.repository.ChatChannelRepository;
import com.korur.oba.apps.kazan.chat.repository.ChatMessageRepository;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

@ExtendWith(MockitoExtension.class)
class ChatChannelQueryServiceImplUnitTest {

    @Mock
    private ChatChannelRepository chatChannelRepository;
    @Mock
    private ChatChannelMemberRepository chatChannelMemberRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;

    private ChatChannelQueryServiceImpl service;

    private final UUID organizationId = UUID.randomUUID();
    private final User user = ChatMocks.user("Ada", "Lovelace");

    @BeforeEach
    void setUp() {
        service = new ChatChannelQueryServiceImpl(chatChannelRepository, chatChannelMemberRepository,
                chatMessageRepository, new ChatChannelMapperImpl(), organizationMemberQueryService,
                new LoggingService());
    }

    private ChatChannel channel(String name, ChatChannelVisibility visibility, ChatChannelPurpose purpose) {
        ChatChannel channel = ChatChannel.builder()
                .name(name)
                .organizationId(organizationId)
                .visibility(visibility)
                .purpose(purpose)
                .build();
        channel.setId(UUID.randomUUID());
        channel.setCreatedAt(Instant.parse("2026-10-20T08:15:00Z"));
        return channel;
    }

    private void givenRole(OrganizationMemberRole role) {
        when(organizationMemberQueryService.findMember(organizationId, user.getId()))
                .thenReturn(Optional.of(OrganizationMember.builder().role(role).build()));
    }

    @Nested
    class ListChannels {

        @Test
        void member_seesPublicChannelsAndOwnPrivateOnesInCreationOrder() {
            // Arrange
            ChatChannel general = channel("General", ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL);
            ChatChannel updates = channel("Updates", ChatChannelVisibility.PUBLIC, ChatChannelPurpose.DAILY_UPDATES);
            ChatChannel joined = channel("Release", ChatChannelVisibility.PRIVATE, ChatChannelPurpose.GENERAL);
            ChatChannel hidden = channel("Board", ChatChannelVisibility.PRIVATE, ChatChannelPurpose.GENERAL);
            givenRole(OrganizationMemberRole.MEMBER);
            when(chatChannelRepository.findByOrganizationIdOrderByCreatedAtAscIdAsc(organizationId))
                    .thenReturn(List.of(general, updates, joined, hidden));
            when(chatChannelMemberRepository.findChannelIdsByUserIdAndChannelIds(user.getId(),
                    List.of(joined.getId(), hidden.getId()))).thenReturn(List.of(joined.getId()));
            when(chatChannelMemberRepository.countMembersByChannelIds(List.of(joined.getId(), hidden.getId())))
                    .thenReturn(List.of(new MemberCount(joined.getId(), 3), new MemberCount(hidden.getId(), 2)));
            when(organizationMemberQueryService.countActiveMembers(organizationId)).thenReturn(12L);

            // Act
            List<ChatChannelResponse> channels = service.listChannels(user, organizationId);

            // Assert
            assertThat(channels).extracting(ChatChannelResponse::id)
                    .containsExactly(general.getId(), updates.getId(), joined.getId());
            assertThat(channels.get(0).member()).isTrue();
            assertThat(channels.get(0).memberCount()).isEqualTo(12);
            assertThat(channels.get(1).purpose()).isEqualTo(ChatChannelPurpose.DAILY_UPDATES);
            assertThat(channels.get(2).member()).isTrue();
            assertThat(channels.get(2).memberCount()).isEqualTo(3);
        }

        @Test
        void admin_seesEveryPrivateChannelWithMemberFalseWhereNotOnTheList() {
            // Arrange
            ChatChannel hidden = channel("Board", ChatChannelVisibility.PRIVATE, ChatChannelPurpose.GENERAL);
            givenRole(OrganizationMemberRole.ADMIN);
            when(chatChannelRepository.findByOrganizationIdOrderByCreatedAtAscIdAsc(organizationId))
                    .thenReturn(List.of(hidden));
            when(chatChannelMemberRepository.findChannelIdsByUserIdAndChannelIds(user.getId(),
                    List.of(hidden.getId()))).thenReturn(List.of());
            when(chatChannelMemberRepository.countMembersByChannelIds(List.of(hidden.getId())))
                    .thenReturn(List.of(new MemberCount(hidden.getId(), 2)));

            // Act
            List<ChatChannelResponse> channels = service.listChannels(user, organizationId);

            // Assert
            assertThat(channels).hasSize(1);
            assertThat(channels.getFirst().member()).isFalse();
            assertThat(channels.getFirst().memberCount()).isEqualTo(2);
            verify(organizationMemberQueryService, never()).countActiveMembers(any());
        }

        @Test
        void onlyPublicChannels_readsNoMemberRows() {
            // Arrange
            ChatChannel general = channel("General", ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL);
            givenRole(OrganizationMemberRole.GUEST);
            when(chatChannelRepository.findByOrganizationIdOrderByCreatedAtAscIdAsc(organizationId))
                    .thenReturn(List.of(general));
            when(organizationMemberQueryService.countActiveMembers(organizationId)).thenReturn(4L);

            // Act
            List<ChatChannelResponse> channels = service.listChannels(user, organizationId);

            // Assert
            assertThat(channels).extracting(ChatChannelResponse::memberCount).containsExactly(4);
            verifyNoInteractions(chatChannelMemberRepository);
        }
    }

    @Nested
    class VerifyChannelAccess {

        @Test
        void publicChannel_isReturnedWithoutAMemberLookup() {
            // Arrange
            ChatChannel general = channel("General", ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL);
            when(chatChannelRepository.findByIdAndOrganizationId(general.getId(), organizationId))
                    .thenReturn(Optional.of(general));

            // Act
            ChatChannel found = service.verifyChannelAccess(organizationId, user.getId(), general.getId());

            // Assert
            assertThat(found).isSameAs(general);
            verify(chatChannelMemberRepository, never()).existsByChannelIdAndUserId(any(), any());
        }

        @Test
        void privateChannel_memberReads() {
            // Arrange
            ChatChannel release = channel("Release", ChatChannelVisibility.PRIVATE, ChatChannelPurpose.GENERAL);
            when(chatChannelRepository.findByIdAndOrganizationId(release.getId(), organizationId))
                    .thenReturn(Optional.of(release));
            when(chatChannelMemberRepository.existsByChannelIdAndUserId(release.getId(), user.getId()))
                    .thenReturn(true);

            // Act
            ChatChannel found = service.verifyChannelAccess(organizationId, user.getId(), release.getId());

            // Assert
            assertThat(found).isSameAs(release);
        }

        @Test
        void privateChannel_nonMemberGetsNotFound() {
            // Arrange
            ChatChannel release = channel("Release", ChatChannelVisibility.PRIVATE, ChatChannelPurpose.GENERAL);
            when(chatChannelRepository.findByIdAndOrganizationId(release.getId(), organizationId))
                    .thenReturn(Optional.of(release));
            when(chatChannelMemberRepository.existsByChannelIdAndUserId(release.getId(), user.getId()))
                    .thenReturn(false);

            // Act and Assert
            assertThatThrownBy(() -> service.verifyChannelAccess(organizationId, user.getId(), release.getId()))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode").isEqualTo(ExceptionDomainKazanError.NOT_CHAT_CHANNEL_FOUND.getCode());
        }

        @Test
        void deletedOrForeignChannel_getsNotFound() {
            // Arrange
            UUID channelId = UUID.randomUUID();
            when(chatChannelRepository.findByIdAndOrganizationId(channelId, organizationId))
                    .thenReturn(Optional.empty());

            // Act and Assert
            assertThatThrownBy(() -> service.verifyChannelAccess(organizationId, user.getId(), channelId))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode").isEqualTo(ExceptionDomainKazanError.NOT_CHAT_CHANNEL_FOUND.getCode());
        }

        @Test
        void missingChannelId_getsNotFoundWithoutALookup() {
            // Act and Assert
            assertThatThrownBy(() -> service.verifyChannelAccess(organizationId, user.getId(), null))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode").isEqualTo(ExceptionDomainKazanError.NOT_CHAT_CHANNEL_FOUND.getCode());
            verifyNoInteractions(chatChannelRepository);
        }
    }

    @Nested
    class CanRead {

        @Test
        void readableChannel_returnsTheExistsResult() {
            // Arrange
            UUID channelId = UUID.randomUUID();
            when(chatChannelRepository.existsReadableChannel(organizationId, user.getId(), channelId))
                    .thenReturn(true);

            // Act and Assert
            assertThat(service.canRead(organizationId, user.getId(), channelId)).isTrue();
        }

        @Test
        void unreadableChannel_returnsFalseWithoutThrowing() {
            // Arrange
            UUID channelId = UUID.randomUUID();
            when(chatChannelRepository.existsReadableChannel(organizationId, user.getId(), channelId))
                    .thenReturn(false);

            // Act and Assert
            assertThat(service.canRead(organizationId, user.getId(), channelId)).isFalse();
        }
    }

    @Nested
    class FindDailyUpdateChannelId {

        @Test
        void organizationWithUpdatesChannel_returnsIt() {
            // Arrange
            UUID channelId = UUID.randomUUID();
            when(chatChannelRepository.findDailyUpdateChannelId(organizationId)).thenReturn(Optional.of(channelId));

            // Act and Assert
            assertThat(service.findDailyUpdateChannelId(organizationId)).contains(channelId);
        }

        @Test
        void organizationWithoutUpdatesChannel_returnsEmpty() {
            // Arrange
            when(chatChannelRepository.findDailyUpdateChannelId(organizationId)).thenReturn(Optional.empty());

            // Act and Assert
            assertThat(service.findDailyUpdateChannelId(organizationId)).isEmpty();
            verify(chatChannelMemberRepository, never()).findChannelIdsByUserIdAndChannelIds(any(), anyCollection());
        }
    }

    @Nested
    class FindPullRequestShareMessages {

        @Test
        void sharesWithLiveEntries_returnsTheEntriesByShareIdInOneLookup() {
            // Arrange
            UUID firstShareId = UUID.randomUUID();
            UUID secondShareId = UUID.randomUUID();
            ChatMessage first = ChatMocks.pullRequestShareMessage(user, organizationId, firstShareId);
            ChatMessage second = ChatMocks.pullRequestShareMessage(user, organizationId, secondShareId);
            List<UUID> shareIds = List.of(firstShareId, secondShareId);
            when(chatMessageRepository.findAllByPullRequestShareIdIn(shareIds)).thenReturn(List.of(first, second));

            // Act
            Map<UUID, ChatMessage> result = service.findPullRequestShareMessages(shareIds);

            // Assert
            assertThat(result).containsOnlyKeys(firstShareId, secondShareId);
            assertThat(result.get(firstShareId)).isSameAs(first);
            assertThat(result.get(secondShareId)).isSameAs(second);
            verify(chatMessageRepository).findAllByPullRequestShareIdIn(shareIds);
        }

        @Test
        void shareWithoutLiveEntry_isAbsentFromTheResult() {
            // Arrange
            UUID postedShareId = UUID.randomUUID();
            UUID goneShareId = UUID.randomUUID();
            ChatMessage posted = ChatMocks.pullRequestShareMessage(user, organizationId, postedShareId);
            List<UUID> shareIds = List.of(postedShareId, goneShareId);
            when(chatMessageRepository.findAllByPullRequestShareIdIn(shareIds)).thenReturn(List.of(posted));

            // Act
            Map<UUID, ChatMessage> result = service.findPullRequestShareMessages(shareIds);

            // Assert
            assertThat(result).containsOnlyKeys(postedShareId);
        }

        @Test
        void nullOrEmptyIds_returnEmptyWithoutAQuery() {
            // Act
            Map<UUID, ChatMessage> forNull = service.findPullRequestShareMessages(null);
            Map<UUID, ChatMessage> forEmpty = service.findPullRequestShareMessages(List.of());

            // Assert
            assertThat(forNull).isEmpty();
            assertThat(forEmpty).isEmpty();
            verifyNoInteractions(chatMessageRepository);
        }
    }

    @Nested
    class GetMessageEntityById {

        @Test
        void liveMessage_isReturnedFromAnyOrganization() {
            // Arrange
            ChatMessage message = ChatMocks.message(user, UUID.randomUUID(), "Hello");
            message.setId(UUID.randomUUID());
            when(chatMessageRepository.findById(message.getId())).thenReturn(Optional.of(message));

            // Act
            ChatMessage found = service.getMessageEntityById(message.getId());

            // Assert
            assertThat(found).isSameAs(message);
        }

        @Test
        void missingMessage_getsNotFound() {
            // Arrange
            UUID messageId = UUID.randomUUID();
            when(chatMessageRepository.findById(messageId)).thenReturn(Optional.empty());

            // Act and Assert
            assertThatThrownBy(() -> service.getMessageEntityById(messageId))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode").isEqualTo(ExceptionDomainKazanError.NOT_CHAT_MESSAGE_FOUND.getCode());
        }
    }

    private record MemberCount(UUID getChannelId, Integer getMemberCount) implements ChatChannelMemberCountProjection {
    }
}
