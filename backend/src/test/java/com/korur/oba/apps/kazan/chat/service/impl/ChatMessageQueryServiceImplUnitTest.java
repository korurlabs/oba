package com.korur.oba.apps.kazan.chat.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.ChatConstant;
import com.korur.oba.apps.kazan.chat.dto.request.ChatMessageQueryRequest;
import com.korur.oba.apps.kazan.chat.dto.request.ChatSearchRequest;
import com.korur.oba.apps.kazan.chat.dto.response.ChatMessagePageResponse;
import com.korur.oba.apps.kazan.chat.dto.response.ChatMessageResponse;
import com.korur.oba.apps.kazan.chat.entity.ChatChannel;
import com.korur.oba.apps.kazan.chat.entity.ChatMessage;
import com.korur.oba.apps.kazan.chat.entity.ChatThread;
import com.korur.oba.apps.kazan.chat.helper.ChatTokenParser;
import com.korur.oba.apps.kazan.chat.mapper.ChatMessageMapperHelper;
import com.korur.oba.apps.kazan.chat.mapper.ChatMessageMapperImpl;
import com.korur.oba.apps.kazan.chat.mock_data.ChatMocks;
import com.korur.oba.apps.kazan.chat.repository.ChatChannelRepository;
import com.korur.oba.apps.kazan.chat.repository.ChatMessageMentionRepository;
import com.korur.oba.apps.kazan.chat.repository.ChatMessageRepository;
import com.korur.oba.apps.kazan.chat.repository.ChatThreadMemberRepository;
import com.korur.oba.apps.kazan.chat.repository.ChatThreadRepository;
import com.korur.oba.apps.kazan.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kazan.chat.validator.ChatValidator;
import com.korur.oba.apps.kazan.dailyupdate.service.DailyUpdateQueryService;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.pullrequest.enums.PullRequestShareStatus;
import com.korur.oba.apps.kazan.ticket.mapper.TicketEmbeddedMapperImpl;
import com.korur.oba.apps.kazan.ticket.service.TicketQueryService;
import com.korur.oba.dojo.exception.enums.ExceptionValidationError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.time.BusinessCalendar;
import com.korur.oba.shrine.access.service.ResourceAccessQueryService;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.service.UserQueryService;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;
import com.korur.oba.shrine.storage.service.attachment.AttachmentQueryService;

@ExtendWith(MockitoExtension.class)
class ChatMessageQueryServiceImplUnitTest {

    private static final List<String> ALL_STATUSES = List.of("DRAFT", "OPEN", "IN_REVIEW", "MERGED", "DISCUSS");

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private AppAccessValidator appAccessValidator;
    @Mock
    private ChatThreadRepository chatThreadRepository;
    @Mock
    private ChatThreadMemberRepository chatThreadMemberRepository;
    @Mock
    private ChatMessageMentionRepository chatMessageMentionRepository;
    @Mock
    private ResourceAccessQueryService resourceAccessQueryService;
    @Mock
    private ChatChannelRepository chatChannelRepository;
    @Mock
    private ChatChannelQueryService chatChannelQueryService;
    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;
    @Mock
    private TicketQueryService ticketQueryService;
    @Mock
    private UserQueryService userQueryService;
    @Mock
    private AttachmentQueryService attachmentQueryService;
    @Mock
    private DailyUpdateQueryService dailyUpdateQueryService;

    private ChatMessageQueryServiceImpl service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID channelId = UUID.randomUUID();
    private final User author = ChatMocks.user("Ada", "Lovelace");
    private final User reader = ChatMocks.user("Alan", "Turing");

    @BeforeEach
    void setUp() {
        LoggingService loggingService = new LoggingService();
        ChatMessageMapperImpl mapper = new ChatMessageMapperImpl(new TicketEmbeddedMapperImpl());
        ChatValidator validator = new ChatValidator(chatMessageRepository, chatThreadRepository,
                dailyUpdateQueryService, null, appAccessValidator, loggingService);
        ChatMessageMapperHelper mapperHelper = new ChatMessageMapperHelper(mapper, new ChatTokenParser(),
                resourceAccessQueryService, chatMessageRepository, chatMessageMentionRepository,
                chatThreadMemberRepository, ticketQueryService, userQueryService, organizationMemberQueryService,
                attachmentQueryService, dailyUpdateQueryService, null);
        service = new ChatMessageQueryServiceImpl(chatMessageRepository, chatChannelRepository,
                chatChannelQueryService, validator, mapperHelper, loggingService,
                new BusinessCalendar(Clock.systemUTC()));

        lenient().when(chatChannelQueryService.verifyChannelAccess(eq(organizationId), eq(reader.getId()), any()))
                .thenAnswer(invocation -> {
                    ChatChannel channel = ChatChannel.builder().name("General").organizationId(organizationId).build();
                    channel.setId(invocation.getArgument(2));
                    return channel;
                });

        lenient().when(organizationMemberQueryService.listActiveMemberSummaries(eq(organizationId), any()))
                .thenReturn(List.of(ChatMocks.member(author), ChatMocks.member(reader)));
        lenient().when(resourceAccessQueryService.withReadGrant(any(), any(), anyCollection(), any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(3).get());
        lenient().when(resourceAccessQueryService.withCheckedReadGrant(any(), any(), any(), anyCollection(), any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(4).get());
    }

    private ChatMessage message(String content, String createdAt) {
        ChatMessage message = ChatMocks.message(author, organizationId, content);
        message.setChannelId(channelId);
        message.setCreatedAt(Instant.parse(createdAt));
        return message;
    }

    private ChatMessage message(String content, String createdAt, String id) {
        ChatMessage message = message(content, createdAt);
        message.setId(UUID.fromString(id));
        return message;
    }

    private ChatMessage givenCursor(ChatMessage cursor) {
        when(chatMessageRepository.findByIdWithDeleted(cursor.getId())).thenReturn(Optional.of(cursor));
        return cursor;
    }

    private ChatMessageQueryRequest page(Integer limit) {
        ChatMessageQueryRequest request = new ChatMessageQueryRequest();
        request.setChannelId(channelId);
        request.setLimit(limit);
        return request;
    }

    private static List<String> contents(ChatMessagePageResponse page) {
        return page.items().stream().map(ChatMessageResponse::content).toList();
    }

    @Nested
    class ChannelPages {

        @Test
        void latest_dropsTheProbeRowAndReportsOlder() {
            // Arrange
            when(chatMessageRepository.findLatestChannelMessages(channelId, null, null, null, true, ALL_STATUSES, 3))
                    .thenReturn(List.of(message("a", "2026-10-20T07:00:00Z"), message("b", "2026-10-20T07:01:00Z"),
                    message("c", "2026-10-20T07:02:00Z")));

            // Act
            ChatMessagePageResponse response = service.listChannelMessages(reader, organizationId, page(2));

            // Assert
            assertThat(contents(response)).containsExactly("b", "c");
            assertThat(response.hasOlder()).isTrue();
            assertThat(response.hasNewer()).isFalse();
            verify(chatChannelQueryService).verifyChannelAccess(organizationId, reader.getId(), channelId);
        }

        @Test
        void before_hasNewerAndDropsOldestProbe() {
            // Arrange
            ChatMessage cursor = givenCursor(message("cursor", "2026-10-20T08:00:00Z"));
            when(chatMessageRepository.findChannelMessagesBefore(channelId, null, null, null, true, ALL_STATUSES,
                    cursor.getCreatedAt(), cursor.getId(), 3)).thenReturn(List.of(message("a", "2026-10-20T07:00:00Z"),
                    message("b", "2026-10-20T07:01:00Z"), message("c", "2026-10-20T07:02:00Z")));
            ChatMessageQueryRequest request = page(2);
            request.setBefore(cursor.getId());

            // Act
            ChatMessagePageResponse response = service.listChannelMessages(reader, organizationId, request);

            // Assert
            assertThat(contents(response)).containsExactly("b", "c");
            assertThat(response.hasOlder()).isTrue();
            assertThat(response.hasNewer()).isTrue();
        }

        @Test
        void after_withoutProbeRow_reportsNoNewer() {
            // Arrange
            ChatMessage cursor = givenCursor(message("cursor", "2026-10-20T06:00:00Z"));
            when(chatMessageRepository.findChannelMessagesAfter(channelId, null, null, null, true, ALL_STATUSES,
                    cursor.getCreatedAt(), cursor.getId(), 3))
                    .thenReturn(List.of(message("a", "2026-10-20T07:00:00Z")));
            ChatMessageQueryRequest request = page(2);
            request.setAfter(cursor.getId());

            // Act
            ChatMessagePageResponse response = service.listChannelMessages(reader, organizationId, request);

            // Assert
            assertThat(contents(response)).containsExactly("a");
            assertThat(response.hasOlder()).isTrue();
            assertThat(response.hasNewer()).isFalse();
        }

        @Test
        void around_splitsAtCursorInDatabaseOrderAndProbesBothSides() {
            // Arrange
            // Same created_at: PostgreSQL orders the IDs by their bytes, so f0... is newer than 80...
            ChatMessage cursor = givenCursor(message("cursor", "2026-10-20T07:00:00Z",
                    "80000000-0000-0000-0000-000000000000"));
            List<ChatMessage> rows = List.of(
                    message("o1", "2026-10-20T06:00:00Z"),
                    message("o2", "2026-10-20T06:30:00Z"),
                    message("o3", "2026-10-20T07:00:00Z", "10000000-0000-0000-0000-000000000000"),
                    cursor,
                    message("n1", "2026-10-20T07:00:00Z", "f0000000-0000-0000-0000-000000000000"));
            when(chatMessageRepository.findChannelMessagesAround(channelId, null, null, null, true, ALL_STATUSES,
                    cursor.getCreatedAt(), cursor.getId(), 3, 3)).thenReturn(rows);
            ChatMessageQueryRequest request = page(4);
            request.setAround(cursor.getId());

            // Act
            ChatMessagePageResponse response = service.listChannelMessages(reader, organizationId, request);

            // Assert
            assertThat(contents(response)).containsExactly("o2", "o3", "cursor", "n1");
            assertThat(response.hasOlder()).isTrue();
            assertThat(response.hasNewer()).isFalse();
        }

        @Test
        void listChannelMessages_dayGiven_passesIstanbulBoundsToEveryPage() {
            // Arrange
            Instant start = Instant.parse("2026-11-02T21:00:00Z");
            Instant end = Instant.parse("2026-11-03T21:00:00Z");
            ChatMessage cursor = givenCursor(message("cursor", "2026-11-03T08:00:00Z"));
            when(chatMessageRepository.findLatestChannelMessages(channelId, start, end, null, true, ALL_STATUSES, 3))
                    .thenReturn(List.of());
            when(chatMessageRepository.findChannelMessagesBefore(channelId, start, end, null, true, ALL_STATUSES,
                    cursor.getCreatedAt(), cursor.getId(), 3)).thenReturn(List.of());
            when(chatMessageRepository.findChannelMessagesAfter(channelId, start, end, null, true, ALL_STATUSES,
                    cursor.getCreatedAt(), cursor.getId(), 3)).thenReturn(List.of());
            when(chatMessageRepository.findChannelMessagesAround(channelId, start, end, null, true, ALL_STATUSES,
                    cursor.getCreatedAt(), cursor.getId(), 3, 3)).thenReturn(List.of());
            LocalDate day = LocalDate.of(2026, 11, 3);

            ChatMessageQueryRequest latest = page(2);
            latest.setDay(day);
            ChatMessageQueryRequest before = page(2);
            before.setDay(day);
            before.setBefore(cursor.getId());
            ChatMessageQueryRequest after = page(2);
            after.setDay(day);
            after.setAfter(cursor.getId());
            ChatMessageQueryRequest around = page(4);
            around.setDay(day);
            around.setAround(cursor.getId());

            // Act
            service.listChannelMessages(reader, organizationId, latest);
            service.listChannelMessages(reader, organizationId, before);
            service.listChannelMessages(reader, organizationId, after);
            service.listChannelMessages(reader, organizationId, around);

            // Assert
            verify(chatMessageRepository).findLatestChannelMessages(channelId, start, end, null, true, ALL_STATUSES, 3);
            verify(chatMessageRepository).findChannelMessagesBefore(channelId, start, end, null, true, ALL_STATUSES,
                    cursor.getCreatedAt(), cursor.getId(), 3);
            verify(chatMessageRepository).findChannelMessagesAfter(channelId, start, end, null, true, ALL_STATUSES,
                    cursor.getCreatedAt(), cursor.getId(), 3);
            verify(chatMessageRepository).findChannelMessagesAround(channelId, start, end, null, true, ALL_STATUSES,
                    cursor.getCreatedAt(), cursor.getId(), 3, 3);
        }

        @Test
        void listChannelMessages_authorIdGiven_passesAuthorId() {
            // Arrange
            UUID authorId = UUID.randomUUID();
            ChatMessage cursor = givenCursor(message("cursor", "2026-11-03T08:00:00Z"));
            when(chatMessageRepository.findLatestChannelMessages(channelId, null, null, authorId, true, ALL_STATUSES,
                    3)).thenReturn(List.of());
            when(chatMessageRepository.findChannelMessagesBefore(channelId, null, null, authorId, true, ALL_STATUSES,
                    cursor.getCreatedAt(), cursor.getId(), 3)).thenReturn(List.of());
            when(chatMessageRepository.findChannelMessagesAfter(channelId, null, null, authorId, true, ALL_STATUSES,
                    cursor.getCreatedAt(), cursor.getId(), 3)).thenReturn(List.of());
            when(chatMessageRepository.findChannelMessagesAround(channelId, null, null, authorId, true, ALL_STATUSES,
                    cursor.getCreatedAt(), cursor.getId(), 3, 3)).thenReturn(List.of());
            ChatMessageQueryRequest latest = page(2);
            latest.setAuthorId(authorId);
            ChatMessageQueryRequest before = page(2);
            before.setAuthorId(authorId);
            before.setBefore(cursor.getId());
            ChatMessageQueryRequest after = page(2);
            after.setAuthorId(authorId);
            after.setAfter(cursor.getId());
            ChatMessageQueryRequest around = page(4);
            around.setAuthorId(authorId);
            around.setAround(cursor.getId());

            // Act
            service.listChannelMessages(reader, organizationId, latest);
            service.listChannelMessages(reader, organizationId, before);
            service.listChannelMessages(reader, organizationId, after);
            service.listChannelMessages(reader, organizationId, around);

            // Assert
            verify(chatMessageRepository).findLatestChannelMessages(channelId, null, null, authorId, true, ALL_STATUSES,
                    3);
            verify(chatMessageRepository).findChannelMessagesBefore(channelId, null, null, authorId, true, ALL_STATUSES,
                    cursor.getCreatedAt(), cursor.getId(), 3);
            verify(chatMessageRepository).findChannelMessagesAfter(channelId, null, null, authorId, true, ALL_STATUSES,
                    cursor.getCreatedAt(), cursor.getId(), 3);
            verify(chatMessageRepository).findChannelMessagesAround(channelId, null, null, authorId, true, ALL_STATUSES,
                    cursor.getCreatedAt(), cursor.getId(), 3, 3);
        }

        @Test
        void listChannelMessages_noFilter_passesNulls() {
            // Arrange
            when(chatMessageRepository.findLatestChannelMessages(channelId, null, null, null, true, ALL_STATUSES, 3))
                    .thenReturn(List.of());

            // Act
            service.listChannelMessages(reader, organizationId, page(2));

            // Assert
            verify(chatMessageRepository).findLatestChannelMessages(eq(channelId), isNull(), isNull(), isNull(),
                    eq(true), eq(ALL_STATUSES), eq(3));
        }

        @Test
        void listChannelMessages_pullRequestStatusesGiven_passesFalseAndTheNamesToEveryPage() {
            // Arrange
            List<String> statuses = List.of("IN_REVIEW", "OPEN");
            ChatMessage cursor = givenCursor(message("cursor", "2026-11-03T08:00:00Z"));
            when(chatMessageRepository.findLatestChannelMessages(channelId, null, null, null, false, statuses, 3))
                    .thenReturn(List.of());
            when(chatMessageRepository.findChannelMessagesBefore(channelId, null, null, null, false, statuses,
                    cursor.getCreatedAt(), cursor.getId(), 3)).thenReturn(List.of());
            when(chatMessageRepository.findChannelMessagesAfter(channelId, null, null, null, false, statuses,
                    cursor.getCreatedAt(), cursor.getId(), 3)).thenReturn(List.of());
            when(chatMessageRepository.findChannelMessagesAround(channelId, null, null, null, false, statuses,
                    cursor.getCreatedAt(), cursor.getId(), 3, 3)).thenReturn(List.of());
            List<PullRequestShareStatus> chosen = List.of(PullRequestShareStatus.IN_REVIEW,
                    PullRequestShareStatus.OPEN);
            ChatMessageQueryRequest latest = page(2);
            latest.setPullRequestStatuses(chosen);
            ChatMessageQueryRequest before = page(2);
            before.setPullRequestStatuses(chosen);
            before.setBefore(cursor.getId());
            ChatMessageQueryRequest after = page(2);
            after.setPullRequestStatuses(chosen);
            after.setAfter(cursor.getId());
            ChatMessageQueryRequest around = page(4);
            around.setPullRequestStatuses(chosen);
            around.setAround(cursor.getId());

            // Act
            service.listChannelMessages(reader, organizationId, latest);
            service.listChannelMessages(reader, organizationId, before);
            service.listChannelMessages(reader, organizationId, after);
            service.listChannelMessages(reader, organizationId, around);

            // Assert
            verify(chatMessageRepository).findLatestChannelMessages(channelId, null, null, null, false, statuses, 3);
            verify(chatMessageRepository).findChannelMessagesBefore(channelId, null, null, null, false, statuses,
                    cursor.getCreatedAt(), cursor.getId(), 3);
            verify(chatMessageRepository).findChannelMessagesAfter(channelId, null, null, null, false, statuses,
                    cursor.getCreatedAt(), cursor.getId(), 3);
            verify(chatMessageRepository).findChannelMessagesAround(channelId, null, null, null, false, statuses,
                    cursor.getCreatedAt(), cursor.getId(), 3, 3);
        }

        @Test
        void listChannelMessages_emptyPullRequestStatuses_appliesNoFilter() {
            // Arrange
            when(chatMessageRepository.findLatestChannelMessages(channelId, null, null, null, true, ALL_STATUSES, 3))
                    .thenReturn(List.of());
            ChatMessageQueryRequest request = page(2);
            request.setPullRequestStatuses(List.of());

            // Act
            service.listChannelMessages(reader, organizationId, request);

            // Assert
            verify(chatMessageRepository).findLatestChannelMessages(channelId, null, null, null, true, ALL_STATUSES,
                    3);
        }

        @Test
        void listChannelMessages_statusesWithDayAndAuthor_passesAllThreeFilters() {
            // Arrange
            UUID authorId = UUID.randomUUID();
            Instant start = Instant.parse("2026-11-02T21:00:00Z");
            Instant end = Instant.parse("2026-11-03T21:00:00Z");
            when(chatMessageRepository.findLatestChannelMessages(channelId, start, end, authorId, false,
                    List.of("MERGED"), 3)).thenReturn(List.of());
            ChatMessageQueryRequest request = page(2);
            request.setDay(LocalDate.of(2026, 11, 3));
            request.setAuthorId(authorId);
            request.setPullRequestStatuses(List.of(PullRequestShareStatus.MERGED));

            // Act
            service.listChannelMessages(reader, organizationId, request);

            // Assert
            verify(chatMessageRepository).findLatestChannelMessages(channelId, start, end, authorId, false,
                    List.of("MERGED"), 3);
        }

        @Test
        void deletedCursor_stillLoadsItsNeighbours() {
            // Arrange
            ChatMessage cursor = message("gone", "2026-10-20T08:00:00Z");
            cursor.setDeleted(true);
            givenCursor(cursor);
            when(chatMessageRepository.findChannelMessagesBefore(eq(channelId), isNull(), isNull(), isNull(), eq(true),
                    eq(ALL_STATUSES), eq(cursor.getCreatedAt()),
                    eq(cursor.getId()), anyInt())).thenReturn(List.of(message("a", "2026-10-20T07:00:00Z")));
            ChatMessageQueryRequest request = page(2);
            request.setBefore(cursor.getId());

            // Act
            ChatMessagePageResponse response = service.listChannelMessages(reader, organizationId, request);

            // Assert
            assertThat(contents(response)).containsExactly("a");
        }

        @Test
        void twoCursors_areRefused() {
            // Arrange
            ChatMessageQueryRequest request = page(2);
            request.setBefore(UUID.randomUUID());
            request.setAfter(UUID.randomUUID());

            // Act and Assert
            assertThatThrownBy(() -> service.listChannelMessages(reader, organizationId, request))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode").isEqualTo(ExceptionDomainKazanError.VAL_CHAT_CURSOR_CONFLICT.getCode());
        }

        @Test
        void cursorOfAnotherOrganization_isNotFound() {
            // Arrange
            ChatMessage foreign = ChatMocks.message(author, UUID.randomUUID(), "elsewhere");
            givenCursor(foreign);
            ChatMessageQueryRequest request = page(2);
            request.setBefore(foreign.getId());

            // Act and Assert
            assertThatThrownBy(() -> service.listChannelMessages(reader, organizationId, request))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode").isEqualTo(ExceptionDomainKazanError.NOT_CHAT_MESSAGE_FOUND.getCode());
            verify(chatMessageRepository, never()).findChannelMessagesBefore(any(), any(), any(), any(), anyBoolean(),
                    any(), any(), any(), anyInt());
        }

        @Test
        void cursorOfAnotherChannel_isNotFound() {
            // Arrange
            ChatMessage elsewhere = message("other channel", "2026-10-20T08:00:00Z");
            elsewhere.setChannelId(UUID.randomUUID());
            givenCursor(elsewhere);
            ChatMessageQueryRequest request = page(2);
            request.setBefore(elsewhere.getId());

            // Act and Assert
            assertThatThrownBy(() -> service.listChannelMessages(reader, organizationId, request))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode").isEqualTo(ExceptionDomainKazanError.NOT_CHAT_MESSAGE_FOUND.getCode());
            verify(chatMessageRepository, never()).findChannelMessagesBefore(any(), any(), any(), any(), anyBoolean(),
                    any(), any(), any(), anyInt());
        }
    }

    @Nested
    class ThreadPages {

        @Test
        void latest_readsTheThreadsMessages() {
            // Arrange
            ChatThread thread = ChatMocks.thread(organizationId, UUID.randomUUID(), author.getId());
            when(chatThreadRepository.findByIdAndOrganizationId(thread.getId(), organizationId))
                    .thenReturn(Optional.of(thread));
            when(chatMessageRepository.findLatestThreadMessages(thread.getId(), 51))
                    .thenReturn(List.of(message("a", "2026-10-20T07:00:00Z")));

            // Act
            ChatMessagePageResponse response = service.listThreadMessages(reader, organizationId, thread.getId(),
                    page(null));

            // Assert
            assertThat(contents(response)).containsExactly("a");
            assertThat(response.hasOlder()).isFalse();
        }

        @Test
        void threadOfAnotherOrganization_isNotFound() {
            // Arrange
            UUID threadId = UUID.randomUUID();
            when(chatThreadRepository.findByIdAndOrganizationId(threadId, organizationId))
                    .thenReturn(Optional.empty());

            // Act and Assert
            assertThatThrownBy(() -> service.listThreadMessages(reader, organizationId, threadId, page(2)))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode").isEqualTo(ExceptionDomainKazanError.NOT_CHAT_THREAD_FOUND.getCode());
        }
    }

    @Nested
    class SingleMessage {

        @Test
        void getMessage_returnsLiveMessage() {
            // Arrange
            ChatMessage message = message("hello", "2026-10-20T07:00:00Z");
            when(chatMessageRepository.findByIdAndOrganizationId(message.getId(), organizationId))
                    .thenReturn(Optional.of(message));

            // Act
            ChatMessageResponse response = service.getMessage(reader, organizationId, message.getId());

            // Assert
            assertThat(response.id()).isEqualTo(message.getId());
            assertThat(response.clientNonce()).isNull();
        }

        @Test
        void getMessage_absentIsNotFound() {
            // Arrange
            UUID messageId = UUID.randomUUID();
            when(chatMessageRepository.findByIdAndOrganizationId(messageId, organizationId))
                    .thenReturn(Optional.empty());

            // Act and Assert
            assertThatThrownBy(() -> service.getMessage(reader, organizationId, messageId))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class NeutralMessage {

        @Test
        void goneMessage_returnsEmpty() {
            // Arrange
            UUID messageId = UUID.randomUUID();
            when(chatMessageRepository.findByIdAndOrganizationId(messageId, organizationId))
                    .thenReturn(Optional.empty());

            // Act and assert
            assertThat(service.findNeutralMessage(organizationId, messageId, null)).isEmpty();
        }

        @Test
        void plainMessage_returnsCopyForNoReader() {
            // Arrange
            ChatMessage message = message("hello " + ChatMocks.userToken(reader.getId()), "2026-10-20T07:30:00Z");
            message.setUpdatedAt(Instant.parse("2026-10-20T07:40:00Z"));
            when(chatMessageRepository.findByIdAndOrganizationId(message.getId(), organizationId))
                    .thenReturn(Optional.of(message));

            // Act
            Optional<ChatMessageResponse> response = service.findNeutralMessage(organizationId, message.getId(),
                    "nonce-1");

            // Assert
            assertThat(response).isPresent();
            assertThat(response.get().mentionsMe()).isFalse();
            assertThat(response.get().clientNonce()).isEqualTo("nonce-1");
            assertThat(response.get().version()).isEqualTo(Instant.parse("2026-10-20T07:40:00Z"));
            verify(chatChannelQueryService, never()).verifyChannelAccess(any(), any(), any());
        }
    }

    @Nested
    class MessageVersion {

        @Test
        void liveMessage_returnsItsLastChange() {
            // Arrange
            ChatMessage message = message("hello", "2026-10-20T07:30:00Z");
            message.setUpdatedAt(Instant.parse("2026-10-20T07:40:00Z"));
            when(chatMessageRepository.findByIdAndOrganizationId(message.getId(), organizationId))
                    .thenReturn(Optional.of(message));

            // Act and assert
            assertThat(service.findMessageVersion(organizationId, message.getId()))
                    .contains(Instant.parse("2026-10-20T07:40:00Z"));
        }

        @Test
        void goneOrDeletedMessage_returnsEmpty() {
            // Arrange
            UUID goneId = UUID.randomUUID();
            when(chatMessageRepository.findByIdAndOrganizationId(goneId, organizationId))
                    .thenReturn(Optional.empty());
            ChatMessage deleted = message("hello", "2026-10-20T07:30:00Z");
            deleted.setDeleted(true);
            when(chatMessageRepository.findByIdAndOrganizationId(deleted.getId(), organizationId))
                    .thenReturn(Optional.of(deleted));

            // Act and assert
            assertThat(service.findMessageVersion(organizationId, goneId)).isEmpty();
            assertThat(service.findMessageVersion(organizationId, deleted.getId())).isEmpty();
        }
    }

    @Nested
    class Pins {

        @Test
        void firstPage_newestPinFirstWithProbe() {
            // Arrange
            when(chatMessageRepository.findPinnedMessages(channelId, 3)).thenReturn(List.of(
                    message("p3", "2026-10-20T07:03:00Z"), message("p2", "2026-10-20T07:02:00Z"),
                    message("p1", "2026-10-20T07:01:00Z")));

            // Act
            ChatMessagePageResponse response = service.listPinnedMessages(reader, organizationId, channelId, null, 2);

            // Assert
            assertThat(contents(response)).containsExactly("p3", "p2");
            assertThat(response.hasOlder()).isTrue();
            assertThat(response.hasNewer()).isFalse();
        }

        @Test
        void nextPage_readsBeforeCursorPin() {
            // Arrange
            ChatMessage cursor = message("p2", "2026-10-20T07:02:00Z");
            cursor.setPinnedAt(Instant.parse("2026-10-20T09:00:00Z"));
            givenCursor(cursor);
            when(chatMessageRepository.findPinnedMessagesBefore(channelId, cursor.getPinnedAt(),
                    cursor.getId(), 3)).thenReturn(List.of(message("p1", "2026-10-20T07:01:00Z")));

            // Act
            ChatMessagePageResponse response = service.listPinnedMessages(reader, organizationId, channelId,
                    cursor.getId(),
                    2);

            // Assert
            assertThat(contents(response)).containsExactly("p1");
            assertThat(response.hasOlder()).isFalse();
            assertThat(response.hasNewer()).isTrue();
        }

        @Test
        void unpinnedCursor_isNotFound() {
            // Arrange
            ChatMessage cursor = givenCursor(message("was pinned", "2026-10-20T07:02:00Z"));

            // Act and Assert
            assertThatThrownBy(() -> service.listPinnedMessages(reader, organizationId, channelId, cursor.getId(), 2))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class Search {

        @Test
        void withCursor_passesItsPositionAndReportsNewer() {
            // Arrange
            ChatMessage cursor = givenCursor(message("release later", "2026-10-20T08:00:00Z"));
            ChatSearchRequest request = new ChatSearchRequest();
            request.setQ("release");
            request.setAuthorId(author.getId());
            request.setBefore(cursor.getId());
            request.setLimit(1);
            when(chatChannelRepository.findReadableChannelIds(organizationId, reader.getId()))
                    .thenReturn(List.of(channelId));
            when(chatMessageRepository.searchMessages(organizationId, List.of(channelId), "release", author.getId(),
                    cursor.getCreatedAt(), cursor.getId(), 2)).thenReturn(List.of(
                    message("release b", "2026-10-20T07:30:00Z"), message("release a", "2026-10-20T07:00:00Z")));

            // Act
            ChatMessagePageResponse response = service.searchMessages(reader, organizationId, request);

            // Assert
            assertThat(contents(response)).containsExactly("release b");
            assertThat(response.hasOlder()).isTrue();
            assertThat(response.hasNewer()).isTrue();
        }

        @Test
        void firstPage_hasNoCursor() {
            // Arrange
            ChatSearchRequest request = new ChatSearchRequest();
            request.setQ("release");
            when(chatChannelRepository.findReadableChannelIds(organizationId, reader.getId()))
                    .thenReturn(List.of(channelId));
            when(chatMessageRepository.searchMessages(organizationId, List.of(channelId), "release", null, null, null,
                    26))
                    .thenReturn(List.of(message("release a", "2026-10-20T07:00:00Z")));

            // Act
            ChatMessagePageResponse response = service.searchMessages(reader, organizationId, request);

            // Assert
            assertThat(contents(response)).containsExactly("release a");
            assertThat(response.hasOlder()).isFalse();
            assertThat(response.hasNewer()).isFalse();
        }

        @Test
        void namedChannel_searchesOnlyThatChannelAfterTheAccessCheck() {
            // Arrange
            ChatSearchRequest request = new ChatSearchRequest();
            request.setQ("release");
            request.setChannelId(channelId);
            when(chatMessageRepository.searchMessages(organizationId, List.of(channelId), "release", null, null, null,
                    26))
                    .thenReturn(List.of(message("release a", "2026-10-20T07:00:00Z")));

            // Act
            ChatMessagePageResponse response = service.searchMessages(reader, organizationId, request);

            // Assert
            assertThat(contents(response)).containsExactly("release a");
            verify(chatChannelQueryService).verifyChannelAccess(organizationId, reader.getId(), channelId);
            verify(chatChannelRepository, never()).findReadableChannelIds(any(), any());
        }

        @Test
        void unreadableNamedChannel_isRefusedWithoutSearching() {
            // Arrange
            ChatSearchRequest request = new ChatSearchRequest();
            request.setQ("release");
            request.setChannelId(channelId);
            when(chatChannelQueryService.verifyChannelAccess(organizationId, reader.getId(), channelId))
                    .thenThrow(new IllegalStateException("no access"));

            // Act and assert
            assertThatThrownBy(() -> service.searchMessages(reader, organizationId, request))
                    .isInstanceOf(IllegalStateException.class);
            verify(chatMessageRepository, never()).searchMessages(any(), any(), any(), any(), any(), any(), anyInt());
        }

        @Test
        void noReadableChannel_returnsAnEmptyPageWithoutSearching() {
            // Arrange
            ChatSearchRequest request = new ChatSearchRequest();
            request.setQ("release");
            when(chatChannelRepository.findReadableChannelIds(organizationId, reader.getId())).thenReturn(List.of());

            // Act
            ChatMessagePageResponse response = service.searchMessages(reader, organizationId, request);

            // Assert
            assertThat(response.items()).isEmpty();
            verify(chatMessageRepository, never()).searchMessages(any(), any(), any(), any(), any(), any(), anyInt());
        }
    }

    @Nested
    class PageSize {

        @ParameterizedTest
        @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, ChatConstant.MAX_PAGE_SIZE + 1, Integer.MAX_VALUE})
        void channelPage_limitOutsideOneToFifty_isRefusedBeforeAnyRead(int limit) {
            // Arrange
            ChatMessageQueryRequest request = page(limit);

            // Act and Assert
            assertThatThrownBy(() -> service.listChannelMessages(reader, organizationId, request))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode").isEqualTo(ExceptionValidationError.VALIDATION.getCode());
            verify(chatMessageRepository, never()).findLatestChannelMessages(any(), any(), any(), any(), anyBoolean(),
                    any(), anyInt());
        }

        @Test
        void channelPage_largestLimit_readsItWithTheProbeRow() {
            // Arrange
            when(chatMessageRepository.findLatestChannelMessages(eq(channelId), isNull(), isNull(), isNull(),
                    eq(true), eq(ALL_STATUSES), eq(ChatConstant.MAX_PAGE_SIZE + 1))).thenReturn(List.of());

            // Act
            ChatMessagePageResponse response = service.listChannelMessages(reader, organizationId,
                    page(ChatConstant.MAX_PAGE_SIZE));

            // Assert
            assertThat(response.items()).isEmpty();
            assertThat(response.hasOlder()).isFalse();
        }

        @Test
        void aroundPage_smallestLimit_probesNoOlderAndOneNewerRow() {
            // Arrange
            ChatMessage cursor = givenCursor(message("cursor", "2026-10-20T07:00:00Z"));
            when(chatMessageRepository.findChannelMessagesAround(channelId, null, null, null, true, ALL_STATUSES,
                    cursor.getCreatedAt(), cursor.getId(), 1, 2)).thenReturn(List.of(
                    message("older", "2026-10-20T06:00:00Z"), cursor));
            ChatMessageQueryRequest request = page(ChatConstant.MIN_PAGE_SIZE);
            request.setAround(cursor.getId());

            // Act
            ChatMessagePageResponse response = service.listChannelMessages(reader, organizationId, request);

            // Assert
            assertThat(contents(response)).containsExactly("cursor");
            assertThat(response.hasOlder()).isTrue();
            assertThat(response.hasNewer()).isFalse();
        }

        @ParameterizedTest
        @ValueSource(ints = {0, ChatConstant.MAX_PAGE_SIZE + 1, Integer.MAX_VALUE})
        void threadPage_limitOutsideOneToFifty_isRefusedBeforeAnyRead(int limit) {
            // Arrange
            ChatThread thread = ChatMocks.thread(organizationId, UUID.randomUUID(), author.getId());
            thread.setChannelId(channelId);
            when(chatThreadRepository.findByIdAndOrganizationId(thread.getId(), organizationId))
                    .thenReturn(Optional.of(thread));

            // Act and Assert
            assertThatThrownBy(() -> service.listThreadMessages(reader, organizationId, thread.getId(), page(limit)))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode").isEqualTo(ExceptionValidationError.VALIDATION.getCode());
            verify(chatMessageRepository, never()).findLatestThreadMessages(any(), anyInt());
        }

        @ParameterizedTest
        @ValueSource(ints = {0, ChatConstant.MAX_PAGE_SIZE + 1, Integer.MAX_VALUE})
        void pins_limitOutsideOneToFifty_isRefusedBeforeAnyRead(int limit) {
            // Act and Assert
            assertThatThrownBy(() -> service.listPinnedMessages(reader, organizationId, channelId, null, limit))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode").isEqualTo(ExceptionValidationError.VALIDATION.getCode());
            verify(chatMessageRepository, never()).findPinnedMessages(any(), anyInt());
        }

        @Test
        void pins_largestLimit_readsItWithTheProbeRow() {
            // Arrange
            when(chatMessageRepository.findPinnedMessages(channelId, ChatConstant.MAX_PAGE_SIZE + 1))
                    .thenReturn(List.of());

            // Act
            ChatMessagePageResponse response = service.listPinnedMessages(reader, organizationId, channelId, null,
                    ChatConstant.MAX_PAGE_SIZE);

            // Assert
            assertThat(response.items()).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(ints = {0, ChatConstant.MAX_PAGE_SIZE + 1, Integer.MAX_VALUE})
        void search_limitOutsideOneToFifty_isRefusedBeforeAnyRead(int limit) {
            // Arrange
            ChatSearchRequest request = new ChatSearchRequest();
            request.setQ("release");
            request.setLimit(limit);

            // Act and Assert
            assertThatThrownBy(() -> service.searchMessages(reader, organizationId, request))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode").isEqualTo(ExceptionValidationError.VALIDATION.getCode());
            verify(chatMessageRepository, never()).searchMessages(any(), any(), any(), any(), any(), any(), anyInt());
        }

        @Test
        void search_smallestLimit_readsOneRowAndTheProbe() {
            // Arrange
            ChatSearchRequest request = new ChatSearchRequest();
            request.setQ("release");
            request.setLimit(ChatConstant.MIN_PAGE_SIZE);
            when(chatChannelRepository.findReadableChannelIds(organizationId, reader.getId()))
                    .thenReturn(List.of(channelId));
            when(chatMessageRepository.searchMessages(organizationId, List.of(channelId), "release", null, null, null,
                    2)).thenReturn(List.of(message("release b", "2026-10-20T07:30:00Z"),
                    message("release a", "2026-10-20T07:00:00Z")));

            // Act
            ChatMessagePageResponse response = service.searchMessages(reader, organizationId, request);

            // Assert
            assertThat(contents(response)).containsExactly("release b");
            assertThat(response.hasOlder()).isTrue();
        }
    }
}
