package com.korur.oba.apps.kaizen.chat.dto.signal;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.event.ChatThreadChangedEvent;
import com.korur.oba.connect.realtime.dto.RealtimeSignal;

/**
 * A created or changed thread, on the channel topic, which only the
 * channel's readers may subscribe to.
 *
 * @param type          THREAD_CREATED or THREAD_UPDATED
 * @param threadId      the thread
 * @param rootMessageId the channel message it was started from
 */
public record ChatThreadSignal(String type, UUID threadId, UUID rootMessageId) implements RealtimeSignal {

    /**
     * Builds the signal of a thread change.
     *
     * @param type  the signal type,
     *              {@link com.korur.oba.apps.kaizen.chat.constant.ChatConstant#SIGNAL_THREAD_CREATED} or
     *              {@link com.korur.oba.apps.kaizen.chat.constant.ChatConstant#SIGNAL_THREAD_UPDATED}
     * @param event the committed change
     * @return the signal
     */
    public static ChatThreadSignal of(String type, ChatThreadChangedEvent event) {
        return new ChatThreadSignal(type, event.threadId(), event.rootMessageId());
    }
}
