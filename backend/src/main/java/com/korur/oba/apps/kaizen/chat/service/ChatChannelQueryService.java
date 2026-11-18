package com.korur.oba.apps.kaizen.chat.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.dto.response.ChatChannelResponse;
import com.korur.oba.apps.kaizen.chat.entity.ChatChannel;
import com.korur.oba.apps.kaizen.chat.entity.ChatMessage;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Read operations for chat channels and the one access check every chat
 * operation on a channel, message or thread goes through.
 *
 * A channel the user cannot read is reported as not found, never as
 * forbidden, so a channel ID reveals nothing about private channels or other
 * organizations.
 */
public interface ChatChannelQueryService {

    /**
     * Lists the channels of the organization the reader may see, in creation
     * order: every public channel, the private channels the reader is a
     * member of, and for an owner or admin every private channel, with member
     * false where the admin is not on its list. Each user orders the list on
     * the client.
     *
     * @param user           the reader; any ACTIVE member of an ACTIVE organization
     * @param organizationId the request's organization
     * @return the channels, oldest first
     */
    List<ChatChannelResponse> listChannels(User user, UUID organizationId);

    /**
     * Ensures the user may act in a channel and returns it: the channel is
     * live, of the organization, and public or has the user on its member
     * list. Owners and admins read a private channel only after adding
     * themselves.
     *
     * This checks the channel only, not the user's organization role. The
     * caller checks the role once beforehand: {@link com.korur.oba.dojo.security.annotation.RequiredUserAccess}
     * on a REST handler, or a call to
     * {@link com.korur.oba.shrine.organization.service.OrganizationAccessService#verifyOrganizationAccess}
     * on an entry point the HTTP interceptor does not reach.
     *
     * @param organizationId the request's organization
     * @param userId         the user
     * @param channelId      the channel; null is reported as not found
     * @return the channel
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         NOT_CHAT_CHANNEL_FOUND (KAI_NOT_015) when the channel is absent, deleted, of another
     *         organization or private without the user on its list
     */
    ChatChannel verifyChannelAccess(UUID organizationId, UUID userId, UUID channelId);

    /**
     * Tells whether the user can read a channel, with one exists query and no
     * exception, for the realtime subscription check and its revalidation,
     * and for a user added to a thread without asking, such as the root's
     * author on a new thread.
     *
     * @param organizationId the organization of the subscription
     * @param userId         the user
     * @param channelId      the channel
     * @return true when the user is an ACTIVE member of the organization and can read the channel
     */
    boolean canRead(UUID organizationId, UUID userId, UUID channelId);

    /**
     * Finds the live channel that receives the organization's daily updates.
     *
     * @param organizationId the organization
     * @return the channel ID, empty when no channel has the DAILY_UPDATES purpose
     */
    Optional<UUID> findDailyUpdateChannelId(UUID organizationId);

    /**
     * Finds the live channel that receives the organization's pull request shares.
     *
     * @param organizationId the organization
     * @return the channel ID, empty when no channel has the PULL_REQUESTS purpose
     */
    Optional<UUID> findPullRequestChannelId(UUID organizationId);

    /**
     * Finds the live chat entries of pull request shares, in one query, so a
     * share can link to its card. Performs no access check: the caller has
     * already checked that the reader may read the shares.
     *
     * @param shareIds IDs of the shares; null or empty returns an empty map
     * @return the live entries keyed by share ID; a share whose entry is deleted or missing is absent
     */
    Map<UUID, ChatMessage> findPullRequestShareMessages(Collection<UUID> shareIds);

    /**
     * Loads a live chat message by id, from any channel of any organization.
     *
     * Performs no access check and no organization filter: the caller reads the organization
     * and the channel from the message and checks the user's access itself, through
     * {@link #verifyChannelAccess} among others.
     *
     * @param messageId the message
     * @return the message
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         NOT_CHAT_MESSAGE_FOUND when no live message has the id
     */
    ChatMessage getMessageEntityById(UUID messageId);
}
