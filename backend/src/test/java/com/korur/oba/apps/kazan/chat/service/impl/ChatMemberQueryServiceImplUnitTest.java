package com.korur.oba.apps.kazan.chat.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.dto.response.ChatMemberResponse;
import com.korur.oba.apps.kazan.chat.entity.ChatChannel;
import com.korur.oba.apps.kazan.chat.enums.ChatChannelVisibility;
import com.korur.oba.apps.kazan.chat.mapper.ChatMessageMapperImpl;
import com.korur.oba.apps.kazan.chat.mock_data.ChatMocks;
import com.korur.oba.apps.kazan.chat.repository.ChatChannelMemberRepository;
import com.korur.oba.apps.kazan.chat.repository.ChatMessageRepository;
import com.korur.oba.apps.kazan.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kazan.ticket.mapper.TicketEmbeddedMapperImpl;
import com.korur.oba.connect.realtime.service.RealtimeSessionRegistry;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.dto.projection.OrganizationMemberSummaryProjection;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

@ExtendWith(MockitoExtension.class)
class ChatMemberQueryServiceImplUnitTest {

    @Mock
    private ChatChannelMemberRepository chatChannelMemberRepository;
    @Mock
    private AppAccessQueryService appAccessQueryService;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatChannelQueryService chatChannelQueryService;
    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;
    @Mock
    private RealtimeSessionRegistry realtimeSessionRegistry;

    private ChatMemberQueryServiceImpl service;

    private final UUID organizationId = UUID.randomUUID();
    private final User reader = ChatMocks.user("Ada", "Lovelace");

    private final MemberRow ada = new MemberRow(reader.getId(), "ada@example.com", "Ada", "Lovelace", "OWNER");
    private final MemberRow grace = new MemberRow(UUID.randomUUID(), "grace@example.com", "Grace", "Hopper", "MEMBER");
    private final MemberRow alan = new MemberRow(UUID.randomUUID(), "turing@example.com", "Alan", "Turing", "GUEST");

    @BeforeEach
    void setUp() {
        service = new ChatMemberQueryServiceImpl(chatChannelMemberRepository, chatMessageRepository,
                chatChannelQueryService, organizationMemberQueryService,
                appAccessQueryService, realtimeSessionRegistry, new ChatMessageMapperImpl(new TicketEmbeddedMapperImpl()));
    }

    @Test
    void noSearch_listsEveryMemberOnlineFirstThenByNameWithKazanAccess() {
        // Arrange
        when(organizationMemberQueryService.listActiveMemberSummaries(any(UUID.class), any(Instant.class)))
                .thenReturn(List.of(grace, ada, alan));
        when(realtimeSessionRegistry.onlineUserIds(organizationId)).thenReturn(Set.of(grace.getUserId()));
        when(appAccessQueryService.findUserIdsWithAppAccess(organizationId, App.KAZAN,
                List.of(grace.getUserId(), ada.getUserId(), alan.getUserId())))
                .thenReturn(Set.of(grace.getUserId(), ada.getUserId()));

        // Act
        List<ChatMemberResponse> members = service.listMembers(reader, organizationId, null, null, false);

        // Assert
        assertThat(members).containsExactly(
                new ChatMemberResponse(grace.getUserId(), "Grace", "Hopper", "grace@example.com",
                        OrganizationMemberRole.MEMBER, true, true),
                new ChatMemberResponse(ada.getUserId(), "Ada", "Lovelace", "ada@example.com",
                        OrganizationMemberRole.OWNER, false, true),
                new ChatMemberResponse(alan.getUserId(), "Alan", "Turing", "turing@example.com",
                        OrganizationMemberRole.GUEST, false, false));
        verify(organizationMemberQueryService).listActiveMemberSummaries(any(UUID.class), any(Instant.class));
    }

    @Test
    void search_matchesNamesFullNameAndEmailIgnoringCaseAndPadding() {
        // Arrange
        when(organizationMemberQueryService.listActiveMemberSummaries(any(UUID.class), any(Instant.class)))
                .thenReturn(List.of(grace, ada, alan));
        when(realtimeSessionRegistry.onlineUserIds(organizationId)).thenReturn(Set.of());

        // Act and Assert
        assertThat(service.listMembers(reader, organizationId, "  HOP ", null, false))
                .extracting(ChatMemberResponse::userId).containsExactly(grace.getUserId());
        assertThat(service.listMembers(reader, organizationId, "ada love", null, false))
                .extracting(ChatMemberResponse::userId).containsExactly(ada.getUserId());
        assertThat(service.listMembers(reader, organizationId, "turing@", null, false))
                .extracting(ChatMemberResponse::userId).containsExactly(alan.getUserId());
        assertThat(service.listMembers(reader, organizationId, "a", null, false))
                .extracting(ChatMemberResponse::userId)
                .containsExactly(ada.getUserId(), alan.getUserId(), grace.getUserId());
        assertThat(service.listMembers(reader, organizationId, "nobody", null, false)).isEmpty();
    }

    @Test
    void blankSearch_listsEveryone() {
        // Arrange
        when(organizationMemberQueryService.listActiveMemberSummaries(any(UUID.class), any(Instant.class)))
                .thenReturn(List.of(grace, ada));
        when(realtimeSessionRegistry.onlineUserIds(organizationId)).thenReturn(Set.of());

        // Act
        List<ChatMemberResponse> members = service.listMembers(reader, organizationId, "   ", null, false);

        // Assert
        assertThat(members).extracting(ChatMemberResponse::userId)
                .containsExactly(ada.getUserId(), grace.getUserId());
    }

    @Test
    void memberWithoutName_sortsFirstAmongTheOfflineAndDoesNotFail() {
        // Arrange
        MemberRow nameless = new MemberRow(UUID.randomUUID(), "new@example.com", null, null, "MEMBER");
        when(organizationMemberQueryService.listActiveMemberSummaries(any(UUID.class), any(Instant.class)))
                .thenReturn(List.of(ada, nameless));
        when(realtimeSessionRegistry.onlineUserIds(organizationId)).thenReturn(Set.of());

        // Act
        List<ChatMemberResponse> members = service.listMembers(reader, organizationId, "example", null, false);

        // Assert
        assertThat(members).extracting(ChatMemberResponse::userId)
                .containsExactly(nameless.getUserId(), ada.getUserId());
    }

    @Test
    void memberWithoutName_isMappedWithNullNamesAndItsRole() {
        // Arrange
        MemberRow nameless = new MemberRow(UUID.randomUUID(), "new@example.com", null, null, "ADMIN");
        when(organizationMemberQueryService.listActiveMemberSummaries(any(UUID.class), any(Instant.class)))
                .thenReturn(List.of(nameless));
        when(realtimeSessionRegistry.onlineUserIds(organizationId)).thenReturn(Set.of(nameless.getUserId()));

        // Act
        List<ChatMemberResponse> members = service.listMembers(reader, organizationId, null, null, false);

        // Assert
        assertThat(members).containsExactly(new ChatMemberResponse(nameless.getUserId(), null, null,
                "new@example.com", OrganizationMemberRole.ADMIN, true, false));
    }

    @Test
    void privateChannel_listsOnlyItsMembers() {
        // Arrange
        ChatChannel channel = channel(ChatChannelVisibility.PRIVATE);
        when(chatChannelQueryService.verifyChannelAccess(organizationId, reader.getId(), channel.getId()))
                .thenReturn(channel);
        when(chatChannelMemberRepository.findUserIdsByChannelId(channel.getId()))
                .thenReturn(List.of(ada.getUserId(), grace.getUserId()));
        when(organizationMemberQueryService.listActiveMemberSummaries(any(UUID.class), any(Instant.class)))
                .thenReturn(List.of(grace, ada, alan));
        when(realtimeSessionRegistry.onlineUserIds(organizationId)).thenReturn(Set.of());

        // Act
        List<ChatMemberResponse> members = service.listMembers(reader, organizationId, null, channel.getId(), false);

        // Assert
        assertThat(members).extracting(ChatMemberResponse::userId)
                .containsExactly(ada.getUserId(), grace.getUserId());
    }

    @Test
    void authorsOnly_listsTheAuthorsOfTheChannel() {
        // Arrange
        ChatChannel channel = channel(ChatChannelVisibility.PUBLIC);
        when(chatChannelQueryService.verifyChannelAccess(organizationId, reader.getId(), channel.getId()))
                .thenReturn(channel);
        when(chatMessageRepository.findAuthorIdsByChannelId(channel.getId())).thenReturn(List.of(alan.getUserId()));
        when(organizationMemberQueryService.listActiveMemberSummaries(any(UUID.class), any(Instant.class)))
                .thenReturn(List.of(grace, ada, alan));
        when(realtimeSessionRegistry.onlineUserIds(organizationId)).thenReturn(Set.of());

        // Act
        List<ChatMemberResponse> members = service.listMembers(reader, organizationId, null, channel.getId(), true);

        // Assert
        assertThat(members).extracting(ChatMemberResponse::userId).containsExactly(alan.getUserId());
    }

    private ChatChannel channel(ChatChannelVisibility visibility) {
        ChatChannel channel = ChatChannel.builder()
                .name("Release")
                .organizationId(organizationId)
                .visibility(visibility)
                .build();
        channel.setId(UUID.randomUUID());
        return channel;
    }

    private record MemberRow(UUID getUserId, String getEmail, String getFirstName, String getLastName,
                             String getRole) implements OrganizationMemberSummaryProjection {
    }
}
