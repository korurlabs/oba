package com.korur.oba.apps.kazan.chat.repository;

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
import com.korur.oba.apps.kazan.chat.dto.projection.ChatChannelMemberCountProjection;
import com.korur.oba.apps.kazan.chat.entity.ChatChannelMember;

/**
 * Repository interface for {@link ChatChannelMember} entity database operations.
 *
 * Only private channels have member rows. Inserts ignore an existing member
 * through the unique constraint, so two admins adding the same user at once
 * both succeed.
 */
@Repository
public interface ChatChannelMemberRepository extends JpaRepository<ChatChannelMember, UUID> {

    /**
     * Checks whether a user is on the member list of a channel.
     *
     * @param channelId ID of the channel
     * @param userId    ID of the user
     * @return true when the user is a member
     */
    boolean existsByChannelIdAndUserId(UUID channelId, UUID userId);

    /**
     * Finds the IDs of the members of a channel, for the realtime fan-out of
     * a private channel and for the users a visibility change removes.
     *
     * @param channelId ID of the channel
     * @return the user IDs of the members
     */
    @Query(value = """
            SELECT cm.user_id FROM kazan.chat_channel_members cm
            WHERE cm.channel_id = :channelId
            """, nativeQuery = true)
    List<UUID> findUserIdsByChannelId(@Param("channelId") UUID channelId);

    /**
     * Finds which of the given channels a user is a member of.
     *
     * @param userId     ID of the user
     * @param channelIds IDs of the channels, not empty
     * @return the IDs of the channels the user is a member of
     */
    @Query(value = """
            SELECT cm.channel_id FROM kazan.chat_channel_members cm
            WHERE cm.user_id = :userId
              AND cm.channel_id IN (:channelIds)
            """, nativeQuery = true)
    List<UUID> findChannelIdsByUserIdAndChannelIds(@Param("userId") UUID userId,
                                                   @Param("channelIds") Collection<UUID> channelIds);

    /**
     * Counts the members of each of the given channels.
     *
     * @param channelIds IDs of the channels, not empty
     * @return one row per channel with at least one member
     */
    @Query(value = """
            SELECT cm.channel_id AS channelId,
                   CAST(COUNT(*) AS integer) AS memberCount
            FROM kazan.chat_channel_members cm
            WHERE cm.channel_id IN (:channelIds)
            GROUP BY cm.channel_id
            """, nativeQuery = true)
    List<ChatChannelMemberCountProjection> countMembersByChannelIds(@Param("channelIds") Collection<UUID> channelIds);

    /**
     * Adds a user to a channel's member list unless the user is on it.
     *
     * @param channelId ID of the channel
     * @param userId    ID of the user
     * @return 1 when the user was added, 0 when the user was a member already
     */
    @Modifying
    @Query(value = """
            INSERT INTO kazan.chat_channel_members (id, channel_id, user_id, created_at, updated_at)
            VALUES (gen_random_uuid(), :channelId, :userId, now(), now())
            ON CONFLICT ON CONSTRAINT chat_channel_members_channel_id_user_id_uk DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("channelId") UUID channelId, @Param("userId") UUID userId);

    /**
     * Removes a user from a channel's member list.
     *
     * @param channelId ID of the channel
     * @param userId    ID of the user
     * @return 1 when the user was removed, 0 when the user was no member
     */
    @Modifying
    @Query(value = """
            DELETE FROM kazan.chat_channel_members
            WHERE channel_id = :channelId
              AND user_id = :userId
            """, nativeQuery = true)
    int deleteByChannelIdAndUserId(@Param("channelId") UUID channelId, @Param("userId") UUID userId);

    /**
     * Removes the whole member list of a channel, when it becomes public and
     * every member of the organization reads it.
     *
     * @param channelId ID of the channel
     * @return number of rows removed
     */
    @Modifying
    @Query(value = """
            DELETE FROM kazan.chat_channel_members
            WHERE channel_id = :channelId
            """, nativeQuery = true)
    int deleteAllByChannelId(@Param("channelId") UUID channelId);
}
