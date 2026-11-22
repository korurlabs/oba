package com.korur.oba.apps.kazan.chat.mapper;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

// 4. Internal
import com.korur.oba.apps.kazan.chat.dto.projection.ChatReactionSummaryProjection;
import com.korur.oba.apps.kazan.chat.dto.response.ChatLinkedTicketResponse;
import com.korur.oba.apps.kazan.chat.dto.response.ChatMessageResponse;
import com.korur.oba.apps.kazan.chat.dto.response.ChatReactionSummaryResponse;
import com.korur.oba.apps.kazan.chat.dto.response.ChatTicketRefResponse;
import com.korur.oba.apps.kazan.chat.entity.ChatMessage;
import com.korur.oba.apps.kazan.chat.entity.ChatMessageMention;
import com.korur.oba.apps.kazan.chat.entity.ChatMessageTicketRef;
import com.korur.oba.apps.kazan.chat.entity.ChatThread;
import com.korur.oba.apps.kazan.chat.helper.ChatTokenParser;
import com.korur.oba.apps.kazan.chat.helper.ChatTokenParser.ChatTokens;
import com.korur.oba.apps.kazan.chat.mock_data.ChatMocks;
import com.korur.oba.apps.kazan.chat.repository.ChatMessageMentionRepository;
import com.korur.oba.apps.kazan.chat.repository.ChatMessageRepository;
import com.korur.oba.apps.kazan.chat.repository.ChatThreadMemberRepository;
import com.korur.oba.apps.kazan.dailyupdate.dto.response.DailyUpdateResponse;
import com.korur.oba.apps.kazan.dailyupdate.service.DailyUpdateQueryService;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.mapper.TicketEmbeddedMapperImpl;
import com.korur.oba.apps.kazan.ticket.service.TicketQueryService;
import com.korur.oba.shrine.access.enums.ResourceAccessType;
import com.korur.oba.shrine.access.service.ResourceAccessQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.service.UserQueryService;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentResponse;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.service.attachment.AttachmentQueryService;

@ExtendWith(MockitoExtension.class)
class ChatMessageMapperHelperUnitTest {

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

    private ChatMessageMapperHelper helper;

    private final UUID organizationId = UUID.randomUUID();
    private final User author = ChatMocks.user("Ada", "Lovelace");
    private final User reader = ChatMocks.user("Alan", "Turing");

    @BeforeEach
    void setUp() {
        helper = new ChatMessageMapperHelper(new ChatMessageMapperImpl(new TicketEmbeddedMapperImpl()),
                new ChatTokenParser(), resourceAccessQueryService, chatMessageRepository,
                chatMessageMentionRepository, chatThreadMemberRepository, ticketQueryService, userQueryService,
                organizationMemberQueryService, attachmentQueryService, dailyUpdateQueryService, null);
        lenient().when(organizationMemberQueryService.listActiveMemberSummaries(eq(organizationId), any()))
                .thenReturn(List.of(ChatMocks.member(author), ChatMocks.member(reader)));
        lenient().when(resourceAccessQueryService.withReadGrant(any(), any(), anyCollection(), any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(3).get());
        lenient().when(resourceAccessQueryService.withCheckedReadGrant(any(), any(), any(), anyCollection(), any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(4).get());
    }

    private ChatMessageResponse map(ChatMessage message) {
        return helper.toResponses(List.of(message), reader, organizationId).getFirst();
    }

    @Nested
    class Tickets {

        @Test
        void ticketRefs_canOpenOnlyWhereReaderMayOpenTicket() {
            // Arrange
            Ticket open = ChatMocks.ticket("ACME-1", "Open");
            Ticket closed = ChatMocks.ticket("SEC-1", "Closed board");
            ChatMessage message = ChatMocks.message(author, organizationId,
                    ChatMocks.ticketToken(open.getId()) + " " + ChatMocks.ticketToken(closed.getId()));
            ChatMocks.ticketRef(message, open, 0);
            ChatMocks.ticketRef(message, closed, 1);
            when(ticketQueryService.getAccessibleTickets(eq(reader.getId()), eq(organizationId), anyList())).thenReturn(List.of(open));

            // Act
            ChatMessageResponse response = map(message);

            // Assert
            assertThat(response.ticketRefs()).extracting(ChatTicketRefResponse::ticketKey, ChatTicketRefResponse::canOpen)
                    .containsExactly(tuple("ACME-1", true),
                            tuple("SEC-1", false));
        }

        @Test
        void typedKeys_linkOnlyOpenableTicketsAndKeepTypedForm() {
            // Arrange
            Ticket open = ChatMocks.ticket("ACME-1", "Open");
            ChatMessage message = ChatMocks.message(author, organizationId, "see acme-1 and SEC-9");
            when(ticketQueryService.getAccessibleTicketsByKeys(reader.getId(), organizationId, Set.of("ACME-1", "SEC-9")))
                    .thenReturn(List.of(open));

            // Act
            ChatMessageResponse response = map(message);

            // Assert
            assertThat(response.linkedTickets()).containsExactly(
                    new ChatLinkedTicketResponse("acme-1", open.getId(), open.getBoard().getId()));
        }
    }

    @Nested
    class Replies {

        @Test
        void deletedTarget_showsNeitherAuthorNorText() {
            // Arrange
            ChatMessage target = ChatMocks.message(author, organizationId, "secret text");
            ChatMessage reply = ChatMocks.message(reader, organizationId, "answer");
            reply.setReplyToMessageId(target.getId());
            when(chatMessageRepository.findReplyPreviewsByIds(Set.of(target.getId()), organizationId))
                    .thenReturn(List.of(ChatMocks.replyPreview(target, true)));

            // Act
            ChatMessageResponse response = map(reply);

            // Assert
            assertThat(response.replyTo().deleted()).isTrue();
            assertThat(response.replyTo().authorId()).isNull();
            assertThat(response.replyTo().snippet()).isEmpty();
            assertThat(response.users()).doesNotContainKey(author.getId());
        }

        @Test
        void absentTarget_isShownAsDeleted() {
            ChatMessage reply = ChatMocks.message(reader, organizationId, "answer");
            reply.setReplyToMessageId(UUID.randomUUID());
            when(chatMessageRepository.findReplyPreviewsByIds(anyCollection(), any())).thenReturn(List.of());

            ChatMessageResponse response = map(reply);

            assertThat(response.replyTo().deleted()).isTrue();
            assertThat(response.replyTo().authorId()).isNull();
        }

        @Test
        void targetOfAnotherOrganization_isLookedUpInTheReadersOrganizationAndShownAsDeleted() {
            // Arrange
            UUID otherOrganizationId = UUID.randomUUID();
            ChatMessage foreignTarget = ChatMocks.message(author, otherOrganizationId, "foreign text");
            ChatMessage reply = ChatMocks.message(reader, organizationId, "answer");
            reply.setReplyToMessageId(foreignTarget.getId());
            when(chatMessageRepository.findReplyPreviewsByIds(Set.of(foreignTarget.getId()), organizationId))
                    .thenReturn(List.of());

            // Act
            ChatMessageResponse response = map(reply);

            // Assert
            verify(chatMessageRepository).findReplyPreviewsByIds(Set.of(foreignTarget.getId()), organizationId);
            verify(chatMessageRepository, never()).findReplyPreviewsByIds(anyCollection(), eq(otherOrganizationId));
            assertThat(response.replyTo().deleted()).isTrue();
            assertThat(response.replyTo().authorId()).isNull();
            assertThat(response.replyTo().snippet()).isEmpty();
        }

        @Test
        void liveTarget_showsRenderedSnippetCutAt200() {
            // Arrange
            ChatMessage target = ChatMocks.message(author, organizationId,
                    "Thanks " + ChatMocks.userToken(reader.getId()) + " " + "x".repeat(200));
            ChatMessage reply = ChatMocks.message(reader, organizationId, "answer");
            reply.setReplyToMessageId(target.getId());
            when(chatMessageRepository.findReplyPreviewsByIds(Set.of(target.getId()), organizationId))
                    .thenReturn(List.of(ChatMocks.replyPreview(target, false)));

            // Act
            ChatMessageResponse response = map(reply);

            // Assert
            assertThat(response.replyTo().authorId()).isEqualTo(author.getId());
            assertThat(response.replyTo().snippet()).startsWith("Thanks @Alan Turing x").hasSize(200);
            assertThat(response.users()).containsKey(author.getId());
        }

        @Test
        void quotedDailyUpdateReaderMayNotRead_hasEmptySnippet() {
            ChatMessage target = ChatMocks.dailyUpdateMessage(author, organizationId, UUID.randomUUID());
            ChatMessage reply = ChatMocks.message(reader, organizationId, "answer");
            reply.setReplyToMessageId(target.getId());
            when(chatMessageRepository.findReplyPreviewsByIds(Set.of(target.getId()), organizationId))
                    .thenReturn(List.of(ChatMocks.replyPreview(target, false)));
            when(dailyUpdateQueryService.getDailyUpdatesByIds(reader, Set.of(target.getDailyUpdateId())))
                    .thenReturn(Map.of());

            ChatMessageResponse response = map(reply);

            assertThat(response.replyTo().deleted()).isFalse();
            assertThat(response.replyTo().snippet()).isEmpty();
        }

        @Test
        void liveTarget_originalOf250Characters_givesSnippetOf200() {
            // Arrange
            ChatMessage target = ChatMocks.message(author, organizationId, "y".repeat(250));
            ChatMessage reply = ChatMocks.message(reader, organizationId, "answer");
            reply.setReplyToMessageId(target.getId());
            when(chatMessageRepository.findReplyPreviewsByIds(Set.of(target.getId()), organizationId))
                    .thenReturn(List.of(ChatMocks.replyPreview(target, false)));

            // Act
            ChatMessageResponse response = map(reply);

            // Assert
            assertThat(response.replyTo().snippet()).isEqualTo("y".repeat(200));
        }

        @Test
        void liveTargetWithTwoFiles_returnsBothInReplyToAttachments() {
            // Arrange
            ChatMessage target = ChatMocks.message(author, organizationId, "see files");
            ChatMessage reply = ChatMocks.message(reader, organizationId, "answer");
            reply.setReplyToMessageId(target.getId());
            AttachmentResponse photo = quotedFile(target.getId(), "photo.png", "image/png");
            AttachmentResponse plan = quotedFile(target.getId(), "plan.pdf", "application/pdf");
            when(chatMessageRepository.findReplyPreviewsByIds(Set.of(target.getId()), organizationId))
                    .thenReturn(List.of(ChatMocks.replyPreview(target, false)));
            when(attachmentQueryService.listAttachmentsByEntities(eq(reader), eq(organizationId), eq(StorageEntityType.CHAT_MESSAGE),
                    anyCollection())).thenReturn(Map.of(target.getId(), List.of(photo, plan)));

            // Act
            ChatMessageResponse response = map(reply);

            // Assert
            assertThat(response.replyTo().attachments()).containsExactly(photo, plan);
        }

        @Test
        void liveTargetWithoutFiles_returnsEmptyAttachments() {
            ChatMessage target = ChatMocks.message(author, organizationId, "no files");
            ChatMessage reply = ChatMocks.message(reader, organizationId, "answer");
            reply.setReplyToMessageId(target.getId());
            when(chatMessageRepository.findReplyPreviewsByIds(Set.of(target.getId()), organizationId))
                    .thenReturn(List.of(ChatMocks.replyPreview(target, false)));
            when(attachmentQueryService.listAttachmentsByEntities(eq(reader), eq(organizationId), eq(StorageEntityType.CHAT_MESSAGE),
                    anyCollection())).thenReturn(Map.of());

            ChatMessageResponse response = map(reply);

            assertThat(response.replyTo().attachments()).isNotNull().isEmpty();
        }

        @Test
        void deletedTarget_returnsNoAttachmentsAndAsksForNoFiles() {
            // Arrange
            ChatMessage target = ChatMocks.message(author, organizationId, "gone");
            ChatMessage reply = ChatMocks.message(reader, organizationId, "answer");
            reply.setReplyToMessageId(target.getId());
            when(chatMessageRepository.findReplyPreviewsByIds(Set.of(target.getId()), organizationId))
                    .thenReturn(List.of(ChatMocks.replyPreview(target, true)));
            when(attachmentQueryService.listAttachmentsByEntities(eq(reader), eq(organizationId), eq(StorageEntityType.CHAT_MESSAGE),
                    anyCollection())).thenReturn(Map.of(target.getId(), List.of(
                            quotedFile(target.getId(), "secret.pdf", "application/pdf"))));

            // Act
            ChatMessageResponse response = map(reply);

            // Assert
            assertThat(response.replyTo().deleted()).isTrue();
            assertThat(response.replyTo().attachments()).isEmpty();
            ArgumentCaptor<Collection<UUID>> asked = ArgumentCaptor.captor();
            verify(attachmentQueryService).listAttachmentsByEntities(eq(reader), eq(organizationId), eq(StorageEntityType.CHAT_MESSAGE),
                    asked.capture());
            assertThat(asked.getValue()).doesNotContain(target.getId());
        }

        @Test
        void absentTarget_returnsNoAttachments() {
            ChatMessage reply = ChatMocks.message(reader, organizationId, "answer");
            reply.setReplyToMessageId(UUID.randomUUID());
            when(chatMessageRepository.findReplyPreviewsByIds(anyCollection(), any())).thenReturn(List.of());

            ChatMessageResponse response = map(reply);

            assertThat(response.replyTo().attachments()).isEmpty();
        }

        @Test
        void quotedDailyUpdate_isNotAskedForFiles() {
            // Arrange
            ChatMessage dailyUpdate = ChatMocks.dailyUpdateMessage(author, organizationId, UUID.randomUUID());
            ChatMessage reply = ChatMocks.message(reader, organizationId, "a");
            reply.setReplyToMessageId(dailyUpdate.getId());
            when(chatMessageRepository.findReplyPreviewsByIds(anyCollection(), eq(organizationId)))
                    .thenReturn(List.of(ChatMocks.replyPreview(dailyUpdate, false)));
            when(dailyUpdateQueryService.getDailyUpdatesByIds(reader, Set.of(dailyUpdate.getDailyUpdateId())))
                    .thenReturn(Map.of());
            when(attachmentQueryService.listAttachmentsByEntities(eq(reader), eq(organizationId), eq(StorageEntityType.CHAT_MESSAGE),
                    anyCollection())).thenReturn(Map.of());

            // Act
            ChatMessageResponse response = map(reply);

            // Assert
            ArgumentCaptor<Collection<UUID>> asked = ArgumentCaptor.captor();
            verify(attachmentQueryService).listAttachmentsByEntities(eq(reader), eq(organizationId), eq(StorageEntityType.CHAT_MESSAGE),
                    asked.capture());
            assertThat(asked.getValue()).containsExactly(reply.getId());
            assertThat(response.replyTo().attachments()).isEmpty();
        }

        @Test
        void quotedAndPageMessages_shareOneStorageCallInsideOneReadGrant() {
            // Arrange
            ChatMessage quoted = ChatMocks.message(author, organizationId, "original");
            ChatMessage reply = ChatMocks.message(reader, organizationId, "answer");
            reply.setReplyToMessageId(quoted.getId());
            AttachmentResponse quotedFile = quotedFile(quoted.getId(), "photo.png", "image/png");
            when(chatMessageRepository.findReplyPreviewsByIds(Set.of(quoted.getId()), organizationId))
                    .thenReturn(List.of(ChatMocks.replyPreview(quoted, false)));
            when(attachmentQueryService.listAttachmentsByEntities(eq(reader), eq(organizationId), eq(StorageEntityType.CHAT_MESSAGE),
                    anyCollection())).thenReturn(Map.of(quoted.getId(), List.of(quotedFile)));

            // Act
            ChatMessageResponse response = helper.toResponses(List.of(reply), reader, organizationId).getFirst();

            // Assert
            ArgumentCaptor<Collection<UUID>> granted = ArgumentCaptor.captor();
            verify(resourceAccessQueryService, times(1)).withReadGrant(eq(reader),
                    eq(ResourceAccessType.CHAT_MESSAGE), granted.capture(), any());
            verify(resourceAccessQueryService, never()).withCheckedReadGrant(any(), any(), any(),
                    anyCollection(), any());
            ArgumentCaptor<Collection<UUID>> asked = ArgumentCaptor.captor();
            verify(attachmentQueryService, times(1)).listAttachmentsByEntities(eq(reader), eq(organizationId),
                    eq(StorageEntityType.CHAT_MESSAGE), asked.capture());
            assertThat(granted.getValue()).containsExactlyInAnyOrder(reply.getId(), quoted.getId());
            assertThat(asked.getValue()).containsExactlyInAnyOrder(reply.getId(), quoted.getId());
            assertThat(response.replyTo().attachments()).containsExactly(quotedFile);
        }

        @Test
        void quotedMessageAlsoOnThePage_isAskedForOnlyOnce() {
            // Arrange
            ChatMessage quoted = ChatMocks.message(author, organizationId, "original");
            ChatMessage reply = ChatMocks.message(reader, organizationId, "answer");
            reply.setReplyToMessageId(quoted.getId());
            when(chatMessageRepository.findReplyPreviewsByIds(Set.of(quoted.getId()), organizationId))
                    .thenReturn(List.of(ChatMocks.replyPreview(quoted, false)));
            when(attachmentQueryService.listAttachmentsByEntities(eq(reader), eq(organizationId), eq(StorageEntityType.CHAT_MESSAGE),
                    anyCollection())).thenReturn(Map.of());

            // Act
            helper.toResponses(List.of(quoted, reply), reader, organizationId);

            // Assert
            ArgumentCaptor<Collection<UUID>> asked = ArgumentCaptor.captor();
            verify(attachmentQueryService, times(1)).listAttachmentsByEntities(eq(reader), eq(organizationId),
                    eq(StorageEntityType.CHAT_MESSAGE), asked.capture());
            assertThat(asked.getValue()).hasSize(2).containsExactlyInAnyOrder(quoted.getId(), reply.getId());
        }

        private AttachmentResponse quotedFile(UUID messageId, String fileName, String contentType) {
            return new AttachmentResponse(UUID.randomUUID(), "https://files/" + fileName, fileName, contentType, 10L,
                    StorageEntityType.CHAT_MESSAGE, messageId, Instant.now(), null, null, null);
        }
    }

    @Nested
    class DailyUpdates {

        @Test
        void missingDailyUpdate_mapsWithoutCardAndWithoutAttachmentLookup() {
            // Arrange
            ChatMessage message = ChatMocks.dailyUpdateMessage(author, organizationId, UUID.randomUUID());
            when(dailyUpdateQueryService.getDailyUpdatesByIds(reader, Set.of(message.getDailyUpdateId())))
                    .thenReturn(Map.of());

            // Act
            ChatMessageResponse response = map(message);

            // Assert
            assertThat(response.dailyUpdate()).isNull();
            assertThat(response.id()).isEqualTo(message.getId());
            verify(attachmentQueryService, never()).listAttachmentsByEntities(any(), any(), any(), anyCollection());
        }

        @Test
        void presentDailyUpdate_isAttached() {
            ChatMessage message = ChatMocks.dailyUpdateMessage(author, organizationId, UUID.randomUUID());
            DailyUpdateResponse dailyUpdate = new DailyUpdateResponse(message.getDailyUpdateId(), Instant.now(),
                    null, null, "focus", null, List.of(), List.of(), "1. Which ticket am I working on?");
            when(dailyUpdateQueryService.getDailyUpdatesByIds(reader, Set.of(message.getDailyUpdateId())))
                    .thenReturn(Map.of(message.getDailyUpdateId(), dailyUpdate));

            assertThat(map(message).dailyUpdate()).isEqualTo(dailyUpdate);
        }
    }

    @Nested
    class Mentions {

        @Test
        void directMention_mentionsReader() {
            ChatMessage message = ChatMocks.message(author, organizationId, "hi " + ChatMocks.userToken(reader.getId()));

            assertThat(map(message).mentionsMe()).isTrue();
        }

        @Test
        void everyoneInChannel_mentionsReader() {
            ChatMessage message = ChatMocks.message(author, organizationId, "<@everyone>");
            message.setMentionsEveryone(true);

            assertThat(map(message).mentionsMe()).isTrue();
        }

        @Test
        void authorsOwnEveryone_doesNotMentionAuthor() {
            ChatMessage message = ChatMocks.message(author, organizationId, "<@everyone>");
            message.setMentionsEveryone(true);

            ChatMessageResponse response = helper.toResponses(List.of(message), author, organizationId).getFirst();

            assertThat(response.mentionsMe()).isFalse();
        }

        @Test
        void authorsSelfMention_doesNotMentionAuthor() {
            ChatMessage message = ChatMocks.message(author, organizationId, "note " + ChatMocks.userToken(author.getId()));

            ChatMessageResponse response = helper.toResponses(List.of(message), author, organizationId).getFirst();

            assertThat(response.mentionsMe()).isFalse();
        }

        @Test
        void hereThatReachedReader_mentionsReader() {
            ChatMessage message = ChatMocks.message(author, organizationId, "<@here>");
            message.setMentionsHere(true);
            when(chatMessageMentionRepository.findMessageIdsMentioningUser(reader.getId(), Set.of(message.getId())))
                    .thenReturn(List.of(message.getId()));

            assertThat(map(message).mentionsMe()).isTrue();
        }

        @Test
        void hereThatDidNotReachReader_doesNotMentionReader() {
            ChatMessage message = ChatMocks.message(author, organizationId, "<@here>");
            message.setMentionsHere(true);
            when(chatMessageMentionRepository.findMessageIdsMentioningUser(reader.getId(), Set.of(message.getId())))
                    .thenReturn(List.of());

            assertThat(map(message).mentionsMe()).isFalse();
        }

        @Test
        void everyoneInThread_mentionsOnlyThreadMembers() {
            // Arrange
            ChatThread thread = ChatMocks.thread(organizationId, UUID.randomUUID(), author.getId());
            ChatMessage message = ChatMocks.message(author, organizationId, "<@everyone>");
            message.setMentionsEveryone(true);
            message.setThread(thread);
            when(chatThreadMemberRepository.findThreadIdsByUserIdAndThreadIds(reader.getId(), Set.of(thread.getId())))
                    .thenReturn(List.of())
                    .thenReturn(List.of(thread.getId()));

            // Act and assert
            assertThat(map(message).mentionsMe()).isFalse();
            assertThat(map(message).mentionsMe()).isTrue();
        }
    }

    @Nested
    class RepliesToReader {

        @Test
        void replyToReadersLiveMessage_repliesToReaderWithoutMention() {
            // Arrange
            ChatMessage target = ChatMocks.message(reader, organizationId, "my question");
            ChatMessage reply = ChatMocks.message(author, organizationId, "my answer");
            reply.setReplyToMessageId(target.getId());
            when(chatMessageRepository.findReplyPreviewsByIds(Set.of(target.getId()), organizationId))
                    .thenReturn(List.of(ChatMocks.replyPreview(target, false)));

            // Act
            ChatMessageResponse response = map(reply);

            // Assert
            assertThat(response.repliesToMe()).isTrue();
            assertThat(response.mentionsMe()).isFalse();
        }

        @Test
        void replyToReadersDeletedMessage_doesNotReplyToReader() {
            // Arrange
            ChatMessage target = ChatMocks.message(reader, organizationId, "my question");
            ChatMessage reply = ChatMocks.message(author, organizationId, "my answer");
            reply.setReplyToMessageId(target.getId());
            when(chatMessageRepository.findReplyPreviewsByIds(Set.of(target.getId()), organizationId))
                    .thenReturn(List.of(ChatMocks.replyPreview(target, true)));

            // Act and assert
            assertThat(map(reply).repliesToMe()).isFalse();
        }

        @Test
        void replyToReadersRemovedMessage_doesNotReplyToReader() {
            // Arrange
            ChatMessage reply = ChatMocks.message(author, organizationId, "my answer");
            reply.setReplyToMessageId(UUID.randomUUID());
            when(chatMessageRepository.findReplyPreviewsByIds(anyCollection(), any())).thenReturn(List.of());

            // Act and assert
            assertThat(map(reply).repliesToMe()).isFalse();
        }

        @Test
        void replyToSomeoneElse_doesNotReplyToReader() {
            // Arrange
            ChatMessage target = ChatMocks.message(author, organizationId, "their question");
            ChatMessage reply = ChatMocks.message(author, organizationId, "their answer");
            reply.setReplyToMessageId(target.getId());
            when(chatMessageRepository.findReplyPreviewsByIds(Set.of(target.getId()), organizationId))
                    .thenReturn(List.of(ChatMocks.replyPreview(target, false)));

            // Act and assert
            assertThat(map(reply).repliesToMe()).isFalse();
        }

        @Test
        void readersOwnReplyToOwnMessage_doesNotReplyToReader() {
            // Arrange
            ChatMessage target = ChatMocks.message(reader, organizationId, "first thought");
            ChatMessage reply = ChatMocks.message(reader, organizationId, "second thought");
            reply.setReplyToMessageId(target.getId());
            when(chatMessageRepository.findReplyPreviewsByIds(Set.of(target.getId()), organizationId))
                    .thenReturn(List.of(ChatMocks.replyPreview(target, false)));

            // Act and assert
            assertThat(map(reply).repliesToMe()).isFalse();
        }

        @Test
        void noReply_doesNotReplyToReaderAndLoadsNoPreview() {
            // Arrange
            ChatMessage message = ChatMocks.message(author, organizationId, "plain");

            // Act and assert
            assertThat(map(message).repliesToMe()).isFalse();
            verify(chatMessageRepository, never()).findReplyPreviewsByIds(anyCollection(), any());
        }
    }

    @Nested
    class Pages {

        @Test
        void deletedRootOnPage_isPlaceholderWithThreadAndNoFileLookup() {
            // Arrange
            ChatMessage root = ChatMocks.message(author, organizationId, "secret root");
            root.setDeleted(true);
            when(chatMessageRepository.findThreadSummariesByRootIds(Set.of(root.getId())))
                    .thenReturn(List.of(ChatMocks.threadSummary(root.getId(), author.getId())));

            // Act
            ChatMessageResponse response = map(root);

            // Assert
            assertThat(response.deleted()).isTrue();
            assertThat(response.content()).isEmpty();
            assertThat(response.author()).isNull();
            assertThat(response.thread()).isNotNull();
            assertThat(response.users()).containsOnlyKeys(author.getId());
            verify(attachmentQueryService, never()).listAttachmentsByEntities(any(), any(), any(), anyCollection());
            verify(chatMessageRepository, never()).findReactionSummaries(anyCollection(), any());
        }

        @Test
        void pageOfThreeMessages_loadsEachKindOnce() {
            // Arrange
            Ticket ticket = ChatMocks.ticket("ACME-1", "Ticket");
            List<ChatMessage> messages = new ArrayList<>();
            for (int index = 0; index < 3; index++) {
                ChatMessage message = ChatMocks.message(author, organizationId,
                        "ACME-" + index + " " + ChatMocks.ticketToken(ticket.getId()) + " <@here>");
                message.setMentionsHere(true);
                ChatMocks.ticketRef(message, ticket, 0);
                messages.add(message);
            }
            messages.get(1).setReplyToMessageId(messages.get(0).getId());
            messages.get(2).setReplyToMessageId(messages.get(1).getId());
            when(chatMessageRepository.findReplyPreviewsByIds(anyCollection(), any())).thenReturn(List.of(
                    ChatMocks.replyPreview(messages.get(0), false), ChatMocks.replyPreview(messages.get(1), false)));
            when(chatMessageRepository.findReactionSummaries(anyCollection(), eq(reader.getId()))).thenReturn(List.of(
                    ChatMocks.reaction(messages.get(0).getId(), "+1", reader.getId())));
            when(attachmentQueryService.listAttachmentsByEntities(eq(reader), eq(organizationId), eq(StorageEntityType.CHAT_MESSAGE),
                    anyCollection())).thenReturn(Map.of(messages.get(2).getId(), List.of(attachment())));

            // Act
            List<ChatMessageResponse> responses = helper.toResponses(messages, reader, organizationId);

            // Assert
            assertThat(responses).hasSize(3);
            assertThat(responses.get(0).reactions()).hasSize(1);
            assertThat(responses.get(2).attachments()).hasSize(1);
            assertThat(responses.get(2).replyTo().snippet()).startsWith("ACME-1 ACME-1 Ticket");
            verify(chatMessageRepository, times(1)).findReplyPreviewsByIds(anyCollection(), any());
            verify(chatMessageRepository, never()).findAllById(any());
            verify(ticketQueryService, times(1)).getAccessibleTickets(any(), eq(organizationId), anyList());
            verify(ticketQueryService, times(1)).getAccessibleTicketsByKeys(any(), eq(organizationId), anyCollection());
            verify(chatMessageRepository, times(1)).findReactionSummaries(anyCollection(), any());
            verify(chatMessageRepository, times(1)).findThreadSummariesByRootIds(anyCollection());
            verify(chatMessageMentionRepository, times(1)).findMessageIdsMentioningUser(any(), anyCollection());
            verify(resourceAccessQueryService, times(1)).withReadGrant(any(), any(), anyCollection(), any());
            verify(attachmentQueryService, times(1)).listAttachmentsByEntities(any(), any(), any(), anyCollection());
            verify(organizationMemberQueryService, times(1)).listActiveMemberSummaries(any(), any());
            verify(userQueryService, never()).getUserEntitiesByIds(anyList());
            verify(chatThreadMemberRepository, never()).findThreadIdsByUserIdAndThreadIds(any(), anyCollection());
            verify(dailyUpdateQueryService, never()).getDailyUpdatesByIds(any(), anyCollection());
        }

        @Test
        void formerMemberMentioned_isLoadedOnceAsNonMember() {
            User former = ChatMocks.user("Old", "Member");
            ChatMessage message = ChatMocks.message(author, organizationId, ChatMocks.userToken(former.getId()));
            when(userQueryService.getUserEntitiesByIds(List.of(former.getId()))).thenReturn(List.of(former));

            ChatMessageResponse response = map(message);

            assertThat(response.users().get(former.getId()).member()).isFalse();
            assertThat(response.users().get(author.getId()).member()).isTrue();
        }

        @Test
        void threadRoots_liveAndDeleted_areAllReturned() {
            // Arrange
            ChatMessage live = ChatMocks.message(author, organizationId, "root");
            UUID deletedRootId = UUID.randomUUID();
            when(chatMessageRepository.findAllById(Set.of(live.getId(), deletedRootId))).thenReturn(List.of(live));
            when(chatMessageRepository.findThreadSummariesByRootIds(Set.of(live.getId(), deletedRootId)))
                    .thenReturn(List.of(ChatMocks.threadSummary(live.getId()),
                            ChatMocks.threadSummary(deletedRootId, reader.getId())));

            // Act
            Map<UUID, ChatMessageResponse> roots = helper.toThreadRootResponses(
                    List.of(live.getId(), deletedRootId), reader, organizationId);

            // Assert
            assertThat(roots.get(live.getId()).deleted()).isFalse();
            assertThat(roots.get(live.getId()).content()).isEqualTo("root");
            assertThat(roots.get(deletedRootId).deleted()).isTrue();
            assertThat(roots.get(deletedRootId).thread()).isNotNull();
            assertThat(roots.get(deletedRootId).users()).containsOnlyKeys(reader.getId());
        }

        private AttachmentResponse attachment() {
            return new AttachmentResponse(UUID.randomUUID(), "https://files/x", "plan.pdf", "application/pdf", 10L,
                    StorageEntityType.CHAT_MESSAGE, UUID.randomUUID(), Instant.now(), null, null, null);
        }
    }

    @Nested
    class Relations {

        @Test
        void create_directMentionsFirstThenHereUsersWithoutAuthorAndDirectOnes() {
            // Arrange
            UUID direct = UUID.randomUUID();
            UUID online = UUID.randomUUID();
            Ticket ticket = ChatMocks.ticket("ACME-1", "Ticket");
            ChatMessage message = ChatMessage.builder().content("x").build();
            ChatTokens tokens = new ChatTokens(List.of(direct), false, true, List.of(ticket.getId()));

            // Act
            helper.setCreateRelations(message, author, organizationId, null, tokens,
                    Map.of(ticket.getId(), ticket), List.of(author.getId(), direct, online));

            // Assert
            assertThat(message.getOrganizationId()).isEqualTo(organizationId);
            assertThat(message.getAuthor()).isEqualTo(author);
            assertThat(message.isMentionsHere()).isTrue();
            assertThat(message.getMentions()).extracting(ChatMessageMention::getUserId, ChatMessageMention::isViaHere)
                    .containsExactly(tuple(direct, false),
                            tuple(online, true));
            assertThat(message.getMentions()).allMatch(mention -> mention.getMessage() == message);
            assertThat(message.getTicketRefs()).singleElement().satisfies(ticketRef -> {
                assertThat(ticketRef.getTicketKey()).isEqualTo("ACME-1");
                assertThat(ticketRef.getBoardId()).isEqualTo(ticket.getBoard().getId());
                assertThat(ticketRef.getPosition()).isZero();
                assertThat(ticketRef.getMessage()).isSameAs(message);
            });
        }

        @Test
        void update_keepsRowsThatStayAndRefreshesSnapshots() {
            // Arrange
            UUID kept = UUID.randomUUID();
            UUID dropped = UUID.randomUUID();
            Ticket ticket = ChatMocks.ticket("ACME-1", "Old title");
            ChatMessage message = ChatMocks.message(author, organizationId, "old");
            ChatMessageMention keptMention = ChatMocks.mention(message, kept, false);
            ChatMocks.mention(message, dropped, false);
            ChatMessageTicketRef keptRef = ChatMocks.ticketRef(message, ticket, 0);
            ticket.setTitle("New title");
            ChatTokens tokens = new ChatTokens(List.of(kept), false, false, List.of(ticket.getId()));

            // Act
            helper.setUpdateRelations(message, tokens, Map.of(ticket.getId(), ticket), List.of());

            // Assert
            assertThat(message.getMentions()).containsExactly(keptMention);
            assertThat(message.getTicketRefs()).containsExactly(keptRef);
            assertThat(keptRef.getTicketTitle()).isEqualTo("New title");
        }

        @Test
        void update_hereKept_keepsUsersItReachedAndIgnoresWhoIsOnlineNow() {
            UUID reached = UUID.randomUUID();
            ChatMessage message = ChatMocks.message(author, organizationId, "<@here>");
            message.setMentionsHere(true);
            ChatMessageMention reachedMention = ChatMocks.mention(message, reached, true);
            ChatTokens tokens = new ChatTokens(List.of(), false, true, List.of());

            helper.setUpdateRelations(message, tokens, Map.of(), List.of(UUID.randomUUID()));

            assertThat(message.getMentions()).containsExactly(reachedMention);
        }

        @Test
        void update_hereAdded_reachesUsersOnlineNow() {
            UUID online = UUID.randomUUID();
            ChatMessage message = ChatMocks.message(author, organizationId, "old");
            ChatTokens tokens = new ChatTokens(List.of(), false, true, List.of());

            helper.setUpdateRelations(message, tokens, Map.of(), List.of(online, author.getId()));

            assertThat(message.isMentionsHere()).isTrue();
            assertThat(message.getMentions()).extracting(ChatMessageMention::getUserId).containsExactly(online);
        }

        @Test
        void update_hereRemoved_dropsItsUsers() {
            ChatMessage message = ChatMocks.message(author, organizationId, "<@here>");
            message.setMentionsHere(true);
            ChatMocks.mention(message, UUID.randomUUID(), true);
            ChatTokens tokens = new ChatTokens(List.of(), false, false, List.of());

            helper.setUpdateRelations(message, tokens, Map.of(), List.of());

            assertThat(message.isMentionsHere()).isFalse();
            assertThat(message.getMentions()).isEmpty();
        }

        @Test
        void update_hereUserNowMentionedDirectly_becomesDirect() {
            UUID user = UUID.randomUUID();
            ChatMessage message = ChatMocks.message(author, organizationId, "<@here>");
            message.setMentionsHere(true);
            ChatMessageMention mention = ChatMocks.mention(message, user, true);
            ChatTokens tokens = new ChatTokens(List.of(user), false, true, List.of());

            helper.setUpdateRelations(message, tokens, Map.of(), List.of());

            assertThat(message.getMentions()).containsExactly(mention);
            assertThat(mention.isViaHere()).isFalse();
        }
    }

    @Nested
    class NeutralResponse {

        @Test
        void plainMessage_carriesNoReaderSpecificFlags() {
            // Arrange
            ChatMessage target = ChatMocks.message(reader, organizationId, "question");
            ChatMessage message = ChatMocks.message(author, organizationId,
                    "<@everyone> thanks " + ChatMocks.userToken(reader.getId()));
            message.setMentionsEveryone(true);
            message.setReplyToMessageId(target.getId());
            Instant updatedAt = Instant.parse("2026-10-20T07:40:00Z");
            message.setUpdatedAt(updatedAt);
            ChatReactionSummaryProjection reaction = mock(ChatReactionSummaryProjection.class);
            when(reaction.getMessageId()).thenReturn(message.getId());
            when(reaction.getEmoji()).thenReturn("+1");
            when(reaction.getReactionCount()).thenReturn(1);
            when(reaction.getUserIds()).thenReturn(new UUID[] { reader.getId() });
            when(chatMessageRepository.findReplyPreviewsByIds(Set.of(target.getId()), organizationId))
                    .thenReturn(List.of(ChatMocks.replyPreview(target, false)));
            when(chatMessageRepository.findReactionSummaries(Set.of(message.getId()), author.getId()))
                    .thenReturn(List.of(reaction));

            // Act
            Optional<ChatMessageResponse> response = helper.toNeutralResponse(message, "nonce-1");

            // Assert
            assertThat(response).isPresent();
            assertThat(response.get().mentionsMe()).isFalse();
            assertThat(response.get().repliesToMe()).isFalse();
            assertThat(response.get().mentionsEveryone()).isTrue();
            assertThat(response.get().mentionedUserIds()).containsExactly(reader.getId());
            assertThat(response.get().reactions()).extracting(ChatReactionSummaryResponse::reactedByMe)
                    .containsExactly(false);
            assertThat(response.get().linkedTickets()).isEmpty();
            assertThat(response.get().dailyUpdate()).isNull();
            assertThat(response.get().clientNonce()).isEqualTo("nonce-1");
            assertThat(response.get().version()).isEqualTo(updatedAt);
            assertThat(response.get().users()).containsKeys(author.getId(), reader.getId());
            verify(resourceAccessQueryService).withCheckedReadGrant(eq(author),
                    eq(ResourceAccessType.CHAT_MESSAGE), eq(organizationId), anyCollection(), any());
            verify(resourceAccessQueryService, never()).withReadGrant(any(), any(), anyCollection(), any());
            verifyNoInteractions(ticketQueryService, chatMessageMentionRepository, chatThreadMemberRepository,
                    dailyUpdateQueryService);
        }

        @Test
        void ticketToken_needsPerReaderRead() {
            Ticket ticket = ChatMocks.ticket("ACME-1", "Open");
            ChatMessage message = ChatMocks.message(author, organizationId, ChatMocks.ticketToken(ticket.getId()));
            ChatMocks.ticketRef(message, ticket, 0);

            assertThat(helper.toNeutralResponse(message, null)).isEmpty();
            verifyNoInteractions(chatMessageRepository);
        }

        @Test
        void typedTicketKey_needsPerReaderRead() {
            ChatMessage message = ChatMocks.message(author, organizationId, "see ACME-1");

            assertThat(helper.toNeutralResponse(message, null)).isEmpty();
        }

        @Test
        void hereMention_needsPerReaderRead() {
            ChatMessage message = ChatMocks.message(author, organizationId, "<@here>");
            message.setMentionsHere(true);

            assertThat(helper.toNeutralResponse(message, null)).isEmpty();
        }

        @Test
        void dailyUpdateEntry_needsPerReaderRead() {
            ChatMessage message = ChatMocks.dailyUpdateMessage(author, organizationId, UUID.randomUUID());

            assertThat(helper.toNeutralResponse(message, null)).isEmpty();
        }

        @Test
        void replyToDailyUpdateEntry_needsPerReaderRead() {
            // Arrange
            ChatMessage entry = ChatMocks.dailyUpdateMessage(reader, organizationId, UUID.randomUUID());
            ChatMessage reply = ChatMocks.message(author, organizationId, "nice");
            reply.setReplyToMessageId(entry.getId());
            when(chatMessageRepository.findReplyPreviewsByIds(Set.of(entry.getId()), organizationId))
                    .thenReturn(List.of(ChatMocks.replyPreview(entry, false)));

            // Act and assert
            assertThat(helper.toNeutralResponse(reply, null)).isEmpty();
            verifyNoInteractions(dailyUpdateQueryService, attachmentQueryService);
        }

        @Test
        void readerResponse_carriesUpdatedAtAsVersion() {
            ChatMessage message = ChatMocks.message(author, organizationId, "hello");
            Instant updatedAt = Instant.parse("2026-10-20T07:35:00Z");
            message.setUpdatedAt(updatedAt);

            assertThat(map(message).version()).isEqualTo(updatedAt);
        }
    }
}
