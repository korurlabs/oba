package com.korur.oba.apps.kazan.chat.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.dto.request.ChatReadStateRequest;
import com.korur.oba.apps.kazan.chat.dto.response.ChatUnreadResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Write operations for read positions in channels and in threads.
 *
 * Marking read needs an ACTIVE membership of any role in an ACTIVE
 * organization and a channel the reader can read, as reading does. A position only moves forward, so a late
 * request from another tab never makes read messages unread again.
 */
public interface ChatReadStateService {

    /**
     * Marks a channel, or one of its threads, read up to a message. A thread
     * the reader is no member of changes nothing. A message older than the
     * stored position does not move it, but a channel read still clears the
     * mentions of a reviewer added to a pull request share by an edit: that
     * mention sits on an entry older than the position, so only opening the
     * channel can clear it, and the read state is touched for it (see
     * {@link com.korur.oba.apps.kazan.chat.repository.ChatReadStateRepository#touchChannelReadStateForShareMentions}).
     * When the position moved or such mentions were cleared, a
     * {@link com.korur.oba.apps.kazan.chat.event.ChatReadStateChangedEvent} tells the reader's other sessions to refresh
     * their badge.
     *
     * @param user           the reader
     * @param organizationId the request's organization
     * @param request        the channel, the thread (null for the channel itself) and the newest message seen
     * @return the reader's unread counts after the change
     */
    ChatUnreadResponse markRead(User user, UUID organizationId, ChatReadStateRequest request);
}
