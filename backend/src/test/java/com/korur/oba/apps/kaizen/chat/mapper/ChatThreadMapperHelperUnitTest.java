package com.korur.oba.apps.kaizen.chat.mapper;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.dto.request.ChatThreadCreateRequest;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatMessageResponse;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatThreadResponse;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatThreadSummaryResponse;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatUserResponse;
import com.korur.oba.apps.kaizen.chat.entity.ChatMessage;
import com.korur.oba.apps.kaizen.chat.entity.ChatThread;
import com.korur.oba.apps.kaizen.chat.entity.ChatThreadMember;
import com.korur.oba.apps.kaizen.chat.enums.ChatThreadStatus;
import com.korur.oba.apps.kaizen.chat.helper.ChatThreadStatusResolver;
import com.korur.oba.apps.kaizen.chat.helper.ChatTokenParser;
import com.korur.oba.apps.kaizen.chat.mapper.ChatMessageMapper.ChatMessageParts;
import com.korur.oba.apps.kaizen.chat.mock_data.ChatMocks;
import com.korur.oba.apps.kaizen.chat.repository.ChatMessageRepository;
import com.korur.oba.apps.kaizen.chat.repository.ChatThreadMemberRepository;
import com.korur.oba.apps.kaizen.chat.repository.ChatThreadRepository;
import com.korur.oba.apps.kaizen.chat.validator.ChatValidator;
import com.korur.oba.apps.kaizen.dailyupdate.service.DailyUpdateQueryService;
import com.korur.oba.apps.kaizen.ticket.mapper.TicketEmbeddedMapperImpl;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.ResourceAccessType;
import com.korur.oba.shrine.access.service.ResourceAccessQueryService;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentResponse;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.service.attachment.AttachmentQueryService;

@ExtendWith(MockitoExtension.class)
class ChatThreadMapperHelperUnitTest {

    @Mock
    private ChatMessageMapperHelper chatMessageMapperHelper;
    @Mock
    private AppAccessValidator appAccessValidator;
    @Mock
    private DailyUpdateQueryService dailyUpdateQueryService;

    @Mock
    private ChatThreadMemberRepository chatThreadMemberRepository;

    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;

    @Mock
    private AttachmentQueryService attachmentQueryService;

    @Mock
    private ResourceAccessQueryService resourceAccessQueryService;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatThreadRepository chatThreadRepository;

    private final ChatMessageMapper chatMessageMapper = new ChatMessageMapperImpl(new TicketEmbeddedMapperImpl());

    private ChatThreadMapperHelper helper;

    private final UUID organizationId = UUID.randomUUID();
    private final User author = ChatMocks.user("Ada", "Lovelace");
    private final User reader = ChatMocks.user("Alan", "Turing");
    private final ChatUserResponse authorResponse =
            new ChatUserResponse(author.getId(), "Ada", "Lovelace", "ada@example.com", true);
    private final ChatUserResponse readerResponse =
            new ChatUserResponse(reader.getId(), "Alan", "Turing", "alan@example.com", true);

    @BeforeEach
    void setUp() {
        helper = new ChatThreadMapperHelper(new ChatThreadMapperImpl(), chatMessageMapperHelper,
                new ChatTokenParser(), new ChatThreadStatusResolver(),
                new ChatValidator(chatMessageRepository, chatThreadRepository,
                        dailyUpdateQueryService, null, appAccessValidator, new LoggingService()),
                chatThreadMemberRepository, organizationMemberQueryService, attachmentQueryService,
                resourceAccessQueryService, null, Clock.systemUTC());
        lenient().when(resourceAccessQueryService.withReadGrant(any(), any(), anyCollection(), any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(3).get());
    }

    private ChatMessageResponse rootWithPreview(UUID rootId, Map<UUID, ChatUserResponse> users, UUID... previewIds) {
        ChatThreadSummaryResponse summary = chatMessageMapper.toThreadSummaryResponse(
                ChatMocks.threadSummary(rootId, previewIds));
        ChatMessageParts parts = new ChatMessageParts(null, null, List.of(), false, false, List.of(), List.of(),
                List.of(), List.of(), summary, null, null, users);
        return chatMessageMapper.toPlaceholderResponse(rootId, organizationId, null, null, null, parts);
    }

    private ChatMessage dailyUpdateRoot() {
        ChatMessage root = ChatMocks.dailyUpdateMessage(author, organizationId, UUID.randomUUID());
        root.setCreatedAt(Instant.parse("2026-10-20T09:00:00Z"));
        return root;
    }

    private OrganizationMember membership(OrganizationMemberRole role) {
        OrganizationMember member = new OrganizationMember();
        member.setRole(role);
        return member;
    }

    @Nested
    class DefaultTitle {

        @Test
        void text_rendersMentionsFoldsWhitespaceAndCutsAt40() {
            // Arrange
            ChatMessage root = ChatMocks.message(author, organizationId,
                    "Release  " + ChatMocks.userToken(reader.getId()) + "\n\nis out, please check the notes today");
            when(chatMessageMapperHelper.resolveUsers(organizationId, List.of(reader.getId())))
                    .thenReturn(Map.of(reader.getId(), readerResponse));

            // Act
            String title = helper.resolveDefaultTitle(root, author, organizationId);

            // Assert
            assertThat(title).isEqualTo("Release @Alan Turing is out, please chec…");
            verify(attachmentQueryService, never()).listAttachmentsByEntity(author, organizationId, StorageEntityType.CHAT_MESSAGE,
                    root.getId());
        }

        @Test
        void shortText_isKeptWithoutEllipsis() {
            ChatMessage root = ChatMocks.message(author, organizationId, "  Ship it ");

            assertThat(helper.resolveDefaultTitle(root, author, organizationId)).isEqualTo("Ship it");
        }

        @Test
        void dailyUpdateRoot_namesAuthorAndDayInReportZone() {
            // Arrange: 23:30 UTC is already the next day in Istanbul
            ChatMessage root = ChatMocks.dailyUpdateMessage(author, organizationId, UUID.randomUUID());
            root.setCreatedAt(Instant.parse("2026-10-19T23:30:00Z"));
            when(chatMessageMapperHelper.resolveUsers(organizationId, List.of(author.getId())))
                    .thenReturn(Map.of(author.getId(), authorResponse));

            // Act and assert
            assertThat(helper.resolveDefaultTitle(root, reader, organizationId))
                    .isEqualTo("Daily update - Ada Lovelace - 20 Oct");
        }

        @Test
        void dailyUpdateRootOfAuthorWithOneName_hasNoNullOrDoubleSpace() {
            ChatMessage root = dailyUpdateRoot();
            when(chatMessageMapperHelper.resolveUsers(organizationId, List.of(author.getId())))
                    .thenReturn(Map.of(author.getId(),
                            new ChatUserResponse(author.getId(), "Ada", null, "ada@example.com", true)));

            assertThat(helper.resolveDefaultTitle(root, reader, organizationId))
                    .isEqualTo("Daily update - Ada - 20 Oct");
        }

        @Test
        void dailyUpdateRootOfAuthorWithoutName_takesEmail() {
            ChatMessage root = dailyUpdateRoot();
            when(chatMessageMapperHelper.resolveUsers(organizationId, List.of(author.getId())))
                    .thenReturn(Map.of(author.getId(),
                            new ChatUserResponse(author.getId(), null, "  ", "ada@example.com", true)));

            assertThat(helper.resolveDefaultTitle(root, reader, organizationId))
                    .isEqualTo("Daily update - ada@example.com - 20 Oct");
        }

        @Test
        void dailyUpdateRootOfUnknownAuthor_isThread() {
            ChatMessage root = dailyUpdateRoot();
            when(chatMessageMapperHelper.resolveUsers(organizationId, List.of(author.getId())))
                    .thenReturn(Map.of(author.getId(), chatMessageMapper.toUnknownUserResponse(author.getId())));

            assertThat(helper.resolveDefaultTitle(root, reader, organizationId)).isEqualTo("Thread");
        }

        @Test
        void dailyUpdateRootOfAuthorWithLongEmail_fitsTheTitleColumn() {
            ChatMessage root = dailyUpdateRoot();
            String email = "a".repeat(200) + "@example.com";
            when(chatMessageMapperHelper.resolveUsers(organizationId, List.of(author.getId())))
                    .thenReturn(Map.of(author.getId(),
                            new ChatUserResponse(author.getId(), null, null, email, true)));

            String title = helper.resolveDefaultTitle(root, reader, organizationId);

            assertThat(title).hasSize(100).startsWith("Daily update - aaa").endsWith("…");
        }

        @Test
        void attachmentOnlyRoot_takesFirstFileName() {
            ChatMessage root = ChatMocks.message(author, organizationId, "");
            when(attachmentQueryService.listAttachmentsByEntity(author, organizationId, StorageEntityType.CHAT_MESSAGE, root.getId()))
                    .thenReturn(List.of(attachment("plan.pdf"), attachment("other.png")));

            assertThat(helper.resolveDefaultTitle(root, author, organizationId)).isEqualTo("plan.pdf");
        }

        @Test
        void attachmentOnlyRoot_readsTheFilesUnderAReadGrantForTheRoot() {
            // Arrange
            ChatMessage root = ChatMocks.message(author, organizationId, "");
            when(attachmentQueryService.listAttachmentsByEntity(author, organizationId, StorageEntityType.CHAT_MESSAGE, root.getId()))
                    .thenReturn(List.of(attachment("plan.pdf")));

            // Act
            helper.resolveDefaultTitle(root, author, organizationId);

            // Assert
            InOrder order = inOrder(resourceAccessQueryService, attachmentQueryService);
            order.verify(resourceAccessQueryService).withReadGrant(eq(author), eq(ResourceAccessType.CHAT_MESSAGE),
                    eq(List.of(root.getId())), any());
            order.verify(attachmentQueryService).listAttachmentsByEntity(author, organizationId, StorageEntityType.CHAT_MESSAGE,
                    root.getId());
        }

        @Test
        void nothingToShow_isThread() {
            ChatMessage root = ChatMocks.message(author, organizationId, "   ");
            when(attachmentQueryService.listAttachmentsByEntity(author, organizationId, StorageEntityType.CHAT_MESSAGE, root.getId()))
                    .thenReturn(List.of());

            assertThat(helper.resolveDefaultTitle(root, author, organizationId)).isEqualTo("Thread");
        }

        @Test
        void requestedTitle_isKept() {
            ChatMessage root = ChatMocks.message(author, organizationId, "text");
            ChatThread thread = new ChatThread();
            Instant now = Instant.now();

            helper.setCreateRelations(thread, new ChatThreadCreateRequest(root.getId(), "My title"), root, reader,
                    organizationId, now);

            assertThat(thread.getTitle()).isEqualTo("My title");
            assertThat(thread.getOrganizationId()).isEqualTo(organizationId);
            assertThat(thread.getRootMessageId()).isEqualTo(root.getId());
            assertThat(thread.getCreatedBy()).isEqualTo(reader.getId());
            assertThat(thread.getLastMessageAt()).isEqualTo(now);
        }

        @Test
        void requestedTitle_isTrimmed() {
            ChatMessage root = ChatMocks.message(author, organizationId, "text");
            ChatThread thread = new ChatThread();

            helper.setCreateRelations(thread, new ChatThreadCreateRequest(root.getId(), "  My title \t"), root,
                    reader, organizationId, Instant.now());

            assertThat(thread.getTitle()).isEqualTo("My title");
        }

        @Test
        void updatedTitle_isTrimmed() {
            ChatThread thread = ChatMocks.thread(organizationId, UUID.randomUUID(), reader.getId());
            thread.setTitle("  Renamed  ");

            helper.normalizeTitle(thread);

            assertThat(thread.getTitle()).isEqualTo("Renamed");
        }

        private AttachmentResponse attachment(String fileName) {
            return new AttachmentResponse(UUID.randomUUID(), "https://files/x", fileName, "application/pdf", 10L,
                    StorageEntityType.CHAT_MESSAGE, UUID.randomUUID(), Instant.now(), null, null);
        }
    }

    @Nested
    class Responses {

        @Test
        void list_usesPreviewMembersMembershipAndRole() {
            // Arrange
            ChatThread joined = ChatMocks.thread(organizationId, UUID.randomUUID(), author.getId());
            ChatThread other = ChatMocks.thread(organizationId, UUID.randomUUID(), author.getId());
            Map<UUID, ChatUserResponse> users = Map.of(author.getId(), authorResponse);
            when(chatMessageMapperHelper.toThreadRootResponses(
                    Set.of(joined.getRootMessageId(), other.getRootMessageId()), reader, organizationId))
                    .thenReturn(Map.of(
                            joined.getRootMessageId(), rootWithPreview(joined.getRootMessageId(), users, author.getId()),
                            other.getRootMessageId(), rootWithPreview(other.getRootMessageId(), Map.of())));
            when(chatThreadMemberRepository.findThreadIdsByUserIdAndThreadIds(eq(reader.getId()), anyCollection()))
                    .thenReturn(List.of(joined.getId()));
            when(organizationMemberQueryService.findMember(organizationId, reader.getId()))
                    .thenReturn(Optional.of(membership(OrganizationMemberRole.ADMIN)));

            // Act
            List<ChatThreadResponse> responses = helper.toResponses(List.of(joined, other), reader, organizationId);

            // Assert
            assertThat(responses).extracting(ChatThreadResponse::id).containsExactly(joined.getId(), other.getId());
            assertThat(responses.get(0).member()).isTrue();
            assertThat(responses.get(1).member()).isFalse();
            assertThat(responses.get(0).members()).containsExactly(authorResponse);
            assertThat(responses.get(1).members()).isEmpty();
            assertThat(responses).allMatch(ChatThreadResponse::canManage);
            assertThat(responses.get(0).rootMessage().deleted()).isTrue();
        }

        @Test
        void detail_listsAllMembersAndDeniesManageToPlainMember() {
            // Arrange
            ChatThread thread = ChatMocks.thread(organizationId, UUID.randomUUID(), author.getId());
            ChatThreadMember first = ChatThreadMember.builder().thread(thread).userId(author.getId()).build();
            ChatThreadMember second = ChatThreadMember.builder().thread(thread).userId(reader.getId()).build();
            when(chatThreadMemberRepository.findByThread_IdOrderByCreatedAtAsc(thread.getId()))
                    .thenReturn(List.of(first, second));
            when(chatMessageMapperHelper.resolveUsers(organizationId, List.of(author.getId(), reader.getId())))
                    .thenReturn(Map.of(author.getId(), authorResponse, reader.getId(), readerResponse));
            when(chatMessageMapperHelper.toThreadRootResponses(List.of(thread.getRootMessageId()), reader,
                    organizationId))
                    .thenReturn(Map.of(thread.getRootMessageId(), rootWithPreview(thread.getRootMessageId(), Map.of())));
            when(organizationMemberQueryService.findMember(organizationId, reader.getId()))
                    .thenReturn(Optional.of(membership(OrganizationMemberRole.MEMBER)));

            // Act
            ChatThreadResponse response = helper.toResponse(thread, reader, organizationId);

            // Assert
            assertThat(response.members()).containsExactlyInAnyOrder(authorResponse, readerResponse);
            assertThat(response.member()).isTrue();
            assertThat(response.canManage()).isFalse();
            assertThat(response.title()).isEqualTo("Release checklist");
            assertThat(response.status()).isEqualTo(ChatThreadStatus.ACTIVE);
            assertThat(response.statusChangedAt()).isNull();
            assertThat(response.statusChangedBy()).isNull();
        }

        @Test
        void detailOfClosedThread_showsWhoClosedItAndWhen() {
            // Arrange
            Instant closedAt = Instant.now().minus(Duration.ofDays(30));
            ChatThread thread = ChatMocks.thread(organizationId, UUID.randomUUID(), author.getId());
            thread.setLastMessageAt(closedAt);
            thread.setStatus(ChatThreadStatus.CLOSED);
            thread.setStatusChangedAt(closedAt);
            thread.setStatusChangedBy(reader.getId());
            when(chatThreadMemberRepository.findByThread_IdOrderByCreatedAtAsc(thread.getId())).thenReturn(List.of());
            when(chatMessageMapperHelper.resolveUsers(organizationId, List.of())).thenReturn(Map.of());
            when(chatMessageMapperHelper.resolveUsers(organizationId, Set.of(reader.getId())))
                    .thenReturn(Map.of(reader.getId(), readerResponse));
            when(chatMessageMapperHelper.toThreadRootResponses(List.of(thread.getRootMessageId()), reader,
                    organizationId))
                    .thenReturn(Map.of(thread.getRootMessageId(), rootWithPreview(thread.getRootMessageId(), Map.of())));

            // Act
            ChatThreadResponse response = helper.toResponse(thread, reader, organizationId);

            // Assert
            assertThat(response.status()).isEqualTo(ChatThreadStatus.CLOSED);
            assertThat(response.statusChangedAt()).isEqualTo(closedAt);
            assertThat(response.statusChangedBy()).isEqualTo(readerResponse);
        }

        @Test
        void listOfThreadArchivedByInactivity_showsWindowEndAndNoUser() {
            // Arrange
            Instant reopenedAt = Instant.now().minus(Duration.ofHours(3));
            ChatThread thread = ChatMocks.thread(organizationId, UUID.randomUUID(), author.getId());
            thread.setAutoArchiveMinutes(60);
            thread.setLastMessageAt(reopenedAt.minus(Duration.ofDays(2)));
            thread.setStatusChangedAt(reopenedAt);
            thread.setStatusChangedBy(reader.getId());
            when(chatMessageMapperHelper.toThreadRootResponses(Set.of(thread.getRootMessageId()), reader,
                    organizationId))
                    .thenReturn(Map.of(thread.getRootMessageId(), rootWithPreview(thread.getRootMessageId(), Map.of())));
            when(chatThreadMemberRepository.findThreadIdsByUserIdAndThreadIds(eq(reader.getId()), anyCollection()))
                    .thenReturn(List.of());

            // Act
            List<ChatThreadResponse> responses = helper.toResponses(List.of(thread), reader, organizationId);

            // Assert
            assertThat(responses.getFirst().status()).isEqualTo(ChatThreadStatus.ARCHIVED);
            assertThat(responses.getFirst().statusChangedAt()).isEqualTo(reopenedAt.plus(Duration.ofMinutes(60)));
            assertThat(responses.getFirst().statusChangedBy()).isNull();
            verify(chatMessageMapperHelper, never()).resolveUsers(eq(organizationId), anyCollection());
        }
    }
}
