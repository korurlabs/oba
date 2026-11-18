package com.korur.oba.apps.kazan.chat.listener;

// 1. Framework-level
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.ChatExceptionLogConstant;
import com.korur.oba.apps.kazan.chat.constant.listener.ChatListenerLogConstant;
import com.korur.oba.apps.kazan.chat.dto.response.ChatUnreadResponse;
import com.korur.oba.apps.kazan.chat.event.ChatPushRequestedEvent;
import com.korur.oba.apps.kazan.chat.event.ChatReadStateChangedEvent;
import com.korur.oba.apps.kazan.chat.service.ChatReadStateQueryService;
import com.korur.oba.connect.push.constant.PushConstant;
import com.korur.oba.connect.push.dto.PushRequest;
import com.korur.oba.connect.push.enums.PushKind;
import com.korur.oba.connect.push.service.PushDispatchService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessQueryService;

/**
 * Turns committed chat changes into push notifications on members' phones.
 *
 * A new message that calls on users goes to them as a chat push, with each
 * recipient's notification count in this organization as the app badge. A
 * moved read position goes to the reader's own devices as a data-only push,
 * so every phone withdraws the notifications of the conversation just read.
 * Who is called on was decided in the sending transaction; this listener
 * drops each of them who may not open Kazan in the organization when the
 * push goes out, because the push carries Kazan content. The push module
 * applies each recipient's switches and previews.
 *
 * Every handler runs after the commit, so a rolled-back write pushes
 * nothing, and on the push executor, so neither the request thread nor its
 * database connection waits for Expo. A failure here is logged at error and
 * not re-thrown: the message or read position has committed and the request
 * succeeded, so there is nothing to roll back, and the unread counts every
 * client reads on its next connect stay the source of truth. When the Kazan
 * access read fails, the message push is not sent at all.
 */
@Component
@RequiredArgsConstructor
public class ChatPushListener {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ChatPushListener.class);

    private final PushDispatchService pushDispatchService;
    private final ChatReadStateQueryService chatReadStateQueryService;
    private final AppAccessQueryService appAccessQueryService;
    private final LoggingService loggingService;

    /**
     * Sends the pushes of a new message to the users it calls on who may
     * still open Kazan in the organization.
     *
     * @param event the committed message and its recipients
     */
    @Async(PushConstant.EXECUTOR_BEAN_NAME)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPushRequested(ChatPushRequestedEvent event) {
        try {
            Map<UUID, PushKind> recipients = recipientsWithKazan(event);
            if (recipients.isEmpty()) {
                return;
            }
            Map<UUID, Integer> badges = badges(event, recipients);
            pushDispatchService.dispatch(PushRequest.chat(event.organizationId(), recipients,
                    event.authorId(), event.conversation(), event.senderName(), event.text(),
                    event.attachmentsOnly(), event.channelId(), event.threadId(), event.messageId(), badges));
        } catch (RuntimeException failure) {
            loggingService.error(LOG_PREFIX, LogLayer.HANDLER, ChatExceptionLogConstant.CHAT_PUSH_DISPATCH_FAILED,
                    failure, event.organizationId(), event.channelId(), event.threadId(), event.messageId());
        }
    }

    /**
     * Tells the reader's other devices that a conversation was read, so they
     * withdraw its notifications. Best effort: the operating system may hold
     * back a data-only push, and the unread count stays correct either way.
     * The reader gets no Kazan access check here: the read position was just
     * moved through a Kazan endpoint that checked it, and the push carries no
     * content, only the conversation to clear.
     *
     * @param event the committed read position
     */
    @Async(PushConstant.EXECUTOR_BEAN_NAME)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReadStateChanged(ChatReadStateChangedEvent event) {
        try {
            pushDispatchService.dispatch(PushRequest.read(event.organizationId(), event.userId(),
                    event.channelId(), event.threadId()));
        } catch (RuntimeException failure) {
            loggingService.error(LOG_PREFIX, LogLayer.HANDLER,
                    ChatExceptionLogConstant.CHAT_READ_PUSH_DISPATCH_FAILED, failure, event.organizationId(),
                    event.userId(), event.channelId(), event.threadId());
        }
    }

    /**
     * Keeps the recipients who may open Kazan in the organization when the
     * push goes out. Access is read after the commit, so a grant revoked
     * between the message and the push already counts. The access service
     * reads each recipient on its own; a message calls on few users.
     */
    private Map<UUID, PushKind> recipientsWithKazan(ChatPushRequestedEvent event) {
        Map<UUID, PushKind> recipients = new LinkedHashMap<>();
        event.recipients().forEach((userId, kind) -> {
            if (appAccessQueryService.hasAppAccess(event.organizationId(), userId, App.KAZAN)) {
                recipients.put(userId, kind);
            }
        });
        int skipped = event.recipients().size() - recipients.size();
        if (skipped > 0) {
            loggingService.debug(LOG_PREFIX, LogLayer.HANDLER,
                    ChatListenerLogConstant.CHAT_PUSH_RECIPIENTS_WITHOUT_KAZAN_SKIPPED, event.organizationId(),
                    event.messageId(), skipped, recipients.size());
        }
        return recipients;
    }

    /**
     * Each recipient's notification count in the organization, read after
     * the commit so it includes the new message, in one query for all of
     * them. A recipient without a readable channel has no entry and gets no
     * badge. When the read fails, the push goes without badges rather than
     * not at all, and the failure is logged.
     */
    private Map<UUID, Integer> badges(ChatPushRequestedEvent event, Map<UUID, PushKind> recipients) {
        Map<UUID, ChatUnreadResponse> unreadByUser;
        try {
            unreadByUser = chatReadStateQueryService.getUnreadByUsers(event.organizationId(),
                    recipients.keySet());
        } catch (RuntimeException failure) {
            loggingService.error(LOG_PREFIX, LogLayer.HANDLER, ChatExceptionLogConstant.CHAT_PUSH_BADGES_FAILED,
                    failure, event.organizationId(), event.messageId());
            return null;
        }
        Map<UUID, Integer> badges = new LinkedHashMap<>();
        unreadByUser.forEach((userId, unread) -> badges.put(userId, unread.mentionTotal()));
        return badges;
    }
}
