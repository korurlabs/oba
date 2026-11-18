package com.korur.oba.apps.kazan.chat.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.dto.request.ChatReactionRequest;
import com.korur.oba.apps.kazan.chat.dto.response.ChatReactionSummaryResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Write operations for emoji reactions on chat messages.
 *
 * Reacting needs an ACTIVE membership with role MEMBER or higher in an ACTIVE
 * organization, and the message must be live in a channel of that
 * organization the user can read. Both operations are idempotent and return
 * the message's reactions after the change, so the client replaces its
 * optimistic state with the server's.
 */
public interface ChatReactionService {

    /**
     * Adds the user's reaction with an emoji. The emoji is one grapheme of at
     * most MAX_EMOJI_LENGTH characters; a message holds at most
     * MAX_REACTIONS_PER_MESSAGE distinct emoji. Reacting again with the same
     * emoji changes nothing.
     *
     * @param user           the current user
     * @param organizationId the request's organization
     * @param messageId      the message
     * @param request        the emoji
     * @return the message's reactions, in first-reaction order
     */
    List<ChatReactionSummaryResponse> addReaction(User user, UUID organizationId, UUID messageId,
                                                  ChatReactionRequest request);

    /**
     * Removes the user's reaction with an emoji; removing an absent reaction
     * changes nothing.
     *
     * @param user           the current user
     * @param organizationId the request's organization
     * @param messageId      the message
     * @param emoji          the emoji
     * @return the message's reactions, in first-reaction order
     */
    List<ChatReactionSummaryResponse> removeReaction(User user, UUID organizationId, UUID messageId, String emoji);
}
