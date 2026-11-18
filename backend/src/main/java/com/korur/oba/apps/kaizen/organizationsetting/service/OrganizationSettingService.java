package com.korur.oba.apps.kaizen.organizationsetting.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.organizationsetting.dto.response.OrganizationSettingResponse;

/**
 * Write service for the Kaizen settings of an organization.
 */
public interface OrganizationSettingService {

    /**
     * Sets or clears the security board of an organization.
     *
     * @param organizationId the organization whose security board changes
     * @param boardId        the board to designate, or null to clear the security board
     * @param userId         the authenticated user, an ADMIN of the organization as checked
     *                       before the request reaches this service, who must be a member of the board
     * @return the organization's settings after the change
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         when the organization or the board does not exist, or the user is not a member of the board
     */
    OrganizationSettingResponse updateSecurityBoard(UUID organizationId, UUID boardId, UUID userId);
}
