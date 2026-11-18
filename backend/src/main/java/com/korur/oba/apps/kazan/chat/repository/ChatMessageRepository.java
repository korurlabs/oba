package com.korur.oba.apps.kazan.chat.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.dto.projection.ChatReactionSummaryProjection;
import com.korur.oba.apps.kazan.chat.dto.projection.ChatReplyPreviewProjection;
import com.korur.oba.apps.kazan.chat.dto.projection.ChatThreadSummaryProjection;
import com.korur.oba.apps.kazan.chat.dto.projection.ChatUnreadCountProjection;
import com.korur.oba.apps.kazan.chat.entity.ChatMessage;
import com.korur.oba.dojo.repository.SoftDeleteRepository;

/**
 * Repository interface for {@link ChatMessage} entity database operations.
 *
 * Message pages are keyset pages on (created_at, id): a cursor message's
 * created_at and ID select the rows strictly older or newer than it, so a page
 * never shifts when messages arrive. Every page query returns its rows oldest
 * first and asks for limit rows; the caller passes one more than it shows to
 * learn whether more exist. Channel pages read the index
 * chat_messages_channel_id_created_at_idx, thread pages
 * chat_messages_thread_id_created_at_idx and pin lists the partial index
 * chat_messages_pinned_idx, each scoped to one channel or thread. A channel
 * page also returns soft-deleted messages that a thread was started from,
 * because the thread lives on and its summary needs a place in the channel;
 * every other deleted message stays hidden.
 *
 * Native queries bypass the Hibernate soft-delete filter, so each one states
 * which deleted rows it wants. The queries that serve one page of messages
 * (reply previews, reactions, thread summaries) take the IDs of the whole
 * page, so a page costs a fixed number of queries.
 */
@Repository
public interface ChatMessageRepository extends SoftDeleteRepository<ChatMessage, UUID> {

    /**
     * Finds a live message of an organization, in its channel or a thread.
     *
     * @param id             ID of the message
     * @param organizationId ID of the organization
     * @return the message, empty when it is absent, deleted or of another organization
     */
    Optional<ChatMessage> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * Finds the live channel entry of a daily update. The unique key on
     * daily_update_id allows at most one.
     *
     * @param dailyUpdateId ID of the daily update
     * @return the message, empty when the update was posted without an organization or its entry is deleted
     */
    Optional<ChatMessage> findByDailyUpdateId(UUID dailyUpdateId);

    /**
     * Tells whether a daily update has a chat entry, a deleted one included:
     * the unique key on daily_update_id covers deleted rows too, so an update
     * is posted to the chat at most once.
     *
     * @param dailyUpdateId ID of the daily update
     * @return true when a message, live or deleted, stands for the update
     */
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM kazan.chat_messages m
                           WHERE m.daily_update_id = :dailyUpdateId)
            """, nativeQuery = true)
    boolean existsByDailyUpdateIdWithDeleted(@Param("dailyUpdateId") UUID dailyUpdateId);

    /**
     * Finds the live channel entry of a pull request share. The unique key on
     * pull_request_share_id allows at most one.
     *
     * @param pullRequestShareId ID of the share
     * @return the message, empty when the share has no entry or its entry is deleted
     */
    Optional<ChatMessage> findByPullRequestShareId(UUID pullRequestShareId);

    /**
     * Finds the live channel entries of several pull request shares, one
     * query for a page of shares. The unique key on pull_request_share_id
     * allows at most one entry per share.
     *
     * @param pullRequestShareIds IDs of the shares, not empty
     * @return the live entries found, in no particular order; a share without one is absent
     */
    List<ChatMessage> findAllByPullRequestShareIdIn(Collection<UUID> pullRequestShareIds);

    /**
     * Tells whether a pull request share has a chat entry, a deleted one
     * included: the unique key on pull_request_share_id covers deleted rows
     * too, so a share is posted to the chat at most once.
     *
     * @param pullRequestShareId ID of the share
     * @return true when a message, live or deleted, stands for the share
     */
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM kazan.chat_messages m
                           WHERE m.pull_request_share_id = :pullRequestShareId)
            """, nativeQuery = true)
    boolean existsByPullRequestShareIdWithDeleted(@Param("pullRequestShareId") UUID pullRequestShareId);

    /**
     * Moves updated_at of a live message to now without any other change, so
     * the message's version advances and readers refetch it when only the
     * record it stands for changed, such as a reviewer's approval of a pull
     * request share. updated_at is set here because a native update skips JPA
     * auditing.
     *
     * The persistence context is flushed before and not cleared after: the
     * caller still holds the managed rows of its own write, and the message
     * itself has no pending change.
     *
     * @param messageId ID of the message
     * @return 1 when the message was touched, 0 when it is absent or deleted
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE kazan.chat_messages
            SET updated_at = now()
            WHERE id = :messageId
              AND deleted = false
            """, nativeQuery = true)
    int touchUpdatedAt(@Param("messageId") UUID messageId);

    /**
     * Finds the message an author's send with this client nonce created, a
     * deleted one included: the unique index on (author_id, client_nonce)
     * covers deleted rows too, so a repeated send is answered with the row it
     * already created and never stores a second one.
     *
     * @param authorId    ID of the author
     * @param clientNonce the nonce of the send
     * @return the message, live or deleted; empty when no send of the author carried the nonce
     */
    @Query(value = """
            SELECT m.* FROM kazan.chat_messages m
            WHERE m.author_id = :authorId
              AND m.client_nonce = :clientNonce
            """, nativeQuery = true)
    Optional<ChatMessage> findByAuthorIdAndClientNonceWithDeleted(@Param("authorId") UUID authorId,
                                                                  @Param("clientNonce") String clientNonce);

    /**
     * Finds the newest messages of a channel, including deleted thread roots.
     *
     * @param channelId      ID of the channel
     * @param dayStart       start of the day to keep, inclusive; null for no bound
     * @param dayEnd         end of the day to keep, exclusive; null for no bound
     * @param authorId       only messages of this author; null for every author
     * @param anyPullRequestStatus true applies no status filter; false keeps only pull request
     *                       share entries whose live share has one of pullRequestStatuses
     * @param pullRequestStatuses status names to keep when anyPullRequestStatus is false; never empty,
     *                       because a native IN list cannot be empty
     * @param limit          maximum number of rows
     * @return up to limit messages, oldest first
     */
    @Query(value = """
            SELECT p.* FROM (
                SELECT u.* FROM (
                    (SELECT m.* FROM kazan.chat_messages m
                     WHERE m.channel_id = :channelId
                       AND (CAST(:dayStart AS timestamptz) IS NULL OR m.created_at >= CAST(:dayStart AS timestamptz))
                       AND (CAST(:dayEnd AS timestamptz) IS NULL OR m.created_at < CAST(:dayEnd AS timestamptz))
                       AND (CAST(:authorId AS uuid) IS NULL OR m.author_id = CAST(:authorId AS uuid))
                       AND (:anyPullRequestStatus OR EXISTS (SELECT 1 FROM kazan.pull_request_shares s
                                                             WHERE s.id = m.pull_request_share_id
                                                               AND s.deleted = false
                                                               AND CAST(s.status AS text) IN (:pullRequestStatuses)))
                       AND m.thread_id IS NULL
                       AND m.deleted = false
                     ORDER BY m.created_at DESC, m.id DESC
                     LIMIT :limit)
                    UNION ALL
                    (SELECT m.* FROM kazan.chat_threads t
                     JOIN kazan.chat_messages m ON m.id = t.root_message_id
                     WHERE t.channel_id = :channelId
                       AND (CAST(:dayStart AS timestamptz) IS NULL OR m.created_at >= CAST(:dayStart AS timestamptz))
                       AND (CAST(:dayEnd AS timestamptz) IS NULL OR m.created_at < CAST(:dayEnd AS timestamptz))
                       AND (CAST(:authorId AS uuid) IS NULL OR m.author_id = CAST(:authorId AS uuid))
                       AND (:anyPullRequestStatus OR EXISTS (SELECT 1 FROM kazan.pull_request_shares s
                                                             WHERE s.id = m.pull_request_share_id
                                                               AND s.deleted = false
                                                               AND CAST(s.status AS text) IN (:pullRequestStatuses)))
                       AND m.deleted = true
                     ORDER BY m.created_at DESC, m.id DESC
                     LIMIT :limit)
                ) u
                ORDER BY u.created_at DESC, u.id DESC
                LIMIT :limit
            ) p
            ORDER BY p.created_at ASC, p.id ASC
            """, nativeQuery = true)
    List<ChatMessage> findLatestChannelMessages(@Param("channelId") UUID channelId,
                                                @Param("dayStart") Instant dayStart,
                                                @Param("dayEnd") Instant dayEnd,
                                                @Param("authorId") UUID authorId,
                                                @Param("anyPullRequestStatus") boolean anyPullRequestStatus,
                                                @Param("pullRequestStatuses") Collection<String> pullRequestStatuses,
                                                @Param("limit") int limit);

    /**
     * Finds the channel messages directly older than a cursor, including
     * deleted thread roots.
     *
     * @param channelId       ID of the channel
     * @param dayStart        start of the day to keep, inclusive; null for no bound
     * @param dayEnd          end of the day to keep, exclusive; null for no bound
     * @param authorId        only messages of this author; null for every author
     * @param anyPullRequestStatus true applies no status filter; false keeps only pull request
     *                        share entries whose live share has one of pullRequestStatuses
     * @param pullRequestStatuses status names to keep when anyPullRequestStatus is false; never empty,
     *                        because a native IN list cannot be empty
     * @param cursorCreatedAt created_at of the cursor message
     * @param cursorId        ID of the cursor message
     * @param limit           maximum number of rows
     * @return up to limit messages strictly older than the cursor, oldest first
     */
    @Query(value = """
            SELECT p.* FROM (
                SELECT u.* FROM (
                    (SELECT m.* FROM kazan.chat_messages m
                     WHERE m.channel_id = :channelId
                       AND (CAST(:dayStart AS timestamptz) IS NULL OR m.created_at >= CAST(:dayStart AS timestamptz))
                       AND (CAST(:dayEnd AS timestamptz) IS NULL OR m.created_at < CAST(:dayEnd AS timestamptz))
                       AND (CAST(:authorId AS uuid) IS NULL OR m.author_id = CAST(:authorId AS uuid))
                       AND (:anyPullRequestStatus OR EXISTS (SELECT 1 FROM kazan.pull_request_shares s
                                                             WHERE s.id = m.pull_request_share_id
                                                               AND s.deleted = false
                                                               AND CAST(s.status AS text) IN (:pullRequestStatuses)))
                       AND m.thread_id IS NULL
                       AND m.deleted = false
                       AND (m.created_at, m.id) < (:cursorCreatedAt, :cursorId)
                     ORDER BY m.created_at DESC, m.id DESC
                     LIMIT :limit)
                    UNION ALL
                    (SELECT m.* FROM kazan.chat_threads t
                     JOIN kazan.chat_messages m ON m.id = t.root_message_id
                     WHERE t.channel_id = :channelId
                       AND (CAST(:dayStart AS timestamptz) IS NULL OR m.created_at >= CAST(:dayStart AS timestamptz))
                       AND (CAST(:dayEnd AS timestamptz) IS NULL OR m.created_at < CAST(:dayEnd AS timestamptz))
                       AND (CAST(:authorId AS uuid) IS NULL OR m.author_id = CAST(:authorId AS uuid))
                       AND (:anyPullRequestStatus OR EXISTS (SELECT 1 FROM kazan.pull_request_shares s
                                                             WHERE s.id = m.pull_request_share_id
                                                               AND s.deleted = false
                                                               AND CAST(s.status AS text) IN (:pullRequestStatuses)))
                       AND m.deleted = true
                       AND (m.created_at, m.id) < (:cursorCreatedAt, :cursorId)
                     ORDER BY m.created_at DESC, m.id DESC
                     LIMIT :limit)
                ) u
                ORDER BY u.created_at DESC, u.id DESC
                LIMIT :limit
            ) p
            ORDER BY p.created_at ASC, p.id ASC
            """, nativeQuery = true)
    List<ChatMessage> findChannelMessagesBefore(@Param("channelId") UUID channelId,
                                                @Param("dayStart") Instant dayStart,
                                                @Param("dayEnd") Instant dayEnd,
                                                @Param("authorId") UUID authorId,
                                                @Param("anyPullRequestStatus") boolean anyPullRequestStatus,
                                                @Param("pullRequestStatuses") Collection<String> pullRequestStatuses,
                                                @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                                @Param("cursorId") UUID cursorId,
                                                @Param("limit") int limit);

    /**
     * Finds the channel messages directly newer than a cursor, including
     * deleted thread roots.
     *
     * @param channelId       ID of the channel
     * @param dayStart        start of the day to keep, inclusive; null for no bound
     * @param dayEnd          end of the day to keep, exclusive; null for no bound
     * @param authorId        only messages of this author; null for every author
     * @param anyPullRequestStatus true applies no status filter; false keeps only pull request
     *                        share entries whose live share has one of pullRequestStatuses
     * @param pullRequestStatuses status names to keep when anyPullRequestStatus is false; never empty,
     *                        because a native IN list cannot be empty
     * @param cursorCreatedAt created_at of the cursor message
     * @param cursorId        ID of the cursor message
     * @param limit           maximum number of rows
     * @return up to limit messages strictly newer than the cursor, oldest first
     */
    @Query(value = """
            SELECT u.* FROM (
                (SELECT m.* FROM kazan.chat_messages m
                 WHERE m.channel_id = :channelId
                   AND (CAST(:dayStart AS timestamptz) IS NULL OR m.created_at >= CAST(:dayStart AS timestamptz))
                   AND (CAST(:dayEnd AS timestamptz) IS NULL OR m.created_at < CAST(:dayEnd AS timestamptz))
                   AND (CAST(:authorId AS uuid) IS NULL OR m.author_id = CAST(:authorId AS uuid))
                   AND (:anyPullRequestStatus OR EXISTS (SELECT 1 FROM kazan.pull_request_shares s
                                                         WHERE s.id = m.pull_request_share_id
                                                           AND s.deleted = false
                                                           AND CAST(s.status AS text) IN (:pullRequestStatuses)))
                   AND m.thread_id IS NULL
                   AND m.deleted = false
                   AND (m.created_at, m.id) > (:cursorCreatedAt, :cursorId)
                 ORDER BY m.created_at ASC, m.id ASC
                 LIMIT :limit)
                UNION ALL
                (SELECT m.* FROM kazan.chat_threads t
                 JOIN kazan.chat_messages m ON m.id = t.root_message_id
                 WHERE t.channel_id = :channelId
                   AND (CAST(:dayStart AS timestamptz) IS NULL OR m.created_at >= CAST(:dayStart AS timestamptz))
                   AND (CAST(:dayEnd AS timestamptz) IS NULL OR m.created_at < CAST(:dayEnd AS timestamptz))
                   AND (CAST(:authorId AS uuid) IS NULL OR m.author_id = CAST(:authorId AS uuid))
                   AND (:anyPullRequestStatus OR EXISTS (SELECT 1 FROM kazan.pull_request_shares s
                                                         WHERE s.id = m.pull_request_share_id
                                                           AND s.deleted = false
                                                           AND CAST(s.status AS text) IN (:pullRequestStatuses)))
                   AND m.deleted = true
                   AND (m.created_at, m.id) > (:cursorCreatedAt, :cursorId)
                 ORDER BY m.created_at ASC, m.id ASC
                 LIMIT :limit)
            ) u
            ORDER BY u.created_at ASC, u.id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<ChatMessage> findChannelMessagesAfter(@Param("channelId") UUID channelId,
                                               @Param("dayStart") Instant dayStart,
                                               @Param("dayEnd") Instant dayEnd,
                                               @Param("authorId") UUID authorId,
                                               @Param("anyPullRequestStatus") boolean anyPullRequestStatus,
                                               @Param("pullRequestStatuses") Collection<String> pullRequestStatuses,
                                               @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                               @Param("cursorId") UUID cursorId,
                                               @Param("limit") int limit);

    /**
     * Finds the channel messages around a cursor, including deleted thread
     * roots: up to olderLimit rows strictly older than the cursor and up to
     * newerLimit rows from the cursor on. The cursor itself is among the
     * newer rows when it is visible in the channel, so a jump to a deleted
     * message lands on its neighbours.
     *
     * @param channelId       ID of the channel
     * @param dayStart        start of the day to keep, inclusive; null for no bound
     * @param dayEnd          end of the day to keep, exclusive; null for no bound
     * @param authorId        only messages of this author; null for every author
     * @param anyPullRequestStatus true applies no status filter; false keeps only pull request
     *                        share entries whose live share has one of pullRequestStatuses
     * @param pullRequestStatuses status names to keep when anyPullRequestStatus is false; never empty,
     *                        because a native IN list cannot be empty
     * @param cursorCreatedAt created_at of the cursor message
     * @param cursorId        ID of the cursor message
     * @param olderLimit      maximum number of rows before the cursor
     * @param newerLimit      maximum number of rows from the cursor on
     * @return the rows of both sides, oldest first
     */
    @Query(value = """
            SELECT a.* FROM (
                (SELECT o.* FROM (
                    (SELECT m.* FROM kazan.chat_messages m
                     WHERE m.channel_id = :channelId
                       AND (CAST(:dayStart AS timestamptz) IS NULL OR m.created_at >= CAST(:dayStart AS timestamptz))
                       AND (CAST(:dayEnd AS timestamptz) IS NULL OR m.created_at < CAST(:dayEnd AS timestamptz))
                       AND (CAST(:authorId AS uuid) IS NULL OR m.author_id = CAST(:authorId AS uuid))
                       AND (:anyPullRequestStatus OR EXISTS (SELECT 1 FROM kazan.pull_request_shares s
                                                             WHERE s.id = m.pull_request_share_id
                                                               AND s.deleted = false
                                                               AND CAST(s.status AS text) IN (:pullRequestStatuses)))
                       AND m.thread_id IS NULL
                       AND m.deleted = false
                       AND (m.created_at, m.id) < (:cursorCreatedAt, :cursorId)
                     ORDER BY m.created_at DESC, m.id DESC
                     LIMIT :olderLimit)
                    UNION ALL
                    (SELECT m.* FROM kazan.chat_threads t
                     JOIN kazan.chat_messages m ON m.id = t.root_message_id
                     WHERE t.channel_id = :channelId
                       AND (CAST(:dayStart AS timestamptz) IS NULL OR m.created_at >= CAST(:dayStart AS timestamptz))
                       AND (CAST(:dayEnd AS timestamptz) IS NULL OR m.created_at < CAST(:dayEnd AS timestamptz))
                       AND (CAST(:authorId AS uuid) IS NULL OR m.author_id = CAST(:authorId AS uuid))
                       AND (:anyPullRequestStatus OR EXISTS (SELECT 1 FROM kazan.pull_request_shares s
                                                             WHERE s.id = m.pull_request_share_id
                                                               AND s.deleted = false
                                                               AND CAST(s.status AS text) IN (:pullRequestStatuses)))
                       AND m.deleted = true
                       AND (m.created_at, m.id) < (:cursorCreatedAt, :cursorId)
                     ORDER BY m.created_at DESC, m.id DESC
                     LIMIT :olderLimit)
                 ) o
                 ORDER BY o.created_at DESC, o.id DESC
                 LIMIT :olderLimit)
                UNION ALL
                (SELECT n.* FROM (
                    (SELECT m.* FROM kazan.chat_messages m
                     WHERE m.channel_id = :channelId
                       AND (CAST(:dayStart AS timestamptz) IS NULL OR m.created_at >= CAST(:dayStart AS timestamptz))
                       AND (CAST(:dayEnd AS timestamptz) IS NULL OR m.created_at < CAST(:dayEnd AS timestamptz))
                       AND (CAST(:authorId AS uuid) IS NULL OR m.author_id = CAST(:authorId AS uuid))
                       AND (:anyPullRequestStatus OR EXISTS (SELECT 1 FROM kazan.pull_request_shares s
                                                             WHERE s.id = m.pull_request_share_id
                                                               AND s.deleted = false
                                                               AND CAST(s.status AS text) IN (:pullRequestStatuses)))
                       AND m.thread_id IS NULL
                       AND m.deleted = false
                       AND (m.created_at, m.id) >= (:cursorCreatedAt, :cursorId)
                     ORDER BY m.created_at ASC, m.id ASC
                     LIMIT :newerLimit)
                    UNION ALL
                    (SELECT m.* FROM kazan.chat_threads t
                     JOIN kazan.chat_messages m ON m.id = t.root_message_id
                     WHERE t.channel_id = :channelId
                       AND (CAST(:dayStart AS timestamptz) IS NULL OR m.created_at >= CAST(:dayStart AS timestamptz))
                       AND (CAST(:dayEnd AS timestamptz) IS NULL OR m.created_at < CAST(:dayEnd AS timestamptz))
                       AND (CAST(:authorId AS uuid) IS NULL OR m.author_id = CAST(:authorId AS uuid))
                       AND (:anyPullRequestStatus OR EXISTS (SELECT 1 FROM kazan.pull_request_shares s
                                                             WHERE s.id = m.pull_request_share_id
                                                               AND s.deleted = false
                                                               AND CAST(s.status AS text) IN (:pullRequestStatuses)))
                       AND m.deleted = true
                       AND (m.created_at, m.id) >= (:cursorCreatedAt, :cursorId)
                     ORDER BY m.created_at ASC, m.id ASC
                     LIMIT :newerLimit)
                 ) n
                 ORDER BY n.created_at ASC, n.id ASC
                 LIMIT :newerLimit)
            ) a
            ORDER BY a.created_at ASC, a.id ASC
            """, nativeQuery = true)
    List<ChatMessage> findChannelMessagesAround(@Param("channelId") UUID channelId,
                                                @Param("dayStart") Instant dayStart,
                                                @Param("dayEnd") Instant dayEnd,
                                                @Param("authorId") UUID authorId,
                                                @Param("anyPullRequestStatus") boolean anyPullRequestStatus,
                                                @Param("pullRequestStatuses") Collection<String> pullRequestStatuses,
                                                @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                                @Param("cursorId") UUID cursorId,
                                                @Param("olderLimit") int olderLimit,
                                                @Param("newerLimit") int newerLimit);

    /**
     * Finds the newest live messages of a thread.
     *
     * @param threadId ID of the thread
     * @param limit    maximum number of rows
     * @return up to limit messages, oldest first
     */
    @Query(value = """
            SELECT p.* FROM (
                SELECT m.* FROM kazan.chat_messages m
                WHERE m.thread_id = :threadId
                  AND m.deleted = false
                ORDER BY m.created_at DESC, m.id DESC
                LIMIT :limit
            ) p
            ORDER BY p.created_at ASC, p.id ASC
            """, nativeQuery = true)
    List<ChatMessage> findLatestThreadMessages(@Param("threadId") UUID threadId,
                                               @Param("limit") int limit);

    /**
     * Finds the live thread messages directly older than a cursor.
     *
     * @param threadId        ID of the thread
     * @param cursorCreatedAt created_at of the cursor message
     * @param cursorId        ID of the cursor message
     * @param limit           maximum number of rows
     * @return up to limit messages strictly older than the cursor, oldest first
     */
    @Query(value = """
            SELECT p.* FROM (
                SELECT m.* FROM kazan.chat_messages m
                WHERE m.thread_id = :threadId
                  AND m.deleted = false
                  AND (m.created_at, m.id) < (:cursorCreatedAt, :cursorId)
                ORDER BY m.created_at DESC, m.id DESC
                LIMIT :limit
            ) p
            ORDER BY p.created_at ASC, p.id ASC
            """, nativeQuery = true)
    List<ChatMessage> findThreadMessagesBefore(@Param("threadId") UUID threadId,
                                               @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                               @Param("cursorId") UUID cursorId,
                                               @Param("limit") int limit);

    /**
     * Finds the live thread messages directly newer than a cursor.
     *
     * @param threadId        ID of the thread
     * @param cursorCreatedAt created_at of the cursor message
     * @param cursorId        ID of the cursor message
     * @param limit           maximum number of rows
     * @return up to limit messages strictly newer than the cursor, oldest first
     */
    @Query(value = """
            SELECT m.* FROM kazan.chat_messages m
            WHERE m.thread_id = :threadId
              AND m.deleted = false
              AND (m.created_at, m.id) > (:cursorCreatedAt, :cursorId)
            ORDER BY m.created_at ASC, m.id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<ChatMessage> findThreadMessagesAfter(@Param("threadId") UUID threadId,
                                              @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                              @Param("cursorId") UUID cursorId,
                                              @Param("limit") int limit);

    /**
     * Finds the live thread messages around a cursor: up to olderLimit rows
     * strictly older than the cursor and up to newerLimit rows from the cursor
     * on, the cursor included when it is live.
     *
     * @param threadId        ID of the thread
     * @param cursorCreatedAt created_at of the cursor message
     * @param cursorId        ID of the cursor message
     * @param olderLimit      maximum number of rows before the cursor
     * @param newerLimit      maximum number of rows from the cursor on
     * @return the rows of both sides, oldest first
     */
    @Query(value = """
            SELECT a.* FROM (
                (SELECT m.* FROM kazan.chat_messages m
                 WHERE m.thread_id = :threadId
                   AND m.deleted = false
                   AND (m.created_at, m.id) < (:cursorCreatedAt, :cursorId)
                 ORDER BY m.created_at DESC, m.id DESC
                 LIMIT :olderLimit)
                UNION ALL
                (SELECT m.* FROM kazan.chat_messages m
                 WHERE m.thread_id = :threadId
                   AND m.deleted = false
                   AND (m.created_at, m.id) >= (:cursorCreatedAt, :cursorId)
                 ORDER BY m.created_at ASC, m.id ASC
                 LIMIT :newerLimit)
            ) a
            ORDER BY a.created_at ASC, a.id ASC
            """, nativeQuery = true)
    List<ChatMessage> findThreadMessagesAround(@Param("threadId") UUID threadId,
                                               @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                               @Param("cursorId") UUID cursorId,
                                               @Param("olderLimit") int olderLimit,
                                               @Param("newerLimit") int newerLimit);

    /**
     * Finds the reply previews of the given messages, deleted ones included,
     * so a reply can say that its target was deleted. IDs without a row, and
     * IDs of another organization's messages, are absent from the result, so
     * a stored reply ID can never show text of another organization.
     *
     * @param ids            IDs of the replied-to messages, not empty
     * @param organizationId ID of the organization the reader reads in
     * @return one row per existing message of the organization
     */
    @Query(value = """
            SELECT m.id AS id,
                   m.author_id AS authorId,
                   m.content AS content,
                   m.daily_update_id AS dailyUpdateId,
                   m.pull_request_share_id AS pullRequestShareId,
                   m.deleted AS deleted
            FROM kazan.chat_messages m
            WHERE m.id IN (:ids)
              AND m.organization_id = :organizationId
            """, nativeQuery = true)
    List<ChatReplyPreviewProjection> findReplyPreviewsByIds(@Param("ids") Collection<UUID> ids,
                                                            @Param("organizationId") UUID organizationId);

    /**
     * Finds the newest pinned live messages of a channel and its threads.
     *
     * @param channelId      ID of the channel
     * @param limit          maximum number of rows
     * @return up to limit messages, most recently pinned first
     */
    @Query(value = """
            SELECT m.* FROM kazan.chat_messages m
            WHERE m.channel_id = :channelId
              AND m.pinned_at IS NOT NULL
              AND m.deleted = false
            ORDER BY m.pinned_at DESC, m.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<ChatMessage> findPinnedMessages(@Param("channelId") UUID channelId,
                                         @Param("limit") int limit);

    /**
     * Finds the pinned live messages of a channel and its threads that were
     * pinned before a cursor message.
     *
     * @param channelId      ID of the channel
     * @param cursorPinnedAt pinned_at of the cursor message
     * @param cursorId       ID of the cursor message
     * @param limit          maximum number of rows
     * @return up to limit messages, most recently pinned first
     */
    @Query(value = """
            SELECT m.* FROM kazan.chat_messages m
            WHERE m.channel_id = :channelId
              AND m.pinned_at IS NOT NULL
              AND m.deleted = false
              AND (m.pinned_at, m.id) < (:cursorPinnedAt, :cursorId)
            ORDER BY m.pinned_at DESC, m.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<ChatMessage> findPinnedMessagesBefore(@Param("channelId") UUID channelId,
                                               @Param("cursorPinnedAt") Instant cursorPinnedAt,
                                               @Param("cursorId") UUID cursorId,
                                               @Param("limit") int limit);

    /**
     * Counts the pinned live messages of a channel, its threads included,
     * which the pin limit caps.
     *
     * @param channelId ID of the channel
     * @return number of pinned messages
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM kazan.chat_messages m
            WHERE m.channel_id = :channelId
              AND m.pinned_at IS NOT NULL
              AND m.deleted = false
            """, nativeQuery = true)
    long countPinnedMessages(@Param("channelId") UUID channelId);

    /**
     * Pins a live message unless it is pinned already. One conditional
     * statement, so two members pinning at the same moment change the row
     * once and only the one that changed it announces the pin. updated_at is
     * set here because a native update skips JPA auditing.
     *
     * The persistence context is flushed before and cleared after, so a
     * message read again in the same transaction carries the new pin.
     *
     * @param messageId ID of the message
     * @param pinnedAt  time of the pin
     * @param pinnedBy  ID of the user pinning it
     * @return 1 when the message was pinned, 0 when it was pinned already or is deleted
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE kazan.chat_messages
            SET pinned_at = :pinnedAt,
                pinned_by_id = :pinnedBy,
                updated_at = now()
            WHERE id = :messageId
              AND pinned_at IS NULL
              AND deleted = false
            """, nativeQuery = true)
    int pinIfUnpinned(@Param("messageId") UUID messageId,
                      @Param("pinnedAt") Instant pinnedAt,
                      @Param("pinnedBy") UUID pinnedBy);

    /**
     * Unpins a live message if it is pinned, with the same single-change
     * guarantee and persistence context handling as {@link #pinIfUnpinned}.
     *
     * @param messageId ID of the message
     * @return 1 when the message was unpinned, 0 when it was not pinned or is deleted
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE kazan.chat_messages
            SET pinned_at = NULL,
                pinned_by_id = NULL,
                updated_at = now()
            WHERE id = :messageId
              AND pinned_at IS NOT NULL
              AND deleted = false
            """, nativeQuery = true)
    int unpinIfPinned(@Param("messageId") UUID messageId);

    /**
     * Searches the live messages of the given channels and their threads for
     * a case-insensitive substring. The text is matched literally: % and _ in
     * it are no wildcards. A message matches through its content with tokens
     * as stored; a daily update entry matches through its update's focus,
     * questions and the ticket key, title and note of its lines, but only
     * while the update's author holds an
     * {@link com.korur.oba.shrine.organization.enums.OrganizationMemberStatus#ACTIVE} membership of the
     * organization, so the words of someone who left are no longer found.
     * Each channel is read through chat_messages_channel_id_created_at_idx.
     *
     * @param organizationId  ID of the organization, a guard on top of the channels
     * @param channelIds      IDs of the channels the reader can read, not empty
     * @param query           text to find, at least 2 characters
     * @param authorId        only messages of this author; null for every author
     * @param cursorCreatedAt created_at of the cursor message; null for the newest matches
     * @param cursorId        ID of the cursor message; null exactly when cursorCreatedAt is null
     * @param limit           maximum number of rows
     * @return up to limit matches strictly older than the cursor, newest first
     */
    @Query(value = """
            WITH needle AS (
                SELECT '%' || REPLACE(REPLACE(REPLACE(:query, '\\', '\\\\'), '%', '\\%'), '_', '\\_') || '%' AS pattern
            )
            SELECT m.* FROM kazan.chat_messages m
            CROSS JOIN needle n
            WHERE m.channel_id IN (:channelIds)
              AND m.organization_id = :organizationId
              AND m.deleted = false
              AND (CAST(:authorId AS uuid) IS NULL OR m.author_id = CAST(:authorId AS uuid))
              AND (CAST(:cursorCreatedAt AS timestamptz) IS NULL
                   OR (m.created_at, m.id) < (CAST(:cursorCreatedAt AS timestamptz), CAST(:cursorId AS uuid)))
              AND (m.content ILIKE n.pattern
                   OR (m.daily_update_id IS NOT NULL
                       AND EXISTS (SELECT 1 FROM kazan.daily_updates d
                                   WHERE d.id = m.daily_update_id
                                     AND d.deleted = false
                                     AND EXISTS (SELECT 1 FROM shrine.organization_members om
                                                 WHERE om.organization_id = m.organization_id
                                                   AND om.user_id = d.author_id
                                                   AND om.deleted = false
                                                   AND om.status = 'ACTIVE')
                                     AND (d.focus ILIKE n.pattern
                                          OR d.questions ILIKE n.pattern
                                          OR EXISTS (SELECT 1 FROM kazan.daily_update_tickets dt
                                                     WHERE dt.daily_update_id = d.id
                                                       AND (dt.ticket_key ILIKE n.pattern
                                                            OR dt.ticket_title ILIKE n.pattern
                                                            OR dt.note ILIKE n.pattern))))))
            ORDER BY m.created_at DESC, m.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<ChatMessage> searchMessages(@Param("organizationId") UUID organizationId,
                                     @Param("channelIds") Collection<UUID> channelIds,
                                     @Param("query") String query,
                                     @Param("authorId") UUID authorId,
                                     @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                     @Param("cursorId") UUID cursorId,
                                     @Param("limit") int limit);

    /**
     * Counts the unread messages of one user in each channel of an
     * organization the user can read and in each live thread of those
     * channels the user is a member of.
     *
     * @param userId         ID of the user
     * @param organizationId ID of the organization
     * @return the channel rows and the thread rows with unread messages; see findUnreadCountsByUsers
     */
    default List<ChatUnreadCountProjection> findUnreadCounts(UUID userId, UUID organizationId) {
        return findUnreadCountsByUsers(organizationId, List.of(userId));
    }

    /**
     * Counts the unread messages of several users in each channel of an
     * organization they can read and in each active thread of those channels
     * they are members of.
     *
     * A channel is readable by a user when it is live and
     * {@link com.korur.oba.apps.kazan.chat.enums.ChatChannelVisibility#PUBLIC}, or
     * {@link com.korur.oba.apps.kazan.chat.enums.ChatChannelVisibility#PRIVATE} with the user on its
     * member list; a thread counts only while its
     * channel is readable, so leaving a private channel also silences its
     * threads. A message is unread when another user wrote it and its
     * (created_at, id) comes after the user's read position (last_read_at,
     * last_read_message_id). Read states created when the chat was introduced
     * carry their creation time, ahead of their message, so the message's
     * created_at is compared with last_read_at first and the ID only breaks a
     * tie. Without a read state the position is the join time. For a channel
     * it is the later of the organization membership's created_at and the
     * channel membership's created_at, so a user added to a private channel
     * does not see its older history as unread; a public channel has no
     * member rows and keeps the organization join time. For a thread it is
     * the thread member's created_at. The mention count uses the same
     * position. Only threads whose effective status is
     * {@link com.korur.oba.apps.kazan.chat.enums.ChatThreadStatus#ACTIVE} are counted: stored
     * {@link com.korur.oba.apps.kazan.chat.enums.ChatThreadStatus#ACTIVE} and not quiet for auto_archive_minutes
     * since the later of last_message_at and status_changed_at, the rule of
     * {@link com.korur.oba.apps.kazan.chat.helper.ChatThreadStatusResolver}. Closed and archived threads are left out, so
     * they add nothing to the badge or the notifications. Each count stops at
     * 101 rows and is capped at 100. The count walks
     * chat_messages_channel_id_created_at_idx or
     * chat_messages_thread_id_created_at_idx from the newest message down to
     * the read position; the ORDER BY keeps the planner on that index, where
     * LIMIT alone would make a sequential scan look cheaper.
     *
     * Each row also counts the unread messages that notify the user
     * (mentionCount), with the same unread filters, the same cap and the
     * same index walk: a message that mentions everyone, that has a mention
     * row for the user (a direct mention, or a here mention that reached the
     * user while online), or that replies to a live message of the user. A
     * reply to a message the user deleted notifies nobody. The mention rows
     * are found through their (message_id, user_id) unique key and the reply
     * target through its primary key, one probe per unread message.
     *
     * A channel row also counts the pull request share entries at or before
     * the read position that mention the user through a mention row created
     * after the read state's updated_at (the join time without one): a
     * reviewer added to a share by an edit is notified by an entry they may
     * have read already. Opening the channel clears it, because
     * {@link ChatReadStateRepository#touchChannelReadStateForShareMentions}
     * moves updated_at even when the position stays. Entries after the
     * position are counted by the mention count above, so none is counted
     * twice; threads hold no share entries. The share mention rows of the
     * requested users are read once, through chat_message_mentions_user_id_idx.
     *
     * Each user with an active membership gets a row per readable channel,
     * also at 0, whose channelId is the channel; a thread row carries its
     * thread's channel and is returned only when its count is above 0. Users
     * without an active membership get no row.
     *
     * @param organizationId ID of the organization
     * @param userIds        IDs of the users, not empty
     * @return one row per active member and readable channel, and one row per thread with unread messages, each
     *         with its notification count
     */
    @Query(value = """
            WITH reader AS (
                SELECT om.user_id AS user_id,
                       om.created_at AS joined_at
                FROM shrine.organization_members om
                WHERE om.organization_id = :organizationId
                  AND om.user_id IN (:userIds)
                  AND om.deleted = false
                  AND om.status = 'ACTIVE'
            ),
            readable AS (
                SELECT r.user_id AS user_id,
                       GREATEST(r.joined_at, COALESCE(cm.created_at, r.joined_at)) AS joined_at,
                       c.id AS channel_id
                FROM reader r
                JOIN kazan.chat_channels c
                  ON c.organization_id = :organizationId
                 AND c.deleted = false
                LEFT JOIN kazan.chat_channel_members cm
                       ON cm.channel_id = c.id
                      AND cm.user_id = r.user_id
                WHERE c.visibility = 'PUBLIC'
                   OR cm.id IS NOT NULL
            ),
            share_mention AS MATERIALIZED (
                SELECT mm.user_id AS user_id,
                       m.channel_id AS channel_id,
                       m.id AS message_id,
                       m.created_at AS created_at,
                       mm.created_at AS mentioned_at
                FROM kazan.chat_message_mentions mm
                JOIN kazan.chat_messages m ON m.id = mm.message_id
                WHERE mm.user_id IN (:userIds)
                  AND m.organization_id = :organizationId
                  AND m.pull_request_share_id IS NOT NULL
                  AND m.thread_id IS NULL
                  AND m.deleted = false
                  AND m.author_id <> mm.user_id
            )
            SELECT rc.user_id AS userId,
                   rc.channel_id AS channelId,
                   CAST(NULL AS uuid) AS threadId,
                   CAST(LEAST(c.unread, 100) AS integer) AS unread,
                   CAST(LEAST(n.mentions + s.mentions, 100) AS integer) AS mentionCount,
                   rs.last_read_message_id AS lastReadMessageId,
                   rs.last_read_at AS lastReadAt
            FROM readable rc
            LEFT JOIN kazan.chat_read_states rs
                   ON rs.user_id = rc.user_id
                  AND rs.channel_id = rc.channel_id
                  AND rs.thread_id IS NULL
            CROSS JOIN LATERAL (
                SELECT COUNT(*) AS unread FROM (
                    SELECT 1 FROM kazan.chat_messages m
                    WHERE m.channel_id = rc.channel_id
                      AND m.thread_id IS NULL
                      AND m.deleted = false
                      AND m.author_id <> rc.user_id
                      AND (m.created_at, m.id) > (COALESCE(rs.last_read_at, rc.joined_at), rs.last_read_message_id)
                    ORDER BY m.created_at DESC, m.id DESC
                    LIMIT 101
                ) x
            ) c
            CROSS JOIN LATERAL (
                SELECT COUNT(*) AS mentions FROM (
                    SELECT 1 FROM kazan.chat_messages m
                    WHERE m.channel_id = rc.channel_id
                      AND m.thread_id IS NULL
                      AND m.deleted = false
                      AND m.author_id <> rc.user_id
                      AND (m.created_at, m.id) > (COALESCE(rs.last_read_at, rc.joined_at), rs.last_read_message_id)
                      AND (m.mentions_everyone
                           OR EXISTS (SELECT 1 FROM kazan.chat_message_mentions mm
                                      WHERE mm.message_id = m.id
                                        AND mm.user_id = rc.user_id)
                           OR EXISTS (SELECT 1 FROM kazan.chat_messages q
                                      WHERE q.id = m.reply_to_message_id
                                        AND q.author_id = rc.user_id
                                        AND q.deleted = false))
                    ORDER BY m.created_at DESC, m.id DESC
                    LIMIT 101
                ) x
            ) n
            CROSS JOIN LATERAL (
                SELECT COUNT(*) AS mentions FROM (
                    SELECT 1 FROM share_mention sm
                    WHERE sm.user_id = rc.user_id
                      AND sm.channel_id = rc.channel_id
                      AND sm.mentioned_at > COALESCE(rs.updated_at, rc.joined_at)
                      AND ((sm.created_at, sm.message_id)
                           > (COALESCE(rs.last_read_at, rc.joined_at), rs.last_read_message_id)) IS NOT TRUE
                    LIMIT 101
                ) x
            ) s
            UNION ALL
            SELECT tm.user_id AS userId,
                   t.channel_id AS channelId,
                   t.id AS threadId,
                   CAST(LEAST(c.unread, 100) AS integer) AS unread,
                   CAST(LEAST(n.mentions, 100) AS integer) AS mentionCount,
                   rs.last_read_message_id AS lastReadMessageId,
                   rs.last_read_at AS lastReadAt
            FROM kazan.chat_thread_members tm
            JOIN kazan.chat_threads t ON t.id = tm.thread_id
            JOIN readable rc
              ON rc.user_id = tm.user_id
             AND rc.channel_id = t.channel_id
            LEFT JOIN kazan.chat_read_states rs
                   ON rs.user_id = tm.user_id
                  AND rs.thread_id = tm.thread_id
            CROSS JOIN LATERAL (
                SELECT COUNT(*) AS unread FROM (
                    SELECT 1 FROM kazan.chat_messages m
                    WHERE m.thread_id = t.id
                      AND m.deleted = false
                      AND m.author_id <> tm.user_id
                      AND (m.created_at, m.id) > (COALESCE(rs.last_read_at, tm.created_at), rs.last_read_message_id)
                    ORDER BY m.created_at DESC, m.id DESC
                    LIMIT 101
                ) x
            ) c
            CROSS JOIN LATERAL (
                SELECT COUNT(*) AS mentions FROM (
                    SELECT 1 FROM kazan.chat_messages m
                    WHERE m.thread_id = t.id
                      AND m.deleted = false
                      AND m.author_id <> tm.user_id
                      AND (m.created_at, m.id) > (COALESCE(rs.last_read_at, tm.created_at), rs.last_read_message_id)
                      AND (m.mentions_everyone
                           OR EXISTS (SELECT 1 FROM kazan.chat_message_mentions mm
                                      WHERE mm.message_id = m.id
                                        AND mm.user_id = tm.user_id)
                           OR EXISTS (SELECT 1 FROM kazan.chat_messages q
                                      WHERE q.id = m.reply_to_message_id
                                        AND q.author_id = tm.user_id
                                        AND q.deleted = false))
                    ORDER BY m.created_at DESC, m.id DESC
                    LIMIT 101
                ) x
            ) n
            WHERE t.organization_id = :organizationId
              AND t.status = 'ACTIVE'
              AND GREATEST(t.last_message_at, t.status_changed_at) + t.auto_archive_minutes * INTERVAL '1 minute' > now()
              AND c.unread > 0
            """, nativeQuery = true)
    List<ChatUnreadCountProjection> findUnreadCountsByUsers(@Param("organizationId") UUID organizationId,
                                                            @Param("userIds") Collection<UUID> userIds);

    /**
     * Finds the distinct authors of the live messages of a channel and its
     * threads, which the dialog that makes a channel private pre-selects.
     * Reads chat_messages_channel_id_created_at_idx.
     *
     * @param channelId ID of the channel
     * @return the author IDs, in no particular order
     */
    @Query(value = """
            SELECT DISTINCT m.author_id FROM kazan.chat_messages m
            WHERE m.channel_id = :channelId
              AND m.deleted = false
            """, nativeQuery = true)
    List<UUID> findAuthorIdsByChannelId(@Param("channelId") UUID channelId);

    /**
     * Finds deleted messages of an organization by ID, for the placeholders
     * of thread roots deleted while their thread lives on. Live messages are
     * left out; the caller reads those through the soft-delete filter.
     *
     * @param ids            IDs of the messages, not empty
     * @param organizationId ID of the organization
     * @return the deleted messages of the organization among the IDs
     */
    @Query(value = """
            SELECT m.* FROM kazan.chat_messages m
            WHERE m.id IN (:ids)
              AND m.organization_id = :organizationId
              AND m.deleted = true
            """, nativeQuery = true)
    List<ChatMessage> findDeletedByIdsAndOrganizationId(@Param("ids") Collection<UUID> ids,
                                                        @Param("organizationId") UUID organizationId);

    /**
     * Groups the reactions of the given messages per message and emoji.
     *
     * @param messageIds IDs of the messages, not empty
     * @param readerId   ID of the user who reads the page, for reactedByMe
     * @return one row per message and emoji, per message in first-reaction order
     */
    @Query(value = """
            SELECT r.message_id AS messageId,
                   r.emoji AS emoji,
                   CAST(COUNT(*) AS integer) AS reactionCount,
                   BOOL_OR(r.user_id = :readerId) AS reactedByMe,
                   ARRAY_AGG(r.user_id ORDER BY r.created_at, r.id) FILTER (WHERE r.position <= 10) AS userIds,
                   MIN(r.created_at) AS firstReactedAt
            FROM (SELECT cr.message_id, cr.emoji, cr.user_id, cr.created_at, cr.id,
                         ROW_NUMBER() OVER (PARTITION BY cr.message_id, cr.emoji
                                            ORDER BY cr.created_at, cr.id) AS position
                  FROM kazan.chat_reactions cr
                  WHERE cr.message_id IN (:messageIds)) r
            GROUP BY r.message_id, r.emoji
            ORDER BY r.message_id, MIN(r.created_at), r.emoji
            """, nativeQuery = true)
    List<ChatReactionSummaryProjection> findReactionSummaries(@Param("messageIds") Collection<UUID> messageIds,
                                                              @Param("readerId") UUID readerId);

    /**
     * Finds the threads started from the given messages with their effective
     * status (the rule of {@link com.korur.oba.apps.kazan.chat.helper.ChatThreadStatusResolver}), member count and up to 3
     * preview members. Preview members are current thread members only, so a
     * user who left drops out even after posting: those among the authors of
     * the 100 newest live replies first, the most recent replier first, then
     * the thread's creator, then the others by join time.
     *
     * @param rootMessageIds IDs of the root messages, not empty
     * @return one row per root message that has a thread
     */
    @Query(value = """
            SELECT t.id AS threadId,
                   t.root_message_id AS rootMessageId,
                   t.title AS title,
                   t.message_count AS messageCount,
                   t.last_message_at AS lastMessageAt,
                   t.auto_archive_minutes AS autoArchiveMinutes,
                   CASE
                       WHEN t.status = 'ACTIVE'
                            AND GREATEST(t.last_message_at, t.status_changed_at)
                                + t.auto_archive_minutes * INTERVAL '1 minute' <= now()
                           THEN 'ARCHIVED'
                       ELSE CAST(t.status AS text)
                   END AS status,
                   t.created_by_id AS createdBy,
                   (SELECT CAST(COUNT(*) AS integer)
                    FROM kazan.chat_thread_members tm
                    WHERE tm.thread_id = t.id) AS memberCount,
                   ARRAY(
                       SELECT tm.user_id
                       FROM kazan.chat_thread_members tm
                       LEFT JOIN (SELECT r.author_id,
                                         MAX(r.created_at) AS last_at
                                  FROM (SELECT m.author_id, m.created_at
                                        FROM kazan.chat_messages m
                                        WHERE m.thread_id = t.id
                                          AND m.deleted = false
                                        ORDER BY m.created_at DESC, m.id DESC
                                        LIMIT 100) r
                                  GROUP BY r.author_id) p ON p.author_id = tm.user_id
                       WHERE tm.thread_id = t.id
                       ORDER BY p.last_at DESC NULLS LAST,
                                CASE WHEN tm.user_id = t.created_by_id THEN 0 ELSE 1 END,
                                tm.created_at, tm.user_id
                       LIMIT 3
                   ) AS memberPreviewIds
            FROM kazan.chat_threads t
            WHERE t.root_message_id IN (:rootMessageIds)
            """, nativeQuery = true)
    List<ChatThreadSummaryProjection> findThreadSummariesByRootIds(@Param("rootMessageIds") Collection<UUID> rootMessageIds);
}
