package com.korur.oba.apps.kazan.chat.dto.signal;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.dto.response.ChatMessageResponse;
import com.korur.oba.apps.kazan.chat.event.ChatMessageChangedEvent;
import com.korur.oba.connect.realtime.dto.RealtimeSignal;

/**
 * A created, edited or deleted message, on the topic of its channel.
 *
 * A created or edited message carries its reader-neutral copy, so an open
 * client shows it without reading it again; the client fills in the fields
 * that concern its own user. The copy is absent when it would differ per
 * reader or could not be built, and the client then reads the message itself.
 *
 * @param type        MESSAGE_CREATED, MESSAGE_UPDATED or MESSAGE_DELETED
 * @param messageId   the message
 * @param threadId    its thread; null in the channel
 * @param authorId    its author
 * @param createdAt   its place in the conversation
 * @param clientNonce the nonce of the send that created it, so the sender replaces its pending row; else null
 * @param version     the message's last change, as {@link ChatMessageResponse#version}; null on a
 *                    deletion or when the message is gone
 * @param message     the reader-neutral message; null on a deletion, when its copy differs per reader,
 *                    or when it could not be built
 */
public record ChatMessageSignal(String type, UUID messageId, UUID threadId, UUID authorId, Instant createdAt,
                                String clientNonce, Instant version, ChatMessageResponse message)
        implements RealtimeSignal {

    /**
     * Builds the signal of a message change without the message.
     *
     * @param type  the signal type, one of the SIGNAL_MESSAGE_* values of
     *              {@link com.korur.oba.apps.kazan.chat.constant.ChatConstant}
     * @param event the committed change
     * @return the signal
     */
    public static ChatMessageSignal of(String type, ChatMessageChangedEvent event) {
        return of(type, event, null, null);
    }

    /**
     * Builds the signal of a message change with its last change and, when
     * one could be built, its reader-neutral copy.
     *
     * @param type    the signal type,
     *                {@link com.korur.oba.apps.kazan.chat.constant.ChatConstant#SIGNAL_MESSAGE_CREATED} or
     *                {@link com.korur.oba.apps.kazan.chat.constant.ChatConstant#SIGNAL_MESSAGE_UPDATED}
     * @param event   the committed change
     * @param version the message's last change; null when the message is gone
     * @param message the reader-neutral message; null to send IDs and the version only
     * @return the signal
     */
    public static ChatMessageSignal of(String type, ChatMessageChangedEvent event, Instant version,
                                       ChatMessageResponse message) {
        return new ChatMessageSignal(type, event.messageId(), event.threadId(), event.authorId(), event.createdAt(),
                event.clientNonce(), version, message);
    }
}
