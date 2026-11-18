package com.korur.oba.apps.kazan.chat.controller.websocket;

// 1. Framework-level
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.ChatConstant;
import com.korur.oba.apps.kazan.chat.constant.controller.ChatControllerLogConstant;
import com.korur.oba.apps.kazan.chat.dto.request.ChatTypingRequest;
import com.korur.oba.apps.kazan.chat.service.ChatMessageService;
import com.korur.oba.connect.realtime.dto.RealtimePrincipal;
import com.korur.oba.connect.realtime.service.RealtimeSessionRegistry;
import com.korur.oba.dojo.exception.internal.infrastructure.ApplicationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.service.OrganizationAccessService;

/**
 * STOMP controller for typing signals, the one destination a client may send
 * to: SEND /app/chat/typing with the body
 * { "channelId": "uuid", "threadId": "uuid" | null }. The signal goes to the
 * channel's topic, so only the channel's readers see who is typing.
 *
 * The session's user and organization come from the principal set on
 * CONNECT, never from the frame, so a client cannot type in another
 * organization's name. A session may signal at most once per minimum
 * interval; faster frames are dropped, because each one fans out to every
 * online member. The session registry holds that throttle with the session's
 * other send windows, so it ends with the session. The organization role,
 * MEMBER or higher as for sending, is checked here; access to Kazan is
 * checked before the frame reaches this controller, by
 * {@link com.korur.oba.apps.kazan.chat.helper.ChatRealtimeDestinationAuthorizer#authorizeSend};
 * the channel and thread rules live in {@link ChatMessageService#signalTyping}.
 *
 * A refused frame (a GUEST session, a channel the user cannot read, a thread
 * that is unknown or outside the channel) or a body that is not readable is
 * dropped and logged at warn level. Both are expected client states, not
 * server faults, and the frame is never answered: an ERROR frame would close
 * the client's whole connection over a typing hint.
 */
@Controller
public class ChatTypingController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ChatTypingController.class);

    private final ChatMessageService chatMessageService;
    private final OrganizationAccessService organizationAccessService;
    private final RealtimeSessionRegistry realtimeSessionRegistry;
    private final LoggingService loggingService;
    private final long typingMinIntervalMs;

    public ChatTypingController(ChatMessageService chatMessageService,
                                OrganizationAccessService organizationAccessService,
                                RealtimeSessionRegistry realtimeSessionRegistry,
                                LoggingService loggingService,
                                @Value(ChatConstant.TYPING_MIN_INTERVAL_MS_PLACEHOLDER) long typingMinIntervalMs) {
        this.chatMessageService = chatMessageService;
        this.organizationAccessService = organizationAccessService;
        this.realtimeSessionRegistry = realtimeSessionRegistry;
        this.loggingService = loggingService;
        this.typingMinIntervalMs = typingMinIntervalMs;
    }

    /**
     * Publishes that the session's user is typing in the channel or a thread,
     * unless the session signalled less than the minimum interval ago.
     *
     * @param request   the channel and the thread typed in; a null threadId for the channel itself
     * @param principal the session's user and organization
     * @param sessionId the STOMP session
     */
    @MessageMapping(ChatConstant.TYPING_MESSAGE_MAPPING)
    public void typing(@Payload(required = false) ChatTypingRequest request,
                       RealtimePrincipal principal,
                       @Header(SimpMessageHeaderAccessor.SESSION_ID_HEADER) String sessionId) {
        if (!realtimeSessionRegistry.tryAcquireSend(sessionId, ChatConstant.TYPING_SEND_BUCKET, 1,
                typingMinIntervalMs)) {
            loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_TYPING_THROTTLED,
                    sessionId);
            return;
        }
        // STOMP frames bypass the HTTP interceptor, so the organization role is checked here.
        organizationAccessService.verifyOrganizationAccess(principal.organizationId(), principal.userId(),
                OrganizationMemberRole.MEMBER);
        UUID channelId = request == null ? null : request.channelId();
        UUID threadId = request == null ? null : request.threadId();
        chatMessageService.signalTyping(principal.userId(), principal.organizationId(), channelId, threadId);
    }

    /**
     * Drops a refused typing frame: a session whose role may not send, a
     * channel the user cannot read, or a thread that is unknown or outside
     * the channel. Handling it here keeps Spring's messaging handler from
     * logging it as an unhandled error, and the void return sends nothing
     * back.
     *
     * @param exception the refusal
     * @param principal the session's user and organization
     */
    @MessageExceptionHandler(ApplicationException.class)
    public void handleRefused(ApplicationException exception, RealtimePrincipal principal) {
        loggingService.warn(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_TYPING_REFUSED,
                principal.userId(), principal.organizationId(), exception.getErrorCode(),
                exception.getErrorValues());
    }

    /**
     * Drops a typing frame whose body is not a typing request, such as a
     * threadId that is no UUID. Nothing is sent back, for the same reason.
     *
     * @param exception the payload conversion failure
     * @param principal the session's user and organization
     */
    @MessageExceptionHandler(MessageConversionException.class)
    public void handleUnreadableBody(MessageConversionException exception, RealtimePrincipal principal) {
        loggingService.warn(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_TYPING_BODY_UNREADABLE,
                principal.userId(), principal.organizationId(), exception.getMostSpecificCause().getMessage());
    }
}
