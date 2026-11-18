package com.korur.oba.shrine.identity.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserStatus;

public interface UserService {

    /**
     * Activates a user after onboarding completion.
     * Transitions status from PENDING_ONBOARDING to ACTIVE.
     */
    User activateUser(UUID userId);

    /**
     * Sets the default organization for a user.
     */
    void setDefaultOrganization(UUID userId, UUID organizationId);

    /**
     * Prepares a user for the deletion of one of their organizations.
     * When that organization is the user's default, the default moves to another organization
     * with an active membership, or to none. A user left without any other membership goes
     * back to {@link UserStatus#PENDING_ONBOARDING}.
     * The change is made on the managed user, so the caller's transaction never flushes a user
     * that still references the organization it deletes.
     */
    void applyOrganizationDeletion(UUID userId, UUID organizationId);
}
