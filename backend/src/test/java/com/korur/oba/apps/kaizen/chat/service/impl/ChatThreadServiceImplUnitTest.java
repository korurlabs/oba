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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.constant.ChatConstant;
import com.korur.oba.apps.kaizen.chat.constant.ChatExceptionLogConstant;
import com.korur.oba.apps.kaizen.chat.dto.request.ChatThreadCreateRequest;
import com.korur.oba.apps.kaizen.chat.dto.request.ChatThreadStatusUpdateRequest;
import com.korur.oba.apps.kaizen.chat.dto.request.ChatThreadUpdateRequest;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatThreadResponse;
import com.korur.oba.apps.kaizen.chat.entity.ChatMessage;
import com.korur.oba.apps.kaizen.chat.entity.ChatThread;
import com.korur.oba.apps.kaizen.chat.enums.ChatThreadStatus;
import com.korur.oba.apps.kaizen.chat.event.ChatThreadChangedEvent;
import com.korur.oba.apps.kaizen.chat.helper.ChatThreadStatusResolver;
import com.korur.oba.apps.kaizen.chat.mapper.ChatThreadMapperHelper;
import com.korur.oba.apps.kaizen.chat.mapper.ChatThreadMapperImpl;
import com.korur.oba.apps.kaizen.chat.mock_data.ChatMocks;
import com.korur.oba.apps.kaizen.chat.repository.ChatMessageRepository;
import com.korur.oba.apps.kaizen.chat.repository.ChatReadStateRepository;
import com.korur.oba.apps.kaizen.chat.repository.ChatThreadMemberRepository;
import com.korur.oba.apps.kaizen.chat.repository.ChatThreadRepository;
import com.korur.oba.apps.kaizen.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kaizen.chat.validator.ChatValidator;
import com.korur.oba.apps.kaizen.dailyupdate.service.DailyUpdateQueryService;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

@ExtendWith(MockitoExtension.class)
class ChatThreadServiceImplUnitTest {

    @Mock
    private ChatThreadRepository chatThreadRepository;
    @Mock
    private AppAccessValidator appAccessValidator;
    @Mock
    private DailyUpdateQueryService dailyUpdateQueryService;
    @Mock
    private ChatThreadMemberRepository chatThreadMemberRepository;
    @Mock
    private ChatReadStateRepository chatReadStateRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatThreadMapperHelper chatThreadMapperHelper;
    @Mock
    private ChatChannelQueryService chatChannelQueryService;
    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private ChatThreadServiceImpl service;

    private final UUID organizationId = UUID.randomUUID();
    private final User user = ChatMocks.user("Ada", "Lovelace");
    private final User rootAuthor = ChatMocks.user("Alan", "Turing");
    private final ChatThreadResponse response = mock(ChatThreadResponse.class);
    private ChatMessage root;

    @BeforeEach
    void setUp() {
        LoggingService loggingService = new LoggingService();
        ChatValidator validator = new ChatValidator(chatMessageRepository, chatThreadRepository,
                dailyUpdateQueryService, null, appAccessValidator, loggingService);
        service = new ChatThreadServiceImpl(chatThreadRepository, chatThreadMemberRepository, chatReadStateRepository,
                validator, new ChatThreadMapperImpl(), chatThreadMapperHelper, new ChatThreadStatusResolver(),
                chatChannelQueryService,
                organizationMemberQueryService, applicationEventPublisher, loggingService);

        root = ChatMocks.message(rootAuthor, organizationId, "Release is out");
        lenient().when(chatMessageRepository.findByIdAndOrganizationId(root.getId(), organizationId))
                .thenReturn(Optional.of(root));
        lenient().when(chatThreadMapperHelper.toResponse(any(ChatThread.class), eq(user), eq(organizationId)))
                .thenReturn(response);
    }

    private ChatThread storedThread(UUID createdBy) {
        ChatThread thread = ChatMocks.thread(organizationId, root.getId(), createdBy);
        lenient().when(chatThreadRepository.findByIdAndOrganizationId(thread.getId(), organizationId))
                .thenReturn(Optional.of(thread));
        return thread;
    }

    private void givenChannelRefused() {
        when(chatChannelQueryService.verifyChannelAccess(eq(organizationId), eq(user.getId()), any()))
                .thenThrow(new NotFoundException(ExceptionDomainKaizenError.NOT_CHAT_CHANNEL_FOUND));
    }

    private void givenRole(OrganizationMemberRole role) {
        OrganizationMember member = OrganizationMember.builder().role(role).build();
        when(organizationMemberQueryService.findMember(organizationId, user.getId())).thenReturn(Optional.of(member));
    }

    private void givenRelationsSet() {
        doAnswer(invocation -> {
            ChatThread thread = invocation.getArgument(0);
            thread.setTitle("Release is out");
            thread.setLastMessageAt(invocation.getArgument(5));
            return null;
        }).when(chatThreadMapperHelper).setCreateRelations(any(), any(), any(), any(), any(), any());
    }

    /**
     * Answers the read-back after the insert with the row as stored under the
     * ID the service generated.
     */
    private void givenStoredAfterInsert() {
        when(chatThreadRepository.findByIdAndOrganizationId(any(UUID.class), eq(organizationId)))
                .thenAnswer(invocation -> {
                    ChatThread stored = ChatMocks.thread(organizationId, root.getId(), user.getId());
                    stored.setId(invocation.getArgument(0));
                    return Optional.of(stored);
                });
    }

    private void verifyNoInsert() {
        verify(chatThreadRepository, never()).insertIfAbsent(any(), any(), any(), any(), any(), any(), any(),
                anyInt());
    }

    private ChatThreadChangedEvent publishedEvent() {
        ArgumentCaptor<ChatThreadChangedEvent> captor = ArgumentCaptor.forClass(ChatThreadChangedEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    @Nested
    class CreateThread {

        @Test
        void channelMessage_insertsThreadAddsCreatorAndRootAuthorAndPublishes() {
            // Arrange
            doAnswer(invocation -> {
                ChatThread thread = invocation.getArgument(0);
                thread.setTitle("Release is out");
                thread.setOrganizationId(organizationId);
                thread.setRootMessageId(root.getId());
                thread.setCreatedBy(user.getId());
                thread.setLastMessageAt(invocation.getArgument(5));
                return null;
            }).when(chatThreadMapperHelper).setCreateRelations(any(ChatThread.class), any(), eq(root), eq(user),
                    eq(organizationId), any(Instant.class));
            when(chatThreadRepository.insertIfAbsent(any(UUID.class), eq("Release is out"), eq(organizationId),
                    eq(root.getChannelId()), eq(root.getId()), eq(user.getId()), any(Instant.class),
                    eq(ChatConstant.DEFAULT_AUTO_ARCHIVE_MINUTES))).thenReturn(1);
            givenStoredAfterInsert();
            when(chatChannelQueryService.canRead(organizationId, rootAuthor.getId(), root.getChannelId()))
                    .thenReturn(true);
            when(chatThreadMemberRepository.insertIfAbsent(any(UUID.class), any(UUID.class))).thenReturn(1);

            // Act
            ChatThreadResponse result = service.createThread(user, organizationId,
                    new ChatThreadCreateRequest(root.getId(), null));

            // Assert
            assertThat(result).isSameAs(response);
            ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
            verify(chatThreadRepository).insertIfAbsent(idCaptor.capture(), any(), any(), any(), any(), any(), any(),
                    eq(ChatConstant.DEFAULT_AUTO_ARCHIVE_MINUTES));
            UUID threadId = idCaptor.getValue();
            verify(chatThreadMemberRepository).insertIfAbsent(threadId, user.getId());
            verify(chatThreadMemberRepository).insertIfAbsent(threadId, rootAuthor.getId());
            ChatThreadChangedEvent event = publishedEvent();
            assertThat(event.type()).isEqualTo(ChatThreadChangedEvent.Type.CREATED);
            assertThat(event.threadId()).isEqualTo(threadId);
            assertThat(event.rootMessageId()).isEqualTo(root.getId());
            assertThat(event.organizationId()).isEqualTo(organizationId);
            assertThat(event.userIds()).containsExactly(user.getId(), rootAuthor.getId());
        }

        @Test
        void rootAuthorWhoCannotReadTheChannel_isNotAdded() {
            // Arrange
            givenRelationsSet();
            when(chatThreadRepository.insertIfAbsent(any(UUID.class), any(), any(), any(), any(), any(), any(),
                    anyInt())).thenReturn(1);
            givenStoredAfterInsert();
            when(chatChannelQueryService.canRead(organizationId, rootAuthor.getId(), root.getChannelId()))
                    .thenReturn(false);
            when(chatThreadMemberRepository.insertIfAbsent(any(UUID.class), eq(user.getId()))).thenReturn(1);

            // Act
            service.createThread(user, organizationId, new ChatThreadCreateRequest(root.getId(), null));

            // Assert
            verify(chatThreadMemberRepository).insertIfAbsent(any(UUID.class), eq(user.getId()));
            verify(chatThreadMemberRepository, never()).insertIfAbsent(any(UUID.class), eq(rootAuthor.getId()));
            assertThat(publishedEvent().userIds()).containsExactly(user.getId());
        }

        @Test
        void ownMessage_addsTheCreatorOnce() {
            // Arrange
            ChatMessage ownRoot = ChatMocks.message(user, organizationId, "My own message");
            when(chatMessageRepository.findByIdAndOrganizationId(ownRoot.getId(), organizationId))
                    .thenReturn(Optional.of(ownRoot));
            doAnswer(invocation -> {
                ChatThread thread = invocation.getArgument(0);
                thread.setTitle("Mine");
                thread.setOrganizationId(organizationId);
                thread.setRootMessageId(ownRoot.getId());
                thread.setLastMessageAt(invocation.getArgument(5));
                return null;
            }).when(chatThreadMapperHelper).setCreateRelations(any(), any(), any(), any(), any(), any());
            when(chatThreadRepository.insertIfAbsent(any(UUID.class), any(), any(), any(), any(), any(), any(),
                    anyInt())).thenReturn(1);
            givenStoredAfterInsert();
            when(chatThreadMemberRepository.insertIfAbsent(any(UUID.class), eq(user.getId()))).thenReturn(1);

            // Act
            service.createThread(user, organizationId, new ChatThreadCreateRequest(ownRoot.getId(), "Mine"));

            // Assert
            verify(chatThreadMemberRepository).insertIfAbsent(any(UUID.class), eq(user.getId()));
            assertThat(publishedEvent().userIds()).containsExactly(user.getId());
        }

        @Test
        void secondThread_isConflictNamingTheExistingThread() {
            // Arrange
            ChatThread existing = ChatMocks.thread(organizationId, root.getId(), rootAuthor.getId());
            when(chatThreadRepository.findByRootMessageId(root.getId())).thenReturn(Optional.of(existing));

            // Act and Assert
            assertThatThrownBy(() -> service.createThread(user, organizationId,
                    new ChatThreadCreateRequest(root.getId(), null)))
                    .isInstanceOf(ConflictException.class)
                    .satisfies(thrown -> {
                        assertThat(thrown).extracting("errorCode")
                                .isEqualTo(ExceptionDomainKaizenError.STA_CHAT_THREAD_EXISTS.getCode());
                        assertThat(thrown).extracting("errorValues")
                                .isEqualTo(Map.of(ChatConstant.FIELD_THREAD_ID, existing.getId()));
                    });
            verifyNoInsert();
            verify(applicationEventPublisher, never()).publishEvent(any());
        }

        @Test
        void concurrentThreadOnTheSameMessage_isConflictNamingTheWinner() {
            // Arrange
            ChatThread winner = ChatMocks.thread(organizationId, root.getId(), rootAuthor.getId());
            when(chatThreadRepository.findByRootMessageId(root.getId()))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(winner));
            givenRelationsSet();
            when(chatThreadRepository.insertIfAbsent(any(UUID.class), any(), any(), any(), any(), any(), any(),
                    anyInt())).thenReturn(0);

            // Act and Assert
            assertThatThrownBy(() -> service.createThread(user, organizationId,
                    new ChatThreadCreateRequest(root.getId(), null)))
                    .isInstanceOf(ConflictException.class)
                    .satisfies(thrown -> {
                        assertThat(thrown).extracting("errorCode")
                                .isEqualTo(ExceptionDomainKaizenError.STA_CHAT_THREAD_EXISTS.getCode());
                        assertThat(thrown).extracting("errorValues")
                                .isEqualTo(Map.of(ChatConstant.FIELD_THREAD_ID, winner.getId()));
                    });
            verify(chatThreadMemberRepository, never()).insertIfAbsent(any(), any());
            verify(applicationEventPublisher, never()).publishEvent(any());
        }

        @Test
        void insertSkippedButNoThreadFound_isNotFoundNotSilent() {
            // Arrange
            givenRelationsSet();
            when(chatThreadRepository.insertIfAbsent(any(UUID.class), any(), any(), any(), any(), any(), any(),
                    anyInt())).thenReturn(0);

            // Act and Assert
            assertThatThrownBy(() -> service.createThread(user, organizationId,
                    new ChatThreadCreateRequest(root.getId(), null)))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKaizenError.NOT_CHAT_THREAD_FOUND.getCode());
            verify(chatThreadMemberRepository, never()).insertIfAbsent(any(), any());
            verify(applicationEventPublisher, never()).publishEvent(any());
        }

        @Test
        void threadMessageAsRoot_isRefused() {
            // Arrange
            root.setThread(ChatMocks.thread(organizationId, UUID.randomUUID(), rootAuthor.getId()));

            // Act and Assert
            assertThatThrownBy(() -> service.createThread(user, organizationId,
                    new ChatThreadCreateRequest(root.getId(), null)))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKaizenError.VAL_CHAT_THREAD_INVALID.getCode());
            verifyNoInsert();
        }

        @Test
        void rootOfAnotherOrganizationOrDeleted_isNotFound() {
            // Arrange
            UUID unknownId = UUID.randomUUID();

            // Act and Assert
            assertThatThrownBy(() -> service.createThread(user, organizationId,
                    new ChatThreadCreateRequest(unknownId, null)))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKaizenError.NOT_CHAT_MESSAGE_FOUND.getCode());
        }

        @Test
        void unreadableChannel_isRefusedBeforeAnyInsert() {
            // Arrange
            givenChannelRefused();

            // Act and Assert
            assertThatThrownBy(() -> service.createThread(user, organizationId,
                    new ChatThreadCreateRequest(root.getId(), null)))
                    .isInstanceOf(NotFoundException.class);
            verifyNoInsert();
        }
    }

    @Nested
    class UpdateThread {

        @Test
        void creator_renamesAndChangesWindow() {
            // Arrange
            ChatThread thread = storedThread(user.getId());
            givenRole(OrganizationMemberRole.MEMBER);
            when(chatThreadRepository.save(thread)).thenReturn(thread);

            // Act
            ChatThreadResponse result = service.updateThread(user, organizationId, thread.getId(),
                    new ChatThreadUpdateRequest("Renamed", 60));

            // Assert
            assertThat(result).isSameAs(response);
            assertThat(thread.getTitle()).isEqualTo("Renamed");
            verify(chatThreadMapperHelper).normalizeTitle(thread);
            assertThat(thread.getAutoArchiveMinutes()).isEqualTo(60);
            ChatThreadChangedEvent event = publishedEvent();
            assertThat(event.type()).isEqualTo(ChatThreadChangedEvent.Type.UPDATED);
            assertThat(event.userIds()).isEmpty();
        }

        @Test
        void owner_mayManageAnotherUsersThread() {
            // Arrange
            ChatThread thread = storedThread(rootAuthor.getId());
            givenRole(OrganizationMemberRole.OWNER);
            when(chatThreadRepository.save(thread)).thenReturn(thread);

            // Act
            service.updateThread(user, organizationId, thread.getId(), new ChatThreadUpdateRequest("Owner's", 1440));

            // Assert
            assertThat(thread.getTitle()).isEqualTo("Owner's");
            assertThat(thread.getAutoArchiveMinutes()).isEqualTo(1440);
        }

        @Test
        void otherMember_isForbidden() {
            // Arrange
            ChatThread thread = storedThread(rootAuthor.getId());
            givenRole(OrganizationMemberRole.MEMBER);

            // Act and Assert
            assertThatThrownBy(() -> service.updateThread(user, organizationId, thread.getId(),
                    new ChatThreadUpdateRequest("Hijacked", 60)))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKaizenError.SEC_CANNOT_MANAGE_THREAD.getCode());
            assertThat(thread.getTitle()).isEqualTo("Release checklist");
            verify(chatThreadRepository, never()).save(any());
            verify(applicationEventPublisher, never()).publishEvent(any());
        }

        @Test
        void windowOutsideTheOptions_isRefused() {
            // Arrange
            ChatThread thread = storedThread(user.getId());
            givenRole(OrganizationMemberRole.MEMBER);

            // Act and Assert
            assertThatThrownBy(() -> service.updateThread(user, organizationId, thread.getId(),
                    new ChatThreadUpdateRequest("Title", 90)))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKaizenError.VAL_CHAT_THREAD_INVALID.getCode());
            verify(chatThreadRepository, never()).save(any());
        }

        @Test
        void threadOfAnotherOrganization_isNotFound() {
            // Arrange
            UUID unknownId = UUID.randomUUID();

            // Act and Assert
            assertThatThrownBy(() -> service.updateThread(user, organizationId, unknownId,
                    new ChatThreadUpdateRequest("Title", 60)))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKaizenError.NOT_CHAT_THREAD_FOUND.getCode());
        }
    }

    @Nested
    class ChangeStatus {

        @BeforeEach
        void givenStatusStored() {
            lenient().when(chatThreadRepository.updateStatus(any(), any(), any(), any())).thenReturn(1);
        }

        @Test
        void creator_closesThreadRecordsChangeAndPublishes() {
            // Arrange
            ChatThread thread = storedThread(user.getId());
            givenRole(OrganizationMemberRole.MEMBER);

            // Act
            ChatThreadResponse result = service.changeStatus(user, organizationId, thread.getId(),
                    new ChatThreadStatusUpdateRequest(ChatThreadStatus.CLOSED));

            // Assert
            assertThat(result).isSameAs(response);
            verify(chatThreadRepository).updateStatus(eq(thread.getId()), eq("CLOSED"), eq(user.getId()),
                    any(Instant.class));
            ChatThreadChangedEvent event = publishedEvent();
            assertThat(event.type()).isEqualTo(ChatThreadChangedEvent.Type.UPDATED);
            assertThat(event.threadId()).isEqualTo(thread.getId());
            assertThat(event.userIds()).isEmpty();
        }

        @Test
        void admin_mayReopenAnotherUsersClosedThread() {
            // Arrange
            ChatThread thread = storedThread(rootAuthor.getId());
            thread.setStatus(ChatThreadStatus.CLOSED);
            givenRole(OrganizationMemberRole.ADMIN);

            // Act
            service.changeStatus(user, organizationId, thread.getId(),
                    new ChatThreadStatusUpdateRequest(ChatThreadStatus.ACTIVE));

            // Assert
            verify(chatThreadRepository).updateStatus(eq(thread.getId()), eq("ACTIVE"), eq(user.getId()),
                    any(Instant.class));
        }

        @Test
        void closedThread_cannotBeArchivedWithoutReopen() {
            // Arrange
            ChatThread thread = storedThread(user.getId());
            thread.setStatus(ChatThreadStatus.CLOSED);
            givenRole(OrganizationMemberRole.MEMBER);

            // Act and Assert
            assertThatThrownBy(() -> service.changeStatus(user, organizationId, thread.getId(),
                    new ChatThreadStatusUpdateRequest(ChatThreadStatus.ARCHIVED)))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKaizenError.STA_CHAT_THREAD_CLOSED.getCode());
            verify(chatThreadRepository, never()).updateStatus(any(), any(), any(), any());
            verify(applicationEventPublisher, never()).publishEvent(any());
        }

        @Test
        void closedThreadArchiveRefusal_isLoggedAtWarnNotError() {
            // Arrange
            LoggingService loggingService = mock(LoggingService.class);
            ChatValidator validator = new ChatValidator(chatMessageRepository, chatThreadRepository,
                    dailyUpdateQueryService, null, appAccessValidator, loggingService);
            ChatThreadServiceImpl loggedService = new ChatThreadServiceImpl(chatThreadRepository,
                    chatThreadMemberRepository, chatReadStateRepository, validator, new ChatThreadMapperImpl(),
                    chatThreadMapperHelper, new ChatThreadStatusResolver(), chatChannelQueryService,
                    organizationMemberQueryService, applicationEventPublisher, loggingService);
            ChatThread thread = storedThread(user.getId());
            thread.setStatus(ChatThreadStatus.CLOSED);
            givenRole(OrganizationMemberRole.MEMBER);

            // Act
            assertThatThrownBy(() -> loggedService.changeStatus(user, organizationId, thread.getId(),
                    new ChatThreadStatusUpdateRequest(ChatThreadStatus.ARCHIVED)))
                    .isInstanceOf(ConflictException.class);

            // Assert
            verify(loggingService).warn(any(), eq(LogLayer.SERVICE),
                    eq(ChatExceptionLogConstant.CHAT_THREAD_CLOSED_NOT_ARCHIVABLE), eq(thread.getId()));
            verify(loggingService, never()).error(any(), any(), any(), any(Object[].class));
        }

        @Test
        void creator_archivesActiveThread() {
            // Arrange
            ChatThread thread = storedThread(user.getId());
            givenRole(OrganizationMemberRole.MEMBER);

            // Act
            ChatThreadResponse result = service.changeStatus(user, organizationId, thread.getId(),
                    new ChatThreadStatusUpdateRequest(ChatThreadStatus.ARCHIVED));

            // Assert
            assertThat(result).isSameAs(response);
            verify(chatThreadRepository).updateStatus(eq(thread.getId()), eq("ARCHIVED"), eq(user.getId()),
                    any(Instant.class));
            assertThat(publishedEvent().type()).isEqualTo(ChatThreadChangedEvent.Type.UPDATED);
        }

        @Test
        void archiveRacingACommittedClose_isRefusedWhenNoRowChanges() {
            // Arrange: the thread was read as ACTIVE, and a close committed
            // before the update, which then skips the CLOSED row.
            ChatThread thread = storedThread(user.getId());
            givenRole(OrganizationMemberRole.MEMBER);
            when(chatThreadRepository.updateStatus(eq(thread.getId()), eq("ARCHIVED"), eq(user.getId()),
                    any(Instant.class))).thenReturn(0);

            // Act and Assert
            assertThatThrownBy(() -> service.changeStatus(user, organizationId, thread.getId(),
                    new ChatThreadStatusUpdateRequest(ChatThreadStatus.ARCHIVED)))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKaizenError.STA_CHAT_THREAD_CLOSED.getCode());
            verify(chatThreadRepository).findByIdAndOrganizationId(thread.getId(), organizationId);
            verify(chatThreadMapperHelper, never()).toResponse(any(), any(), any());
            verify(applicationEventPublisher, never()).publishEvent(any());
        }

        @Test
        void archivedThread_mayBeClosed() {
            // Arrange
            ChatThread thread = storedThread(user.getId());
            thread.setStatus(ChatThreadStatus.ARCHIVED);
            givenRole(OrganizationMemberRole.MEMBER);

            // Act
            service.changeStatus(user, organizationId, thread.getId(),
                    new ChatThreadStatusUpdateRequest(ChatThreadStatus.CLOSED));

            // Assert
            verify(chatThreadRepository).updateStatus(eq(thread.getId()), eq("CLOSED"), eq(user.getId()),
                    any(Instant.class));
            assertThat(publishedEvent().type()).isEqualTo(ChatThreadChangedEvent.Type.UPDATED);
        }

        @Test
        void unarchiveOfThreadArchivedByInactivity_restartsTheWindow() {
            // Arrange
            ChatThread thread = storedThread(user.getId());
            thread.setAutoArchiveMinutes(60);
            thread.setLastMessageAt(Instant.now().minus(Duration.ofDays(3)));
            givenRole(OrganizationMemberRole.MEMBER);

            // Act
            service.changeStatus(user, organizationId, thread.getId(),
                    new ChatThreadStatusUpdateRequest(ChatThreadStatus.ACTIVE));

            // Assert
            verify(chatThreadRepository).updateStatus(eq(thread.getId()), eq("ACTIVE"), eq(user.getId()),
                    any(Instant.class));
            assertThat(publishedEvent().type()).isEqualTo(ChatThreadChangedEvent.Type.UPDATED);
        }

        @Test
        void statusTheThreadAlreadyShows_changesNothing() {
            // Arrange
            ChatThread thread = storedThread(user.getId());
            thread.setStatus(ChatThreadStatus.ARCHIVED);
            givenRole(OrganizationMemberRole.MEMBER);

            // Act
            ChatThreadResponse result = service.changeStatus(user, organizationId, thread.getId(),
                    new ChatThreadStatusUpdateRequest(ChatThreadStatus.ARCHIVED));

            // Assert
            assertThat(result).isSameAs(response);
            verify(chatThreadRepository, never()).updateStatus(any(), any(), any(), any());
            verify(applicationEventPublisher, never()).publishEvent(any());
        }

        @Test
        void otherMember_isForbidden() {
            // Arrange
            ChatThread thread = storedThread(rootAuthor.getId());
            givenRole(OrganizationMemberRole.MEMBER);

            // Act and Assert
            assertThatThrownBy(() -> service.changeStatus(user, organizationId, thread.getId(),
                    new ChatThreadStatusUpdateRequest(ChatThreadStatus.CLOSED)))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKaizenError.SEC_CANNOT_MANAGE_THREAD.getCode());
            verify(chatThreadRepository, never()).updateStatus(any(), any(), any(), any());
            verify(applicationEventPublisher, never()).publishEvent(any());
        }

        @Test
        void unreadableChannel_isRefusedBeforeAnyRead() {
            // Arrange
            ChatThread thread = storedThread(user.getId());
            givenChannelRefused();

            // Act and Assert
            assertThatThrownBy(() -> service.changeStatus(user, organizationId, thread.getId(),
                    new ChatThreadStatusUpdateRequest(ChatThreadStatus.CLOSED)))
                    .isInstanceOf(NotFoundException.class);
            verify(chatThreadRepository, never()).updateStatus(any(), any(), any(), any());
        }

        @Test
        void threadOfAnotherOrganization_isNotFound() {
            // Arrange
            UUID threadId = UUID.randomUUID();

            // Act and Assert
            assertThatThrownBy(() -> service.changeStatus(user, organizationId, threadId,
                    new ChatThreadStatusUpdateRequest(ChatThreadStatus.CLOSED)))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKaizenError.NOT_CHAT_THREAD_FOUND.getCode());
            verify(chatThreadRepository, never()).updateStatus(any(), any(), any(), any());
        }
    }

    @Nested
    class JoinThread {

        @Test
        void nonMember_joinsAndIsPublished() {
            // Arrange
            ChatThread thread = storedThread(rootAuthor.getId());
            when(chatThreadMemberRepository.insertIfAbsent(thread.getId(), user.getId())).thenReturn(1);

            // Act
            ChatThreadResponse result = service.joinThread(user, organizationId, thread.getId());

            // Assert
            assertThat(result).isSameAs(response);
            ChatThreadChangedEvent event = publishedEvent();
            assertThat(event.type()).isEqualTo(ChatThreadChangedEvent.Type.MEMBERS_JOINED);
            assertThat(event.userIds()).containsExactly(user.getId());
        }

        @Test
        void member_changesNothing() {
            // Arrange
            ChatThread thread = storedThread(rootAuthor.getId());
            when(chatThreadMemberRepository.insertIfAbsent(thread.getId(), user.getId())).thenReturn(0);

            // Act
            service.joinThread(user, organizationId, thread.getId());

            // Assert
            verify(applicationEventPublisher, never()).publishEvent(any());
        }

        @Test
        void unreadableChannel_isRefusedBeforeJoining() {
            // Arrange
            ChatThread thread = storedThread(rootAuthor.getId());
            givenChannelRefused();

            // Act and Assert
            assertThatThrownBy(() -> service.joinThread(user, organizationId, thread.getId()))
                    .isInstanceOf(NotFoundException.class);
            verify(chatThreadMemberRepository, never()).insertIfAbsent(any(), any());
        }
    }

    @Nested
    class LeaveThread {

        @Test
        void member_leavesLosesReadStateAndIsPublished() {
            // Arrange
            ChatThread thread = storedThread(rootAuthor.getId());
            when(chatThreadMemberRepository.deleteByThreadIdAndUserId(thread.getId(), user.getId())).thenReturn(1);

            // Act
            ChatThreadResponse result = service.leaveThread(user, organizationId, thread.getId());

            // Assert
            assertThat(result).isSameAs(response);
            verify(chatReadStateRepository).deleteByUserIdAndThreadId(user.getId(), thread.getId());
            ChatThreadChangedEvent event = publishedEvent();
            assertThat(event.type()).isEqualTo(ChatThreadChangedEvent.Type.MEMBER_LEFT);
            assertThat(event.userIds()).containsExactly(user.getId());
        }

        @Test
        void nonMember_changesNothing() {
            // Arrange
            ChatThread thread = storedThread(rootAuthor.getId());
            when(chatThreadMemberRepository.deleteByThreadIdAndUserId(thread.getId(), user.getId())).thenReturn(0);

            // Act
            service.leaveThread(user, organizationId, thread.getId());

            // Assert
            verify(chatReadStateRepository, never()).deleteByUserIdAndThreadId(any(), any());
            verify(applicationEventPublisher, never()).publishEvent(any());
        }

        @Test
        void threadOfAnotherOrganization_isNotFound() {
            // Arrange
            UUID unknownId = UUID.randomUUID();

            // Act and Assert
            assertThatThrownBy(() -> service.leaveThread(user, organizationId, unknownId))
                    .isInstanceOf(NotFoundException.class);
            verify(chatThreadMemberRepository, never()).deleteByThreadIdAndUserId(any(), any());
        }
    }

    @Test
    void update_keepsReplyCountMembersAndReadStates() {
        // Arrange
        ChatThread thread = storedThread(user.getId());
        thread.setMessageCount(7);
        givenRole(OrganizationMemberRole.MEMBER);
        when(chatThreadRepository.save(thread)).thenReturn(thread);

        // Act
        service.updateThread(user, organizationId, thread.getId(), new ChatThreadUpdateRequest("Renamed", 4320));

        // Assert
        assertThat(thread.getMessageCount()).isEqualTo(7);
        verify(chatThreadMemberRepository, never()).insertIfAbsent(any(), any());
        verify(chatReadStateRepository, never()).upsertThreadReadState(any(), any(), any(), any(), any(), any());
    }
}
