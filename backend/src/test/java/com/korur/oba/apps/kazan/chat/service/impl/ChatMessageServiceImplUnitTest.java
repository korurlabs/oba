package com.korur.oba.apps.kazan.chat.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 2. Third-party
import org.hibernate.exception.ConstraintViolationException;

// 3. Java SDK
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.ChatConstant;
import com.korur.oba.apps.kazan.chat.dto.request.ChatMessageCreateRequest;
import com.korur.oba.apps.kazan.chat.dto.request.ChatMessageUpdateRequest;
import com.korur.oba.apps.kazan.chat.dto.response.ChatMessageResponse;
import com.korur.oba.apps.kazan.chat.dto.signal.ChatTypingSignal;
import com.korur.oba.apps.kazan.chat.entity.ChatChannel;
import com.korur.oba.apps.kazan.chat.entity.ChatMessage;
import com.korur.oba.apps.kazan.chat.entity.ChatMessageMention;
import com.korur.oba.apps.kazan.chat.entity.ChatThread;
import com.korur.oba.apps.kazan.chat.enums.ChatChannelPurpose;
import com.korur.oba.apps.kazan.chat.enums.ChatChannelVisibility;
import com.korur.oba.apps.kazan.chat.enums.ChatThreadStatus;
import com.korur.oba.apps.kazan.chat.event.ChatMessageChangedEvent;
import com.korur.oba.apps.kazan.chat.event.ChatNotificationsChangedEvent;
import com.korur.oba.apps.kazan.chat.event.ChatReadStateChangedEvent;
import com.korur.oba.apps.kazan.chat.event.ChatThreadChangedEvent;
import com.korur.oba.apps.kazan.chat.helper.ChatSendRateLimiter;
import com.korur.oba.apps.kazan.chat.helper.ChatThreadStatusResolver;
import com.korur.oba.apps.kazan.chat.helper.ChatTokenParser;
import com.korur.oba.apps.kazan.chat.mapper.ChatMessageMapperHelper;
import com.korur.oba.apps.kazan.chat.mapper.ChatMessageMapperImpl;
import com.korur.oba.apps.kazan.chat.mock_data.ChatMocks;
import com.korur.oba.apps.kazan.chat.properties.ChatSendRateProperties;
import com.korur.oba.apps.kazan.chat.repository.ChatChannelMemberRepository;
import com.korur.oba.apps.kazan.chat.repository.ChatMessageMentionRepository;
import com.korur.oba.apps.kazan.chat.repository.ChatMessageRepository;
import com.korur.oba.apps.kazan.chat.repository.ChatThreadMemberRepository;
import com.korur.oba.apps.kazan.chat.repository.ChatThreadRepository;
import com.korur.oba.apps.kazan.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kazan.chat.validator.ChatValidator;
import com.korur.oba.apps.kazan.dailyupdate.entity.DailyUpdate;
import com.korur.oba.apps.kazan.dailyupdate.service.DailyUpdateQueryService;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.mapper.TicketEmbeddedMapperImpl;
import com.korur.oba.apps.kazan.ticket.service.TicketQueryService;
import com.korur.oba.connect.realtime.service.RealtimeSessionRegistry;
import com.korur.oba.connect.realtime.service.RealtimeSignalBus;
import com.korur.oba.connect.realtime.util.RealtimeDestinationUtil;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.TooManyRequestsException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.ResourceAccessType;
import com.korur.oba.shrine.access.service.ResourceAccessQueryService;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.service.UserQueryService;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;
import com.korur.oba.shrine.storage.dto.request.attachment.AttachmentCreateRequest;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentResponse;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.service.attachment.AttachmentQueryService;
import com.korur.oba.shrine.storage.service.attachment.AttachmentService;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceImplUnitTest {

    private static final Instant SAVED_AT = Instant.parse("2026-10-21T09:15:00Z");

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private AppAccessValidator appAccessValidator;
    @Mock
    private ChatThreadRepository chatThreadRepository;
    @Mock
    private ChatThreadMemberRepository chatThreadMemberRepository;
    @Mock
    private ChatChannelMemberRepository chatChannelMemberRepository;
    @Mock
    private ChatMessageMentionRepository chatMessageMentionRepository;
    @Mock
    private ResourceAccessQueryService resourceAccessQueryService;
    @Mock
    private ChatChannelQueryService chatChannelQueryService;
    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;
    @Mock
    private TicketQueryService ticketQueryService;
    @Mock
    private UserQueryService userQueryService;
    @Mock
    private AttachmentService attachmentService;
    @Mock
    private AttachmentQueryService attachmentQueryService;
    @Mock
    private DailyUpdateQueryService dailyUpdateQueryService;
    @Mock
    private RealtimeSessionRegistry realtimeSessionRegistry;
    @Mock
    private RealtimeSignalBus realtimeSignalBus;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private PlatformTransactionManager transactionManager;

    private ChatMessageServiceImpl service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID channelId = UUID.randomUUID();
    private final User author = ChatMocks.user("Ada", "Lovelace");
    private final User colleague = ChatMocks.user("Alan", "Turing");
    private final User outsider = ChatMocks.user("Grace", "Hopper");

    @BeforeEach
    void setUp() {
        LoggingService loggingService = new LoggingService();
        ChatMessageMapperImpl mapper = new ChatMessageMapperImpl(new TicketEmbeddedMapperImpl());
        ChatTokenParser tokenParser = new ChatTokenParser();
        ChatValidator validator = new ChatValidator(chatMessageRepository, chatThreadRepository,
                dailyUpdateQueryService, null, appAccessValidator, loggingService);
        ChatMessageMapperHelper mapperHelper = new ChatMessageMapperHelper(mapper, tokenParser,
                resourceAccessQueryService, chatMessageRepository, chatMessageMentionRepository,
                chatThreadMemberRepository, ticketQueryService, userQueryService, organizationMemberQueryService,
                attachmentQueryService, dailyUpdateQueryService, null);
        ChatSendRateLimiter rateLimiter = new ChatSendRateLimiter(new ChatSendRateProperties(), loggingService);
        service = new ChatMessageServiceImpl(chatMessageRepository, chatThreadRepository, chatThreadMemberRepository,
                chatChannelMemberRepository, validator, mapper, mapperHelper, tokenParser, new ChatThreadStatusResolver(),
                rateLimiter,
                chatChannelQueryService, organizationMemberQueryService, ticketQueryService, attachmentService,
                attachmentQueryService, resourceAccessQueryService, realtimeSessionRegistry, realtimeSignalBus, applicationEventPublisher,
                new TransactionTemplate(transactionManager), loggingService);

        lenient().when(organizationMemberQueryService.listActiveMemberSummaries(eq(organizationId), any()))
                .thenReturn(List.of(ChatMocks.member(author), ChatMocks.member(colleague)));
        lenient().when(resourceAccessQueryService.withReadGrant(any(), any(), anyCollection(), any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(3).get());
        lenient().when(resourceAccessQueryService.withCheckedReadGrant(any(), any(), any(), anyCollection(), any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(4).get());
        lenient().when(chatMessageRepository.saveAndFlush(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            if (message.getId() == null) {
                message.setId(UUID.randomUUID());
                message.setCreatedAt(SAVED_AT);
            }
            return message;
        });
        lenient().when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(chatChannelQueryService.verifyChannelAccess(eq(organizationId), any(), any()))
                .thenAnswer(invocation -> channel(invocation.getArgument(2), ChatChannelVisibility.PUBLIC,
                        ChatChannelPurpose.GENERAL));
        lenient().when(chatThreadMemberRepository.insertIfAbsent(any(), any())).thenReturn(1);
        lenient().when(chatThreadRepository.incrementMessageCount(any(), any(), any())).thenReturn(1);
    }

    private ChatChannel channel(UUID id, ChatChannelVisibility visibility, ChatChannelPurpose purpose) {
        ChatChannel channel = ChatChannel.builder()
                .name("General")
                .organizationId(organizationId)
                .visibility(visibility)
                .purpose(purpose)
                .build();
        channel.setId(id);
        return channel;
    }

    private void givenChannel(ChatChannelVisibility visibility, ChatChannelPurpose purpose) {
        when(chatChannelQueryService.verifyChannelAccess(eq(organizationId), eq(author.getId()), eq(channelId)))
                .thenReturn(channel(channelId, visibility, purpose));
    }

    private void givenChannelRefused() {
        when(chatChannelQueryService.verifyChannelAccess(eq(organizationId), eq(author.getId()), any()))
                .thenThrow(new NotFoundException(ExceptionDomainKazanError.NOT_CHAT_CHANNEL_FOUND));
    }

    private ChatThread givenThread() {
        ChatThread thread = ChatMocks.thread(organizationId, UUID.randomUUID(), colleague.getId());
        thread.setChannelId(channelId);
        when(chatThreadRepository.findByIdAndOrganizationId(thread.getId(), organizationId))
                .thenReturn(Optional.of(thread));
        return thread;
    }

    private ChatMessage givenStored(ChatMessage message) {
        message.setChannelId(channelId);
        lenient().when(chatMessageRepository.findByIdAndOrganizationId(message.getId(), organizationId))
                .thenReturn(Optional.of(message));
        return message;
    }

    private void givenRole(User user, OrganizationMemberRole role) {
        when(organizationMemberQueryService.findMember(organizationId, user.getId()))
                .thenReturn(Optional.of(OrganizationMember.builder().role(role).build()));
    }

    private List<Object> publishedEvents(int count) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher, times(count)).publishEvent(captor.capture());
        return captor.getAllValues();
    }

    private <T> T publishedEvent(Class<T> type) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher, atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues().stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }

    @Nested
    class CreateMessage {

        @Test
        void channelMessage_savesAndPublishesCreatedWithNonceWithoutMovingTheReadPosition() {
            // Arrange
            ChatMessageCreateRequest request = new ChatMessageCreateRequest("Release is out", channelId, null, null,
                    null, null, "nonce-1");

            // Act
            ChatMessageResponse response = service.postMessage(author, organizationId, request, null);

            // Assert
            assertThat(response.clientNonce()).isEqualTo("nonce-1");
            assertThat(response.content()).isEqualTo("Release is out");
            verify(chatThreadMemberRepository, never()).insertIfAbsent(any(), any());
            verify(chatThreadRepository, never()).incrementMessageCount(any(), any(), any());
            ChatMessageChangedEvent event = publishedEvent(ChatMessageChangedEvent.class);
            assertThat(event.type()).isEqualTo(ChatMessageChangedEvent.Type.CREATED);
            assertThat(event.messageId()).isEqualTo(response.id());
            assertThat(event.threadId()).isNull();
            assertThat(event.authorId()).isEqualTo(author.getId());
            assertThat(event.clientNonce()).isEqualTo("nonce-1");
            verify(applicationEventPublisher, never()).publishEvent(any(ChatReadStateChangedEvent.class));
        }

        @Test
        void threadMessage_addsAuthorAndDirectMentionsAsMembersAndMovesCounters() {
            // Arrange
            ChatThread thread = givenThread();
            when(realtimeSessionRegistry.onlineUserIds(organizationId)).thenReturn(Set.of(outsider.getId()));
            String content = "ping " + ChatMocks.userToken(colleague.getId()) + " <@everyone> <@here>";
            ChatMessageCreateRequest request = new ChatMessageCreateRequest(content, channelId, thread.getId(), null,
                    null, null, "nonce-2");

            // Act
            ChatMessageResponse response = service.postMessage(author, organizationId, request, List.of());

            // Assert
            verify(chatThreadMemberRepository).insertIfAbsent(thread.getId(), author.getId());
            verify(chatThreadMemberRepository).insertIfAbsent(thread.getId(), colleague.getId());
            verify(chatThreadMemberRepository, never()).insertIfAbsent(thread.getId(), outsider.getId());
            verify(chatThreadRepository).incrementMessageCount(thread.getId(), SAVED_AT, author.getId());
            ChatThreadChangedEvent membership = publishedEvent(ChatThreadChangedEvent.class);
            assertThat(membership.type()).isEqualTo(ChatThreadChangedEvent.Type.MEMBERS_JOINED);
            assertThat(membership.userIds()).containsExactly(author.getId(), colleague.getId());
            assertThat(publishedEvent(ChatMessageChangedEvent.class).threadId()).isEqualTo(thread.getId());
        }

        @Test
        void closedThread_isRefusedBeforeAnythingIsSaved() {
            // Arrange
            ChatThread thread = givenThread();
            thread.setStatus(ChatThreadStatus.CLOSED);
            ChatMessageCreateRequest request = new ChatMessageCreateRequest("late", channelId, thread.getId(), null,
                    null, null, "nonce-c");

            // Act and Assert
            assertThatThrownBy(() -> service.postMessage(author, organizationId, request, List.of()))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.STA_CHAT_THREAD_CLOSED.getCode());
            verify(chatMessageRepository, never()).saveAndFlush(any());
            verify(chatThreadRepository, never()).incrementMessageCount(any(), any(), any());
            verify(applicationEventPublisher, never()).publishEvent(any());
        }

        @Test
        void threadClosedDuringTheSend_isRefusedAndPublishesNothing() {
            // Arrange
            ChatThread thread = givenThread();
            when(chatThreadRepository.incrementMessageCount(eq(thread.getId()), any(), eq(author.getId())))
                    .thenReturn(0);
            ChatMessageCreateRequest request = new ChatMessageCreateRequest("race", channelId, thread.getId(), null,
                    null, null, "nonce-r");

            // Act and Assert
            assertThatThrownBy(() -> service.postMessage(author, organizationId, request, List.of()))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.STA_CHAT_THREAD_CLOSED.getCode());
            verify(applicationEventPublisher, never()).publishEvent(any(ChatMessageChangedEvent.class));
        }

        @Test
        void threadClosedDuringTheSend_isRefusedBeforeAnyFileIsUploaded() {
            // Arrange
            ChatThread thread = givenThread();
            when(chatThreadRepository.incrementMessageCount(eq(thread.getId()), any(), eq(author.getId())))
                    .thenReturn(0);
            MultipartFile file = new MockMultipartFile("files", "plan.pdf", "application/pdf", new byte[]{1, 2});
            ChatMessageCreateRequest request = new ChatMessageCreateRequest("race", channelId, thread.getId(), null,
                    null, null, "nonce-u");

            // Act and Assert
            assertThatThrownBy(() -> service.postMessage(author, organizationId, request, List.of(file)))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.STA_CHAT_THREAD_CLOSED.getCode());
            verify(attachmentService, never()).uploadAttachments(any(), any(), anyList(), anyList());
        }

        @Test
        void threadMessageWithFiles_joinsAndCountsBeforeTheUpload() {
            // Arrange
            ChatThread thread = givenThread();
            MultipartFile file = new MockMultipartFile("files", "plan.pdf", "application/pdf", new byte[]{1, 2});
            ChatMessageCreateRequest request = new ChatMessageCreateRequest("see file", channelId, thread.getId(),
                    null, null, null, "n");

            // Act
            service.postMessage(author, organizationId, request, List.of(file));

            // Assert
            InOrder order = inOrder(chatThreadMemberRepository, chatThreadRepository, attachmentService);
            order.verify(chatThreadMemberRepository).insertIfAbsent(thread.getId(), author.getId());
            order.verify(chatThreadRepository).incrementMessageCount(thread.getId(), SAVED_AT, author.getId());
            order.verify(attachmentService).uploadAttachments(eq(author), eq(organizationId), anyList(), eq(List.of(file)));
        }

        @Test
        void replyToThreadArchivedByHand_announcesTheThreadUpdate() {
            // Arrange
            ChatThread thread = givenThread();
            thread.setStatus(ChatThreadStatus.ARCHIVED);
            when(chatThreadMemberRepository.insertIfAbsent(thread.getId(), author.getId())).thenReturn(0);
            ChatMessageCreateRequest request = new ChatMessageCreateRequest("back", channelId, thread.getId(), null,
                    null, null, "nonce-a");

            // Act
            service.postMessage(author, organizationId, request, List.of());

            // Assert
            verify(chatThreadRepository).incrementMessageCount(thread.getId(), SAVED_AT, author.getId());
            ChatThreadChangedEvent update = publishedEvent(ChatThreadChangedEvent.class);
            assertThat(update.type()).isEqualTo(ChatThreadChangedEvent.Type.UPDATED);
            assertThat(update.threadId()).isEqualTo(thread.getId());
        }

        @Test
        void replyToThreadArchivedByInactivity_announcesTheThreadUpdate() {
            // Arrange
            ChatThread thread = givenThread();
            thread.setAutoArchiveMinutes(60);
            thread.setLastMessageAt(SAVED_AT.minus(Duration.ofDays(2)));
            when(chatThreadMemberRepository.insertIfAbsent(thread.getId(), author.getId())).thenReturn(0);
            ChatMessageCreateRequest request = new ChatMessageCreateRequest("back", channelId, thread.getId(), null,
                    null, null, "nonce-i");

            // Act
            service.postMessage(author, organizationId, request, List.of());

            // Assert
            assertThat(publishedEvent(ChatThreadChangedEvent.class).type())
                    .isEqualTo(ChatThreadChangedEvent.Type.UPDATED);
        }

        @Test
        void replyToActiveThread_announcesNoThreadUpdate() {
            // Arrange
            ChatThread thread = givenThread();
            thread.setLastMessageAt(SAVED_AT.minus(Duration.ofMinutes(5)));
            when(chatThreadMemberRepository.insertIfAbsent(thread.getId(), author.getId())).thenReturn(0);
            ChatMessageCreateRequest request = new ChatMessageCreateRequest("more", channelId, thread.getId(), null,
                    null, null, "nonce-m");

            // Act
            service.postMessage(author, organizationId, request, List.of());

            // Assert
            verify(applicationEventPublisher, never()).publishEvent(any(ChatThreadChangedEvent.class));
        }

        @Test
        void threadMessage_existingMembersPublishNoMembershipChange() {
            // Arrange
            ChatThread thread = givenThread();
            when(chatThreadMemberRepository.insertIfAbsent(any(), any())).thenReturn(0);
            ChatMessageCreateRequest request = new ChatMessageCreateRequest("again", channelId, thread.getId(), null,
                    null, null, "n");

            // Act
            service.postMessage(author, organizationId, request, null);

            // Assert
            assertThat(publishedEvents(1)).noneMatch(ChatThreadChangedEvent.class::isInstance);
        }

        @Test
        void threadReplyToColleaguesMessage_addsQuotedAuthorAsMember() {
            // Arrange
            ChatThread thread = givenThread();
            ChatMessage target = givenStored(ChatMocks.message(colleague, organizationId, "question"));
            target.setThread(thread);
            ChatMessageCreateRequest request = new ChatMessageCreateRequest("answer", channelId, thread.getId(),
                    target.getId(), null, null,
                    "n");

            // Act
            service.postMessage(author, organizationId, request, null);

            // Assert
            verify(chatThreadMemberRepository).insertIfAbsent(thread.getId(), colleague.getId());
            ChatThreadChangedEvent membership = publishedEvent(ChatThreadChangedEvent.class);
            assertThat(membership.userIds()).containsExactly(author.getId(), colleague.getId());
        }

        @Test
        void threadReplyToFormerMembersMessage_doesNotAddThem() {
            // Arrange
            ChatThread thread = givenThread();
            ChatMessage target = givenStored(ChatMocks.message(outsider, organizationId, "question"));
            target.setThread(thread);
            ChatMessageCreateRequest request = new ChatMessageCreateRequest("answer", channelId, thread.getId(),
                    target.getId(), null, null,
                    "n");

            // Act
            service.postMessage(author, organizationId, request, null);

            // Assert
            verify(chatThreadMemberRepository).insertIfAbsent(thread.getId(), author.getId());
            verify(chatThreadMemberRepository, never()).insertIfAbsent(thread.getId(), outsider.getId());
        }

        @Test
        void threadReplyToOwnMessage_addsOnlyTheSender() {
            // Arrange
            ChatThread thread = givenThread();
            ChatMessage target = givenStored(ChatMocks.message(author, organizationId, "first thought"));
            target.setThread(thread);
            ChatMessageCreateRequest request = new ChatMessageCreateRequest("second thought", channelId, thread.getId(),
                    target.getId(), null, null, "n");

            // Act
            service.postMessage(author, organizationId, request, null);

            // Assert
            verify(chatThreadMemberRepository, times(1)).insertIfAbsent(any(), any());
            verify(chatThreadMemberRepository).insertIfAbsent(thread.getId(), author.getId());
        }

        @Test
        void channelReply_addsNoThreadMember() {
            // Arrange
            ChatMessage target = givenStored(ChatMocks.message(colleague, organizationId, "question"));
            ChatMessageCreateRequest request = new ChatMessageCreateRequest("answer", channelId, null, target.getId(),
                    null, null, "n");

            // Act
            service.postMessage(author, organizationId, request, null);

            // Assert
            verify(chatThreadMemberRepository, never()).insertIfAbsent(any(), any());
        }

        @Test
        void files_uploadedForTheNewMessage() {
            // Arrange
            MultipartFile file = new MockMultipartFile("files", "plan.pdf", "application/pdf", new byte[]{1, 2});
            ChatMessageCreateRequest request = new ChatMessageCreateRequest(null, channelId, null, null, null, null, "n");

            // Act
            ChatMessageResponse response = service.postMessage(author, organizationId, request, List.of(file));

            // Assert
            ArgumentCaptor<List<AttachmentCreateRequest>> captor = ArgumentCaptor.forClass(List.class);
            verify(attachmentService).uploadAttachments(eq(author), eq(organizationId), captor.capture(), eq(List.of(file)));
            assertThat(captor.getValue()).containsExactly(
                    new AttachmentCreateRequest("plan.pdf", StorageEntityType.CHAT_MESSAGE, response.id()));
        }

        @Test
        void files_uploadForTheNewMessageOnly() {
            // Arrange
            MultipartFile file = new MockMultipartFile("files", "plan.pdf", "application/pdf", new byte[]{1, 2});
            ChatMessageCreateRequest request = new ChatMessageCreateRequest(null, channelId, null, null, null, null, "n");

            // Act
            ChatMessageResponse response = service.postMessage(author, organizationId, request, List.of(file));

            // Assert
            ArgumentCaptor<List<AttachmentCreateRequest>> uploaded = ArgumentCaptor.captor();
            verify(attachmentService).uploadAttachments(eq(author), eq(organizationId), uploaded.capture(), eq(List.of(file)));
            assertThat(uploaded.getValue()).extracting(AttachmentCreateRequest::entityId)
                    .containsExactly(response.id());
        }

        @Test
        void mentionOfNonMember_isRefused() {
            // Arrange
            ChatMessageCreateRequest request = new ChatMessageCreateRequest(
                    "hi " + ChatMocks.userToken(outsider.getId()), channelId, null, null, null, null, "n");

            // Act and Assert
            assertThatThrownBy(() -> service.postMessage(author, organizationId, request, null))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_CHAT_MENTION_NOT_MEMBER.getCode());
            verify(chatMessageRepository, never()).saveAndFlush(any());
        }

        @Test
        void privateChannel_mentionOfAMemberOffTheList_isRefused() {
            // Arrange
            givenChannel(ChatChannelVisibility.PRIVATE, ChatChannelPurpose.GENERAL);
            when(chatChannelMemberRepository.findUserIdsByChannelId(channelId)).thenReturn(List.of(author.getId()));
            ChatMessageCreateRequest request = new ChatMessageCreateRequest(
                    "hi " + ChatMocks.userToken(colleague.getId()), channelId, null, null, null, null, "n");

            // Act and Assert
            assertThatThrownBy(() -> service.postMessage(author, organizationId, request, null))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_CHAT_MENTION_NOT_MEMBER.getCode());
            verify(chatMessageRepository, never()).saveAndFlush(any());
        }

        @Test
        void inaccessibleTicket_isNotFound() {
            // Arrange
            Ticket ticket = ChatMocks.ticket("SEC-1", "Hidden");
            when(ticketQueryService.getAccessibleTickets(eq(author.getId()), eq(organizationId), anyList())).thenReturn(List.of());
            ChatMessageCreateRequest request = new ChatMessageCreateRequest(
                    "see " + ChatMocks.ticketToken(ticket.getId()), channelId, null, null, null, null, "n");

            // Act and Assert
            assertThatThrownBy(() -> service.postMessage(author, organizationId, request, null))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode").isEqualTo(ExceptionDomainKazanError.NOT_TICKET_FOUND.getCode());
            verify(chatMessageRepository, never()).saveAndFlush(any());
        }

        @Test
        void replyToMessageInAnotherThread_isRefused() {
            // Arrange
            ChatThread otherThread = ChatMocks.thread(organizationId, UUID.randomUUID(), colleague.getId());
            ChatMessage target = givenStored(ChatMocks.message(colleague, organizationId, "elsewhere"));
            target.setThread(otherThread);
            ChatMessageCreateRequest request = new ChatMessageCreateRequest("reply", channelId, null, target.getId(),
                    null, null, "n");

            // Act and Assert
            assertThatThrownBy(() -> service.postMessage(author, organizationId, request, null))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_CHAT_REPLY_OUTSIDE_CONVERSATION.getCode());
        }

        @Test
        void replyToMessageOfAnotherChannel_isNotFound() {
            // Arrange
            ChatMessage target = givenStored(ChatMocks.message(colleague, organizationId, "private words"));
            target.setChannelId(UUID.randomUUID());
            ChatMessageCreateRequest request = new ChatMessageCreateRequest("reply", channelId, null, target.getId(),
                    null, null, "n");

            // Act and Assert
            assertThatThrownBy(() -> service.postMessage(author, organizationId, request, null))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.NOT_CHAT_MESSAGE_FOUND.getCode());
            verify(chatMessageRepository, never()).saveAndFlush(any());
        }

        @Test
        void unreadableChannel_isRefusedBeforeAnySave() {
            // Arrange
            givenChannelRefused();
            ChatMessageCreateRequest request = new ChatMessageCreateRequest("hi", channelId, null, null, null, null, "n");

            // Act and Assert
            assertThatThrownBy(() -> service.postMessage(author, organizationId, request, null))
                    .isInstanceOf(NotFoundException.class);
            verify(chatMessageRepository, never()).saveAndFlush(any());
        }

        @Test
        void sixthSendWithinWindow_isRateLimited() {
            // Arrange
            ChatMessageCreateRequest request = new ChatMessageCreateRequest("hi", channelId, null, null, null, null, "n");
            for (int i = 0; i < 5; i++) {
                service.postMessage(author, organizationId, request, null);
            }

            // Act and Assert
            assertThatThrownBy(() -> service.postMessage(author, organizationId, request, null))
                    .isInstanceOf(TooManyRequestsException.class);
            verify(chatMessageRepository, times(5)).saveAndFlush(any());
        }
    }

    @Nested
    class RepeatedSend {

        private ChatMessage givenSent(String clientNonce) {
            ChatMessage message = ChatMocks.message(author, organizationId, "Release is out");
            message.setChannelId(channelId);
            message.setClientNonce(clientNonce);
            when(chatMessageRepository.findByAuthorIdAndClientNonceWithDeleted(author.getId(), clientNonce))
                    .thenReturn(Optional.of(message));
            return message;
        }

        private DataIntegrityViolationException violationOf(String constraintName) {
            return new DataIntegrityViolationException("could not execute statement",
                    new ConstraintViolationException("duplicate key", new SQLException("duplicate key", "23505"),
                            constraintName));
        }

        @Test
        void newNonce_storesTheNonceOnTheMessage() {
            // Arrange
            ChatMessageCreateRequest request = new ChatMessageCreateRequest("Release is out", channelId, null, null,
                    null, null, "nonce-new");

            // Act
            service.postMessage(author, organizationId, request, null);

            // Assert
            ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
            verify(chatMessageRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getClientNonce()).isEqualTo("nonce-new");
        }

        @Test
        void sameNonceAgain_returnsTheStoredMessageAndChangesNothing() {
            // Arrange
            UUID threadId = UUID.randomUUID();
            ChatMessage stored = givenSent("nonce-again");
            MultipartFile file = new MockMultipartFile("files", "plan.pdf", "application/pdf", new byte[]{1, 2});
            ChatMessageCreateRequest request = new ChatMessageCreateRequest("Release is out", channelId,
                    threadId, null, null, null, "nonce-again");

            // Act
            ChatMessageResponse response = service.postMessage(author, organizationId, request, List.of(file));

            // Assert
            assertThat(response.id()).isEqualTo(stored.getId());
            assertThat(response.content()).isEqualTo("Release is out");
            assertThat(response.clientNonce()).isEqualTo("nonce-again");
            verify(chatMessageRepository, never()).saveAndFlush(any());
            verify(chatMessageRepository, never()).save(any());
            verify(attachmentService, never()).uploadAttachments(any(), any(), anyList(), anyList());
            verify(chatThreadMemberRepository, never()).insertIfAbsent(any(), any());
            verify(chatThreadRepository, never()).findByIdAndOrganizationId(any(), any());
            verify(chatThreadRepository, never()).incrementMessageCount(any(), any(), any());
            verify(applicationEventPublisher, never()).publishEvent(any());
        }

        @Test
        void sameNonceRepeatedBeyondTheSendRate_isNotRateLimited() {
            // Arrange
            givenSent("nonce-rate");
            ChatMessageCreateRequest request = new ChatMessageCreateRequest("hi", channelId, null, null, null, null,
                    "nonce-rate");
            for (int i = 0; i < 10; i++) {
                service.postMessage(author, organizationId, request, null);
            }
            ChatMessageCreateRequest fresh = new ChatMessageCreateRequest("hi", channelId, null, null, null, null,
                    "nonce-fresh");

            // Act
            ChatMessageResponse response = service.postMessage(author, organizationId, fresh, null);

            // Assert
            assertThat(response.clientNonce()).isEqualTo("nonce-fresh");
            verify(chatMessageRepository, times(1)).saveAndFlush(any());
        }

        @Test
        void sameNonceOfADeletedMessage_returnsTheDeletedPlaceholder() {
            // Arrange
            ChatMessage stored = givenSent("nonce-deleted");
            stored.setDeleted(true);
            ChatMessageCreateRequest request = new ChatMessageCreateRequest("Release is out", channelId, null, null,
                    null, null, "nonce-deleted");

            // Act
            ChatMessageResponse response = service.postMessage(author, organizationId, request, null);

            // Assert
            assertThat(response.id()).isEqualTo(stored.getId());
            assertThat(response.deleted()).isTrue();
            assertThat(response.content()).isEmpty();
            assertThat(response.author()).isNull();
            verify(chatMessageRepository, never()).saveAndFlush(any());
            verify(applicationEventPublisher, never()).publishEvent(any());
        }

        @Test
        void sameNonceAfterLosingChannelAccess_isNotFoundAndStoresNothing() {
            // Arrange
            ChatMessage stored = givenSent("nonce-lost");
            when(chatChannelQueryService.verifyChannelAccess(organizationId, author.getId(), stored.getChannelId()))
                    .thenThrow(new NotFoundException(ExceptionDomainKazanError.NOT_CHAT_CHANNEL_FOUND));
            ChatMessageCreateRequest request = new ChatMessageCreateRequest("Release is out", channelId, null, null,
                    null, null, "nonce-lost");

            // Act and Assert
            assertThatThrownBy(() -> service.postMessage(author, organizationId, request, null))
                    .isInstanceOf(NotFoundException.class);
            verify(chatMessageRepository, never()).saveAndFlush(any());
        }

        @Test
        void concurrentSendWonTheNonceIndex_returnsTheWinnersMessage() {
            // Arrange
            ChatMessage winner = ChatMocks.message(author, organizationId, "Release is out");
            winner.setChannelId(channelId);
            winner.setClientNonce("nonce-race");
            when(chatMessageRepository.findByAuthorIdAndClientNonceWithDeleted(author.getId(), "nonce-race"))
                    .thenReturn(Optional.empty(), Optional.of(winner));
            when(chatMessageRepository.saveAndFlush(any(ChatMessage.class)))
                    .thenThrow(violationOf(ChatConstant.CLIENT_NONCE_UNIQUE_INDEX));
            MultipartFile file = new MockMultipartFile("files", "plan.pdf", "application/pdf", new byte[]{1, 2});
            ChatMessageCreateRequest request = new ChatMessageCreateRequest("Release is out", channelId, null, null,
                    null, null, "nonce-race");

            // Act
            ChatMessageResponse response = service.postMessage(author, organizationId, request, List.of(file));

            // Assert
            assertThat(response.id()).isEqualTo(winner.getId());
            assertThat(response.clientNonce()).isEqualTo("nonce-race");
            verify(transactionManager).rollback(any());
            verify(attachmentService, never()).uploadAttachments(any(), any(), anyList(), anyList());
            verify(applicationEventPublisher, never()).publishEvent(any());
        }

        @Test
        void otherIntegrityViolation_isRethrown() {
            // Arrange
            DataIntegrityViolationException violation = violationOf("chat_messages_daily_update_id_uk");
            when(chatMessageRepository.saveAndFlush(any(ChatMessage.class))).thenThrow(violation);
            ChatMessageCreateRequest request = new ChatMessageCreateRequest("Release is out", channelId, null, null,
                    null, null, "nonce-other");

            // Act and Assert
            assertThatThrownBy(() -> service.postMessage(author, organizationId, request, null))
                    .isSameAs(violation);
            verify(chatMessageRepository, times(1)).findByAuthorIdAndClientNonceWithDeleted(any(), any());
        }

        @Test
        void integrityViolationWithoutConstraintName_isRethrown() {
            // Arrange
            DataIntegrityViolationException violation = new DataIntegrityViolationException("value too long");
            when(chatMessageRepository.saveAndFlush(any(ChatMessage.class))).thenThrow(violation);
            ChatMessageCreateRequest request = new ChatMessageCreateRequest("Release is out", channelId, null, null,
                    null, null, "nonce-plain");

            // Act and Assert
            assertThatThrownBy(() -> service.postMessage(author, organizationId, request, null))
                    .isSameAs(violation);
        }

        @Test
        void nonceIndexViolationInsideACallersTransaction_isRethrown() {
            // Arrange
            DataIntegrityViolationException violation = violationOf(ChatConstant.CLIENT_NONCE_UNIQUE_INDEX);
            when(chatMessageRepository.saveAndFlush(any(ChatMessage.class))).thenThrow(violation);
            ChatMessageCreateRequest request = new ChatMessageCreateRequest("Release is out", channelId, null, null,
                    null, null, "nonce-outer");
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                // Act and Assert
                assertThatThrownBy(() -> service.postMessage(author, organizationId, request, null))
                        .isSameAs(violation);
                verify(chatMessageRepository, times(1)).findByAuthorIdAndClientNonceWithDeleted(any(), any());
            } finally {
                TransactionSynchronizationManager.setActualTransactionActive(false);
            }
        }

        @Test
        void nonceIndexViolationWithoutAStoredWinner_isRethrown() {
            // Arrange
            DataIntegrityViolationException violation = violationOf(ChatConstant.CLIENT_NONCE_UNIQUE_INDEX);
            when(chatMessageRepository.saveAndFlush(any(ChatMessage.class))).thenThrow(violation);
            ChatMessageCreateRequest request = new ChatMessageCreateRequest("Release is out", channelId, null, null,
                    null, null, "nonce-gone");

            // Act and Assert
            assertThatThrownBy(() -> service.postMessage(author, organizationId, request, null))
                    .isSameAs(violation);
        }
    }

    @Nested
    class EditMessage {

        @Test
        void replyInClosedThread_isRefusedAndKept() {
            // Arrange
            ChatThread thread = ChatMocks.thread(organizationId, UUID.randomUUID(), author.getId());
            thread.setStatus(ChatThreadStatus.CLOSED);
            ChatMessage message = givenStored(ChatMocks.message(author, organizationId, "before"));
            message.setThread(thread);

            // Act and Assert
            assertThatThrownBy(() -> service.editMessage(author, organizationId, message.getId(),
                    new ChatMessageUpdateRequest("after")))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.STA_CHAT_THREAD_CLOSED.getCode());
            assertThat(message.getContent()).isEqualTo("before");
            verify(chatMessageRepository, never()).save(any());
        }

        @Test
        void byAuthor_replacesTextSetsEditedAtAndAddsOnlyNewMentionsToThread() {
            // Arrange
            ChatThread thread = ChatMocks.thread(organizationId, UUID.randomUUID(), author.getId());
            ChatMessage message = givenStored(ChatMocks.message(author, organizationId,
                    "hi " + ChatMocks.userToken(author.getId())));
            message.setThread(thread);
            ChatMocks.mention(message, author.getId(), false);
            ChatMessageUpdateRequest request = new ChatMessageUpdateRequest(
                    "hi " + ChatMocks.userToken(author.getId()) + " " + ChatMocks.userToken(colleague.getId()));

            // Act
            ChatMessageResponse response = service.editMessage(author, organizationId, message.getId(), request);

            // Assert
            assertThat(response.editedAt()).isNotNull();
            assertThat(message.getMentions()).extracting(ChatMessageMention::getUserId)
                    .containsExactly(author.getId(), colleague.getId());
            verify(chatThreadMemberRepository).insertIfAbsent(thread.getId(), colleague.getId());
            verify(chatThreadMemberRepository, never()).insertIfAbsent(thread.getId(), author.getId());
            ChatMessageChangedEvent event = publishedEvent(ChatMessageChangedEvent.class);
            assertThat(event.type()).isEqualTo(ChatMessageChangedEvent.Type.UPDATED);
            assertThat(event.clientNonce()).isNull();
            ChatNotificationsChangedEvent notifications = publishedEvent(ChatNotificationsChangedEvent.class);
            assertThat(notifications.userIds()).containsExactly(colleague.getId());
            assertThat(notifications.threadId()).isEqualTo(thread.getId());
            assertThat(notifications.broadcast()).isFalse();
        }

        @Test
        void removedMention_publishesNotificationChangeForThatUser() {
            // Arrange
            ChatMessage message = givenStored(ChatMocks.message(author, organizationId,
                    "hi " + ChatMocks.userToken(colleague.getId())));
            ChatMocks.mention(message, colleague.getId(), false);

            // Act
            service.editMessage(author, organizationId, message.getId(), new ChatMessageUpdateRequest("hi"));

            // Assert
            ChatNotificationsChangedEvent notifications = publishedEvent(ChatNotificationsChangedEvent.class);
            assertThat(notifications.messageId()).isEqualTo(message.getId());
            assertThat(notifications.authorId()).isEqualTo(author.getId());
            assertThat(notifications.threadId()).isNull();
            assertThat(notifications.userIds()).containsExactly(colleague.getId());
            assertThat(notifications.broadcast()).isFalse();
        }

        @Test
        void addedEveryone_publishesBroadcast() {
            // Arrange
            ChatMessage message = givenStored(ChatMocks.message(author, organizationId, "hi"));

            // Act
            service.editMessage(author, organizationId, message.getId(),
                    new ChatMessageUpdateRequest("hi <@everyone>"));

            // Assert
            ChatNotificationsChangedEvent notifications = publishedEvent(ChatNotificationsChangedEvent.class);
            assertThat(notifications.broadcast()).isTrue();
            assertThat(notifications.userIds()).isEmpty();
        }

        @Test
        void removedEveryone_publishesBroadcast() {
            // Arrange
            ChatMessage message = givenStored(ChatMocks.message(author, organizationId, "hi <@everyone>"));
            message.setMentionsEveryone(true);

            // Act
            service.editMessage(author, organizationId, message.getId(), new ChatMessageUpdateRequest("hi"));

            // Assert
            assertThat(publishedEvent(ChatNotificationsChangedEvent.class).broadcast()).isTrue();
        }

        @Test
        void sameMentionsAndKeptHere_publishNoNotificationChange() {
            // Arrange
            ChatMessage message = givenStored(ChatMocks.message(author, organizationId,
                    "hi " + ChatMocks.userToken(colleague.getId()) + " <@here>"));
            message.setMentionsHere(true);
            ChatMocks.mention(message, colleague.getId(), false);
            ChatMocks.mention(message, outsider.getId(), true);

            // Act
            service.editMessage(author, organizationId, message.getId(), new ChatMessageUpdateRequest(
                    "hello " + ChatMocks.userToken(colleague.getId()) + " <@here>"));

            // Assert
            assertThat(publishedEvents(1)).noneMatch(ChatNotificationsChangedEvent.class::isInstance);
            assertThat(message.getMentions()).extracting(ChatMessageMention::getUserId)
                    .containsExactly(colleague.getId(), outsider.getId());
        }

        @Test
        void byNonAuthor_isForbidden() {
            // Arrange
            ChatMessage message = givenStored(ChatMocks.message(colleague, organizationId, "mine"));

            // Act and Assert
            assertThatThrownBy(() -> service.editMessage(author, organizationId, message.getId(),
                    new ChatMessageUpdateRequest("changed")))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.SEC_CANNOT_MODIFY_OTHERS_MESSAGE.getCode());
            verify(chatMessageRepository, never()).save(any());
        }

        @Test
        void blankTextBesideFiles_readsTheFilesUnderAReadGrantForTheMessage() {
            // Arrange
            ChatMessage message = givenStored(ChatMocks.message(author, organizationId, "caption"));
            AttachmentResponse file = new AttachmentResponse(UUID.randomUUID(), "https://files/x", "plan.pdf",
                    "application/pdf", 10L, StorageEntityType.CHAT_MESSAGE, message.getId(), Instant.now(), null, null);
            when(attachmentQueryService.listAttachmentsByEntity(author, organizationId, StorageEntityType.CHAT_MESSAGE,
                    message.getId())).thenReturn(List.of(file));

            // Act
            service.editMessage(author, organizationId, message.getId(), new ChatMessageUpdateRequest(""));

            // Assert
            InOrder order = inOrder(resourceAccessQueryService, attachmentQueryService);
            order.verify(resourceAccessQueryService).withReadGrant(eq(author), eq(ResourceAccessType.CHAT_MESSAGE),
                    eq(List.of(message.getId())), any());
            order.verify(attachmentQueryService).listAttachmentsByEntity(author, organizationId, StorageEntityType.CHAT_MESSAGE,
                    message.getId());
        }

        @Test
        void dailyUpdateMessage_isReadOnly() {
            // Arrange
            ChatMessage message = givenStored(ChatMocks.dailyUpdateMessage(author, organizationId, UUID.randomUUID()));

            // Act and Assert
            assertThatThrownBy(() -> service.editMessage(author, organizationId, message.getId(),
                    new ChatMessageUpdateRequest("changed")))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_CHAT_DAILY_UPDATE_MESSAGE_READ_ONLY.getCode());
        }
    }

    @Nested
    class DeleteMessage {

        @Test
        void replyInClosedThread_isStillDeleted() {
            // Arrange
            ChatThread thread = ChatMocks.thread(organizationId, UUID.randomUUID(), author.getId());
            thread.setStatus(ChatThreadStatus.CLOSED);
            ChatMessage message = givenStored(ChatMocks.message(author, organizationId, "reply"));
            message.setThread(thread);

            // Act
            service.deleteMessage(author, organizationId, message.getId());

            // Assert
            verify(chatMessageRepository).deleteByIdWithoutRelations(message.getId());
            verify(chatThreadRepository).decrementMessageCount(thread.getId());
        }

        @Test
        void threadRoot_isSoftDeletedAndItsThreadKept() {
            // Arrange
            ChatMessage root = givenStored(ChatMocks.message(author, organizationId, "root"));

            // Act
            service.deleteMessage(author, organizationId, root.getId());

            // Assert
            verify(chatMessageRepository).deleteByIdWithoutRelations(root.getId());
            verify(chatThreadRepository, never()).delete(any());
            verify(chatThreadRepository, never()).deleteById(any());
            ChatMessageChangedEvent event = publishedEvent(ChatMessageChangedEvent.class);
            assertThat(event.type()).isEqualTo(ChatMessageChangedEvent.Type.DELETED);
        }

        @Test
        void pinnedThreadMessage_decrementsCountUnpinsAndPublishesBoth() {
            // Arrange
            ChatThread thread = ChatMocks.thread(organizationId, UUID.randomUUID(), author.getId());
            ChatMessage message = givenStored(ChatMocks.message(author, organizationId, "reply"));
            message.setThread(thread);
            message.setPinnedAt(Instant.now());
            message.setPinnedBy(author.getId());

            // Act
            service.deleteMessage(author, organizationId, message.getId());

            // Assert
            verify(chatThreadRepository).decrementMessageCount(thread.getId());
            assertThat(message.getPinnedAt()).isNull();
            assertThat(publishedEvents(2)).extracting(event -> ((ChatMessageChangedEvent) event).type())
                    .containsExactly(ChatMessageChangedEvent.Type.DELETED, ChatMessageChangedEvent.Type.UNPINNED);
        }

        @Test
        void byNonAuthorMember_isForbiddenWithTheDeleteCode() {
            // Arrange
            ChatMessage message = givenStored(ChatMocks.message(colleague, organizationId, "mine"));
            givenRole(author, OrganizationMemberRole.MEMBER);

            // Act and Assert
            assertThatThrownBy(() -> service.deleteMessage(author, organizationId, message.getId()))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.SEC_CANNOT_DELETE_OTHERS_MESSAGE.getCode());
            verify(chatMessageRepository, never()).deleteByIdWithoutRelations(any());
            verify(applicationEventPublisher, never()).publishEvent(any());
        }

        @Test
        void byAdmin_deletesAnothersMessageAndPublishesDeleted() {
            // Arrange
            ChatMessage message = givenStored(ChatMocks.message(colleague, organizationId, "off topic"));
            givenRole(author, OrganizationMemberRole.ADMIN);

            // Act
            service.deleteMessage(author, organizationId, message.getId());

            // Assert
            verify(chatMessageRepository).deleteByIdWithoutRelations(message.getId());
            assertThat(publishedEvent(ChatMessageChangedEvent.class).type())
                    .isEqualTo(ChatMessageChangedEvent.Type.DELETED);
        }

        @Test
        void byOwner_deletesAnothersMessageAndPublishesDeleted() {
            // Arrange
            ChatMessage message = givenStored(ChatMocks.message(colleague, organizationId, "off topic"));
            givenRole(author, OrganizationMemberRole.OWNER);

            // Act
            service.deleteMessage(author, organizationId, message.getId());

            // Assert
            verify(chatMessageRepository).deleteByIdWithoutRelations(message.getId());
            assertThat(publishedEvent(ChatMessageChangedEvent.class).type())
                    .isEqualTo(ChatMessageChangedEvent.Type.DELETED);
        }

        @Test
        void byAuthorMember_deletesOwnMessage() {
            // Arrange
            ChatMessage message = givenStored(ChatMocks.message(author, organizationId, "typo"));
            givenRole(author, OrganizationMemberRole.MEMBER);

            // Act
            service.deleteMessage(author, organizationId, message.getId());

            // Assert
            verify(chatMessageRepository).deleteByIdWithoutRelations(message.getId());
            assertThat(publishedEvent(ChatMessageChangedEvent.class).type())
                    .isEqualTo(ChatMessageChangedEvent.Type.DELETED);
        }

        @Test
        void dailyUpdateMessage_isReadOnly() {
            // Arrange
            ChatMessage message = givenStored(ChatMocks.dailyUpdateMessage(author, organizationId, UUID.randomUUID()));

            // Act and Assert
            assertThatThrownBy(() -> service.deleteMessage(author, organizationId, message.getId()))
                    .isInstanceOf(ValidationException.class);
            verify(chatMessageRepository, never()).deleteByIdWithoutRelations(any());
        }
    }

    @Nested
    class SetPinned {

        /**
         * Lets the conditional pin statements act on the stored message as
         * the database would: they change it only when its pin differs.
         */
        private void givenPinStatements(ChatMessage message) {
            lenient().when(chatMessageRepository.pinIfUnpinned(eq(message.getId()), any(), any()))
                    .thenAnswer(invocation -> {
                        if (message.getPinnedAt() != null) {
                            return 0;
                        }
                        message.setPinnedAt(invocation.getArgument(1));
                        message.setPinnedBy(invocation.getArgument(2));
                        return 1;
                    });
            lenient().when(chatMessageRepository.unpinIfPinned(message.getId())).thenAnswer(invocation -> {
                if (message.getPinnedAt() == null) {
                    return 0;
                }
                message.setPinnedAt(null);
                message.setPinnedBy(null);
                return 1;
            });
        }

        @Test
        void byAuthor_pinsAndPublishes() {
            // Arrange
            ChatMessage message = givenStored(ChatMocks.message(author, organizationId, "note"));
            givenPinStatements(message);
            givenRole(author, OrganizationMemberRole.MEMBER);

            // Act
            ChatMessageResponse response = service.setPinned(author, organizationId, message.getId(), true);

            // Assert
            assertThat(response.pinnedAt()).isNotNull();
            verify(chatMessageRepository).pinIfUnpinned(eq(message.getId()), any(), eq(author.getId()));
            verify(chatMessageRepository, never()).save(any());
            assertThat(publishedEvent(ChatMessageChangedEvent.class).type())
                    .isEqualTo(ChatMessageChangedEvent.Type.PINNED);
        }

        @Test
        void byAdmin_pinsAnothersMessage() {
            // Arrange
            ChatMessage message = givenStored(ChatMocks.message(colleague, organizationId, "note"));
            givenPinStatements(message);
            givenRole(author, OrganizationMemberRole.ADMIN);

            // Act
            ChatMessageResponse response = service.setPinned(author, organizationId, message.getId(), true);

            // Assert
            assertThat(response.pinnedAt()).isNotNull();
            verify(chatMessageRepository).pinIfUnpinned(eq(message.getId()), any(), eq(author.getId()));
        }

        @Test
        void byMemberNotAuthor_isForbidden() {
            // Arrange
            ChatMessage message = givenStored(ChatMocks.message(colleague, organizationId, "note"));
            givenRole(author, OrganizationMemberRole.MEMBER);

            // Act and Assert
            assertThatThrownBy(() -> service.setPinned(author, organizationId, message.getId(), true))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting("errorCode").isEqualTo(ExceptionDomainKazanError.SEC_CANNOT_PIN_MESSAGE.getCode());
            verify(chatMessageRepository, never()).pinIfUnpinned(any(), any(), any());
        }

        @Test
        void fiftyFirstPin_isRefusedBeforeThePin() {
            // Arrange
            ChatMessage message = givenStored(ChatMocks.message(author, organizationId, "note"));
            givenRole(author, OrganizationMemberRole.MEMBER);
            when(chatMessageRepository.countPinnedMessages(message.getChannelId())).thenReturn(50L);

            // Act and Assert
            assertThatThrownBy(() -> service.setPinned(author, organizationId, message.getId(), true))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_CHAT_PIN_LIMIT_REACHED.getCode());
            verify(chatMessageRepository, never()).pinIfUnpinned(any(), any(), any());
        }

        @Test
        void pinningPinnedMessage_changesNothing() {
            // Arrange
            ChatMessage message = givenStored(ChatMocks.message(author, organizationId, "note"));
            message.setPinnedAt(Instant.parse("2026-10-20T08:00:00Z"));
            givenRole(author, OrganizationMemberRole.MEMBER);

            // Act
            service.setPinned(author, organizationId, message.getId(), true);

            // Assert
            assertThat(message.getPinnedAt()).isEqualTo(Instant.parse("2026-10-20T08:00:00Z"));
            verify(chatMessageRepository, never()).pinIfUnpinned(any(), any(), any());
            verify(applicationEventPublisher, never()).publishEvent(any());
        }

        @Test
        void pinTwiceAtTheSameMoment_publishesOnce() {
            // Arrange: both requests read the message before either pinned it,
            // so only the conditional statement tells them apart.
            ChatMessage message = givenStored(ChatMocks.message(author, organizationId, "note"));
            givenRole(author, OrganizationMemberRole.MEMBER);
            givenRole(colleague, OrganizationMemberRole.ADMIN);
            when(chatMessageRepository.pinIfUnpinned(eq(message.getId()), any(), any())).thenReturn(1, 0);

            // Act
            service.setPinned(author, organizationId, message.getId(), true);
            service.setPinned(colleague, organizationId, message.getId(), true);

            // Assert
            verify(chatMessageRepository, times(2)).pinIfUnpinned(eq(message.getId()), any(), any());
            assertThat(publishedEvents(1)).extracting(event -> ((ChatMessageChangedEvent) event).type())
                    .containsExactly(ChatMessageChangedEvent.Type.PINNED);
        }

        @Test
        void unpin_clearsPinAndPublishes() {
            // Arrange
            ChatMessage message = givenStored(ChatMocks.message(author, organizationId, "note"));
            message.setPinnedAt(Instant.now());
            message.setPinnedBy(author.getId());
            givenPinStatements(message);
            givenRole(author, OrganizationMemberRole.MEMBER);

            // Act
            ChatMessageResponse response = service.setPinned(author, organizationId, message.getId(), false);

            // Assert
            assertThat(response.pinnedAt()).isNull();
            verify(chatMessageRepository).unpinIfPinned(message.getId());
            assertThat(publishedEvent(ChatMessageChangedEvent.class).type())
                    .isEqualTo(ChatMessageChangedEvent.Type.UNPINNED);
        }

        @Test
        void unpinAlreadyUnpinnedByAnother_publishesNothing() {
            // Arrange
            ChatMessage message = givenStored(ChatMocks.message(author, organizationId, "note"));
            message.setPinnedAt(Instant.now());
            givenRole(author, OrganizationMemberRole.MEMBER);
            when(chatMessageRepository.unpinIfPinned(message.getId())).thenReturn(0);

            // Act
            service.setPinned(author, organizationId, message.getId(), false);

            // Assert
            verify(applicationEventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    class SignalTyping {

        @Test
        void member_publishesTypingToTheChannelTopic() {
            // Act
            service.signalTyping(author.getId(), organizationId, channelId, null);

            // Assert
            verify(chatChannelQueryService).verifyChannelAccess(organizationId, author.getId(), channelId);
            verify(realtimeSignalBus).publishToTopic(channelTopic(), ChatTypingSignal.of(author.getId(), null));
        }

        @Test
        void memberInThread_checksTheThreadAndPublishes() {
            // Arrange
            ChatThread thread = givenThread();

            // Act
            service.signalTyping(author.getId(), organizationId, channelId, thread.getId());

            // Assert
            verify(realtimeSignalBus).publishToTopic(channelTopic(), ChatTypingSignal.of(author.getId(),
                    thread.getId()));
        }

        @Test
        void threadOfAnotherChannel_isNotFoundAndPublishesNothing() {
            // Arrange
            ChatThread thread = givenThread();
            thread.setChannelId(UUID.randomUUID());

            // Act and Assert
            assertThatThrownBy(() -> service.signalTyping(author.getId(), organizationId, channelId, thread.getId()))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.NOT_CHAT_THREAD_FOUND.getCode());
            verify(realtimeSignalBus, never()).publishToTopic(any(), any());
        }

        private String channelTopic() {
            return RealtimeDestinationUtil.organizationTopic(organizationId,
                    ChatConstant.CHAT_CHANNEL_TOPIC_SUFFIX_PREFIX + channelId);
        }

        @Test
        void unreadableChannel_isRefusedAndPublishesNothing() {
            // Arrange
            givenChannelRefused();

            // Act and Assert
            assertThatThrownBy(() -> service.signalTyping(author.getId(), organizationId, channelId, null))
                    .isInstanceOf(NotFoundException.class);
            verify(realtimeSignalBus, never()).publishToTopic(any(), any());
        }

        @Test
        void threadOfAnotherOrganization_isNotFoundAndPublishesNothing() {
            // Arrange
            UUID foreignThreadId = UUID.randomUUID();
            when(chatThreadRepository.findByIdAndOrganizationId(foreignThreadId, organizationId))
                    .thenReturn(Optional.empty());

            // Act and Assert
            assertThatThrownBy(() -> service.signalTyping(author.getId(), organizationId, channelId, foreignThreadId))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.NOT_CHAT_THREAD_FOUND.getCode());
            verify(realtimeSignalBus, never()).publishToTopic(any(), any());
        }
    }

    @Nested
    class PostDailyUpdateEntry {

        private final UUID dailyUpdateId = UUID.randomUUID();

        private void givenOwnUpdate() {
            DailyUpdate dailyUpdate = DailyUpdate.builder().author(author).organizationId(organizationId).build();
            dailyUpdate.setId(dailyUpdateId);
            when(dailyUpdateQueryService.findDailyUpdate(dailyUpdateId)).thenReturn(Optional.of(dailyUpdate));
        }

        @Test
        void ownUpdateInTheDailyUpdatesChannel_isPostedWithoutText() {
            // Arrange
            givenChannel(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.DAILY_UPDATES);
            givenOwnUpdate();
            ChatMessageCreateRequest request = new ChatMessageCreateRequest(null, channelId, null, null,
                    dailyUpdateId, null, dailyUpdateId.toString());

            // Act
            ChatMessageResponse response = service.postMessage(author, organizationId, request, List.of());

            // Assert
            assertThat(response.dailyUpdateId()).isEqualTo(dailyUpdateId);
            assertThat(response.channelId()).isEqualTo(channelId);
            assertThat(publishedEvent(ChatMessageChangedEvent.class).type())
                    .isEqualTo(ChatMessageChangedEvent.Type.CREATED);
        }

        @Test
        void otherChannel_isRefusedBeforeAnythingIsSaved() {
            // Arrange
            givenChannel(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL);
            givenOwnUpdate();
            ChatMessageCreateRequest request = new ChatMessageCreateRequest(null, channelId, null, null,
                    dailyUpdateId, null, "n");

            // Act and Assert
            assertThatThrownBy(() -> service.postMessage(author, organizationId, request, List.of()))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_CHAT_DAILY_UPDATE_CHANNEL.getCode());
            verify(chatMessageRepository, never()).saveAndFlush(any());
        }

        @Test
        void anotherAuthorsUpdate_isForbiddenAndNothingIsSaved() {
            // Arrange
            givenChannel(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.DAILY_UPDATES);
            DailyUpdate dailyUpdate = DailyUpdate.builder().author(colleague).organizationId(organizationId).build();
            dailyUpdate.setId(dailyUpdateId);
            when(dailyUpdateQueryService.findDailyUpdate(dailyUpdateId)).thenReturn(Optional.of(dailyUpdate));
            ChatMessageCreateRequest request = new ChatMessageCreateRequest(null, channelId, null, null,
                    dailyUpdateId, null, "n");

            // Act and Assert
            assertThatThrownBy(() -> service.postMessage(author, organizationId, request, List.of()))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.SEC_CANNOT_POST_OTHERS_UPDATE.getCode());
            verify(chatMessageRepository, never()).saveAndFlush(any());
        }

        @Test
        void updateOfAnotherOrganization_isNotFoundAndNothingIsSaved() {
            // Arrange
            givenChannel(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.DAILY_UPDATES);
            DailyUpdate dailyUpdate = DailyUpdate.builder().author(author).organizationId(UUID.randomUUID()).build();
            dailyUpdate.setId(dailyUpdateId);
            when(dailyUpdateQueryService.findDailyUpdate(dailyUpdateId)).thenReturn(Optional.of(dailyUpdate));
            ChatMessageCreateRequest request = new ChatMessageCreateRequest(null, channelId, null, null,
                    dailyUpdateId, null, "n");

            // Act and Assert
            assertThatThrownBy(() -> service.postMessage(author, organizationId, request, List.of()))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.NOT_DAILY_UPDATE_FOUND.getCode());
            verify(chatMessageRepository, never()).saveAndFlush(any());
        }

        @Test
        void secondEntryForTheSameUpdate_isAConflictAndNothingIsSaved() {
            // Arrange
            givenChannel(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.DAILY_UPDATES);
            givenOwnUpdate();
            when(chatMessageRepository.existsByDailyUpdateIdWithDeleted(dailyUpdateId)).thenReturn(true);
            ChatMessageCreateRequest request = new ChatMessageCreateRequest(null, channelId, null, null,
                    dailyUpdateId, null, "n");

            // Act and Assert
            assertThatThrownBy(() -> service.postMessage(author, organizationId, request, List.of()))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.STA_CHAT_DAILY_UPDATE_POSTED.getCode());
            verify(chatMessageRepository, never()).saveAndFlush(any());
        }

        @Test
        void emptyTextWithoutADailyUpdate_isRefused() {
            // Arrange
            givenChannel(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.DAILY_UPDATES);
            ChatMessageCreateRequest request = new ChatMessageCreateRequest(null, channelId, null, null, null, null, "n");

            // Act and Assert
            assertThatThrownBy(() -> service.postMessage(author, organizationId, request, List.of()))
                    .isInstanceOf(ValidationException.class);
            verify(chatMessageRepository, never()).saveAndFlush(any());
        }

        @Test
        void insideAThread_isRefusedBeforeAnythingIsSaved() {
            // Arrange
            givenChannel(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.DAILY_UPDATES);
            ChatThread thread = givenThread();
            ChatMessageCreateRequest request = new ChatMessageCreateRequest(null, channelId, thread.getId(), null,
                    dailyUpdateId, null, "n");

            // Act and Assert
            assertThatThrownBy(() -> service.postMessage(author, organizationId, request, List.of()))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_CHAT_DAILY_UPDATE_CHANNEL.getCode());
            verify(chatMessageRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    class TouchDailyUpdateMessage {

        @Test
        void existingEntry_takesTheEditTimeAndPublishesUpdated() {
            // Arrange
            UUID dailyUpdateId = UUID.randomUUID();
            Instant editedAt = Instant.parse("2026-10-21T10:00:00Z");
            DailyUpdate dailyUpdate = DailyUpdate.builder().author(author).organizationId(organizationId)
                    .editedAt(editedAt).build();
            dailyUpdate.setId(dailyUpdateId);
            ChatMessage entry = ChatMocks.dailyUpdateMessage(author, organizationId, dailyUpdateId);
            when(chatMessageRepository.findByDailyUpdateId(dailyUpdateId)).thenReturn(Optional.of(entry));

            // Act
            service.touchDailyUpdateMessage(dailyUpdate);

            // Assert
            assertThat(entry.getEditedAt()).isEqualTo(editedAt);
            verify(chatMessageRepository).save(entry);
            ChatMessageChangedEvent event = publishedEvent(ChatMessageChangedEvent.class);
            assertThat(event.type()).isEqualTo(ChatMessageChangedEvent.Type.UPDATED);
            assertThat(event.messageId()).isEqualTo(entry.getId());
        }

        @Test
        void noEntry_changesNothing() {
            // Arrange
            DailyUpdate dailyUpdate = DailyUpdate.builder().author(author).build();
            dailyUpdate.setId(UUID.randomUUID());
            when(chatMessageRepository.findByDailyUpdateId(dailyUpdate.getId())).thenReturn(Optional.empty());

            // Act
            service.touchDailyUpdateMessage(dailyUpdate);

            // Assert
            verify(chatMessageRepository, never()).save(any());
            verify(applicationEventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    class RemoveDailyUpdateMessage {

        @Test
        void existingEntry_isSoftDeletedAndPublishesDeleted() {
            // Arrange
            UUID dailyUpdateId = UUID.randomUUID();
            DailyUpdate dailyUpdate = DailyUpdate.builder().author(author).organizationId(organizationId).build();
            dailyUpdate.setId(dailyUpdateId);
            ChatMessage entry = ChatMocks.dailyUpdateMessage(author, organizationId, dailyUpdateId);
            when(chatMessageRepository.findByDailyUpdateId(dailyUpdateId)).thenReturn(Optional.of(entry));

            // Act
            service.removeDailyUpdateMessage(dailyUpdate);

            // Assert
            verify(chatMessageRepository).deleteByIdWithoutRelations(entry.getId());
            assertThat(publishedEvents(1)).extracting(event -> ((ChatMessageChangedEvent) event).type())
                    .containsExactly(ChatMessageChangedEvent.Type.DELETED);
        }

        @Test
        void pinnedEntry_losesItsPinAndPublishesDeletedThenUnpinned() {
            // Arrange
            UUID dailyUpdateId = UUID.randomUUID();
            DailyUpdate dailyUpdate = DailyUpdate.builder().author(author).organizationId(organizationId).build();
            dailyUpdate.setId(dailyUpdateId);
            ChatMessage entry = ChatMocks.dailyUpdateMessage(author, organizationId, dailyUpdateId);
            entry.setPinnedAt(Instant.now());
            entry.setPinnedBy(colleague.getId());
            when(chatMessageRepository.findByDailyUpdateId(dailyUpdateId)).thenReturn(Optional.of(entry));

            // Act
            service.removeDailyUpdateMessage(dailyUpdate);

            // Assert
            assertThat(entry.getPinnedAt()).isNull();
            assertThat(entry.getPinnedBy()).isNull();
            verify(chatMessageRepository).saveAndFlush(entry);
            verify(chatMessageRepository).deleteByIdWithoutRelations(entry.getId());
            assertThat(publishedEvents(2)).extracting(event -> ((ChatMessageChangedEvent) event).type())
                    .containsExactly(ChatMessageChangedEvent.Type.DELETED, ChatMessageChangedEvent.Type.UNPINNED);
        }

        @Test
        void noEntry_changesNothing() {
            // Arrange
            DailyUpdate dailyUpdate = DailyUpdate.builder().author(author).build();
            dailyUpdate.setId(UUID.randomUUID());
            when(chatMessageRepository.findByDailyUpdateId(dailyUpdate.getId())).thenReturn(Optional.empty());

            // Act
            service.removeDailyUpdateMessage(dailyUpdate);

            // Assert
            verify(chatMessageRepository, never()).deleteByIdWithoutRelations(any());
            verify(applicationEventPublisher, never()).publishEvent(any());
        }
    }
}
