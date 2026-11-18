package com.korur.oba.apps.kazan.chat.listener;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.ChatExceptionLogConstant;
import com.korur.oba.apps.kazan.chat.dto.response.ChatUnreadResponse;
import com.korur.oba.apps.kazan.chat.event.ChatPushRequestedEvent;
import com.korur.oba.apps.kazan.chat.event.ChatReadStateChangedEvent;
import com.korur.oba.apps.kazan.chat.service.ChatReadStateQueryService;
import com.korur.oba.connect.push.dto.PushRequest;
import com.korur.oba.connect.push.enums.PushKind;
import com.korur.oba.connect.push.service.PushDispatchService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessQueryService;

@ExtendWith(MockitoExtension.class)
class ChatPushListenerUnitTest {

    @Mock
    private PushDispatchService pushDispatchService;
    @Mock
    private ChatReadStateQueryService chatReadStateQueryService;
    @Mock
    private AppAccessQueryService appAccessQueryService;
    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private ChatPushListener listener;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID channelId = UUID.randomUUID();
    private final UUID threadId = UUID.randomUUID();
    private final UUID messageId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();
    private final UUID recipientId = UUID.randomUUID();

    private ChatPushRequestedEvent event() {
        return new ChatPushRequestedEvent(organizationId, channelId, threadId, messageId, authorId,
                Map.of(recipientId, PushKind.MENTION), "#general", "Ada Lovelace", "hello", false);
    }

    private void givenKazan(UUID userId, boolean granted) {
        when(appAccessQueryService.hasAppAccess(organizationId, userId, App.KAZAN)).thenReturn(granted);
    }

    private PushRequest dispatched() {
        ArgumentCaptor<PushRequest> captor = ArgumentCaptor.forClass(PushRequest.class);
        verify(pushDispatchService).dispatch(captor.capture());
        return captor.getValue();
    }

    @Test
    void onPushRequested_event_dispatchesAChatPushWithTheRecipientsMentionTotalAsBadge() {
        // Arrange
        ChatUnreadResponse unread = new ChatUnreadResponse(List.of(), List.of(), 9, 3);
        givenKazan(recipientId, true);
        when(chatReadStateQueryService.getUnreadByUsers(organizationId, event().recipients().keySet()))
                .thenReturn(Map.of(recipientId, unread));

        // Act
        listener.onPushRequested(event());

        // Assert
        PushRequest request = dispatched();
        assertThat(request.recipients()).isEqualTo(Map.of(recipientId, PushKind.MENTION));
        assertThat(request.actorId()).isEqualTo(authorId);
        assertThat(request.conversation()).isEqualTo("#general");
        assertThat(request.senderName()).isEqualTo("Ada Lovelace");
        assertThat(request.text()).isEqualTo("hello");
        assertThat(request.channelId()).isEqualTo(channelId);
        assertThat(request.threadId()).isEqualTo(threadId);
        assertThat(request.messageId()).isEqualTo(messageId);
        assertThat(request.badges()).isEqualTo(Map.of(recipientId, 3));
    }

    @Test
    void onPushRequested_badgeReadFails_sendsWithoutBadgesAndLogsTheFailure() {
        // Arrange
        RuntimeException failure = new IllegalStateException("database down");
        givenKazan(recipientId, true);
        when(chatReadStateQueryService.getUnreadByUsers(any(), any())).thenThrow(failure);

        // Act
        listener.onPushRequested(event());

        // Assert
        assertThat(dispatched().badges()).isNull();
        verify(loggingService).error(any(), eq(LogLayer.HANDLER),
                eq(ChatExceptionLogConstant.CHAT_PUSH_BADGES_FAILED), eq(failure), any(Object[].class));
    }

    @Test
    void onPushRequested_recipientWithoutKazan_isLeftOutOfThePushAndItsBadges() {
        // Arrange
        UUID revokedId = UUID.randomUUID();
        ChatPushRequestedEvent event = new ChatPushRequestedEvent(organizationId, channelId, threadId, messageId,
                authorId, Map.of(recipientId, PushKind.MENTION, revokedId, PushKind.MENTION), "#general",
                "Ada Lovelace", "hello", false);
        givenKazan(recipientId, true);
        givenKazan(revokedId, false);
        when(chatReadStateQueryService.getUnreadByUsers(organizationId, Set.of(recipientId)))
                .thenReturn(Map.of());

        // Act
        listener.onPushRequested(event);

        // Assert
        assertThat(dispatched().recipients()).isEqualTo(Map.of(recipientId, PushKind.MENTION));
    }

    @Test
    void onPushRequested_noRecipientWithKazan_dispatchesNothingAndReadsNoBadges() {
        // Arrange
        givenKazan(recipientId, false);

        // Act
        listener.onPushRequested(event());

        // Assert
        verifyNoInteractions(pushDispatchService, chatReadStateQueryService);
    }

    @Test
    void onPushRequested_accessReadFails_dispatchesNothingAndLogsTheFailure() {
        // Arrange
        RuntimeException failure = new IllegalStateException("database down");
        when(appAccessQueryService.hasAppAccess(organizationId, recipientId, App.KAZAN)).thenThrow(failure);

        // Act and assert
        assertThatCode(() -> listener.onPushRequested(event())).doesNotThrowAnyException();
        verifyNoInteractions(pushDispatchService, chatReadStateQueryService);
        verify(loggingService).error(any(), eq(LogLayer.HANDLER),
                eq(ChatExceptionLogConstant.CHAT_PUSH_DISPATCH_FAILED), eq(failure), any(Object[].class));
    }

    @Test
    void onPushRequested_dispatchFails_isLoggedAndNotRethrown() {
        // Arrange
        RuntimeException failure = new IllegalStateException("expo down");
        givenKazan(recipientId, true);
        when(chatReadStateQueryService.getUnreadByUsers(any(), any())).thenReturn(Map.of());
        doThrow(failure).when(pushDispatchService).dispatch(any(PushRequest.class));

        // Act and assert
        assertThatCode(() -> listener.onPushRequested(event())).doesNotThrowAnyException();
        verify(loggingService).error(any(), eq(LogLayer.HANDLER),
                eq(ChatExceptionLogConstant.CHAT_PUSH_DISPATCH_FAILED), eq(failure), any(Object[].class));
    }

    @Test
    void onReadStateChanged_event_dispatchesADataOnlyReadPushToTheReader() {
        // Arrange
        ChatReadStateChangedEvent event = new ChatReadStateChangedEvent(organizationId, channelId, recipientId,
                threadId, messageId);

        // Act
        listener.onReadStateChanged(event);

        // Assert
        PushRequest request = dispatched();
        assertThat(request.recipients()).isEqualTo(Map.of(recipientId, PushKind.READ));
        assertThat(request.actorId()).isEqualTo(recipientId);
        assertThat(request.channelId()).isEqualTo(channelId);
        assertThat(request.threadId()).isEqualTo(threadId);
        verify(chatReadStateQueryService, never()).getUnreadByUsers(any(), any());
        verifyNoInteractions(appAccessQueryService);
    }

    @Test
    void onReadStateChanged_dispatchFails_isLoggedAndNotRethrown() {
        // Arrange
        RuntimeException failure = new IllegalStateException("expo down");
        doThrow(failure).when(pushDispatchService).dispatch(any(PushRequest.class));
        ChatReadStateChangedEvent event = new ChatReadStateChangedEvent(organizationId, channelId, recipientId,
                null, messageId);

        // Act and assert
        assertThatCode(() -> listener.onReadStateChanged(event)).doesNotThrowAnyException();
        verify(loggingService).error(any(), eq(LogLayer.HANDLER),
                eq(ChatExceptionLogConstant.CHAT_READ_PUSH_DISPATCH_FAILED), eq(failure), any(Object[].class));
    }
}
