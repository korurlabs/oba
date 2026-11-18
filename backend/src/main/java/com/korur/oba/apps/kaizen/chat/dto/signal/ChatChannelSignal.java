package com.korur.oba.apps.kaizen.chat.dto.signal;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.constant.ChatConstant;
import com.korur.oba.apps.kaizen.chat.event.ChatChannelChangedEvent;
import com.korur.oba.connect.realtime.dto.RealtimeSignal;

/**
 * A created, changed or deleted channel, or a changed member list: on the
 * organization topic for a public channel, on the user queue of each
 * concerned user for a private one. The client reloads its channel list.
 *
 * @param type      CHANNEL_CHANGED
 * @param channelId the channel
 */
public record ChatChannelSignal(String type, UUID channelId) implements RealtimeSignal {

    /**
     * Builds the signal of a channel change.
     *
     * @param event the committed change
     * @return the signal
     */
    public static ChatChannelSignal of(ChatChannelChangedEvent event) {
        return new ChatChannelSignal(ChatConstant.SIGNAL_CHANNEL_CHANGED, event.channelId());
    }
}
