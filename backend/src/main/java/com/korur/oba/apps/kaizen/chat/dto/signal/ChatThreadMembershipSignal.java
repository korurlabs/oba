package com.korur.oba.apps.kaizen.chat.dto.signal;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.constant.ChatConstant;
import com.korur.oba.connect.realtime.dto.RealtimeSignal;

/**
 * A user joined or left a thread, on that user's queue.
 *
 * @param type     THREAD_MEMBERSHIP_CHANGED
 * @param threadId the thread
 * @param member   true when the user is a member now
 */
public record ChatThreadMembershipSignal(String type, UUID threadId, boolean member) implements RealtimeSignal {

    /**
     * Builds the signal of a membership change.
     *
     * @param threadId the thread
     * @param member   true when the user is a member now
     * @return the signal
     */
    public static ChatThreadMembershipSignal of(UUID threadId, boolean member) {
        return new ChatThreadMembershipSignal(ChatConstant.SIGNAL_THREAD_MEMBERSHIP_CHANGED, threadId, member);
    }
}
