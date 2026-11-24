package com.korur.oba.apps.kazan.chat.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.entity.ChatChannel;
import com.korur.oba.apps.kazan.chat.enums.ChatChannelPurpose;
import com.korur.oba.dojo.repository.SoftDeleteRepository;

/**
 * Repository interface for {@link ChatChannel} entity database operations.
 *
 * A channel is readable by a user when it is live, belongs to the
 * organization and is {@link com.korur.oba.apps.kazan.chat.enums.ChatChannelVisibility#PUBLIC}
 * or has the user on its member list. The derived queries skip deleted
 * channels through the soft-delete filter; the native queries state
 * deleted = false themselves. Every lookup is scoped to an
 * organization, so a channel ID of another organization finds nothing.
 */
@Repository
public interface ChatChannelRepository extends SoftDeleteRepository<ChatChannel, UUID> {

    /**
     * Finds a live channel of an organization.
     *
     * @param id             ID of the channel
     * @param organizationId ID of the organization
     * @return the channel, empty when it is absent, deleted or of another organization
     */
    Optional<ChatChannel> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * Finds the live channels of an organization in creation order, the order
     * of the channel list before a user's own order is applied.
     *
     * @param organizationId ID of the organization
     * @return the channels, oldest first, the ID breaking ties
     */
    List<ChatChannel> findByOrganizationIdOrderByCreatedAtAscIdAsc(UUID organizationId);

    /**
     * Checks whether a live channel of an organization already uses the given slug.
     *
     * @param organizationId ID of the organization
     * @param slug           the slug to check
     * @return true when a live channel of the organization has the slug
     */
    boolean existsByOrganizationIdAndSlug(UUID organizationId, String slug);

    /**
     * Checks whether a live channel of an organization other than the given one uses
     * the given slug.
     *
     * @param organizationId ID of the organization
     * @param slug           the slug to check
     * @param id             ID of the channel whose own slug does not count
     * @return true when another live channel of the organization has the slug
     */
    boolean existsByOrganizationIdAndSlugAndIdNot(UUID organizationId, String slug, UUID id);

    /**
     * Finds the IDs of the live channels of an organization a user can read:
     * the public ones and the private ones with the user on the member list.
     * Search, unread counts and thread lists take these IDs as their scope.
     *
     * @param organizationId ID of the organization
     * @param userId         ID of the user
     * @return the readable channel IDs, in no particular order
     */
    @Query(value = """
            SELECT c.id FROM kazan.chat_channels c
            WHERE c.organization_id = :organizationId
              AND c.deleted = false
              AND (c.visibility = 'PUBLIC'
                   OR EXISTS (SELECT 1 FROM kazan.chat_channel_members cm
                              WHERE cm.channel_id = c.id
                                AND cm.user_id = :userId))
            """, nativeQuery = true)
    List<UUID> findReadableChannelIds(@Param("organizationId") UUID organizationId,
                                      @Param("userId") UUID userId);

    /**
     * Tells in one statement whether a user can read a channel: the user is
     * an {@link com.korur.oba.shrine.organization.enums.OrganizationMemberStatus#ACTIVE}
     * member of the organization, and the channel is live, of the
     * organization, and {@link com.korur.oba.apps.kazan.chat.enums.ChatChannelVisibility#PUBLIC}
     * or has the user on its member list. Serves the
     * realtime subscription check and its revalidation, which must not throw.
     *
     * @param organizationId ID of the organization
     * @param userId         ID of the user
     * @param channelId      ID of the channel
     * @return true when the user can read the channel
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM kazan.chat_channels c
                JOIN shrine.organization_members om
                  ON om.organization_id = c.organization_id
                 AND om.user_id = :userId
                 AND om.deleted = false
                 AND om.status = 'ACTIVE'
                WHERE c.id = :channelId
                  AND c.organization_id = :organizationId
                  AND c.deleted = false
                  AND (c.visibility = 'PUBLIC'
                       OR EXISTS (SELECT 1 FROM kazan.chat_channel_members cm
                                  WHERE cm.channel_id = c.id
                                    AND cm.user_id = :userId)))
            """, nativeQuery = true)
    boolean existsReadableChannel(@Param("organizationId") UUID organizationId,
                                  @Param("userId") UUID userId,
                                  @Param("channelId") UUID channelId);

    /**
     * Finds the live channel that receives the organization's daily updates,
     * through the unique index chat_channels_purpose_uk. A deleted channel
     * never counts, so its purpose is free for another channel.
     *
     * @param organizationId ID of the organization
     * @return the channel ID, empty when no live channel has the DAILY_UPDATES purpose
     */
    @Query(value = """
            SELECT c.id FROM kazan.chat_channels c
            WHERE c.organization_id = :organizationId
              AND c.purpose = 'DAILY_UPDATES'
              AND c.deleted = false
            """, nativeQuery = true)
    Optional<UUID> findDailyUpdateChannelId(@Param("organizationId") UUID organizationId);

    /**
     * Finds the live channel that receives the organization's pull request
     * shares, through the unique index chat_channels_purpose_uk. A deleted
     * channel never counts, so its purpose is free for another channel.
     *
     * @param organizationId ID of the organization
     * @return the channel ID, empty when no live channel has the PULL_REQUESTS purpose
     */
    @Query(value = """
            SELECT c.id FROM kazan.chat_channels c
            WHERE c.organization_id = :organizationId
              AND c.purpose = 'PULL_REQUESTS'
              AND c.deleted = false
            """, nativeQuery = true)
    Optional<UUID> findPullRequestChannelId(@Param("organizationId") UUID organizationId);

    /**
     * Finds the live channel that holds a purpose other than GENERAL, through
     * the unique index chat_channels_purpose_uk. A deleted channel never
     * counts, so its purpose is free for another channel. Not meant for
     * {@link ChatChannelPurpose#GENERAL}, which many channels share.
     *
     * @param organizationId ID of the organization
     * @param purpose        name of the purpose, a {@link ChatChannelPurpose} constant
     * @return the channel ID, empty when no live channel has the purpose
     */
    @Query(value = """
            SELECT c.id FROM kazan.chat_channels c
            WHERE c.organization_id = :organizationId
              AND c.purpose = CAST(:purpose AS kazan_chat_channel_purpose_enum)
              AND c.deleted = false
            """, nativeQuery = true)
    Optional<UUID> findChannelIdByPurpose(@Param("organizationId") UUID organizationId,
                                          @Param("purpose") String purpose);

    /**
     * Creates the channels every organization starts with: "General", then
     * "Updates" for the daily updates, then "Pull Requests" for the pull request talk,
     * all public, one millisecond apart so the creation order is stable. Runs
     * only for an organization without any channel, deleted ones included, so
     * running it twice creates nothing the second time; ON CONFLICT also
     * leaves a live {@link ChatChannelPurpose#DAILY_UPDATES} or
     * {@link ChatChannelPurpose#PULL_REQUESTS} channel alone. "Pull Requests" keeps
     * the slug "pr" that the channel had under its earlier name "PR", so every
     * organization reaches it at the same address; an organization without
     * channels has every slug free.
     *
     * @param organizationId ID of the organization
     * @return number of channels created, 3 or 0
     */
    @Modifying
    @Query(value = """
            INSERT INTO kazan.chat_channels
                (id, name, slug, type, purpose, visibility, organization_id, created_by_id, deleted, created_at, updated_at)
            SELECT gen_random_uuid(), v.name, v.slug, CAST('TEXT' AS kazan_chat_channel_type_enum),
                   CAST(v.purpose AS kazan_chat_channel_purpose_enum),
                   CAST('PUBLIC' AS kazan_chat_channel_visibility_enum),
                   :organizationId, NULL, false, v.created_at, v.created_at
            FROM (VALUES ('General', 'general', 'GENERAL', now()),
                         ('Updates', 'updates', 'DAILY_UPDATES', now() + INTERVAL '1 millisecond'),
                         ('Pull Requests', 'pr', 'PULL_REQUESTS', now() + INTERVAL '2 milliseconds'))
                 AS v(name, slug, purpose, created_at)
            WHERE NOT EXISTS (SELECT 1 FROM kazan.chat_channels c
                              WHERE c.organization_id = :organizationId)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertInitialChannelsIfAbsent(@Param("organizationId") UUID organizationId);
}
