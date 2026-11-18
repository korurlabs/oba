package com.korur.oba.apps.kaizen.chat.dto.signal;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.constant.ChatConstant;
import com.korur.oba.connect.realtime.dto.RealtimeSignal;

/**
 * A member came online with their first session in the organization, or went
 * offline with their last one, on the organization topic.
 *
 * @param type   PRESENCE
 * @param userId the member whose presence changed
 * @param online true on the first session, false when the last one closed
 */
public record ChatPresenceSignal(String type, UUID userId, boolean online) implements RealtimeSignal {

    /**
     * Builds the presence signal of one member.
     *
     * @param userId the member whose presence changed
     * @param online whether the member is now online
     * @return the signal
     */
    public static ChatPresenceSignal of(UUID userId, boolean online) {
        return new ChatPresenceSignal(ChatConstant.SIGNAL_PRESENCE, userId, online);
    }
}
