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
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 2. Third-party
import org.hibernate.exception.ConstraintViolationException;

// 3. Java SDK
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.ChatConstant;
import com.korur.oba.apps.kazan.chat.dto.projection.ChatChannelMemberCountProjection;
import com.korur.oba.apps.kazan.chat.dto.request.ChatChannelCreateRequest;
import com.korur.oba.apps.kazan.chat.dto.request.ChatChannelMembersRequest;
import com.korur.oba.apps.kazan.chat.dto.request.ChatChannelUpdateRequest;
import com.korur.oba.apps.kazan.chat.dto.response.ChatChannelResponse;
import com.korur.oba.apps.kazan.chat.entity.ChatChannel;
import com.korur.oba.apps.kazan.chat.enums.ChatChannelPurpose;
import com.korur.oba.apps.kazan.chat.enums.ChatChannelVisibility;
import com.korur.oba.apps.kazan.chat.event.ChatChannelChangedEvent;
import com.korur.oba.apps.kazan.chat.mapper.ChatChannelMapperImpl;
import com.korur.oba.apps.kazan.chat.mock_data.ChatMocks;
import com.korur.oba.apps.kazan.chat.repository.ChatChannelMemberRepository;
import com.korur.oba.apps.kazan.chat.repository.ChatChannelRepository;
import com.korur.oba.apps.kazan.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kazan.chat.validator.ChatValidator;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.exception.ExceptionDomainAccessError;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.event.OrganizationCreatedEvent;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

@ExtendWith(MockitoExtension.class)
class ChatChannelServiceImplUnitTest {

    @Mock
    private ChatChannelRepository chatChannelRepository;
    @Mock
    private AppAccessValidator appAccessValidator;
    @Mock
    private ChatChannelMemberRepository chatChannelMemberRepository;
    @Mock
    private ChatChannelQueryService chatChannelQueryService;
    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private ChatChannelServiceImpl service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID channelId = UUID.randomUUID();
    private final User admin = ChatMocks.user("Ada", "Lovelace");
    private final User colleague = ChatMocks.user("Alan", "Turing");
    private final User outsider = ChatMocks.user("Grace", "Hopper");

    @BeforeEach
    void setUp() {
        LoggingService loggingService = new LoggingService();
        ChatValidator validator = new ChatValidator(null, null, null, null, appAccessValidator, loggingService);
        service = new ChatChannelServiceImpl(chatChannelRepository, chatChannelMemberRepository,
                chatChannelQueryService, new ChatChannelMapperImpl(), validator, organizationMemberQueryService, appAccessValidator, applicationEventPublisher, loggingService);

        lenient().when(chatChannelRepository.saveAndFlush(any(ChatChannel.class))).thenAnswer(invocation -> {
            ChatChannel saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
        lenient().when(organizationMemberQueryService.countActiveMembers(organizationId)).thenReturn(5L);
        lenient().when(organizationMemberQueryService.findActiveUserIdsByRoles(eq(organizationId), any()))
                .thenReturn(Set.of(admin.getId(), colleague.getId()));
        lenient().when(chatChannelMemberRepository.insertIfAbsent(any(), any())).thenReturn(1);
    }

    private ChatChannel stored(ChatChannelVisibility visibility, ChatChannelPurpose purpose) {
        return stored(channelId, visibility, purpose);
    }

    private ChatChannel stored(UUID id, ChatChannelVisibility visibility, ChatChannelPurpose purpose) {
        ChatChannel channel = ChatChannel.builder()
                .name("release")
                .organizationId(organizationId)
                .visibility(visibility)
                .purpose(purpose)
                .build();
        channel.setId(id);
        when(chatChannelRepository.findByIdAndOrganizationId(id, organizationId)).thenReturn(Optional.of(channel));
        return channel;
    }

    private void givenRole(User user, OrganizationMemberRole role) {
        OrganizationMember member = OrganizationMember.builder().role(role).build();
        when(organizationMemberQueryService.findMember(organizationId, user.getId())).thenReturn(Optional.of(member));
    }

    private List<ChatChannelChangedEvent> publishedEvents() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher, atLeast(0)).publishEvent(captor.capture());
        return captor.getAllValues().stream()
                .filter(ChatChannelChangedEvent.class::isInstance)
                .map(ChatChannelChangedEvent.class::cast)
                .toList();
    }

    private static DataIntegrityViolationException integrityViolation(String constraintName) {
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("duplicate key", new SQLException("duplicate key"),
                        constraintName));
    }

    private static ChatChannelCreateRequest createRequest(ChatChannelVisibility visibility,
                                                          ChatChannelPurpose purpose, List<UUID> memberIds) {
        return new ChatChannelCreateRequest("  release  ", visibility, purpose, memberIds);
    }

    private static ChatChannelUpdateRequest updateRequest(ChatChannelVisibility visibility,
                                                          ChatChannelPurpose purpose, List<UUID> memberIds) {
        return new ChatChannelUpdateRequest("renamed", visibility, purpose, memberIds);
    }

    @Nested
    class CreateChannel {

        @Test
        void publicChannel_savesTrimmedNameAndPublishesCreatedWithoutMemberRows() {
            // Arrange
            ChatChannelCreateRequest request = createRequest(ChatChannelVisibility.PUBLIC,
                    ChatChannelPurpose.GENERAL, null);

            // Act
            ChatChannelResponse response = service.createChannel(admin, organizationId, request);

            // Assert
            ArgumentCaptor<ChatChannel> saved = ArgumentCaptor.forClass(ChatChannel.class);
            verify(chatChannelRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getName()).isEqualTo("release");
            assertThat(saved.getValue().getOrganizationId()).isEqualTo(organizationId);
            assertThat(saved.getValue().getCreatedBy()).isEqualTo(admin.getId());
            assertThat(response.member()).isTrue();
            assertThat(response.memberCount()).isEqualTo(5);
            verifyNoInteractions(chatChannelMemberRepository);
            List<ChatChannelChangedEvent> events = publishedEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0).type()).isEqualTo(ChatChannelChangedEvent.Type.CREATED);
            assertThat(events.get(0).affectedUserIds()).isEmpty();
        }

        @Test
        void privateChannel_putsTheAdminAndTheNamedUsersOnTheList() {
            // Arrange
            ChatChannelCreateRequest request = createRequest(ChatChannelVisibility.PRIVATE,
                    ChatChannelPurpose.GENERAL, List.of(colleague.getId(), admin.getId()));
            when(chatChannelMemberRepository.existsByChannelIdAndUserId(any(), eq(admin.getId()))).thenReturn(true);
            ChatChannelMemberCountProjection count = mock(ChatChannelMemberCountProjection.class);
            when(count.getMemberCount()).thenReturn(2);
            when(chatChannelMemberRepository.countMembersByChannelIds(any())).thenReturn(List.of(count));

            // Act
            ChatChannelResponse response = service.createChannel(admin, organizationId, request);

            // Assert
            verify(chatChannelMemberRepository).insertIfAbsent(response.id(), admin.getId());
            verify(chatChannelMemberRepository).insertIfAbsent(response.id(), colleague.getId());
            verify(chatChannelMemberRepository, times(2)).insertIfAbsent(any(), any());
            assertThat(response.member()).isTrue();
            assertThat(response.memberCount()).isEqualTo(2);
        }

        @Test
        void membersOnAPublicChannel_areRefusedWithVal051() {
            // Arrange
            ChatChannelCreateRequest request = createRequest(ChatChannelVisibility.PUBLIC,
                    ChatChannelPurpose.GENERAL, List.of(colleague.getId()));

            // Act and Assert
            assertThatThrownBy(() -> service.createChannel(admin, organizationId, request))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_CHAT_CHANNEL_MEMBERS_NOT_EXPECTED.getCode());
            verify(chatChannelRepository, never()).saveAndFlush(any());
        }

        @Test
        void privateDailyUpdatesChannel_isRefusedWithVal053() {
            // Arrange
            ChatChannelCreateRequest request = createRequest(ChatChannelVisibility.PRIVATE,
                    ChatChannelPurpose.DAILY_UPDATES, null);

            // Act and Assert
            assertThatThrownBy(() -> service.createChannel(admin, organizationId, request))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_CHAT_CHANNEL_PURPOSE_PRIVATE.getCode());
            verify(chatChannelRepository, never()).saveAndFlush(any());
        }

        @Test
        void privatePullRequestsChannel_isRefusedWithVal053() {
            // Arrange
            ChatChannelCreateRequest request = createRequest(ChatChannelVisibility.PRIVATE,
                    ChatChannelPurpose.PULL_REQUESTS, null);

            // Act and Assert
            assertThatThrownBy(() -> service.createChannel(admin, organizationId, request))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_CHAT_CHANNEL_PURPOSE_PRIVATE.getCode());
            verify(chatChannelRepository, never()).saveAndFlush(any());
        }

        @Test
        void pullRequestsPurposeHeldByAnotherChannel_isRefusedWithSta016AndSavesNothing() {
            // Arrange
            when(chatChannelRepository.findChannelIdByPurpose(organizationId, ChatChannelPurpose.PULL_REQUESTS.name()))
                    .thenReturn(Optional.of(UUID.randomUUID()));

            // Act and Assert
            assertThatThrownBy(() -> service.createChannel(admin, organizationId,
                    createRequest(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.PULL_REQUESTS, null)))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.STA_CHAT_PURPOSE_TAKEN.getCode());
            verify(chatChannelRepository, never()).saveAndFlush(any());
            verifyNoInteractions(applicationEventPublisher);
        }

        @Test
        void pullRequestsPurposeWithNoLiveHolder_createsTheChannel() {
            // Arrange
            when(chatChannelRepository.findChannelIdByPurpose(organizationId, ChatChannelPurpose.PULL_REQUESTS.name()))
                    .thenReturn(Optional.empty());

            // Act
            ChatChannelResponse response = service.createChannel(admin, organizationId,
                    createRequest(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.PULL_REQUESTS, null));

            // Assert
            assertThat(response.purpose()).isEqualTo(ChatChannelPurpose.PULL_REQUESTS);
            verify(chatChannelRepository, times(1)).saveAndFlush(any());
        }

        @Test
        void pullRequestsPurposeIndexRefusesTheWriteAfterAConcurrentCreate_isRefusedWithSta016() {
            // Arrange
            when(chatChannelRepository.findChannelIdByPurpose(organizationId, ChatChannelPurpose.PULL_REQUESTS.name()))
                    .thenReturn(Optional.empty());
            when(chatChannelRepository.saveAndFlush(any(ChatChannel.class)))
                    .thenThrow(integrityViolation(ChatConstant.CHANNEL_PURPOSE_UNIQUE_INDEX));

            // Act and Assert
            assertThatThrownBy(() -> service.createChannel(admin, organizationId,
                    createRequest(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.PULL_REQUESTS, null)))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.STA_CHAT_PURPOSE_TAKEN.getCode());
            verifyNoInteractions(applicationEventPublisher);
        }

        @Test
        void memberWhoLeftTheOrganization_isRefusedWithVal044() {
            // Arrange
            ChatChannelCreateRequest request = createRequest(ChatChannelVisibility.PRIVATE,
                    ChatChannelPurpose.GENERAL, List.of(outsider.getId()));

            // Act and Assert
            assertThatThrownBy(() -> service.createChannel(admin, organizationId, request))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_CHAT_MENTION_NOT_MEMBER.getCode());
            verify(chatChannelRepository, never()).saveAndFlush(any());
        }

        @Test
        void dailyUpdatesPurposeHeldByAnotherChannel_isRefusedWithSta016AndSavesNothing() {
            // Arrange
            when(chatChannelRepository.findChannelIdByPurpose(organizationId, ChatChannelPurpose.DAILY_UPDATES.name()))
                    .thenReturn(Optional.of(UUID.randomUUID()));

            // Act and Assert
            assertThatThrownBy(() -> service.createChannel(admin, organizationId,
                    createRequest(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.DAILY_UPDATES, null)))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.STA_CHAT_PURPOSE_TAKEN.getCode());
            verify(chatChannelRepository, never()).saveAndFlush(any());
            verifyNoInteractions(applicationEventPublisher);
        }

        @Test
        void dailyUpdatesPurposeWithNoLiveHolder_createsTheChannelAndPublishesOnlyCreated() {
            // Arrange: a deleted holder is not returned by the live-channel lookup
            when(chatChannelRepository.findChannelIdByPurpose(organizationId, ChatChannelPurpose.DAILY_UPDATES.name())).thenReturn(Optional.empty());

            // Act
            ChatChannelResponse response = service.createChannel(admin, organizationId,
                    createRequest(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.DAILY_UPDATES, null));

            // Assert
            assertThat(response.purpose()).isEqualTo(ChatChannelPurpose.DAILY_UPDATES);
            verify(chatChannelRepository, times(1)).saveAndFlush(any());
            assertThat(publishedEvents()).extracting(ChatChannelChangedEvent::type)
                    .containsExactly(ChatChannelChangedEvent.Type.CREATED);
        }

        @Test
        void generalPurpose_looksUpNoDailyUpdatesHolder() {
            // Act
            service.createChannel(admin, organizationId,
                    createRequest(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL, null));

            // Assert
            verify(chatChannelRepository, never()).findChannelIdByPurpose(any(), any());
        }

        @Test
        void purposeIndexRefusesTheWriteAfterAConcurrentCreate_isRefusedWithSta016() {
            // Arrange
            when(chatChannelRepository.findChannelIdByPurpose(organizationId, ChatChannelPurpose.DAILY_UPDATES.name())).thenReturn(Optional.empty());
            when(chatChannelRepository.saveAndFlush(any(ChatChannel.class)))
                    .thenThrow(integrityViolation(ChatConstant.CHANNEL_PURPOSE_UNIQUE_INDEX));

            // Act and Assert
            assertThatThrownBy(() -> service.createChannel(admin, organizationId,
                    createRequest(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.DAILY_UPDATES, null)))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.STA_CHAT_PURPOSE_TAKEN.getCode());
            verifyNoInteractions(applicationEventPublisher);
        }

        @Test
        void otherIntegrityFailure_isRethrownUnchanged() {
            // Arrange
            DataIntegrityViolationException failure = integrityViolation("chat_channels_organization_fk");
            when(chatChannelRepository.saveAndFlush(any(ChatChannel.class))).thenThrow(failure);

            // Act and Assert
            assertThatThrownBy(() -> service.createChannel(admin, organizationId,
                    createRequest(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL, null)))
                    .isSameAs(failure);
        }
    }

    @Nested
    class UpdateChannel {

        @Test
        void unknownChannel_isNotFound() {
            // Arrange
            when(chatChannelRepository.findByIdAndOrganizationId(channelId, organizationId))
                    .thenReturn(Optional.empty());

            // Act and Assert
            assertThatThrownBy(() -> service.updateChannel(admin, organizationId, channelId,
                    updateRequest(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL, null)))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.NOT_CHAT_CHANNEL_FOUND.getCode());
        }

        @Test
        void nameOnly_publishesUpdatedAndTouchesNoMemberRow() {
            // Arrange
            ChatChannel channel = stored(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL);

            // Act
            ChatChannelResponse response = service.updateChannel(admin, organizationId, channelId,
                    updateRequest(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL, null));

            // Assert
            assertThat(channel.getName()).isEqualTo("renamed");
            assertThat(response.name()).isEqualTo("renamed");
            verifyNoInteractions(chatChannelMemberRepository);
            assertThat(publishedEvents()).extracting(ChatChannelChangedEvent::type)
                    .containsExactly(ChatChannelChangedEvent.Type.UPDATED);
        }

        @Test
        void publicToPrivate_putsTheAdminAndNamedUsersOnTheListAndPublishesVisibilityChanged() {
            // Arrange
            stored(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL);

            // Act
            service.updateChannel(admin, organizationId, channelId,
                    updateRequest(ChatChannelVisibility.PRIVATE, ChatChannelPurpose.GENERAL,
                            List.of(colleague.getId())));

            // Assert
            verify(chatChannelMemberRepository).insertIfAbsent(channelId, admin.getId());
            verify(chatChannelMemberRepository).insertIfAbsent(channelId, colleague.getId());
            List<ChatChannelChangedEvent> events = publishedEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0).type()).isEqualTo(ChatChannelChangedEvent.Type.VISIBILITY_CHANGED);
            assertThat(events.get(0).visibility()).isEqualTo(ChatChannelVisibility.PRIVATE);
            assertThat(events.get(0).affectedUserIds()).containsExactlyInAnyOrder(admin.getId(), colleague.getId());
        }

        @Test
        void publicToPrivateWithoutNamedUsers_leavesOnlyTheAdminOnTheList() {
            // Arrange
            stored(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL);

            // Act
            service.updateChannel(admin, organizationId, channelId,
                    updateRequest(ChatChannelVisibility.PRIVATE, ChatChannelPurpose.GENERAL, null));

            // Assert
            verify(chatChannelMemberRepository).insertIfAbsent(channelId, admin.getId());
            verify(chatChannelMemberRepository, times(1)).insertIfAbsent(any(), any());
        }

        @Test
        void privateToPublic_deletesTheListAndPublishesTheFormerMembers() {
            // Arrange
            stored(ChatChannelVisibility.PRIVATE, ChatChannelPurpose.GENERAL);
            when(chatChannelMemberRepository.findUserIdsByChannelId(channelId))
                    .thenReturn(List.of(admin.getId(), colleague.getId()));

            // Act
            service.updateChannel(admin, organizationId, channelId,
                    updateRequest(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL, null));

            // Assert
            verify(chatChannelMemberRepository).deleteAllByChannelId(channelId);
            List<ChatChannelChangedEvent> events = publishedEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0).type()).isEqualTo(ChatChannelChangedEvent.Type.VISIBILITY_CHANGED);
            assertThat(events.get(0).visibility()).isEqualTo(ChatChannelVisibility.PUBLIC);
            assertThat(events.get(0).affectedUserIds()).containsExactly(admin.getId(), colleague.getId());
        }

        @Test
        void membersOnAChannelThatStaysPrivate_areRefusedWithVal051() {
            // Arrange
            stored(ChatChannelVisibility.PRIVATE, ChatChannelPurpose.GENERAL);

            // Act and Assert
            assertThatThrownBy(() -> service.updateChannel(admin, organizationId, channelId,
                    updateRequest(ChatChannelVisibility.PRIVATE, ChatChannelPurpose.GENERAL,
                            List.of(colleague.getId()))))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_CHAT_CHANNEL_MEMBERS_NOT_EXPECTED.getCode());
            verify(chatChannelRepository, never()).saveAndFlush(any());
        }

        @Test
        void membersOnAChannelThatStaysPublic_areRefusedWithVal051() {
            // Arrange
            stored(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL);

            // Act and Assert
            assertThatThrownBy(() -> service.updateChannel(admin, organizationId, channelId,
                    updateRequest(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL,
                            List.of(colleague.getId()))))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_CHAT_CHANNEL_MEMBERS_NOT_EXPECTED.getCode());
        }

        @Test
        void dailyUpdatesChannelMadePrivate_isRefusedWithVal053() {
            // Arrange
            stored(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.DAILY_UPDATES);

            // Act and Assert
            assertThatThrownBy(() -> service.updateChannel(admin, organizationId, channelId,
                    updateRequest(ChatChannelVisibility.PRIVATE, ChatChannelPurpose.DAILY_UPDATES, null)))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_CHAT_CHANNEL_PURPOSE_PRIVATE.getCode());
            verify(chatChannelRepository, never()).saveAndFlush(any());
        }

        @Test
        void dailyUpdatesChannelMadePrivateWhileMovingThePurpose_succeeds() {
            // Arrange
            stored(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.DAILY_UPDATES);

            // Act and Assert
            assertThatCode(() -> service.updateChannel(admin, organizationId, channelId,
                    updateRequest(ChatChannelVisibility.PRIVATE, ChatChannelPurpose.GENERAL, null)))
                    .doesNotThrowAnyException();
            verify(chatChannelRepository, never()).findChannelIdByPurpose(any(), any());
        }

        @Test
        void pullRequestsChannelMadePrivate_isRefusedWithVal053() {
            // Arrange
            stored(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.PULL_REQUESTS);

            // Act and Assert
            assertThatThrownBy(() -> service.updateChannel(admin, organizationId, channelId,
                    updateRequest(ChatChannelVisibility.PRIVATE, ChatChannelPurpose.PULL_REQUESTS, null)))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_CHAT_CHANNEL_PURPOSE_PRIVATE.getCode());
            verify(chatChannelRepository, never()).saveAndFlush(any());
        }

        @Test
        void pullRequestsPurposeHeldByAnotherChannel_isRefusedWithSta016AndChangesNothing() {
            // Arrange
            ChatChannel channel = stored(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL);
            when(chatChannelRepository.findChannelIdByPurpose(organizationId, ChatChannelPurpose.PULL_REQUESTS.name()))
                    .thenReturn(Optional.of(UUID.randomUUID()));

            // Act and Assert
            assertThatThrownBy(() -> service.updateChannel(admin, organizationId, channelId,
                    updateRequest(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.PULL_REQUESTS, null)))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.STA_CHAT_PURPOSE_TAKEN.getCode());
            assertThat(channel.getPurpose()).isEqualTo(ChatChannelPurpose.GENERAL);
            verify(chatChannelRepository, never()).saveAndFlush(any());
            verifyNoInteractions(applicationEventPublisher);
        }

        @Test
        void pullRequestsHolderRenamedKeepingThePurpose_succeedsWithoutALookup() {
            // Arrange
            ChatChannel channel = stored(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.PULL_REQUESTS);

            // Act
            service.updateChannel(admin, organizationId, channelId,
                    updateRequest(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.PULL_REQUESTS, null));

            // Assert
            assertThat(channel.getPurpose()).isEqualTo(ChatChannelPurpose.PULL_REQUESTS);
            verify(chatChannelRepository, never()).findChannelIdByPurpose(any(), any());
        }

        @Test
        void pullRequestsPurposeWithNoLiveHolder_changesThePurpose() {
            // Arrange
            ChatChannel channel = stored(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL);
            when(chatChannelRepository.findChannelIdByPurpose(organizationId, ChatChannelPurpose.PULL_REQUESTS.name()))
                    .thenReturn(Optional.empty());

            // Act
            service.updateChannel(admin, organizationId, channelId,
                    updateRequest(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.PULL_REQUESTS, null));

            // Assert
            assertThat(channel.getPurpose()).isEqualTo(ChatChannelPurpose.PULL_REQUESTS);
            verify(chatChannelRepository).saveAndFlush(channel);
        }

        @Test
        void pullRequestsPurposeIndexRefusesTheWriteAfterAConcurrentChange_isRefusedWithSta016() {
            // Arrange
            stored(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL);
            when(chatChannelRepository.findChannelIdByPurpose(organizationId, ChatChannelPurpose.PULL_REQUESTS.name()))
                    .thenReturn(Optional.empty());
            when(chatChannelRepository.saveAndFlush(any(ChatChannel.class)))
                    .thenThrow(integrityViolation(ChatConstant.CHANNEL_PURPOSE_UNIQUE_INDEX));

            // Act and Assert
            assertThatThrownBy(() -> service.updateChannel(admin, organizationId, channelId,
                    updateRequest(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.PULL_REQUESTS, null)))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.STA_CHAT_PURPOSE_TAKEN.getCode());
            verifyNoInteractions(applicationEventPublisher);
        }

        @Test
        void nonActiveMemberOnTheNewList_isRefusedWithVal044() {
            // Arrange
            stored(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL);

            // Act and Assert
            assertThatThrownBy(() -> service.updateChannel(admin, organizationId, channelId,
                    updateRequest(ChatChannelVisibility.PRIVATE, ChatChannelPurpose.GENERAL,
                            List.of(outsider.getId()))))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_CHAT_MENTION_NOT_MEMBER.getCode());
            verify(chatChannelMemberRepository, never()).insertIfAbsent(any(), any());
        }

        @Test
        void dailyUpdatesPurposeHeldByAnotherChannel_isRefusedWithSta016AndChangesNothing() {
            // Arrange
            ChatChannel channel = stored(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL);
            when(chatChannelRepository.findChannelIdByPurpose(organizationId, ChatChannelPurpose.DAILY_UPDATES.name()))
                    .thenReturn(Optional.of(UUID.randomUUID()));

            // Act and Assert
            assertThatThrownBy(() -> service.updateChannel(admin, organizationId, channelId,
                    updateRequest(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.DAILY_UPDATES, null)))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.STA_CHAT_PURPOSE_TAKEN.getCode());
            assertThat(channel.getPurpose()).isEqualTo(ChatChannelPurpose.GENERAL);
            verify(chatChannelRepository, never()).saveAndFlush(any());
            verifyNoInteractions(applicationEventPublisher);
        }

        @Test
        void holderRenamedKeepingThePurpose_succeedsWithoutALookup() {
            // Arrange
            ChatChannel channel = stored(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.DAILY_UPDATES);

            // Act
            service.updateChannel(admin, organizationId, channelId,
                    updateRequest(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.DAILY_UPDATES, null));

            // Assert
            assertThat(channel.getName()).isEqualTo("renamed");
            assertThat(channel.getPurpose()).isEqualTo(ChatChannelPurpose.DAILY_UPDATES);
            verify(chatChannelRepository, never()).findChannelIdByPurpose(any(), any());
            assertThat(publishedEvents()).extracting(ChatChannelChangedEvent::type)
                    .containsExactly(ChatChannelChangedEvent.Type.UPDATED);
        }

        @Test
        void holderSetToGeneralThenAnotherChannelToDailyUpdates_succeeds() {
            // Arrange: the lookup reports the holder only while it keeps the purpose
            ChatChannel holder = stored(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.DAILY_UPDATES);
            UUID otherId = UUID.randomUUID();
            ChatChannel other = stored(otherId, ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL);
            when(chatChannelRepository.findChannelIdByPurpose(organizationId, ChatChannelPurpose.DAILY_UPDATES.name())).thenAnswer(invocation ->
                    holder.getPurpose() == ChatChannelPurpose.DAILY_UPDATES
                            ? Optional.of(channelId)
                            : Optional.empty());

            // Act
            service.updateChannel(admin, organizationId, channelId,
                    updateRequest(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL, null));
            service.updateChannel(admin, organizationId, otherId,
                    updateRequest(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.DAILY_UPDATES, null));

            // Assert
            assertThat(holder.getPurpose()).isEqualTo(ChatChannelPurpose.GENERAL);
            assertThat(other.getPurpose()).isEqualTo(ChatChannelPurpose.DAILY_UPDATES);
        }

        @Test
        void dailyUpdatesPurposeWithOnlyADeletedHolder_succeeds() {
            // Arrange: the live-channel lookup leaves a deleted holder out
            ChatChannel channel = stored(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL);
            when(chatChannelRepository.findChannelIdByPurpose(organizationId, ChatChannelPurpose.DAILY_UPDATES.name())).thenReturn(Optional.empty());

            // Act
            service.updateChannel(admin, organizationId, channelId,
                    updateRequest(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.DAILY_UPDATES, null));

            // Assert
            assertThat(channel.getPurpose()).isEqualTo(ChatChannelPurpose.DAILY_UPDATES);
            verify(chatChannelRepository).saveAndFlush(channel);
        }

        @Test
        void purposeIndexRefusesTheWriteAfterAConcurrentChange_isRefusedWithSta016() {
            // Arrange
            stored(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL);
            when(chatChannelRepository.findChannelIdByPurpose(organizationId, ChatChannelPurpose.DAILY_UPDATES.name())).thenReturn(Optional.empty());
            when(chatChannelRepository.saveAndFlush(any(ChatChannel.class)))
                    .thenThrow(integrityViolation(ChatConstant.CHANNEL_PURPOSE_UNIQUE_INDEX));

            // Act and Assert
            assertThatThrownBy(() -> service.updateChannel(admin, organizationId, channelId,
                    updateRequest(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.DAILY_UPDATES, null)))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.STA_CHAT_PURPOSE_TAKEN.getCode());
            verifyNoInteractions(applicationEventPublisher);
        }
    }

    @Nested
    class DeleteChannel {

        @Test
        void admin_deletesTheChannelAndPublishesDeleted() {
            // Arrange
            ChatChannel channel = stored(ChatChannelVisibility.PRIVATE, ChatChannelPurpose.GENERAL);

            // Act
            service.deleteChannel(admin, organizationId, channelId);

            // Assert
            verify(chatChannelRepository).delete(channel);
            List<ChatChannelChangedEvent> events = publishedEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0).type()).isEqualTo(ChatChannelChangedEvent.Type.DELETED);
            assertThat(events.get(0).channelId()).isEqualTo(channelId);
            assertThat(events.get(0).affectedUserIds()).isEmpty();
        }

        @Test
        void channelOfAnotherOrganization_isNotFoundAndDeletesNothing() {
            // Arrange
            when(chatChannelRepository.findByIdAndOrganizationId(channelId, organizationId))
                    .thenReturn(Optional.empty());

            // Act and Assert
            assertThatThrownBy(() -> service.deleteChannel(admin, organizationId, channelId))
                    .isInstanceOf(NotFoundException.class);
            verify(chatChannelRepository, never()).delete(any(ChatChannel.class));
        }
    }

    @Nested
    class AddMembers {

        @Test
        void publicChannel_isRefusedWithVal052() {
            // Arrange
            stored(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL);

            // Act and Assert
            assertThatThrownBy(() -> service.addMembers(admin, organizationId, channelId,
                    new ChatChannelMembersRequest(List.of(colleague.getId()))))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_CHAT_CHANNEL_NOT_PRIVATE.getCode());
            verify(chatChannelMemberRepository, never()).insertIfAbsent(any(), any());
        }

        @Test
        void privateChannel_publishesOnlyTheUsersNewlyAdded() {
            // Arrange
            stored(ChatChannelVisibility.PRIVATE, ChatChannelPurpose.GENERAL);
            when(chatChannelMemberRepository.insertIfAbsent(channelId, admin.getId())).thenReturn(0);
            when(chatChannelMemberRepository.insertIfAbsent(channelId, colleague.getId())).thenReturn(1);

            // Act
            service.addMembers(admin, organizationId, channelId,
                    new ChatChannelMembersRequest(List.of(admin.getId(), colleague.getId())));

            // Assert
            List<ChatChannelChangedEvent> events = publishedEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0).type()).isEqualTo(ChatChannelChangedEvent.Type.MEMBERS_ADDED);
            assertThat(events.get(0).affectedUserIds()).containsExactly(colleague.getId());
        }

        @Test
        void usersAlreadyOnTheList_publishNothing() {
            // Arrange
            stored(ChatChannelVisibility.PRIVATE, ChatChannelPurpose.GENERAL);
            when(chatChannelMemberRepository.insertIfAbsent(any(), any())).thenReturn(0);

            // Act
            service.addMembers(admin, organizationId, channelId,
                    new ChatChannelMembersRequest(List.of(colleague.getId())));

            // Assert
            verifyNoInteractions(applicationEventPublisher);
        }

        @Test
        void userOutsideTheOrganization_isRefusedWithVal044() {
            // Arrange
            stored(ChatChannelVisibility.PRIVATE, ChatChannelPurpose.GENERAL);

            // Act and Assert
            assertThatThrownBy(() -> service.addMembers(admin, organizationId, channelId,
                    new ChatChannelMembersRequest(List.of(outsider.getId()))))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_CHAT_MENTION_NOT_MEMBER.getCode());
            verify(chatChannelMemberRepository, never()).insertIfAbsent(any(), any());
        }

        @Test
        void colleagueWithoutKazan_isRefusedWithApp002AndAddsNobody() {
            // Arrange
            stored(ChatChannelVisibility.PRIVATE, ChatChannelPurpose.GENERAL);
            doThrow(new ValidationException(ExceptionDomainAccessError.VAL_MEMBER_WITHOUT_APP_ACCESS))
                    .when(appAccessValidator)
                    .ensureUsersHaveAppAccess(organizationId, App.KAZAN, List.of(colleague.getId()));

            // Act and Assert
            assertThatThrownBy(() -> service.addMembers(admin, organizationId, channelId,
                    new ChatChannelMembersRequest(List.of(colleague.getId()))))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainAccessError.VAL_MEMBER_WITHOUT_APP_ACCESS.getCode());
            verify(chatChannelMemberRepository, never()).insertIfAbsent(any(), any());
            verifyNoInteractions(applicationEventPublisher);
        }
    }

    @Nested
    class RemoveMember {

        @Test
        void adminRemovingAnotherUser_publishesMembersRemoved() {
            // Arrange
            givenRole(admin, OrganizationMemberRole.ADMIN);
            stored(ChatChannelVisibility.PRIVATE, ChatChannelPurpose.GENERAL);
            when(chatChannelMemberRepository.deleteByChannelIdAndUserId(channelId, colleague.getId()))
                    .thenReturn(1);

            // Act
            service.removeMember(admin, organizationId, channelId, colleague.getId());

            // Assert
            List<ChatChannelChangedEvent> events = publishedEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0).type()).isEqualTo(ChatChannelChangedEvent.Type.MEMBERS_REMOVED);
            assertThat(events.get(0).affectedUserIds()).containsExactly(colleague.getId());
        }

        @Test
        void ownerRemovingAnotherUser_isAllowed() {
            // Arrange
            givenRole(admin, OrganizationMemberRole.OWNER);
            stored(ChatChannelVisibility.PRIVATE, ChatChannelPurpose.GENERAL);
            when(chatChannelMemberRepository.deleteByChannelIdAndUserId(channelId, colleague.getId()))
                    .thenReturn(1);

            // Act
            service.removeMember(admin, organizationId, channelId, colleague.getId());

            // Assert
            verify(chatChannelMemberRepository).deleteByChannelIdAndUserId(channelId, colleague.getId());
        }

        @Test
        void memberRemovingAnotherUser_isRefusedWithSec011() {
            // Arrange
            givenRole(colleague, OrganizationMemberRole.MEMBER);

            // Act and Assert
            assertThatThrownBy(() -> service.removeMember(colleague, organizationId, channelId, admin.getId()))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.SEC_CANNOT_REMOVE_CHANNEL_MEMBER.getCode());
            verifyNoInteractions(chatChannelMemberRepository);
            verify(chatChannelRepository, never()).findByIdAndOrganizationId(any(), any());
        }

        @Test
        void selfLeave_needsOnlyReadAccessToTheChannel() {
            // Arrange
            ChatChannel channel = ChatChannel.builder().name("release").organizationId(organizationId)
                    .visibility(ChatChannelVisibility.PRIVATE).purpose(ChatChannelPurpose.GENERAL).build();
            channel.setId(channelId);
            when(chatChannelQueryService.verifyChannelAccess(organizationId, colleague.getId(), channelId))
                    .thenReturn(channel);
            when(chatChannelMemberRepository.deleteByChannelIdAndUserId(channelId, colleague.getId()))
                    .thenReturn(1);

            // Act
            service.removeMember(colleague, organizationId, channelId, colleague.getId());

            // Assert
            verify(organizationMemberQueryService, never()).findMember(any(), any());
            List<ChatChannelChangedEvent> events = publishedEvents();
            assertThat(events).extracting(ChatChannelChangedEvent::type)
                    .containsExactly(ChatChannelChangedEvent.Type.MEMBERS_REMOVED);
            assertThat(events.get(0).affectedUserIds()).containsExactly(colleague.getId());
        }

        @Test
        void selfLeaveOfAPrivateChannelTheUserIsNotOn_isNotFound() {
            // Arrange
            when(chatChannelQueryService.verifyChannelAccess(organizationId, outsider.getId(), channelId))
                    .thenThrow(new NotFoundException(ExceptionDomainKazanError.NOT_CHAT_CHANNEL_FOUND));

            // Act and Assert
            assertThatThrownBy(() -> service.removeMember(outsider, organizationId, channelId, outsider.getId()))
                    .isInstanceOf(NotFoundException.class);
            verifyNoInteractions(chatChannelMemberRepository);
        }

        @Test
        void publicChannel_isRefusedWithVal052() {
            // Arrange
            givenRole(admin, OrganizationMemberRole.ADMIN);
            stored(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL);

            // Act and Assert
            assertThatThrownBy(() -> service.removeMember(admin, organizationId, channelId, colleague.getId()))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_CHAT_CHANNEL_NOT_PRIVATE.getCode());
            verify(chatChannelMemberRepository, never()).deleteByChannelIdAndUserId(any(), any());
        }

        @Test
        void userNotOnTheList_publishesNothing() {
            // Arrange
            givenRole(admin, OrganizationMemberRole.ADMIN);
            stored(ChatChannelVisibility.PRIVATE, ChatChannelPurpose.GENERAL);
            when(chatChannelMemberRepository.deleteByChannelIdAndUserId(channelId, colleague.getId()))
                    .thenReturn(0);

            // Act
            service.removeMember(admin, organizationId, channelId, colleague.getId());

            // Assert
            verifyNoInteractions(applicationEventPublisher);
        }
    }

    @Nested
    class OnOrganizationCreated {

        @Test
        void newOrganization_createsTheInitialChannels() {
            // Arrange
            when(chatChannelRepository.insertInitialChannelsIfAbsent(organizationId)).thenReturn(3);

            // Act
            service.onOrganizationCreated(new OrganizationCreatedEvent(organizationId));

            // Assert
            verify(chatChannelRepository).insertInitialChannelsIfAbsent(organizationId);
        }

        @Test
        void eventHandledTwice_isIdempotentAndCreatesNothingTheSecondTime() {
            // Arrange
            when(chatChannelRepository.insertInitialChannelsIfAbsent(organizationId)).thenReturn(3, 0);
            OrganizationCreatedEvent event = new OrganizationCreatedEvent(organizationId);

            // Act and Assert
            assertThatCode(() -> {
                service.onOrganizationCreated(event);
                service.onOrganizationCreated(event);
            }).doesNotThrowAnyException();
            verify(chatChannelRepository, times(2)).insertInitialChannelsIfAbsent(organizationId);
            verifyNoInteractions(applicationEventPublisher);
        }
    }

    @Nested
    class ChannelSlugs {

        private ChatChannel savedChannel() {
            ArgumentCaptor<ChatChannel> saved = ArgumentCaptor.forClass(ChatChannel.class);
            verify(chatChannelRepository).saveAndFlush(saved.capture());
            return saved.getValue();
        }

        @Test
        void create_freeBase_savesBaseSlugOfTheName() {
            // Act
            service.createChannel(admin, organizationId,
                    createRequest(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL, null));

            // Assert
            assertThat(savedChannel().getSlug()).isEqualTo("release");
        }

        @Test
        void create_baseTaken_savesFirstFreeSuffix() {
            // Arrange
            when(chatChannelRepository.existsByOrganizationIdAndSlug(organizationId, "release")).thenReturn(true);
            when(chatChannelRepository.existsByOrganizationIdAndSlug(organizationId, "release-1")).thenReturn(true);

            // Act
            service.createChannel(admin, organizationId,
                    createRequest(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL, null));

            // Assert
            assertThat(savedChannel().getSlug()).isEqualTo("release-2");
        }

        @Test
        void create_turkishName_isTransliterated() {
            // Act
            service.createChannel(admin, organizationId, new ChatChannelCreateRequest("Çalışma Odası",
                    ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL, null));

            // Assert
            assertThat(savedChannel().getSlug()).isEqualTo("calisma-odasi");
        }

        @Test
        void create_nameWithoutLettersOrDigits_usesFallbackSlug() {
            // Act
            service.createChannel(admin, organizationId, new ChatChannelCreateRequest("🚀",
                    ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL, null));

            // Assert
            assertThat(savedChannel().getSlug()).isEqualTo(ChatConstant.CHANNEL_SLUG_FALLBACK);
        }

        @Test
        void create_slugIndexRefusesTheWrite_isRefusedWithSta017NamingTheChannel() {
            // Arrange
            when(chatChannelRepository.saveAndFlush(any(ChatChannel.class)))
                    .thenThrow(integrityViolation(ChatConstant.CHANNEL_SLUG_UNIQUE_INDEX));

            // Act and Assert
            assertThatThrownBy(() -> service.createChannel(admin, organizationId,
                    createRequest(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL, null)))
                    .isInstanceOfSatisfying(ConflictException.class, exception -> {
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ExceptionDomainKazanError.STA_SLUG_TAKEN.getCode());
                        assertThat(exception.getErrorValues()).containsEntry(ChatConstant.FIELD_NAME, "release");
                    });
            verifyNoInteractions(applicationEventPublisher);
        }

        @Test
        void update_renameToNewBase_movesTheSlugIgnoringTheChannelsOwnRow() {
            // Arrange
            ChatChannel channel = stored(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL);
            channel.setSlug("release");
            when(chatChannelRepository.existsByOrganizationIdAndSlugAndIdNot(organizationId, "renamed", channelId))
                    .thenReturn(true);

            // Act
            service.updateChannel(admin, organizationId, channelId,
                    updateRequest(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL, null));

            // Assert
            assertThat(channel.getSlug()).isEqualTo("renamed-1");
            verify(chatChannelRepository, never()).existsByOrganizationIdAndSlug(any(), any());
        }

        @Test
        void update_renameKeepingTheBase_keepsTheSlugWithoutALookup() {
            // Arrange
            ChatChannel channel = stored(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL);
            channel.setSlug("renamed-3");

            // Act
            service.updateChannel(admin, organizationId, channelId,
                    new ChatChannelUpdateRequest("RENAMED!", ChatChannelVisibility.PUBLIC,
                            ChatChannelPurpose.GENERAL, null));

            // Assert
            assertThat(channel.getSlug()).isEqualTo("renamed-3");
            verify(chatChannelRepository, never()).existsByOrganizationIdAndSlugAndIdNot(any(), any(), any());
        }

        @Test
        void update_slugIndexRefusesTheWrite_isRefusedWithSta017() {
            // Arrange
            ChatChannel channel = stored(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL);
            channel.setSlug("release");
            when(chatChannelRepository.saveAndFlush(any(ChatChannel.class)))
                    .thenThrow(integrityViolation(ChatConstant.CHANNEL_SLUG_UNIQUE_INDEX));

            // Act and Assert
            assertThatThrownBy(() -> service.updateChannel(admin, organizationId, channelId,
                    updateRequest(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL, null)))
                    .isInstanceOfSatisfying(ConflictException.class, exception -> {
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ExceptionDomainKazanError.STA_SLUG_TAKEN.getCode());
                        assertThat(exception.getErrorValues()).containsEntry(ChatConstant.FIELD_NAME, "renamed");
                    });
            verifyNoInteractions(applicationEventPublisher);
        }
    }
}
