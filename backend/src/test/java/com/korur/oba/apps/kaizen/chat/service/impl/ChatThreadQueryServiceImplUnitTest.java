package com.korur.oba.apps.kaizen.chat.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.constant.ChatConstant;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatThreadPageResponse;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatThreadResponse;
import com.korur.oba.apps.kaizen.chat.entity.ChatChannel;
import com.korur.oba.apps.kaizen.chat.entity.ChatThread;
import com.korur.oba.apps.kaizen.chat.enums.ChatChannelVisibility;
import com.korur.oba.apps.kaizen.chat.enums.ChatThreadStatus;
import com.korur.oba.apps.kaizen.chat.mapper.ChatThreadMapperHelper;
import com.korur.oba.apps.kaizen.chat.mock_data.ChatMocks;
import com.korur.oba.apps.kaizen.chat.repository.ChatMessageRepository;
import com.korur.oba.apps.kaizen.chat.repository.ChatThreadRepository;
import com.korur.oba.apps.kaizen.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kaizen.chat.validator.ChatValidator;
import com.korur.oba.apps.kaizen.dailyupdate.service.DailyUpdateQueryService;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class ChatThreadQueryServiceImplUnitTest {

    @Mock
    private ChatThreadRepository chatThreadRepository;
    @Mock
    private AppAccessValidator appAccessValidator;
    @Mock
    private DailyUpdateQueryService dailyUpdateQueryService;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatThreadMapperHelper chatThreadMapperHelper;
    @Mock
    private ChatChannelQueryService chatChannelQueryService;

    private ChatThreadQueryServiceImpl service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID channelId = UUID.randomUUID();
    private final User user = ChatMocks.user("Ada", "Lovelace");

    @BeforeEach
    void setUp() {
        ChatValidator validator = new ChatValidator(chatMessageRepository, chatThreadRepository,
                dailyUpdateQueryService, null, appAccessValidator, new LoggingService());
        service = new ChatThreadQueryServiceImpl(chatThreadRepository, validator, chatThreadMapperHelper,
                chatChannelQueryService);
        ChatChannel channel = ChatChannel.builder()
                .name("General")
                .organizationId(organizationId)
                .visibility(ChatChannelVisibility.PUBLIC)
                .build();
        channel.setId(channelId);
        lenient().when(chatChannelQueryService.verifyChannelAccess(organizationId, user.getId(), channelId))
                .thenReturn(channel);
    }

    private List<ChatThread> threads(int count) {
        List<ChatThread> threads = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            ChatThread thread = ChatMocks.thread(organizationId, UUID.randomUUID(), user.getId());
            thread.setChannelId(channelId);
            threads.add(thread);
        }
        return threads;
    }

    @Nested
    class ListThreads {

        @Test
        void firstPage_withExtraRow_hasMoreAndDropsIt() {
            // Arrange
            List<ChatThread> rows = threads(3);
            when(chatThreadRepository.findLatestThreads(channelId, "ACTIVE", 3)).thenReturn(rows);
            ChatThreadPageResponse page = new ChatThreadPageResponse(List.of(), true);
            when(chatThreadMapperHelper.toPageResponse(rows.subList(0, 2), true, user, organizationId))
                    .thenReturn(page);

            // Act
            ChatThreadPageResponse result = service.listThreads(user, organizationId, channelId,
                    ChatThreadStatus.ACTIVE, null, 2);

            // Assert
            assertThat(result).isSameAs(page);
            verify(chatChannelQueryService).verifyChannelAccess(organizationId, user.getId(), channelId);
        }

        @Test
        void archivedPageAfterCursor_readsFromTheCursorsActivity() {
            // Arrange
            ChatThread cursor = ChatMocks.thread(organizationId, UUID.randomUUID(), user.getId());
            cursor.setChannelId(channelId);
            when(chatThreadRepository.findByIdAndOrganizationId(cursor.getId(), organizationId))
                    .thenReturn(Optional.of(cursor));
            List<ChatThread> rows = threads(1);
            when(chatThreadRepository.findThreadsBefore(channelId, "ARCHIVED", cursor.getLastMessageAt(),
                    cursor.getId(), 11)).thenReturn(rows);

            // Act
            service.listThreads(user, organizationId, channelId, ChatThreadStatus.ARCHIVED, cursor.getId(), 10);

            // Assert
            verify(chatThreadMapperHelper).toPageResponse(rows, false, user, organizationId);
        }

        @Test
        void closedListWithoutLimit_takesTheDefaultPageSize() {
            // Arrange
            when(chatThreadRepository.findLatestThreads(channelId, "CLOSED", ChatConstant.DEFAULT_PAGE_SIZE + 1))
                    .thenReturn(List.of());

            // Act
            service.listThreads(user, organizationId, channelId, ChatThreadStatus.CLOSED, null, null);

            // Assert
            verify(chatThreadMapperHelper).toPageResponse(List.of(), false, user, organizationId);
        }

        @Test
        void cursorOfAnotherOrganization_isNotFound() {
            // Arrange
            UUID unknownId = UUID.randomUUID();

            // Act and Assert
            assertThatThrownBy(() -> service.listThreads(user, organizationId, channelId, ChatThreadStatus.ACTIVE,
                    unknownId, 10))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKaizenError.NOT_CHAT_THREAD_FOUND.getCode());
            verify(chatThreadRepository, never()).findThreadsBefore(any(), anyString(), any(), any(), anyInt());
        }

        @Test
        void unreadableChannel_isRefusedBeforeAnyRead() {
            // Arrange
            when(chatChannelQueryService.verifyChannelAccess(organizationId, user.getId(), channelId))
                    .thenThrow(new NotFoundException(ExceptionDomainKaizenError.NOT_CHAT_CHANNEL_FOUND));

            // Act and Assert
            assertThatThrownBy(() -> service.listThreads(user, organizationId, channelId, ChatThreadStatus.ACTIVE,
                    null, 10))
                    .isInstanceOf(NotFoundException.class);
            verify(chatThreadRepository, never()).findLatestThreads(any(), anyString(), anyInt());
        }
    }

    @Nested
    class GetThread {

        @Test
        void threadOfTheOrganization_isMapped() {
            // Arrange
            ChatThread thread = ChatMocks.thread(organizationId, UUID.randomUUID(), user.getId());
            thread.setChannelId(channelId);
            when(chatThreadRepository.findByIdAndOrganizationId(thread.getId(), organizationId))
                    .thenReturn(Optional.of(thread));
            ChatThreadResponse response = mock(ChatThreadResponse.class);
            when(chatThreadMapperHelper.toResponse(thread, user, organizationId)).thenReturn(response);

            // Act
            ChatThreadResponse result = service.getThread(user, organizationId, thread.getId());

            // Assert
            assertThat(result).isSameAs(response);
        }

        @Test
        void threadOfAnotherOrganization_isNotFound() {
            // Arrange
            UUID unknownId = UUID.randomUUID();

            // Act and Assert
            assertThatThrownBy(() -> service.getThread(user, organizationId, unknownId))
                    .isInstanceOf(NotFoundException.class);
        }
    }
}
