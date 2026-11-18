package com.korur.oba.apps.kaizen.chat.dto.signal;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.constant.ChatConstant;
import com.korur.oba.apps.kaizen.chat.event.ChatMessageChangedEvent;
import com.korur.oba.connect.realtime.dto.RealtimeSignal;

/**
 * A pinned or unpinned message, on the channel topic, which only the
 * channel's readers may subscribe to.
 *
 * @param type      PIN_CHANGED
 * @param messageId the message
 * @param threadId  its thread; null in the channel
 * @param pinned    true when it is pinned now
 */
public record ChatPinSignal(String type, UUID messageId, UUID threadId, boolean pinned) implements RealtimeSignal {

    /**
     * Builds the signal of a pin change.
     *
     * @param event  the committed pin or unpin
     * @param pinned true when the message is pinned now
     * @return the signal
     */
    public static ChatPinSignal of(ChatMessageChangedEvent event, boolean pinned) {
        return new ChatPinSignal(ChatConstant.SIGNAL_PIN_CHANGED, event.messageId(), event.threadId(), pinned);
    }
}
