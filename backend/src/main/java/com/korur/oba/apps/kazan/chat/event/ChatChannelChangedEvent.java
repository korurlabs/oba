package com.korur.oba.apps.kazan.chat.event;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.enums.ChatChannelVisibility;

/**
 * Published when a channel was created, changed or deleted, or when users
 * were added to or removed from a private channel's member list.
 *
 * Consumers act after the transaction commits, so a change that rolls back
 * signals nothing. A public channel's change goes to the whole organization;
 * a private channel's change goes only to its members, the users named in
 * affectedUserIds and the organization's owners and admins. MEMBERS_REMOVED,
 * VISIBILITY_CHANGED and DELETED can end a user's right to read the channel,
 * so the realtime layer checks the open subscriptions of those users again.
 *
 * @param type            what happened to the channel
 * @param organizationId  organization of the channel
 * @param channelId       the channel
 * @param visibility      visibility of the channel after the change
 * @param affectedUserIds for MEMBERS_ADDED and MEMBERS_REMOVED the users added or removed; for
 *                        VISIBILITY_CHANGED the new member list when the channel became private and the
 *                        former member list when it became public; empty otherwise
 */
public record ChatChannelChangedEvent(Type type,
                                      UUID organizationId,
                                      UUID channelId,
                                      ChatChannelVisibility visibility,
                                      List<UUID> affectedUserIds) {

    /**
     * What happened to a channel.
     */
    public enum Type {
        CREATED,
        UPDATED,
        VISIBILITY_CHANGED,
        DELETED,
        MEMBERS_ADDED,
        MEMBERS_REMOVED
    }
}
