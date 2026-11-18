package com.korur.oba.apps.kaizen.chat.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.entity.ChatReaction;

/**
 * Repository interface for {@link ChatReaction} entity database operations.
 *
 * Reactions of a page are grouped by {@link ChatMessageRepository#findReactionSummaries};
 * this repository serves adding and removing one reaction and the limit on
 * distinct emoji per message.
 */
@Repository
public interface ChatReactionRepository extends JpaRepository<ChatReaction, UUID> {

    /**
     * Checks whether a user has reacted to a message with an emoji.
     *
     * @param messageId ID of the message
     * @param userId    ID of the user
     * @param emoji     the emoji
     * @return true when the reaction exists
     */
    boolean existsByMessageIdAndUserIdAndEmoji(UUID messageId, UUID userId, String emoji);

    /**
     * Checks whether anyone has reacted to a message with an emoji, which
     * decides whether a new reaction adds a distinct emoji.
     *
     * @param messageId ID of the message
     * @param emoji     the emoji
     * @return true when the message has a reaction with this emoji
     */
    boolean existsByMessageIdAndEmoji(UUID messageId, String emoji);

    /**
     * Counts the distinct emoji on a message.
     *
     * @param messageId ID of the message
     * @return number of distinct emoji
     */
    @Query(value = """
            SELECT COUNT(DISTINCT r.emoji)
            FROM kaizen.chat_reactions r
            WHERE r.message_id = :messageId
            """, nativeQuery = true)
    long countDistinctEmojiByMessageId(@Param("messageId") UUID messageId);

    /**
     * Adds a user's reaction with an emoji to a message unless it already
     * exists. Two concurrent adds of the same reaction both succeed, as the
     * unique constraint turns the second insert into a no-op; a failed insert
     * would instead abort the whole PostgreSQL transaction.
     *
     * @param messageId ID of the message
     * @param userId    ID of the user
     * @param emoji     the emoji
     * @return 1 when the reaction was added, 0 when it already existed
     */
    @Modifying
    @Query(value = """
            INSERT INTO kaizen.chat_reactions (id, message_id, user_id, emoji, created_at, updated_at)
            VALUES (gen_random_uuid(), :messageId, :userId, :emoji, now(), now())
            ON CONFLICT ON CONSTRAINT chat_reactions_message_id_user_id_emoji_uk DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("messageId") UUID messageId,
                       @Param("userId") UUID userId,
                       @Param("emoji") String emoji);

    /**
     * Removes a user's reaction with an emoji from a message.
     *
     * @param messageId ID of the message
     * @param userId    ID of the user
     * @param emoji     the emoji
     * @return 1 when the reaction was removed, 0 when it did not exist
     */
    @Modifying
    @Query(value = """
            DELETE FROM kaizen.chat_reactions
            WHERE message_id = :messageId
              AND user_id = :userId
              AND emoji = :emoji
            """, nativeQuery = true)
    int deleteByMessageIdAndUserIdAndEmoji(@Param("messageId") UUID messageId,
                                           @Param("userId") UUID userId,
                                           @Param("emoji") String emoji);
}
