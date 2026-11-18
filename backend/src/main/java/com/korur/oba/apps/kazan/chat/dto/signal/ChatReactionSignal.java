package com.korur.oba.apps.kazan.chat.dto.signal;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.ChatConstant;
import com.korur.oba.apps.kazan.chat.event.ChatReactionChangedEvent;
import com.korur.oba.connect.realtime.dto.RealtimeSignal;

/**
 * An added or removed reaction, on the channel topic, which only the
 * channel's readers may subscribe to.
 *
 * @param type      REACTION_CHANGED
 * @param messageId the message
 * @param threadId  its thread; null in the channel
 * @param emoji     the emoji
 * @param userId    who reacted
 * @param added     true when added, false when removed
 */
public record ChatReactionSignal(String type, UUID messageId, UUID threadId, String emoji, UUID userId,
                                 boolean added) implements RealtimeSignal {

    /**
     * Builds the signal of a reaction change.
     *
     * @param event the committed change
     * @return the signal
     */
    public static ChatReactionSignal of(ChatReactionChangedEvent event) {
        return new ChatReactionSignal(ChatConstant.SIGNAL_REACTION_CHANGED, event.messageId(), event.threadId(),
                event.emoji(), event.userId(), event.added());
    }
}
