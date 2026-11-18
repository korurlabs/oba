package com.korur.oba.apps.kaizen.chat.service.impl;

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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.constant.ChatConstant;
import com.korur.oba.apps.kaizen.chat.dto.request.ChatMessageCreateRequest;
import com.korur.oba.apps.kaizen.chat.dto.request.ChatMessageUpdateRequest;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatMessageResponse;
import com.korur.oba.apps.kaizen.chat.dto.signal.ChatTypingSignal;
import com.korur.oba.apps.kaizen.chat.entity.ChatChannel;
import com.korur.oba.apps.kaizen.chat.entity.ChatMessage;
import com.korur.oba.apps.kaizen.chat.entity.ChatMessageMention;
import com.korur.oba.apps.kaizen.chat.entity.ChatThread;
import com.korur.oba.apps.kaizen.chat.enums.ChatChannelPurpose;
import com.korur.oba.apps.kaizen.chat.enums.ChatChannelVisibility;
import com.korur.oba.apps.kaizen.chat.enums.ChatThreadStatus;
import com.korur.oba.apps.kaizen.chat.event.ChatMessageChangedEvent;
import com.korur.oba.apps.kaizen.chat.event.ChatPushRequestedEvent;
import com.korur.oba.connect.push.enums.PushKind;
import com.korur.oba.apps.kaizen.chat.event.ChatNotificationsChangedEvent;
import com.korur.oba.apps.kaizen.chat.event.ChatReadStateChangedEvent;
import com.korur.oba.apps.kaizen.chat.event.ChatThreadChangedEvent;
import com.korur.oba.apps.kaizen.chat.helper.ChatSendRateLimiter;
import com.korur.oba.apps.kaizen.chat.helper.ChatThreadStatusResolver;
import com.korur.oba.apps.kaizen.chat.helper.ChatTokenParser;
import com.korur.oba.apps.kaizen.chat.mapper.ChatMessageMapperHelper;
import com.korur.oba.apps.kaizen.chat.mapper.ChatMessageMapperImpl;
import com.korur.oba.apps.kaizen.chat.mock_data.ChatMocks;
import com.korur.oba.apps.kaizen.chat.properties.ChatSendRateProperties;
import com.korur.oba.apps.kaizen.chat.repository.ChatChannelMemberRepository;
import com.korur.oba.apps.kaizen.chat.repository.ChatMessageMentionRepository;
import com.korur.oba.apps.kaizen.chat.repository.ChatMessageRepository;
import com.korur.oba.apps.kaizen.chat.repository.ChatThreadMemberRepository;
import com.korur.oba.apps.kaizen.chat.repository.ChatThreadRepository;
import com.korur.oba.apps.kaizen.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kaizen.chat.validator.ChatValidator;
import com.korur.oba.apps.kaizen.dailyupdate.entity.DailyUpdate;
import com.korur.oba.apps.kaizen.dailyupdate.service.DailyUpdateQueryService;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.apps.kaizen.ticket.mapper.TicketEmbeddedMapperImpl;
import com.korur.oba.apps.kaizen.ticket.service.TicketQueryService;
import com.korur.oba.connect.realtime.service.RealtimeSessionRegistry;
import com.korur.oba.connect.realtime.service.RealtimeSignalBus;
import com.korur.oba.connect.realtime.util.RealtimeDestinationUtil;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.TooManyRequestsException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
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
class ChatMessageServiceImplUnitPushTest {

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
    private final User offline = ChatMocks.user("Linus", "Torvalds");

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
                .thenReturn(List.of(ChatMocks.member(author), ChatMocks.member(colleague),
                        ChatMocks.member(outsider), ChatMocks.member(offline)));
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
                .thenThrow(new NotFoundException(ExceptionDomainKaizenError.NOT_CHAT_CHANNEL_FOUND));
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

    private ChatPushRequestedEvent pushEvent() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher, atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues().stream().filter(ChatPushRequestedEvent.class::isInstance)
                .map(ChatPushRequestedEvent.class::cast).findFirst().orElseThrow();
    }

    private void assertNoPush() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher, atLeastOnce()).publishEvent(captor.capture());
        assertThat(captor.getAllValues()).noneMatch(ChatPushRequestedEvent.class::isInstance);
    }

    private ChatMessageResponse post(String content, UUID threadId, UUID replyToMessageId) {
        return service.postMessage(author, organizationId,
                new ChatMessageCreateRequest(content, channelId, threadId, replyToMessageId, null, null, "n"), null);
    }

    @Nested
    class PushRecipients {

        @Test
        void directMention_pushesMentionToTheMentionedUserWithPlainTextAndTitle() {
            // Act
            post("ping " + ChatMocks.userToken(colleague.getId()) + " now", null, null);

            // Assert
            ChatPushRequestedEvent event = pushEvent();
            assertThat(event.recipients()).isEqualTo(Map.of(colleague.getId(), PushKind.MENTION));
            assertThat(event.authorId()).isEqualTo(author.getId());
            assertThat(event.organizationId()).isEqualTo(organizationId);
            assertThat(event.channelId()).isEqualTo(channelId);
            assertThat(event.threadId()).isNull();
            assertThat(event.conversation()).isEqualTo("#General");
            assertThat(event.senderName()).isEqualTo("Ada Lovelace");
            assertThat(event.text()).doesNotContain("<@").contains("ping").contains("now");
            assertThat(event.attachmentsOnly()).isFalse();
        }

        @Test
        void replyToColleaguesMessage_pushesReplyToTheQuotedAuthor() {
            // Arrange
            ChatMessage target = givenStored(ChatMocks.message(colleague, organizationId, "question"));

            // Act
            post("answer", null, target.getId());

            // Assert
            assertThat(pushEvent().recipients()).isEqualTo(Map.of(colleague.getId(), PushKind.REPLY));
        }

        @Test
        void replyToOwnMessage_pushesNothing() {
            // Arrange
            ChatMessage target = givenStored(ChatMocks.message(author, organizationId, "note"));

            // Act
            post("more", null, target.getId());

            // Assert
            assertNoPush();
        }

        @Test
        void mentionAndReplyToTheSameUser_collapseToTheMentionKind() {
            // Arrange
            ChatMessage target = givenStored(ChatMocks.message(colleague, organizationId, "question"));

            // Act
            post("see " + ChatMocks.userToken(colleague.getId()), null, target.getId());

            // Assert
            assertThat(pushEvent().recipients()).isEqualTo(Map.of(colleague.getId(), PushKind.MENTION));
        }

        @Test
        void everyone_pushesEveryoneToEveryReaderExceptTheAuthor() {
            // Act
            post("<@everyone> release is out", null, null);

            // Assert
            ChatPushRequestedEvent event = pushEvent();
            assertThat(event.recipients()).containsOnlyKeys(colleague.getId(), outsider.getId(), offline.getId());
            assertThat(event.recipients().values()).containsOnly(PushKind.EVERYONE);
        }

        @Test
        void everyoneWithADirectMention_keepsTheMentionKindForThatUser() {
            // Act
            post("<@everyone> " + ChatMocks.userToken(colleague.getId()), null, null);

            // Assert
            ChatPushRequestedEvent event = pushEvent();
            assertThat(event.recipients().get(colleague.getId())).isEqualTo(PushKind.MENTION);
            assertThat(event.recipients().get(outsider.getId())).isEqualTo(PushKind.EVERYONE);
        }

        @Test
        void here_pushesHereToOnlineReadersAndNotToOfflineOnes() {
            // Arrange
            when(realtimeSessionRegistry.onlineUserIds(organizationId))
                    .thenReturn(Set.of(outsider.getId(), author.getId()));

            // Act
            post("<@here> standup", null, null);

            // Assert
            assertThat(pushEvent().recipients()).isEqualTo(Map.of(outsider.getId(), PushKind.HERE));
        }

        @Test
        void hereWithNobodyOnlineButTheAuthor_pushesNothing() {
            // Arrange
            when(realtimeSessionRegistry.onlineUserIds(organizationId)).thenReturn(Set.of(author.getId()));

            // Act
            post("<@here> anyone?", null, null);

            // Assert
            assertNoPush();
        }

        @Test
        void plainMessage_pushesNothing() {
            // Act
            post("Release is out", null, null);

            // Assert
            assertNoPush();
        }

        @Test
        void editWithANewMention_pushesNothing() {
            // Arrange
            ChatMessage message = givenStored(ChatMocks.message(author, organizationId, "hi"));

            // Act
            service.editMessage(author, organizationId, message.getId(),
                    new ChatMessageUpdateRequest("hi " + ChatMocks.userToken(colleague.getId()) + " <@everyone>"));

            // Assert
            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(applicationEventPublisher, atLeastOnce()).publishEvent(captor.capture());
            assertThat(captor.getAllValues()).noneMatch(ChatPushRequestedEvent.class::isInstance);
        }

        @Test
        void privateChannel_everyoneReachesOnlyTheChannelMembers() {
            // Arrange
            givenChannel(ChatChannelVisibility.PRIVATE, ChatChannelPurpose.GENERAL);
            when(chatChannelMemberRepository.findUserIdsByChannelId(channelId))
                    .thenReturn(List.of(author.getId(), colleague.getId()));

            // Act
            post("<@everyone> private news", null, null);

            // Assert
            assertThat(pushEvent().recipients()).isEqualTo(Map.of(colleague.getId(), PushKind.EVERYONE));
        }

        @Test
        void privateChannel_repliedAuthorWhoLeftTheChannelHearsNothing() {
            // Arrange
            givenChannel(ChatChannelVisibility.PRIVATE, ChatChannelPurpose.GENERAL);
            when(chatChannelMemberRepository.findUserIdsByChannelId(channelId)).thenReturn(List.of(author.getId()));
            ChatMessage target = givenStored(ChatMocks.message(colleague, organizationId, "question"));

            // Act
            post("answer", null, target.getId());

            // Assert
            assertNoPush();
        }

        @Test
        void threadEveryone_reachesOnlyTheThreadMembersAndTitlesWithTheThread() {
            // Arrange
            ChatThread thread = givenThread();
            when(chatThreadMemberRepository.findUserIdsByThreadId(thread.getId()))
                    .thenReturn(List.of(author.getId(), colleague.getId()));

            // Act
            post("<@everyone> thread news", thread.getId(), null);

            // Assert
            ChatPushRequestedEvent event = pushEvent();
            assertThat(event.recipients()).isEqualTo(Map.of(colleague.getId(), PushKind.EVERYONE));
            assertThat(event.threadId()).isEqualTo(thread.getId());
            assertThat(event.conversation()).isEqualTo("Release checklist \u00b7 #General");
        }

        @Test
        void threadReply_pushesReplyToTheRootAuthorWhileAMemberOfTheThread() {
            // Arrange
            ChatThread thread = givenThread();
            ChatMessage root = givenStored(ChatMocks.message(colleague, organizationId, "root"));
            thread.setRootMessageId(root.getId());
            when(chatThreadMemberRepository.findUserIdsByThreadId(thread.getId()))
                    .thenReturn(List.of(author.getId(), colleague.getId()));

            // Act
            post("a reply", thread.getId(), null);

            // Assert
            assertThat(pushEvent().recipients()).isEqualTo(Map.of(colleague.getId(), PushKind.REPLY));
        }

        @Test
        void threadReply_rootAuthorWhoLeftTheThreadHearsNothing() {
            // Arrange
            ChatThread thread = givenThread();
            ChatMessage root = givenStored(ChatMocks.message(colleague, organizationId, "root"));
            thread.setRootMessageId(root.getId());
            when(chatThreadMemberRepository.findUserIdsByThreadId(thread.getId()))
                    .thenReturn(List.of(author.getId()));

            // Act
            post("a reply", thread.getId(), null);

            // Assert
            assertNoPush();
        }

        @Test
        void filesWithoutTextBesideAReply_marksTheMessageAsAttachmentsOnly() {
            // Arrange
            ChatMessage target = givenStored(ChatMocks.message(colleague, organizationId, "question"));
            MultipartFile file = new MockMultipartFile("files", "plan.pdf", "application/pdf", new byte[]{1, 2});

            // Act
            service.postMessage(author, organizationId,
                    new ChatMessageCreateRequest(null, channelId, null, target.getId(), null, null, "n"),
                    List.of(file));

            // Assert
            ChatPushRequestedEvent event = pushEvent();
            assertThat(event.attachmentsOnly()).isTrue();
            assertThat(event.text()).isEmpty();
            assertThat(event.recipients()).isEqualTo(Map.of(colleague.getId(), PushKind.REPLY));
        }

        @Test
        void textWithFiles_isNotAttachmentsOnly() {
            // Arrange
            ChatMessage target = givenStored(ChatMocks.message(colleague, organizationId, "question"));
            MultipartFile file = new MockMultipartFile("files", "plan.pdf", "application/pdf", new byte[]{1, 2});

            // Act
            service.postMessage(author, organizationId,
                    new ChatMessageCreateRequest("see attached", channelId, null, target.getId(), null, null, "n"),
                    List.of(file));

            // Assert
            assertThat(pushEvent().attachmentsOnly()).isFalse();
        }
    }
}
