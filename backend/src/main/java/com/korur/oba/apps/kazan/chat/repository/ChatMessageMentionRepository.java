package com.korur.oba.apps.kazan.chat.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.entity.ChatMessageMention;

/**
 * Repository interface for {@link ChatMessageMention} entity database operations.
 *
 * Mentions are saved and replaced through {@link com.korur.oba.apps.kazan.chat.entity.ChatMessage#mentions}, so this
 * repository only reads them.
 */
@Repository
public interface ChatMessageMentionRepository extends JpaRepository<ChatMessageMention, UUID> {

    /**
     * Finds which of the given messages mention a user, directly or as one of
     * the members online when a here mention was sent.
     *
     * @param userId     ID of the user
     * @param messageIds IDs of the messages, not empty
     * @return the IDs of the messages that mention the user
     */
    @Query(value = """
            SELECT mm.message_id FROM kazan.chat_message_mentions mm
            WHERE mm.user_id = :userId
              AND mm.message_id IN (:messageIds)
            """, nativeQuery = true)
    List<UUID> findMessageIdsMentioningUser(@Param("userId") UUID userId,
                                            @Param("messageIds") Collection<UUID> messageIds);
}
