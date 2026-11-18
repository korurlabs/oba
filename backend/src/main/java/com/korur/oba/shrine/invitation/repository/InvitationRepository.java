package com.korur.oba.shrine.invitation.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.invitation.entity.Invitation;
import com.korur.oba.shrine.invitation.enums.InvitationStatus;

/**
 * Repository for Invitation entity database operations.
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
     * Finds all invitations by email and status.
     * Used to list pending invitations for the user decision flow.
     */
    List<Invitation> findByEmailIgnoreCaseAndStatus(String email, InvitationStatus status);
}
