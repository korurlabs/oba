package com.korur.oba.shrine.invitation.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.invitation.dto.request.InvitationCreateRequest;
import com.korur.oba.shrine.invitation.dto.request.InvitationDecisionRequest;
import com.korur.oba.shrine.invitation.dto.response.InvitationResponse;

/**
 * Write operations for managing organization invitations.
 */
public interface InvitationService {

    /**
     * Creates a PENDING invitation to join an organization.
     * Always creates as PENDING regardless of whether the user exists.
     * The invited user must accept or decline via processDecision.
     */
    InvitationResponse createInvitation(User user, UUID organizationId, InvitationCreateRequest request);

    /**
     * Revokes a pending invitation.
     */
    void revokeInvitation(User user, UUID organizationId, UUID invitationId);

    /**
     * Processes an invitation decision (accept or decline) by the invited user.
     * An invitation of a deleted organization is treated as not found.
     */
    InvitationResponse processDecision(User user, InvitationDecisionRequest request);

    /**
     * Revokes every pending invitation of an organization that is being deleted. A deleted
     * organization can no longer be joined, so no invitation to it may stay open for a decision.
     * Runs in the caller's transaction, so the invitations are revoked only if the deletion commits.
     */
    void revokePendingInvitationsOfOrganization(UUID organizationId);
}
