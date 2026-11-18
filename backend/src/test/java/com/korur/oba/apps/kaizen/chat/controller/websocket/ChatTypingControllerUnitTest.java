package com.korur.oba.apps.kaizen.chat.controller.websocket;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.support.AnnotationExceptionHandlerMethodResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.constant.ChatConstant;
import com.korur.oba.apps.kaizen.chat.constant.controller.ChatControllerLogConstant;
import com.korur.oba.apps.kaizen.chat.dto.request.ChatTypingRequest;
import com.korur.oba.apps.kaizen.chat.service.ChatMessageService;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.connect.realtime.dto.RealtimePrincipal;
import com.korur.oba.connect.realtime.service.RealtimeSessionRegistry;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.exception.ExceptionDomainOrganizationError;
import com.korur.oba.shrine.organization.service.OrganizationAccessService;

@ExtendWith(MockitoExtension.class)
class ChatTypingControllerUnitTest {

    private static final long TYPING_MIN_INTERVAL_MS = 3_000L;
    private static final String SESSION_ID = "session-1";

    @Mock
    private ChatMessageService chatMessageService;
    @Mock
    private OrganizationAccessService organizationAccessService;
    @Mock
    private RealtimeSessionRegistry realtimeSessionRegistry;
    @Mock
    private LoggingService loggingService;

    private ChatTypingController controller;

    private final UUID channelId = UUID.randomUUID();

    private final RealtimePrincipal principal = new RealtimePrincipal(UUID.randomUUID(), UUID.randomUUID(),
            Instant.now().plusSeconds(300));

    @BeforeEach
    void setUp() {
        controller = new ChatTypingController(chatMessageService, organizationAccessService,
                realtimeSessionRegistry, loggingService,
                TYPING_MIN_INTERVAL_MS);
    }

    private void acceptFrames(boolean accepted) {
        when(realtimeSessionRegistry.tryAcquireSend(SESSION_ID, ChatConstant.TYPING_SEND_BUCKET, 1,
                TYPING_MIN_INTERVAL_MS)).thenReturn(accepted);
    }

    @Test
    void channelTyping_acceptedFrame_signalsWithoutAThread() {
        // Arrange
        acceptFrames(true);

        // Act
        controller.typing(new ChatTypingRequest(channelId, null), principal, SESSION_ID);

        // Assert
        verify(organizationAccessService).verifyOrganizationAccess(principal.organizationId(), principal.userId(),
                OrganizationMemberRole.MEMBER);
        verify(chatMessageService).signalTyping(principal.userId(), principal.organizationId(), channelId, null);
    }

    @Test
    void typing_nonMember_isRefusedBeforeTheService() {
        // Arrange
        acceptFrames(true);
        doThrow(notMember()).when(organizationAccessService).verifyOrganizationAccess(principal.organizationId(),
                principal.userId(), OrganizationMemberRole.MEMBER);

        // Act and Assert
        assertThatThrownBy(() -> controller.typing(new ChatTypingRequest(channelId, null), principal, SESSION_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ExceptionDomainOrganizationError.NOT_ORGANIZATION_MEMBER_FOUND.getCode());
        verifyNoInteractions(chatMessageService);
    }

    @Test
    void typing_guest_isRefusedBeforeTheService() {
        // Arrange
        acceptFrames(true);
        doThrow(guestRefused()).when(organizationAccessService).verifyOrganizationAccess(
                principal.organizationId(), principal.userId(), OrganizationMemberRole.MEMBER);

        // Act and Assert
        assertThatThrownBy(() -> controller.typing(new ChatTypingRequest(channelId, null), principal, SESSION_ID))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode")
                .isEqualTo(ExceptionDomainOrganizationError.SEC_INSUFFICIENT_PERMISSIONS.getCode());
        verifyNoInteractions(chatMessageService);
    }

    @Test
    void handleRefused_nonMember_isLoggedOnceAtWarnAndNothingIsSent() {
        // Act
        controller.handleRefused(notMember(), principal);

        // Assert
        verify(loggingService).warn(any(), eq(LogLayer.CONTROLLER),
                eq(ChatControllerLogConstant.CHAT_TYPING_REFUSED), eq(principal.userId()),
                eq(principal.organizationId()),
                eq(ExceptionDomainOrganizationError.NOT_ORGANIZATION_MEMBER_FOUND.getCode()), any());
        verifyNoMoreInteractions(loggingService);
        verifyNoInteractions(chatMessageService);
    }

    @Test
    void threadTyping_acceptedFrame_signalsTheThreadInTheSessionsOrganization() {
        // Arrange
        UUID threadId = UUID.randomUUID();
        acceptFrames(true);

        // Act
        controller.typing(new ChatTypingRequest(channelId, threadId), principal, SESSION_ID);

        // Assert
        verify(chatMessageService).signalTyping(principal.userId(), principal.organizationId(), channelId, threadId);
    }

    @Test
    void typing_frameWithinTheInterval_isDroppedBeforeTheService() {
        // Arrange
        acceptFrames(false);

        // Act
        controller.typing(new ChatTypingRequest(channelId, UUID.randomUUID()), principal, SESSION_ID);

        // Assert
        verifyNoInteractions(chatMessageService, organizationAccessService);
        verify(loggingService).observe(any(), eq(LogLayer.CONTROLLER),
                eq(ChatControllerLogConstant.CHAT_TYPING_THROTTLED), eq(SESSION_ID));
    }

    @Test
    void typing_serviceRefuses_throwsForTheExceptionHandler() {
        // Arrange
        UUID threadId = UUID.randomUUID();
        acceptFrames(true);
        doThrow(threadNotFound(threadId)).when(chatMessageService)
                .signalTyping(principal.userId(), principal.organizationId(), channelId, threadId);

        // Act and Assert
        assertThatThrownBy(() -> controller.typing(new ChatTypingRequest(channelId, threadId), principal, SESSION_ID))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void handleRefused_threadNotFound_isLoggedOnceAtWarnAndNothingIsSent() {
        // Arrange
        UUID threadId = UUID.randomUUID();

        // Act
        controller.handleRefused(threadNotFound(threadId), principal);

        // Assert
        verify(loggingService).warn(any(), eq(LogLayer.CONTROLLER),
                eq(ChatControllerLogConstant.CHAT_TYPING_REFUSED), eq(principal.userId()),
                eq(principal.organizationId()), eq(ExceptionDomainKaizenError.NOT_CHAT_THREAD_FOUND.getCode()),
                eq(Map.of(ChatConstant.FIELD_THREAD_ID, threadId)));
        verifyNoMoreInteractions(loggingService);
        verifyNoInteractions(chatMessageService);
    }

    @Test
    void handleRefused_guestRole_isLoggedOnceAtWarnAndNothingIsSent() {
        // Act
        controller.handleRefused(guestRefused(), principal);

        // Assert
        verify(loggingService).warn(any(), eq(LogLayer.CONTROLLER),
                eq(ChatControllerLogConstant.CHAT_TYPING_REFUSED), eq(principal.userId()),
                eq(principal.organizationId()),
                eq(ExceptionDomainOrganizationError.SEC_INSUFFICIENT_PERMISSIONS.getCode()), any());
        verifyNoMoreInteractions(loggingService);
        verifyNoInteractions(chatMessageService);
    }

    @Test
    void handleUnreadableBody_isLoggedOnceAtWarnAndNothingIsSent() {
        // Act
        controller.handleUnreadableBody(new MessageConversionException("Could not read JSON"), principal);

        // Assert
        verify(loggingService).warn(any(), eq(LogLayer.CONTROLLER),
                eq(ChatControllerLogConstant.CHAT_TYPING_BODY_UNREADABLE), eq(principal.userId()),
                eq(principal.organizationId()), anyString());
        verifyNoMoreInteractions(loggingService);
        verifyNoInteractions(chatMessageService);
    }

    @Test
    void exceptionHandlers_springRoutesEveryDropToTheControllersHandlers() {
        // Arrange
        AnnotationExceptionHandlerMethodResolver resolver =
                new AnnotationExceptionHandlerMethodResolver(ChatTypingController.class);

        // Act
        Method threadHandler = resolver.resolveMethod(threadNotFound(UUID.randomUUID()));
        Method roleHandler = resolver.resolveMethod(guestRefused());
        Method memberHandler = resolver.resolveMethod(notMember());
        Method bodyHandler = resolver.resolveMethod(new MessageConversionException("Could not read JSON"));

        // Assert
        assertThat(threadHandler).isNotNull();
        assertThat(threadHandler.getName()).isEqualTo("handleRefused");
        assertThat(threadHandler.getReturnType()).isEqualTo(void.class);
        assertThat(roleHandler).isEqualTo(threadHandler);
        assertThat(memberHandler).isEqualTo(threadHandler);
        assertThat(bodyHandler).isNotNull();
        assertThat(bodyHandler.getName()).isEqualTo("handleUnreadableBody");
        assertThat(bodyHandler.getReturnType()).isEqualTo(void.class);
    }

    private static NotFoundException threadNotFound(UUID threadId) {
        return new NotFoundException(ExceptionDomainKaizenError.NOT_CHAT_THREAD_FOUND,
                Map.of(ChatConstant.FIELD_THREAD_ID, threadId));
    }

    private static NotFoundException notMember() {
        return new NotFoundException(ExceptionDomainOrganizationError.NOT_ORGANIZATION_MEMBER_FOUND);
    }

    private static ForbiddenException guestRefused() {
        return new ForbiddenException(ExceptionDomainOrganizationError.SEC_INSUFFICIENT_PERMISSIONS);
    }
}
