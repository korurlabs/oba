package com.korur.oba.apps.kaizen.chat.mapper;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.dto.response.ChatMessageResponse;
import com.korur.oba.apps.kaizen.chat.entity.ChatMessage;
import com.korur.oba.apps.kaizen.chat.helper.ChatTokenParser;
import com.korur.oba.apps.kaizen.chat.mock_data.ChatMocks;
import com.korur.oba.apps.kaizen.chat.repository.ChatMessageMentionRepository;
import com.korur.oba.apps.kaizen.chat.repository.ChatMessageRepository;
import com.korur.oba.apps.kaizen.chat.repository.ChatThreadMemberRepository;
import com.korur.oba.apps.kaizen.dailyupdate.service.DailyUpdateQueryService;
import com.korur.oba.apps.kaizen.pullrequest.dto.response.PullRequestShareResponse;
import com.korur.oba.apps.kaizen.pullrequest.dto.response.PullRequestShareReviewerResponse;
import com.korur.oba.apps.kaizen.pullrequest.mock_data.PullRequestMocks;
import com.korur.oba.apps.kaizen.pullrequest.service.PullRequestShareQueryService;
import com.korur.oba.apps.kaizen.ticket.mapper.TicketEmbeddedMapperImpl;
import com.korur.oba.apps.kaizen.ticket.service.TicketQueryService;
import com.korur.oba.shrine.access.service.ResourceAccessQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.service.UserQueryService;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.service.attachment.AttachmentQueryService;

/**
 * The pull request share entry of a chat message: the card attached from the share query service, the
 * reviewer highlight, the quoted summary and the neutral copy.
 */
@ExtendWith(MockitoExtension.class)
class ChatMessageMapperHelperUnitPullRequestShareTest {

    @Mock
    private ResourceAccessQueryService resourceAccessQueryService;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatMessageMentionRepository chatMessageMentionRepository;

    @Mock
    private ChatThreadMemberRepository chatThreadMemberRepository;

    @Mock
    private TicketQueryService ticketQueryService;

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;

    @Mock
    private AttachmentQueryService attachmentQueryService;

    @Mock
    private DailyUpdateQueryService dailyUpdateQueryService;

    @Mock
    private PullRequestShareQueryService pullRequestShareQueryService;

    private ChatMessageMapperHelper helper;

    private final UUID organizationId = UUID.randomUUID();
    private final User author = ChatMocks.user("Ada", "Lovelace");
    private final User reader = ChatMocks.user("Alan", "Turing");
    private final User bystander = ChatMocks.user("Grace", "Hopper");

    @BeforeEach
    void setUp() {
        helper = new ChatMessageMapperHelper(new ChatMessageMapperImpl(new TicketEmbeddedMapperImpl()),
                new ChatTokenParser(), resourceAccessQueryService, chatMessageRepository,
                chatMessageMentionRepository, chatThreadMemberRepository, ticketQueryService, userQueryService,
                organizationMemberQueryService, attachmentQueryService, dailyUpdateQueryService,
                pullRequestShareQueryService);
        lenient().when(organizationMemberQueryService.listActiveMemberSummaries(eq(organizationId), any()))
                .thenReturn(List.of(ChatMocks.member(author), ChatMocks.member(reader),
                        ChatMocks.member(bystander)));
        lenient().when(resourceAccessQueryService.withReadGrant(any(), any(), anyCollection(), any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(3).get());
        lenient().when(resourceAccessQueryService.withCheckedReadGrant(any(), any(), any(), anyCollection(), any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(4).get());
    }

    private ChatMessageResponse map(ChatMessage message, User forReader) {
        return helper.toResponses(List.of(message), forReader, organizationId).getFirst();
    }

    private PullRequestShareResponse share(UUID shareId, User... reviewers) {
        List<PullRequestShareReviewerResponse> reviewerResponses = Arrays.stream(reviewers)
                .map((User user) -> PullRequestMocks.reviewerResponse(user, null))
                .toList();
        return PullRequestMocks.shareResponse(shareId, author, reviewerResponses);
    }

    @Nested
    class Card {

        @Test
        void presentShare_isAttachedWithoutAttachmentLookup() {
            // Arrange
            UUID shareId = UUID.randomUUID();
            ChatMessage message = ChatMocks.pullRequestShareMessage(author, organizationId, shareId);
            PullRequestShareResponse card = share(shareId);
            when(pullRequestShareQueryService.getSharesByIds(reader, organizationId, Set.of(shareId)))
                    .thenReturn(Map.of(shareId, card));

            // Act
            ChatMessageResponse response = map(message, reader);

            // Assert
            assertThat(response.pullRequestShare()).isEqualTo(card);
            assertThat(response.pullRequestShareId()).isEqualTo(shareId);
            verify(attachmentQueryService, never()).listAttachmentsByEntities(any(), any(), any(), anyCollection());
        }

        @Test
        void missingShare_mapsWithoutCard() {
            // Arrange
            UUID shareId = UUID.randomUUID();
            ChatMessage message = ChatMocks.pullRequestShareMessage(author, organizationId, shareId);
            when(pullRequestShareQueryService.getSharesByIds(reader, organizationId, Set.of(shareId)))
                    .thenReturn(Map.of());

            // Act
            ChatMessageResponse response = map(message, reader);

            // Assert
            assertThat(response.pullRequestShare()).isNull();
            assertThat(response.id()).isEqualTo(message.getId());
        }

        @Test
        void pageWithoutShareEntries_neverAsksTheShareService() {
            // Arrange
            ChatMessage message = ChatMocks.message(author, organizationId, "hello");

            // Act
            ChatMessageResponse response = map(message, reader);

            // Assert
            assertThat(response.pullRequestShare()).isNull();
            verifyNoInteractions(pullRequestShareQueryService);
        }
    }

    @Nested
    class MentionsMe {

        @Test
        void readerIsAReviewer_isHighlighted() {
            // Arrange
            UUID shareId = UUID.randomUUID();
            ChatMessage message = ChatMocks.pullRequestShareMessage(author, organizationId, shareId);
            when(pullRequestShareQueryService.getSharesByIds(reader, organizationId, Set.of(shareId)))
                    .thenReturn(Map.of(shareId, share(shareId, reader)));

            // Act
            ChatMessageResponse response = map(message, reader);

            // Assert
            assertThat(response.mentionsMe()).isTrue();
        }

        @Test
        void readerIsNotAReviewer_isNotHighlighted() {
            // Arrange
            UUID shareId = UUID.randomUUID();
            ChatMessage message = ChatMocks.pullRequestShareMessage(author, organizationId, shareId);
            when(pullRequestShareQueryService.getSharesByIds(bystander, organizationId, Set.of(shareId)))
                    .thenReturn(Map.of(shareId, share(shareId, reader)));

            // Act
            ChatMessageResponse response = map(message, bystander);

            // Assert
            assertThat(response.mentionsMe()).isFalse();
        }

        @Test
        void readerIsTheAuthor_isNeverHighlighted() {
            // Arrange
            UUID shareId = UUID.randomUUID();
            ChatMessage message = ChatMocks.pullRequestShareMessage(author, organizationId, shareId);
            when(pullRequestShareQueryService.getSharesByIds(author, organizationId, Set.of(shareId)))
                    .thenReturn(Map.of(shareId, share(shareId, author)));

            // Act
            ChatMessageResponse response = map(message, author);

            // Assert
            assertThat(response.mentionsMe()).isFalse();
        }
    }

    @Nested
    class QuotedShare {

        @Test
        void quotedShare_showsItsSummaryAsTheSnippet() {
            // Arrange
            UUID shareId = UUID.randomUUID();
            ChatMessage target = ChatMocks.pullRequestShareMessage(author, organizationId, shareId);
            ChatMessage reply = ChatMocks.message(reader, organizationId, "looks good");
            reply.setReplyToMessageId(target.getId());
            PullRequestShareResponse card = share(shareId);
            when(chatMessageRepository.findReplyPreviewsByIds(Set.of(target.getId()), organizationId))
                    .thenReturn(List.of(ChatMocks.replyPreview(target, false)));
            when(pullRequestShareQueryService.getSharesByIds(reader, organizationId, Set.of(shareId)))
                    .thenReturn(Map.of(shareId, card));

            // Act
            ChatMessageResponse response = map(reply, reader);

            // Assert
            assertThat(response.replyTo().deleted()).isFalse();
            assertThat(response.replyTo().snippet()).isEqualTo(card.summaryText());
        }

        @Test
        void quotedShareThatIsGone_hasEmptySnippet() {
            // Arrange
            UUID shareId = UUID.randomUUID();
            ChatMessage target = ChatMocks.pullRequestShareMessage(author, organizationId, shareId);
            ChatMessage reply = ChatMocks.message(reader, organizationId, "looks good");
            reply.setReplyToMessageId(target.getId());
            when(chatMessageRepository.findReplyPreviewsByIds(Set.of(target.getId()), organizationId))
                    .thenReturn(List.of(ChatMocks.replyPreview(target, false)));
            when(pullRequestShareQueryService.getSharesByIds(reader, organizationId, Set.of(shareId)))
                    .thenReturn(Map.of());

            // Act
            ChatMessageResponse response = map(reply, reader);

            // Assert
            assertThat(response.replyTo().deleted()).isFalse();
            assertThat(response.replyTo().snippet()).isEmpty();
        }

        @Test
        void deletedQuotedShareEntry_isNotLookedUp() {
            // Arrange
            UUID shareId = UUID.randomUUID();
            ChatMessage target = ChatMocks.pullRequestShareMessage(author, organizationId, shareId);
            ChatMessage reply = ChatMocks.message(reader, organizationId, "looks good");
            reply.setReplyToMessageId(target.getId());
            when(chatMessageRepository.findReplyPreviewsByIds(Set.of(target.getId()), organizationId))
                    .thenReturn(List.of(ChatMocks.replyPreview(target, true)));

            // Act
            ChatMessageResponse response = map(reply, reader);

            // Assert
            assertThat(response.replyTo().deleted()).isTrue();
            assertThat(response.replyTo().snippet()).isEmpty();
            verifyNoInteractions(pullRequestShareQueryService);
        }
    }

    @Nested
    class NeutralResponse {

        @Test
        void shareEntry_needsPerReaderRead() {
            // Arrange
            ChatMessage message = ChatMocks.pullRequestShareMessage(author, organizationId, UUID.randomUUID());

            // Act
            Optional<ChatMessageResponse> response = helper.toNeutralResponse(message, null);

            // Assert
            assertThat(response).isEmpty();
            verifyNoInteractions(chatMessageRepository, pullRequestShareQueryService);
        }

        @Test
        void replyToShareEntry_quotesTheReaderIndependentSummary() {
            // Arrange
            UUID shareId = UUID.randomUUID();
            ChatMessage target = ChatMocks.pullRequestShareMessage(reader, organizationId, shareId);
            ChatMessage reply = ChatMocks.message(author, organizationId, "nice");
            reply.setReplyToMessageId(target.getId());
            reply.setUpdatedAt(Instant.parse("2026-10-27T09:00:00Z"));
            PullRequestShareResponse card = share(shareId);
            when(chatMessageRepository.findReplyPreviewsByIds(Set.of(target.getId()), organizationId))
                    .thenReturn(List.of(ChatMocks.replyPreview(target, false)));
            when(pullRequestShareQueryService.getSharesByIds(null, organizationId, Set.of(shareId)))
                    .thenReturn(Map.of(shareId, card));

            // Act
            Optional<ChatMessageResponse> response = helper.toNeutralResponse(reply, "nonce-1");

            // Assert
            assertThat(response).isPresent();
            assertThat(response.get().replyTo().snippet()).isEqualTo(card.summaryText());
            assertThat(response.get().pullRequestShare()).isNull();
        }
    }

    @Nested
    class QuotedShareFiles {

        @Test
        void quotedShare_isNotAskedForFilesAndHasNoReplyAttachments() {
            // Arrange
            UUID shareId = UUID.randomUUID();
            ChatMessage quotedShare = ChatMocks.pullRequestShareMessage(author, organizationId, shareId);
            ChatMessage reply = ChatMocks.message(reader, organizationId, "looks good");
            reply.setReplyToMessageId(quotedShare.getId());
            when(chatMessageRepository.findReplyPreviewsByIds(anyCollection(), eq(organizationId)))
                    .thenReturn(List.of(ChatMocks.replyPreview(quotedShare, false)));
            when(pullRequestShareQueryService.getSharesByIds(eq(reader), eq(organizationId), anyCollection()))
                    .thenReturn(Map.of(shareId, share(shareId)));
            when(attachmentQueryService.listAttachmentsByEntities(eq(reader), eq(organizationId), eq(StorageEntityType.CHAT_MESSAGE),
                    anyCollection())).thenReturn(Map.of());

            // Act
            ChatMessageResponse response = map(reply, reader);

            // Assert
            ArgumentCaptor<Collection<UUID>> asked = ArgumentCaptor.captor();
            verify(attachmentQueryService).listAttachmentsByEntities(eq(reader), eq(organizationId), eq(StorageEntityType.CHAT_MESSAGE),
                    asked.capture());
            assertThat(asked.getValue()).containsExactly(reply.getId());
            assertThat(response.replyTo().attachments()).isEmpty();
        }
    }
}
