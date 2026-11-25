package com.korur.oba.shrine.invitation.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.invitation.entity.Invitation;
import com.korur.oba.shrine.invitation.enums.InvitationStatus;

/**
 * Repository for Invitation entity database operations.
 *
 * An invitation keeps its row when its organization is soft-deleted, but Hibernate hides the deleted
 * organization, so loading such an invitation with its organization fails. The lookups by id and by
 * email therefore leave out invitations of deleted organizations, which no longer exist for anyone.
 * The lookups by organization serve callers that have already checked their access to that
 * organization, and so that it is not deleted.
 */
@Repository
public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    /**
     * Finds all invitations for an organization with the specified status.
     */
    List<Invitation> findByOrganizationIdAndStatus(UUID organizationId, InvitationStatus status);

    /**
     * Finds an invitation by email, organization, and status.
     * Used to detect duplicate pending invitations.
     */
    Optional<Invitation> findByEmailIgnoreCaseAndOrganizationIdAndStatus(
            String email, UUID organizationId, InvitationStatus status);

    /**
     * Finds the invitations of an email with the given status whose organization is live, with the
     * organization loaded in the same query. Used to list pending invitations for the user decision
     * flow, where an invitation of a deleted organization must not appear.
     *
     * @param email  the invited email, compared without regard to case
     * @param status the status the invitations must have
     * @return the matching invitations of live organizations
     */
    @Query("""
            SELECT i FROM Invitation i
            JOIN FETCH i.organization o
            WHERE LOWER(i.email) = LOWER(:email)
              AND i.status = :status
              AND o.deleted = false
            """)
    List<Invitation> findByEmailAndStatusInLiveOrganization(@Param("email") String email,
                                                            @Param("status") InvitationStatus status);

    /**
     * Finds an invitation by id when its organization is live, with the organization loaded in the
     * same query. An invitation of a deleted organization is not found, so accepting, declining or
     * revoking it answers that the invitation does not exist.
     *
     * @param id the invitation identifier
     * @return the invitation, or empty when it does not exist or its organization is deleted
     */
    @Query("""
            SELECT i FROM Invitation i
            JOIN FETCH i.organization o
            WHERE i.id = :id
              AND o.deleted = false
            """)
    Optional<Invitation> findByIdInLiveOrganization(@Param("id") UUID id);

    /**
     * Revokes every pending invitation of an organization in one statement. The rows are updated
     * without being loaded, so an invitation whose inviter or organization can no longer be loaded
     * cannot stop the revocation. The pending changes of the caller's transaction are flushed first,
     * so the statement runs against the state the caller has built.
     *
     * @param organizationId the organization whose invitations are revoked
     * @param revokedAt      the moment of revocation, stored as the update time
     * @return the number of invitations revoked
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE shrine.user_invitations
            SET status = 'REVOKED'::shrine_identity_user_invitation_status_enum, updated_at = :revokedAt
            WHERE organization_id = :organizationId
              AND status = 'PENDING'::shrine_identity_user_invitation_status_enum
            """, nativeQuery = true)
    int revokePendingByOrganizationId(@Param("organizationId") UUID organizationId,
                                      @Param("revokedAt") Instant revokedAt);
}
