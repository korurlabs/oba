package com.korur.oba.shrine.invitation.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.invitation.dto.response.InvitationResponse;
import com.korur.oba.shrine.invitation.entity.Invitation;

/**
 * Read operations for invitations.
 */
public interface InvitationQueryService {

    /**
     * Lists all pending invitations for an organization. Callers have already checked that the
     * user is at least ADMIN of the organization, through
     * {@link com.korur.oba.dojo.security.annotation.RequiredUserAccess}.
     */
    List<InvitationResponse> listPendingInvitations(User user, UUID organizationId);

    /**
     * Lists pending invitations for a user by email, filtering out expired ones and those of deleted
     * organizations. Used by GET /users/me to include pending invitations in the response.
     */
    List<InvitationResponse> listPendingInvitationsByEmail(String email);

    /**
     * Finds all pending invitations by email (raw entities), leaving out those of deleted organizations.
     */
    List<Invitation> findPendingByEmail(String email);
}
