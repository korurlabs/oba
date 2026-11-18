package com.korur.oba.apps.kaizen.chat.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.dto.request.ChatChannelCreateRequest;
import com.korur.oba.apps.kaizen.chat.dto.request.ChatChannelMembersRequest;
import com.korur.oba.apps.kaizen.chat.dto.request.ChatChannelUpdateRequest;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatChannelResponse;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.event.OrganizationCreatedEvent;

/**
 * Write operations for chat channels: create, change and delete a channel,
 * manage a private channel's member list, and create the channels every new
 * organization starts with.
 *
 * Only an organization's owners and admins manage channels; any member may
 * leave a private channel. A channel of another organization is reported as
 * not found. Each change publishes a {@link com.korur.oba.apps.kaizen.chat.event.ChatChannelChangedEvent}, which the
 * realtime layer sends after the commit.
 */
public interface ChatChannelService {

    /**
     * Creates a text channel. A private channel's members are the creator
     * plus memberIds, each an ACTIVE member of the organization; a public
     * channel takes no memberIds. A purpose other than
     * {@link com.korur.oba.apps.kaizen.chat.enums.ChatChannelPurpose#GENERAL} that another channel
     * already holds is refused with 409
     * {@link com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError#STA_CHAT_PURPOSE_TAKEN};
     * that channel must be set to GENERAL first. The channel gets a URL slug
     * derived from its name and free among the organization's live channels;
     * when a concurrent write takes that slug first, the create is refused with
     * 409 {@link com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError#STA_SLUG_TAKEN}
     * (KAI_STA_017) and a retry succeeds.
     *
     * @param user           the creator, an owner or admin
     * @param organizationId the request's organization
     * @param request        name, visibility, purpose and the members of a private channel
     * @return the new channel as the creator sees it
     */
    ChatChannelResponse createChannel(User user, UUID organizationId, ChatChannelCreateRequest request);

    /**
     * Applies a full update of name, visibility and purpose. Public to
     * private makes the admin plus memberIds the members; private to public
     * deletes the member rows; memberIds in any other update is refused.
     * A purpose other than
     * {@link com.korur.oba.apps.kaizen.chat.enums.ChatChannelPurpose#GENERAL} that another channel
     * already holds is refused with 409
     * {@link com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError#STA_CHAT_PURPOSE_TAKEN};
     * that channel must be set to GENERAL first. A channel with such a
     * purpose cannot be private. A rename moves the channel to a slug derived
     * from the new name unless the current slug already has the new name's
     * base; when a concurrent write takes the new slug first, the update is
     * refused with 409
     * {@link com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError#STA_SLUG_TAKEN}
     * (KAI_STA_017) and a retry succeeds.
     *
     * @param user           an owner or admin
     * @param organizationId the request's organization
     * @param channelId      the channel
     * @param request        name, visibility, purpose and the members of a channel turning private
     * @return the channel after the change
     */
    ChatChannelResponse updateChannel(User user, UUID organizationId, UUID channelId,
                                      ChatChannelUpdateRequest request);

    /**
     * Soft-deletes a channel; its content is kept and hidden. Deleting the
     * daily updates channel leaves daily updates without a chat card.
     *
     * @param user           an owner or admin
     * @param organizationId the request's organization
     * @param channelId      the channel
     */
    void deleteChannel(User user, UUID organizationId, UUID channelId);

    /**
     * Adds ACTIVE members of the organization to a private channel's member
     * list; users already on it stay as they are.
     *
     * @param user           an owner or admin
     * @param organizationId the request's organization
     * @param channelId      the private channel
     * @param request        the users to add
     * @return the channel after the change
     */
    ChatChannelResponse addMembers(User user, UUID organizationId, UUID channelId, ChatChannelMembersRequest request);

    /**
     * Removes a user from a private channel's member list. An owner or admin
     * may remove anyone; any member may remove themselves to leave. The
     * removed user's live subscription of the channel ends.
     *
     * @param user           an owner or admin, or the member leaving
     * @param organizationId the request's organization
     * @param channelId      the private channel
     * @param userId         the user to remove
     */
    void removeMember(User user, UUID organizationId, UUID channelId, UUID userId);

    /**
     * Creates "General" and then "Updates" for a new organization, in the
     * transaction that creates the organization, so the organization never
     * exists without them. Running it again creates nothing.
     *
     * @param event the organization that was created
     */
    void onOrganizationCreated(OrganizationCreatedEvent event);
}
