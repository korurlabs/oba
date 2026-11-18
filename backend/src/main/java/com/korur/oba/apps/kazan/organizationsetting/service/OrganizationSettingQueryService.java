package com.korur.oba.apps.kazan.organizationsetting.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal

/**
 * Read service for the Kazan settings of an organization.
 */
public interface OrganizationSettingQueryService {

    /**
     * Returns the security board of an organization. Performs no access check; callers
     * that serve a user check the organization access themselves.
     *
     * @param organizationId the organization identifier
     * @return the security board id, or empty when the organization has none
     */
    Optional<UUID> getSecurityBoardId(UUID organizationId);
}
