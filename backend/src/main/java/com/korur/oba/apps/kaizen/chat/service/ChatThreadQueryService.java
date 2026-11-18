package com.korur.oba.apps.kaizen.chat.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.dto.response.ChatThreadPageResponse;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatThreadResponse;
import com.korur.oba.apps.kaizen.chat.enums.ChatThreadStatus;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Read operations for chat threads: the thread list of a channel and one
 * thread's detail.
 *
 * Reading needs an ACTIVE membership of any role in an ACTIVE organization
 * and a channel the reader can read; a thread of another organization or of
 * an unreadable channel is reported as not found. The status is
 * the effective one: an ACTIVE thread quiet for its window reads as ARCHIVED.
 */
public interface ChatThreadQueryService {

    /**
     * Lists the threads of a channel with one effective status, most recently
     * active first, as a keyset page.
     *
     * @param user           the reader
     * @param organizationId the request's organization
     * @param channelId      the channel
     * @param status         the effective status to list
     * @param before         the last thread of the previous page; null for the first page
     * @param limit          the page size, at most MAX_PAGE_SIZE; null for the default
     * @return the page; hasMore tells whether more threads follow
     */
    ChatThreadPageResponse listThreads(User user, UUID organizationId, UUID channelId, ChatThreadStatus status,
                                       UUID before, Integer limit);

    /**
     * Reads one thread for its panel: the root message (or its placeholder
     * when the root was deleted), every member, and the reader's membership
     * and right to manage it.
     *
     * @param user           the reader
     * @param organizationId the request's organization
     * @param threadId       the thread
     * @return the thread
     */
    ChatThreadResponse getThread(User user, UUID organizationId, UUID threadId);
}
