package com.korur.oba.apps.kaizen.chat.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.dto.response.ChatUnreadResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Read operations for unread counts: each channel of an organization the user
 * can read and each active (neither closed nor archived) thread the user is a
 * member of.
 *
 * A message is unread when another user wrote it, it is live and it comes
 * after the user's read position; without a read position, after the user's
 * join time (the membership for a channel, the thread member row for a
 * thread). Each count stops at MAX_UNREAD_COUNT.
 */
public interface ChatReadStateQueryService {

    /**
     * Reads the reader's unread counts, for the sidebar badge and the "New
     * messages" divider.
     *
     * @param user           the reader
     * @param organizationId the request's organization
     * @return the channel counts and positions, the thread counts above zero and their totals
     */
    ChatUnreadResponse getUnread(User user, UUID organizationId);

    /**
     * Reads the unread counts of several users in one query, for the realtime
     * fan-out after a message was posted or deleted. No access check: the
     * caller already knows the users are online members.
     *
     * @param organizationId the organization
     * @param userIds        the users
     * @return the counts per user; a user without an active membership is absent
     */
    Map<UUID, ChatUnreadResponse> getUnreadByUsers(UUID organizationId, Collection<UUID> userIds);
}
