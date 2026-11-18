package com.korur.oba.apps.kazan.chat.dto.signal;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.ChatConstant;
import com.korur.oba.connect.realtime.dto.RealtimeSignal;

/**
 * A member typing, on the channel topic, which only the channel's readers
 * may subscribe to. Clients ignore their own userId and show thread typing
 * only in the thread they have open.
 *
 * @param type     TYPING
 * @param userId   the typing user
 * @param threadId the thread typed in; null for the channel
 */
public record ChatTypingSignal(String type, UUID userId, UUID threadId) implements RealtimeSignal {

    /**
     * Builds the signal of a typing frame.
     *
     * @param userId   the typing user
     * @param threadId the thread typed in; null for the channel
     * @return the signal
     */
    public static ChatTypingSignal of(UUID userId, UUID threadId) {
        return new ChatTypingSignal(ChatConstant.SIGNAL_TYPING, userId, threadId);
    }
}
