package com.korur.oba.apps.kaizen.chat.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.entity.ChatThreadMember;

/**
 * Repository interface for {@link ChatThreadMember} entity database operations.
 *
 * A member row's created_at is the join time, from which the member's unread
 * messages count until a read state exists.
 */
@Repository
public interface ChatThreadMemberRepository extends JpaRepository<ChatThreadMember, UUID> {

    /**
     * Finds the members of a thread in join order.
     *
     * @param threadId ID of the thread
     * @return the members
     */
    List<ChatThreadMember> findByThread_IdOrderByCreatedAtAsc(UUID threadId);

    /**
     * Checks whether a user is a member of a thread.
     *
     * @param threadId ID of the thread
     * @param userId   ID of the user
     * @return true when the user is a member
     */
    boolean existsByThread_IdAndUserId(UUID threadId, UUID userId);

    /**
     * Finds the IDs of the members of a thread, for the unread fan-out of a
     * thread message.
     *
     * @param threadId ID of the thread
     * @return the user IDs of the members
     */
    @Query(value = """
            SELECT tm.user_id FROM kaizen.chat_thread_members tm
            WHERE tm.thread_id = :threadId
            """, nativeQuery = true)
    List<UUID> findUserIdsByThreadId(@Param("threadId") UUID threadId);

    /**
     * Finds which of the given threads a user is a member of.
     *
     * @param userId    ID of the user
     * @param threadIds IDs of the threads, not empty
     * @return the IDs of the threads the user is a member of
     */
    @Query(value = """
            SELECT tm.thread_id FROM kaizen.chat_thread_members tm
            WHERE tm.user_id = :userId
              AND tm.thread_id IN (:threadIds)
            """, nativeQuery = true)
    List<UUID> findThreadIdsByUserIdAndThreadIds(@Param("userId") UUID userId,
                                                 @Param("threadIds") Collection<UUID> threadIds);

    /**
     * Adds a user to a thread unless the user is already a member. Two
     * messages that mention the same user at once both succeed, as the unique
     * constraint turns the second insert into a no-op instead of an error.
     *
     * @param threadId ID of the thread
     * @param userId   ID of the user
     * @return 1 when the user joined, 0 when the user was already a member
     */
    @Modifying
    @Query(value = """
            INSERT INTO kaizen.chat_thread_members (id, thread_id, user_id, created_at, updated_at)
            VALUES (gen_random_uuid(), :threadId, :userId, now(), now())
            ON CONFLICT ON CONSTRAINT chat_thread_members_thread_id_user_id_uk DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("threadId") UUID threadId, @Param("userId") UUID userId);

    /**
     * Removes a user from a thread.
     *
     * @param threadId ID of the thread
     * @param userId   ID of the user
     * @return 1 when the user left, 0 when the user was no member
     */
    @Modifying
    @Query(value = """
            DELETE FROM kaizen.chat_thread_members
            WHERE thread_id = :threadId
              AND user_id = :userId
            """, nativeQuery = true)
    int deleteByThreadIdAndUserId(@Param("threadId") UUID threadId, @Param("userId") UUID userId);
}
