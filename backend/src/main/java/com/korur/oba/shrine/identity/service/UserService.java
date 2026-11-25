package com.korur.oba.shrine.identity.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.entity.embedded.UserAgentDetail;
import com.korur.oba.shrine.identity.enums.AgentRole;
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

    /**
     * Creates the ACTIVE user of an agent: kind AGENT, no Keycloak account, the agent's organization as
     * its default organization, and the name as its first name. The row is flushed, so a name another
     * live agent of the organization holds fails here on the unique index.
     *
     * @param email       a unique address no person can own, under which the agent is stored
     * @param name        the agent name
     * @param agentDetail role, flow, provider, organization and creator of the agent
     * @return the saved agent user
     * @throws org.springframework.dao.DataIntegrityViolationException when the name is taken
     */
    User createAgentUser(String email, String name, UserAgentDetail agentDetail);

    /**
     * Changes the name, role or status of an agent user; a null argument leaves that value unchanged.
     * Flow, provider and organization never change. The row is flushed, so a taken name fails here.
     *
     * @param agent  the agent user
     * @param name   the new name, or null
     * @param role   the new role, or null
     * @param status the new status, ACTIVE or DISABLED, or null
     * @return the saved agent user
     * @throws org.springframework.dao.DataIntegrityViolationException when the name is taken
     */
    User updateAgentUser(User agent, String name, AgentRole role, UserStatus status);

    /**
     * Soft-deletes an agent user, and with it its memberships.
     *
     * @param agent the agent user
     */
    void deleteAgentUser(User agent);
}
