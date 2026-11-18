package com.korur.oba.apps.kazan.chat.dto.signal;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.ChatConstant;
import com.korur.oba.apps.kazan.chat.dto.response.ChatUnreadResponse;
import com.korur.oba.connect.realtime.dto.RealtimeSignal;

/**
 * A user's fresh unread counts, on that user's queue.
 *
 * @param type   UNREAD_CHANGED
 * @param unread the counts, as GET /kazan/chat/unread returns them
 */
public record ChatUnreadSignal(String type, ChatUnreadResponse unread) implements RealtimeSignal {

    /**
     * Builds the signal of a user's unread counts.
     *
     * @param unread the counts
     * @return the signal
     */
    public static ChatUnreadSignal of(ChatUnreadResponse unread) {
        return new ChatUnreadSignal(ChatConstant.SIGNAL_UNREAD_CHANGED, unread);
    }
}
