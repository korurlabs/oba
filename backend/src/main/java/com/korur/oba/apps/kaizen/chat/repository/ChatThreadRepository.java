package com.korur.oba.apps.kaizen.chat.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.entity.ChatThread;

/**
 * Repository interface for {@link ChatThread} entity database operations.
 *
 * The thread list is a keyset page on (last_message_at, id), most recently
 * active first, filtered by the status readers see:
 * {@link com.korur.oba.apps.kaizen.chat.enums.ChatThreadStatus#CLOSED} when stored CLOSED;
 * {@link com.korur.oba.apps.kaizen.chat.enums.ChatThreadStatus#ARCHIVED} when stored ARCHIVED, or stored
 * ACTIVE and quiet for auto_archive_minutes since the later of
 * last_message_at and status_changed_at;
 * {@link com.korur.oba.apps.kaizen.chat.enums.ChatThreadStatus#ACTIVE} otherwise. The archive that follows
 * from inactivity is derived at read time, as
 * {@link com.korur.oba.apps.kaizen.chat.helper.ChatThreadStatusResolver} does, and never stored.
 *
 * The reply counter and the last activity time change with every send and
 * delete, so they are written by single UPDATE statements that read the stored
 * values, never through the entity, where concurrent sends would overwrite
 * each other's change. The status is written the same way, so a reply and a
 * status change never undo each other. New threads are inserted the same way, so a second
 * thread on one message is a no-op the service reports as a conflict rather
 * than a unique key violation.
 */
@Repository
public interface ChatThreadRepository extends JpaRepository<ChatThread, UUID> {

    /**
     * Finds a thread of an organization.
     *
     * @param id             ID of the thread
     * @param organizationId ID of the organization
     * @return the thread, empty when it is absent or of another organization
     */
    Optional<ChatThread> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * Finds the thread started from a message; a message hosts at most one.
     *
     * @param rootMessageId ID of the root message
     * @return the thread, empty when the message has none
     */
    Optional<ChatThread> findByRootMessageId(UUID rootMessageId);

    /**
     * Finds the most recently active threads of a channel with a given
     * effective status, through chat_threads_channel_id_last_message_at_idx.
     *
     * @param channelId ID of the channel
     * @param status    effective status: ACTIVE, CLOSED or ARCHIVED
     * @param limit     maximum number of rows
     * @return up to limit threads, most recently active first
     */
    @Query(value = """
            SELECT t.* FROM kaizen.chat_threads t
            WHERE t.channel_id = :channelId
              AND (CASE
                       WHEN t.status = 'ACTIVE'
                            AND GREATEST(t.last_message_at, t.status_changed_at)
                                + t.auto_archive_minutes * INTERVAL '1 minute' <= now()
                           THEN 'ARCHIVED'
                       ELSE CAST(t.status AS text)
                   END) = :status
            ORDER BY t.last_message_at DESC, t.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<ChatThread> findLatestThreads(@Param("channelId") UUID channelId,
                                       @Param("status") String status,
                                       @Param("limit") int limit);

    /**
     * Finds the threads of a channel with a given effective status that were
     * last active before a cursor thread.
     *
     * @param channelId           ID of the channel
     * @param status              effective status: ACTIVE, CLOSED or ARCHIVED
     * @param cursorLastMessageAt last_message_at of the cursor thread
     * @param cursorId            ID of the cursor thread
     * @param limit               maximum number of rows
     * @return up to limit threads after the cursor, most recently active first
     */
    @Query(value = """
            SELECT t.* FROM kaizen.chat_threads t
            WHERE t.channel_id = :channelId
              AND (CASE
                       WHEN t.status = 'ACTIVE'
                            AND GREATEST(t.last_message_at, t.status_changed_at)
                                + t.auto_archive_minutes * INTERVAL '1 minute' <= now()
                           THEN 'ARCHIVED'
                       ELSE CAST(t.status AS text)
                   END) = :status
              AND (t.last_message_at, t.id) < (:cursorLastMessageAt, :cursorId)
            ORDER BY t.last_message_at DESC, t.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<ChatThread> findThreadsBefore(@Param("channelId") UUID channelId,
                                       @Param("status") String status,
                                       @Param("cursorLastMessageAt") Instant cursorLastMessageAt,
                                       @Param("cursorId") UUID cursorId,
                                       @Param("limit") int limit);

    /**
     * Starts a thread on a message unless the message already hosts one. Two
     * requests that start a thread on the same message at once both pass the
     * existence check; the unique key on root_message_id then turns the
     * second insert into a no-op instead of an error, and the caller reports
     * the existing thread.
     *
     * The reply counter starts at 0 and the activity time at the creation
     * time, as for a thread without replies.
     *
     * @param id                 ID of the new thread
     * @param title              title of the thread
     * @param organizationId     ID of the organization
     * @param channelId          ID of the root message's channel
     * @param rootMessageId      ID of the root message
     * @param createdById        ID of the user starting the thread
     * @param createdAt          creation time, also the first activity time
     * @param autoArchiveMinutes quiet time after which the thread archives
     * @return 1 when the thread was created, 0 when the message already hosts one
     */
    @Modifying
    @Query(value = """
            INSERT INTO kaizen.chat_threads (id, title, organization_id, channel_id, root_message_id, created_by_id,
                                             last_message_at, message_count, auto_archive_minutes,
                                             created_at, updated_at)
            VALUES (:id, :title, :organizationId, :channelId, :rootMessageId, :createdById,
                    :createdAt, 0, :autoArchiveMinutes, :createdAt, :createdAt)
            ON CONFLICT (root_message_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("title") String title,
                       @Param("organizationId") UUID organizationId,
                       @Param("channelId") UUID channelId,
                       @Param("rootMessageId") UUID rootMessageId,
                       @Param("createdById") UUID createdById,
                       @Param("createdAt") Instant createdAt,
                       @Param("autoArchiveMinutes") int autoArchiveMinutes);

    /**
     * Records a new reply in a thread: one more reply, and the activity time
     * moved to the reply unless a later one is already stored. A reply to a
     * thread archived by hand makes it
     * {@link com.korur.oba.apps.kaizen.chat.enums.ChatThreadStatus#ACTIVE} again, recorded as a status
     * change by the replier at the reply's time; one archived by inactivity
     * revives through the new activity time alone.
     *
     * A {@link com.korur.oba.apps.kaizen.chat.enums.ChatThreadStatus#CLOSED} thread is left unchanged and 0 is returned. The row lock of
     * this statement orders the reply against a concurrent close: a close
     * committed while the reply waits makes it find no row, so no reply lands
     * in a closed thread.
     *
     * @param threadId         ID of the thread
     * @param messageCreatedAt creation time of the reply
     * @param authorId         ID of the replier
     * @return 1 when the thread was updated, 0 when it is closed or does not exist
     */
    @Modifying
    @Query(value = """
            UPDATE kaizen.chat_threads
            SET message_count = message_count + 1,
                last_message_at = GREATEST(last_message_at, :messageCreatedAt),
                status = CAST('ACTIVE' AS kaizen_chat_thread_status_enum),
                status_changed_at = CASE WHEN status = 'ARCHIVED' THEN :messageCreatedAt
                                         ELSE status_changed_at END,
                status_changed_by_id = CASE WHEN status = 'ARCHIVED' THEN :authorId
                                            ELSE status_changed_by_id END,
                updated_at = now()
            WHERE id = :threadId
              AND status <> 'CLOSED'
            """, nativeQuery = true)
    int incrementMessageCount(@Param("threadId") UUID threadId,
                              @Param("messageCreatedAt") Instant messageCreatedAt,
                              @Param("authorId") UUID authorId);

    /**
     * Records a deleted reply in a thread; the count never drops below 0. The
     * activity time stays, as the thread was active then.
     *
     * @param threadId ID of the thread
     * @return 1 when the thread was updated, 0 when it does not exist
     */
    @Modifying
    @Query(value = """
            UPDATE kaizen.chat_threads
            SET message_count = GREATEST(message_count - 1, 0),
                updated_at = now()
            WHERE id = :threadId
            """, nativeQuery = true)
    int decrementMessageCount(@Param("threadId") UUID threadId);

    /**
     * Stores a thread's status with who changed it and when. The change time
     * also restarts the auto-archive window of an
     * {@link com.korur.oba.apps.kaizen.chat.enums.ChatThreadStatus#ACTIVE} thread.
     *
     * A {@link com.korur.oba.apps.kaizen.chat.enums.ChatThreadStatus#CLOSED} thread is never archived and 0 is returned: a reply revives an
     * archived thread, so an archived closed thread would reopen with the
     * next reply. The row lock of this statement orders an archive against a
     * concurrent close: a close committed while the archive waits makes it
     * find no row, so the close is never overwritten.
     *
     * The persistence context is flushed before and cleared after, so a
     * thread read again in the same transaction carries the new status, as
     * the entity cannot write it.
     *
     * @param threadId    ID of the thread
     * @param status      the new stored status: ACTIVE, CLOSED or ARCHIVED
     * @param changedById ID of the user changing it
     * @param changedAt   time of the change
     * @return 1 when the thread was updated, 0 when it does not exist or is
     *         CLOSED and the new status is ARCHIVED
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE kaizen.chat_threads
            SET status = CAST(:status AS kaizen_chat_thread_status_enum),
                status_changed_at = :changedAt,
                status_changed_by_id = :changedById,
                updated_at = now()
            WHERE id = :threadId
              AND (status <> 'CLOSED'
                   OR CAST(:status AS kaizen_chat_thread_status_enum) <> 'ARCHIVED')
            """, nativeQuery = true)
    int updateStatus(@Param("threadId") UUID threadId,
                     @Param("status") String status,
                     @Param("changedById") UUID changedById,
                     @Param("changedAt") Instant changedAt);
}
