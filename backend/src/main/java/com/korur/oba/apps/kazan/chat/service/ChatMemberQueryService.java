package com.korur.oba.apps.kazan.chat.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.dto.response.ChatMemberResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Read operations for the people of a chat channel: the mention list of the
 * composer, the member popover of the channel header and the authors a
 * channel dialog pre-selects.
 */
public interface ChatMemberQueryService {

    /**
     * Lists the ACTIVE members with an ACTIVE account who can read a channel,
     * with whether at least one socket of theirs is connected to the
     * organization: every such member of the organization without a channel
     * or for a public channel, the member list for a private channel. Online
     * members come first, then everyone by first name, last name and email.
     *
     * @param user           the reader; any ACTIVE member of an ACTIVE organization who can read the channel
     * @param organizationId the request's organization
     * @param search         optional text matched case-insensitively against the first name,
     *                       the last name, the full name and the email; blank lists everyone
     * @param channelId      optional channel whose readers are listed; null lists the organization
     * @param authorsOnly    true to list only the distinct authors of live messages in the channel;
     *                       ignored without a channel
     * @return the members, empty when none match
     */
    List<ChatMemberResponse> listMembers(User user, UUID organizationId, String search, UUID channelId,
                                         boolean authorsOnly);
}
