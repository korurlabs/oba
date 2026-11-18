package com.korur.oba.apps.kazan.chat.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.entity.ChatReadState;

/**
 * Repository interface for {@link ChatReadState} entity database operations.
 *
 * A read position only moves forward: the upserts write in one statement,
 * through ON CONFLICT on the partial unique indexes chat_read_states_channel_uk
 * (user, channel) and chat_read_states_thread_uk (user, thread), and change an
 * existing row only when the new position (last_read_at, last_read_message_id)
 * is after the stored one. Two
 * sessions that mark read at once therefore never create a second row or move
 * the position back. A read state created when the chat was introduced
 * carries its creation time, ahead of its message, so an older message never
 * moves it.
 *
 * The upserts do not clear the persistence context: a {@link ChatReadState} loaded in
 * the same transaction is stale after one of them and must be read again.
 */
@Repository
public interface ChatReadStateRepository extends JpaRepository<ChatReadState, UUID> {

    /**
     * Finds a user's read state of a channel itself.
     *
     * @param userId    ID of the user
     * @param channelId ID of the channel
     * @return the read state, empty when the user has none yet
     */
    Optional<ChatReadState> findByUserIdAndChannelIdAndThreadIdIsNull(UUID userId, UUID channelId);

    /**
     * Finds a user's read state of a thread.
     *
     * @param userId   ID of the user
     * @param threadId ID of the thread
     * @return the read state, empty when the user has none yet
     */
    Optional<ChatReadState> findByUserIdAndThreadId(UUID userId, UUID threadId);

    /**
     * Moves a user's channel read position forward to a message, creating the
     * read state when the user has none.
     *
     * @param userId           ID of the user
     * @param organizationId   ID of the organization of the channel
     * @param channelId        ID of the channel
     * @param messageId        ID of the last read message
     * @param messageCreatedAt created_at of that message
     * @return 1 when the read state was created or moved, 0 when it already was at or after the message
     */
    @Modifying
    @Query(value = """
            INSERT INTO kazan.chat_read_states AS rs
                (id, user_id, organization_id, channel_id, thread_id, last_read_message_id, last_read_at,
                 created_at, updated_at)
            VALUES (gen_random_uuid(), :userId, :organizationId, :channelId, NULL, :messageId, :messageCreatedAt,
                    now(), now())
            ON CONFLICT (user_id, channel_id) WHERE thread_id IS NULL
            DO UPDATE SET last_read_message_id = EXCLUDED.last_read_message_id,
                          last_read_at = EXCLUDED.last_read_at,
                          updated_at = now()
            WHERE (rs.last_read_at, rs.last_read_message_id) < (EXCLUDED.last_read_at, EXCLUDED.last_read_message_id)
            """, nativeQuery = true)
    int upsertChannelReadState(@Param("userId") UUID userId,
                               @Param("organizationId") UUID organizationId,
                               @Param("channelId") UUID channelId,
                               @Param("messageId") UUID messageId,
                               @Param("messageCreatedAt") Instant messageCreatedAt);

    /**
     * Records that a user saw a channel again without moving the read
     * position, when a pull request share entry of the channel mentions the
     * user through a mention row added after the read state last changed.
     *
     * A reviewer added to a share by an edit gets a mention row on an entry
     * that may be older than the read position, so the read position cannot
     * clear it; the unread count compares that row with updated_at instead
     * (see {@link ChatMessageRepository#findUnreadCountsByUsers}), and this
     * update moves updated_at past it. Nothing changes without such a row,
     * so a read that clears nothing writes nothing.
     *
     * @param userId    ID of the user
     * @param channelId ID of the channel
     * @return 1 when the read state was touched, 0 when there was nothing to clear or no read state
     */
    @Modifying
    @Query(value = """
            UPDATE kazan.chat_read_states rs
            SET updated_at = now()
            WHERE rs.user_id = :userId
              AND rs.channel_id = :channelId
              AND rs.thread_id IS NULL
              AND EXISTS (SELECT 1
                          FROM kazan.chat_message_mentions mm
                          JOIN kazan.chat_messages m ON m.id = mm.message_id
                          WHERE mm.user_id = :userId
                            AND mm.created_at > rs.updated_at
                            AND m.channel_id = :channelId
                            AND m.thread_id IS NULL
                            AND m.pull_request_share_id IS NOT NULL
                            AND m.deleted = false
                            AND m.author_id <> :userId)
            """, nativeQuery = true)
    int touchChannelReadStateForShareMentions(@Param("userId") UUID userId, @Param("channelId") UUID channelId);

    /**
     * Moves a user's thread read position forward to a message, creating the
     * read state when the user has none.
     *
     * @param userId           ID of the user
     * @param organizationId   ID of the organization of the thread
     * @param channelId        ID of the channel of the thread
     * @param threadId         ID of the thread
     * @param messageId        ID of the last read message
     * @param messageCreatedAt created_at of that message
     * @return 1 when the read state was created or moved, 0 when it already was at or after the message
     */
    @Modifying
    @Query(value = """
            INSERT INTO kazan.chat_read_states AS rs
                (id, user_id, organization_id, channel_id, thread_id, last_read_message_id, last_read_at,
                 created_at, updated_at)
            VALUES (gen_random_uuid(), :userId, :organizationId, :channelId, :threadId, :messageId,
                    :messageCreatedAt, now(), now())
            ON CONFLICT (user_id, thread_id) WHERE thread_id IS NOT NULL
            DO UPDATE SET last_read_message_id = EXCLUDED.last_read_message_id,
                          last_read_at = EXCLUDED.last_read_at,
                          updated_at = now()
            WHERE (rs.last_read_at, rs.last_read_message_id) < (EXCLUDED.last_read_at, EXCLUDED.last_read_message_id)
            """, nativeQuery = true)
    int upsertThreadReadState(@Param("userId") UUID userId,
                              @Param("organizationId") UUID organizationId,
                              @Param("channelId") UUID channelId,
                              @Param("threadId") UUID threadId,
                              @Param("messageId") UUID messageId,
                              @Param("messageCreatedAt") Instant messageCreatedAt);

    /**
     * Removes a user's read state of a thread, when the user leaves it.
     *
     * @param userId   ID of the user
     * @param threadId ID of the thread
     * @return number of rows removed, 0 or 1
     */
    @Modifying
    @Query(value = """
            DELETE FROM kazan.chat_read_states
            WHERE user_id = :userId
              AND thread_id = :threadId
            """, nativeQuery = true)
    int deleteByUserIdAndThreadId(@Param("userId") UUID userId, @Param("threadId") UUID threadId);
}
