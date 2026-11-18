package com.korur.oba.apps.kaizen.chat.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.dto.request.ChatMessageQueryRequest;
import com.korur.oba.apps.kaizen.chat.dto.request.ChatSearchRequest;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatMessagePageResponse;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatMessageResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Read operations for chat messages: keyset pages of a channel and of a
 * thread, one message, the pinned messages and search.
 *
 * Reading needs an ACTIVE membership of any role in an ACTIVE organization
 * and a channel the reader can read; a channel, message or thread the reader
 * cannot read is reported as not found.
 * Every response is built for the reader: ticket links, linked keys, mentions
 * of the reader and reactions by the reader differ per user.
 *
 * A message page is a keyset page on (created_at, id). The cursor is a message
 * ID, read with deleted messages included, so a page next to a message that
 * was deleted meanwhile still loads; a cursor of another organization is
 * reported as not found. Items are always oldest first.
 */
public interface ChatMessageQueryService {

    /**
     * Lists a page of a channel: the newest messages without
     * a cursor, the messages strictly older than before or newer than after,
     * or the messages around a cursor, half before it and the rest from it on.
     * Deleted messages that a thread was started from appear as placeholders.
     *
     * @param user           the reader
     * @param organizationId the request's organization
     * @param request        the channel, at most one cursor and the page size
     * @return the page, oldest first, with hasOlder and hasNewer
     */
    ChatMessagePageResponse listChannelMessages(User user, UUID organizationId, ChatMessageQueryRequest request);

    /**
     * Lists a page of a thread's messages, with the same cursors as the channel.
     *
     * @param user           the reader
     * @param organizationId the request's organization
     * @param threadId       the thread
     * @param request        at most one cursor and the page size
     * @return the page, oldest first, with hasOlder and hasNewer
     */
    ChatMessagePageResponse listThreadMessages(User user, UUID organizationId, UUID threadId,
                                               ChatMessageQueryRequest request);

    /**
     * Reads one live message, as a client does after a realtime signal.
     *
     * @param user           the reader
     * @param organizationId the request's organization
     * @param messageId      the message
     * @return the message
     */
    ChatMessageResponse getMessage(User user, UUID organizationId, UUID messageId);

    /**
     * Reads one live message as no particular reader sees it, for the
     * realtime signal of its creation or edit. No user is checked: it is
     * read after a committed write, and the signal reaches only the
     * subscribers of the message's channel topic, who may all read it.
     *
     * @param organizationId the message's organization
     * @param messageId      the message
     * @param clientNonce    nonce of the send that created the message; null otherwise
     * @return the reader-neutral message; empty when it is gone or its copy
     *         would differ per reader, so each client reads it for itself
     */
    Optional<ChatMessageResponse> findNeutralMessage(UUID organizationId, UUID messageId, String clientNonce);

    /**
     * Reads the last change of one live message, its
     * {@link ChatMessageResponse#version}, without building the message. A
     * realtime signal sent without the reader-neutral copy still carries it,
     * so a client can tell whether the copy it holds is current.
     *
     * @param organizationId the message's organization
     * @param messageId      the message
     * @return the message's last change; empty when it is gone
     */
    Optional<Instant> findMessageVersion(UUID organizationId, UUID messageId);

    /**
     * Lists the pinned messages of a channel and its threads, most recently
     * pinned first.
     *
     * @param user           the reader
     * @param organizationId the request's organization
     * @param channelId      the channel
     * @param before         the last pinned message of the previous page; null for the first page
     * @param limit          the page size, at most MAX_PAGE_SIZE; null for the default
     * @return the page, newest pin first; hasOlder tells whether more pins follow
     */
    ChatMessagePageResponse listPinnedMessages(User user, UUID organizationId, UUID channelId, UUID before,
                                               Integer limit);

    /**
     * Searches the live messages of the channels the reader can read and of
     * their threads, or of one channel, newest first. A daily update matches
     * on its text only while its author is an ACTIVE member of the
     * organization.
     *
     * @param user           the reader
     * @param organizationId the request's organization
     * @param request        the text, an optional channel and author, the cursor and the page size
     * @return the page, newest first; hasOlder tells whether more matches follow
     */
    ChatMessagePageResponse searchMessages(User user, UUID organizationId, ChatSearchRequest request);
}
