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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

// 4. Internal
import com.korur.oba.apps.kazan.chat.dto.request.ChatMessageCreateRequest;
import com.korur.oba.apps.kazan.chat.dto.request.ChatMessageUpdateRequest;
import com.korur.oba.apps.kazan.chat.dto.response.ChatMessageResponse;
import com.korur.oba.apps.kazan.chat.entity.ChatChannel;
import com.korur.oba.apps.kazan.chat.entity.ChatMessage;
import com.korur.oba.apps.kazan.chat.entity.ChatMessageMention;
import com.korur.oba.apps.kazan.chat.enums.ChatChannelPurpose;
import com.korur.oba.apps.kazan.chat.enums.ChatChannelVisibility;
import com.korur.oba.apps.kazan.chat.event.ChatMessageChangedEvent;
import com.korur.oba.apps.kazan.chat.event.ChatNotificationsChangedEvent;
import com.korur.oba.apps.kazan.chat.event.ChatPushRequestedEvent;
import com.korur.oba.connect.push.enums.PushKind;
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
import com.korur.oba.apps.kazan.dailyupdate.service.DailyUpdateQueryService;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.pullrequest.entity.PullRequestShare;
import com.korur.oba.apps.kazan.pullrequest.mapper.PullRequestShareMapperImpl;
import com.korur.oba.apps.kazan.pullrequest.mock_data.PullRequestMocks;
import com.korur.oba.apps.kazan.pullrequest.service.PullRequestShareQueryService;
import com.korur.oba.apps.kazan.ticket.mapper.TicketEmbeddedMapperImpl;
import com.korur.oba.apps.kazan.ticket.service.TicketQueryService;
import com.korur.oba.connect.realtime.service.RealtimeSessionRegistry;
import com.korur.oba.connect.realtime.service.RealtimeSignalBus;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.service.ResourceAccessQueryService;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.service.UserQueryService;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;
import com.korur.oba.shrine.storage.service.attachment.AttachmentQueryService;
import com.korur.oba.shrine.storage.service.attachment.AttachmentService;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceImplUnitPullRequestShareTest {

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

    @Mock
    private PullRequestShareQueryService pullRequestShareQueryService;

    private ChatMessageServiceImpl service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID channelId = UUID.randomUUID();
    private final User author = ChatMocks.user("Ada", "Lovelace");
    private final User reviewerOne = ChatMocks.user("Alan", "Turing");
    private final User reviewerTwo = ChatMocks.user("Grace", "Hopper");
    private final PullRequestShareMapperImpl shareMapper = new PullRequestShareMapperImpl();

    @BeforeEach
    void setUp() {
        LoggingService loggingService = new LoggingService();
        ChatMessageMapperImpl mapper = new ChatMessageMapperImpl(new TicketEmbeddedMapperImpl());
        ChatTokenParser tokenParser = new ChatTokenParser();
        ChatValidator validator = new ChatValidator(chatMessageRepository, chatThreadRepository,
                dailyUpdateQueryService, pullRequestShareQueryService, appAccessValidator, loggingService);
        ChatMessageMapperHelper mapperHelper = new ChatMessageMapperHelper(mapper, tokenParser,
                resourceAccessQueryService, chatMessageRepository, chatMessageMentionRepository,
                chatThreadMemberRepository, ticketQueryService, userQueryService, organizationMemberQueryService,
                attachmentQueryService, dailyUpdateQueryService, pullRequestShareQueryService);
        ChatSendRateLimiter rateLimiter = new ChatSendRateLimiter(new ChatSendRateProperties(), loggingService);
        service = new ChatMessageServiceImpl(chatMessageRepository, chatThreadRepository, chatThreadMemberRepository,
                chatChannelMemberRepository, validator, mapper, mapperHelper, tokenParser, new ChatThreadStatusResolver(),
                rateLimiter,
                chatChannelQueryService, organizationMemberQueryService, ticketQueryService, attachmentService,
                attachmentQueryService, resourceAccessQueryService, realtimeSessionRegistry, realtimeSignalBus, applicationEventPublisher,
                new TransactionTemplate(transactionManager), loggingService);

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
        lenient().when(pullRequestShareQueryService.getSharesByIds(any(), any(), any())).thenReturn(Map.of());
    }

    private ChatChannel channel(ChatChannelPurpose purpose) {
        ChatChannel channel = ChatChannel.builder()
                .name("Pull requests")
                .organizationId(organizationId)
                .visibility(ChatChannelVisibility.PUBLIC)
                .purpose(purpose)
                .build();
        channel.setId(channelId);
        return channel;
    }

    private void givenChannel(ChatChannelPurpose purpose) {
        when(chatChannelQueryService.verifyChannelAccess(eq(organizationId), any(), eq(channelId)))
                .thenReturn(channel(purpose));
    }

    private PullRequestShare shareWithReviewers(User... reviewers) {
        PullRequestShare share = PullRequestMocks.share(author, organizationId);
        for (int position = 0; position < reviewers.length; position++) {
            PullRequestMocks.reviewer(share, reviewers[position], position, null);
        }
        return share;
    }

    private ChatMessage shareEntry(PullRequestShare share) {
        ChatMessage message = ChatMocks.message(author, organizationId, "");
        message.setChannelId(channelId);
        message.setPullRequestShareId(share.getId());
        return message;
    }

    private ChatMessageCreateRequest sendRequest(PullRequestShare share) {
        return shareMapper.toChatMessageCreateRequest(share, channelId);
    }

    private <T> List<T> published(Class<T> type) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher, atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues().stream().filter(type::isInstance).map(type::cast).toList();
    }

    @Nested
    class PostShareEntry {

        @Test
        void authorInPullRequestChannel_postsTextlessEntryWithOneMentionPerReviewer() {
            // Arrange
            givenChannel(ChatChannelPurpose.PULL_REQUESTS);
            PullRequestShare share = shareWithReviewers(reviewerTwo, reviewerOne);
            when(pullRequestShareQueryService.findShare(organizationId, share.getId()))
                    .thenReturn(Optional.of(share));

            // Act
            ChatMessageResponse response = service.postMessage(author, organizationId, sendRequest(share), null);

            // Assert
            ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
            verify(chatMessageRepository, atLeastOnce()).saveAndFlush(saved.capture());
            ChatMessage entry = saved.getAllValues().getFirst();
            assertThat(entry.getPullRequestShareId()).isEqualTo(share.getId());
            assertThat(entry.getMentions()).extracting(ChatMessageMention::getUserId)
                    .containsExactly(reviewerTwo.getId(), reviewerOne.getId());
            assertThat(entry.getMentions()).noneMatch(ChatMessageMention::isViaHere);
            assertThat(response.clientNonce()).isEqualTo(share.getId().toString());
            assertThat(published(ChatMessageChangedEvent.class))
                    .extracting(ChatMessageChangedEvent::type).contains(ChatMessageChangedEvent.Type.CREATED);
        }

        @Test
        void authorPostingAShare_pushesReviewToEachReviewerAndMarksNoAttachments() {
            // Arrange
            givenChannel(ChatChannelPurpose.PULL_REQUESTS);
            PullRequestShare share = shareWithReviewers(reviewerTwo, reviewerOne);
            when(pullRequestShareQueryService.findShare(organizationId, share.getId()))
                    .thenReturn(Optional.of(share));
            when(organizationMemberQueryService.listActiveMemberSummaries(eq(organizationId), any()))
                    .thenReturn(List.of(ChatMocks.member(author), ChatMocks.member(reviewerOne),
                            ChatMocks.member(reviewerTwo)));

            // Act
            service.postMessage(author, organizationId, sendRequest(share), null);

            // Assert
            ChatPushRequestedEvent event = published(ChatPushRequestedEvent.class).getFirst();
            assertThat(event.recipients()).isEqualTo(
                    Map.of(reviewerOne.getId(), PushKind.REVIEW, reviewerTwo.getId(), PushKind.REVIEW));
            assertThat(event.authorId()).isEqualTo(author.getId());
            assertThat(event.attachmentsOnly()).isFalse();
        }

        @Test
        void shareOfAnotherOrganizationOrDeleted_throwsNotFound() {
            // Arrange
            givenChannel(ChatChannelPurpose.PULL_REQUESTS);
            PullRequestShare share = shareWithReviewers(reviewerOne);
            when(pullRequestShareQueryService.findShare(organizationId, share.getId())).thenReturn(Optional.empty());

            // Act and Assert
            assertThatThrownBy(() -> service.postMessage(author, organizationId, sendRequest(share), null))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.NOT_PULL_REQUEST_SHARE_FOUND.getCode());
            verify(chatMessageRepository, never()).saveAndFlush(any());
        }

        @Test
        void someoneElsesShare_throwsForbidden() {
            // Arrange
            givenChannel(ChatChannelPurpose.PULL_REQUESTS);
            PullRequestShare share = shareWithReviewers(reviewerOne);
            when(pullRequestShareQueryService.findShare(organizationId, share.getId()))
                    .thenReturn(Optional.of(share));

            // Act and Assert
            assertThatThrownBy(() -> service.postMessage(reviewerOne, organizationId, sendRequest(share), null))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.SEC_CANNOT_EDIT_OTHERS_SHARE.getCode());
        }

        @Test
        void channelWithoutPullRequestPurpose_throwsValidation() {
            // Arrange
            givenChannel(ChatChannelPurpose.GENERAL);
            PullRequestShare share = shareWithReviewers(reviewerOne);
            when(pullRequestShareQueryService.findShare(organizationId, share.getId()))
                    .thenReturn(Optional.of(share));

            // Act and Assert
            assertThatThrownBy(() -> service.postMessage(author, organizationId, sendRequest(share), null))
                    .isInstanceOf(ValidationException.class);
            verify(chatMessageRepository, never()).saveAndFlush(any());
        }

        @Test
        void shareAlreadyPosted_throwsConflict() {
            // Arrange
            givenChannel(ChatChannelPurpose.PULL_REQUESTS);
            PullRequestShare share = shareWithReviewers(reviewerOne);
            when(pullRequestShareQueryService.findShare(organizationId, share.getId()))
                    .thenReturn(Optional.of(share));
            when(chatMessageRepository.existsByPullRequestShareIdWithDeleted(share.getId())).thenReturn(true);

            // Act and Assert
            assertThatThrownBy(() -> service.postMessage(author, organizationId, sendRequest(share), null))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void entryInAThread_throwsValidation() {
            // Arrange
            givenChannel(ChatChannelPurpose.PULL_REQUESTS);
            PullRequestShare share = shareWithReviewers(reviewerOne);
            ChatMessageCreateRequest inThread = new ChatMessageCreateRequest(null, channelId, UUID.randomUUID(), null,
                    null, share.getId(), "n");

            // Act and Assert
            assertThatThrownBy(() -> service.postMessage(author, organizationId, inThread, null))
                    .isInstanceOf(ValidationException.class);
            verify(chatMessageRepository, never()).saveAndFlush(any());
        }

        @Test
        void entryWithFiles_throwsValidation() {
            // Arrange
            givenChannel(ChatChannelPurpose.PULL_REQUESTS);
            PullRequestShare share = shareWithReviewers(reviewerOne);
            List<MultipartFile> files = List.of(new MockMultipartFile("file", "a.txt", "text/plain", new byte[] {1}));

            // Act and Assert
            assertThatThrownBy(() -> service.postMessage(author, organizationId, sendRequest(share), files))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class ReadOnlyEntry {

        @Test
        void editMessage_shareEntry_isRefused() {
            // Arrange
            ChatMessage entry = shareEntry(shareWithReviewers(reviewerOne));
            when(chatMessageRepository.findByIdAndOrganizationId(entry.getId(), organizationId))
                    .thenReturn(Optional.of(entry));
            givenChannel(ChatChannelPurpose.PULL_REQUESTS);

            // Act and Assert
            assertThatThrownBy(() -> service.editMessage(author, organizationId, entry.getId(),
                    new ChatMessageUpdateRequest("changed")))
                    .isInstanceOf(ValidationException.class);
            verify(chatMessageRepository, never()).save(any());
        }

        @Test
        void deleteMessage_shareEntry_isRefused() {
            // Arrange
            ChatMessage entry = shareEntry(shareWithReviewers(reviewerOne));
            when(chatMessageRepository.findByIdAndOrganizationId(entry.getId(), organizationId))
                    .thenReturn(Optional.of(entry));
            givenChannel(ChatChannelPurpose.PULL_REQUESTS);

            // Act and Assert
            assertThatThrownBy(() -> service.deleteMessage(author, organizationId, entry.getId()))
                    .isInstanceOf(ValidationException.class);
            verify(chatMessageRepository, never()).deleteByIdWithoutRelations(any());
        }
    }

    @Nested
    class TouchShareEntry {

        @Test
        void edit_mentionRowsEqualReviewersAndKeptReviewersKeepTheirRow() {
            // Arrange
            PullRequestShare share = shareWithReviewers(reviewerOne, reviewerTwo);
            share.setEditedAt(Instant.parse("2026-10-28T08:00:00Z"));
            ChatMessage entry = shareEntry(share);
            ChatMessageMention keptRow = ChatMessageMention.builder().userId(reviewerOne.getId()).message(entry)
                    .build();
            ChatMessageMention lostRow = ChatMessageMention.builder().userId(author.getId()).message(entry)
                    .build();
            entry.getMentions().add(keptRow);
            entry.getMentions().add(lostRow);
            when(chatMessageRepository.findByPullRequestShareId(share.getId())).thenReturn(Optional.of(entry));

            // Act
            service.touchPullRequestShareMessage(share, Set.of(reviewerOne.getId(), author.getId()), true);

            // Assert
            assertThat(entry.getMentions()).extracting(ChatMessageMention::getUserId)
                    .containsExactly(reviewerOne.getId(), reviewerTwo.getId());
            assertThat(entry.getMentions().getFirst()).isSameAs(keptRow);
            assertThat(entry.getEditedAt()).isEqualTo(share.getEditedAt());
            verify(chatMessageRepository).save(entry);
        }

        @Test
        void edit_publishesUpdatedAndNotifiesOnlyGainedAndLostReviewers() {
            // Arrange
            PullRequestShare share = shareWithReviewers(reviewerOne, reviewerTwo);
            share.setEditedAt(Instant.parse("2026-10-28T08:00:00Z"));
            ChatMessage entry = shareEntry(share);
            entry.getMentions().add(ChatMessageMention.builder().userId(reviewerOne.getId()).message(entry).build());
            ChatMessage stored = entry;
            UUID lostReviewerId = UUID.randomUUID();
            stored.getMentions().add(ChatMessageMention.builder().userId(lostReviewerId).message(entry).build());
            when(chatMessageRepository.findByPullRequestShareId(share.getId())).thenReturn(Optional.of(entry));

            // Act
            service.touchPullRequestShareMessage(share, Set.of(reviewerOne.getId(), lostReviewerId), true);

            // Assert
            assertThat(published(ChatMessageChangedEvent.class)).extracting(ChatMessageChangedEvent::type)
                    .containsExactly(ChatMessageChangedEvent.Type.UPDATED);
            List<ChatNotificationsChangedEvent> notifications = published(ChatNotificationsChangedEvent.class);
            assertThat(notifications).hasSize(1);
            assertThat(notifications.getFirst().userIds()).containsExactlyInAnyOrder(reviewerTwo.getId(),
                    lostReviewerId);
            assertThat(notifications.getFirst().broadcast()).isFalse();
        }

        @Test
        void edit_sameReviewers_publishesNoNotificationChange() {
            // Arrange
            PullRequestShare share = shareWithReviewers(reviewerOne);
            ChatMessage entry = shareEntry(share);
            entry.getMentions().add(ChatMessageMention.builder().userId(reviewerOne.getId()).message(entry).build());
            when(chatMessageRepository.findByPullRequestShareId(share.getId())).thenReturn(Optional.of(entry));

            // Act
            service.touchPullRequestShareMessage(share, Set.of(reviewerOne.getId()), true);

            // Assert
            verify(applicationEventPublisher, never()).publishEvent(any(ChatNotificationsChangedEvent.class));
            assertThat(entry.getEditedAt()).as("falls back to now when the share has no edit time").isNotNull();
        }

        @Test
        void approvalChange_touchesUpdatedAtOnlyWithoutEditMarkOrMentionChange() {
            // Arrange
            PullRequestShare share = shareWithReviewers(reviewerOne);
            ChatMessage entry = shareEntry(share);
            when(chatMessageRepository.findByPullRequestShareId(share.getId())).thenReturn(Optional.of(entry));

            // Act
            service.touchPullRequestShareMessage(share, Set.of(reviewerOne.getId()), false);

            // Assert
            verify(chatMessageRepository).touchUpdatedAt(entry.getId());
            verify(chatMessageRepository, never()).save(any());
            assertThat(entry.getEditedAt()).isNull();
            assertThat(entry.getMentions()).isEmpty();
            verify(applicationEventPublisher, never()).publishEvent(any(ChatNotificationsChangedEvent.class));
            assertThat(published(ChatMessageChangedEvent.class)).extracting(ChatMessageChangedEvent::type)
                    .containsExactly(ChatMessageChangedEvent.Type.UPDATED);
        }

        @Test
        void entryAbsent_doesNothing() {
            // Arrange
            PullRequestShare share = shareWithReviewers(reviewerOne);
            when(chatMessageRepository.findByPullRequestShareId(share.getId())).thenReturn(Optional.empty());

            // Act
            service.touchPullRequestShareMessage(share, Set.of(), true);

            // Assert
            verify(chatMessageRepository, never()).save(any());
            verify(applicationEventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    class RemoveShareEntry {

        @Test
        void entry_isSoftDeletedWithoutCascadeAndDeletionPublished() {
            // Arrange
            PullRequestShare share = shareWithReviewers(reviewerOne);
            ChatMessage entry = shareEntry(share);
            when(chatMessageRepository.findByPullRequestShareId(share.getId())).thenReturn(Optional.of(entry));

            // Act
            service.removePullRequestShareMessage(share);

            // Assert
            verify(chatMessageRepository).deleteByIdWithoutRelations(entry.getId());
            assertThat(published(ChatMessageChangedEvent.class)).extracting(ChatMessageChangedEvent::type)
                    .contains(ChatMessageChangedEvent.Type.DELETED);
        }

        @Test
        void entryAbsent_doesNothing() {
            // Arrange
            PullRequestShare share = shareWithReviewers(reviewerOne);
            when(chatMessageRepository.findByPullRequestShareId(share.getId())).thenReturn(Optional.empty());

            // Act
            service.removePullRequestShareMessage(share);

            // Assert
            verify(chatMessageRepository, never()).deleteByIdWithoutRelations(any());
        }
    }
}
