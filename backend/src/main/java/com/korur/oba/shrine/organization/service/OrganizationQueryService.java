package com.korur.oba.shrine.organization.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.dto.response.OrganizationDetailResponse;
import com.korur.oba.shrine.organization.dto.response.OrganizationListResponse;
import com.korur.oba.shrine.organization.entity.Organization;

/**
 * Read operations for organizations.
 */
public interface OrganizationQueryService {

    /**
     * Retrieves organization details by id.
     */
    OrganizationDetailResponse getOrganization(UUID organizationId);

    /**
     * Lists organizations for the current user.
     */
    List<OrganizationListResponse> listOrganizations(User user);

    /**
     * Retrieves the Organization entity by ID.
     * Used by other domains that need the entity for relational mappings.
     *
     * @param organizationId the organization ID
     * @return the Organization entity
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException if organization not found
     */
    Organization getOrganizationEntityById(UUID organizationId);

    /**
     * Tells whether the organization is ACTIVE, for a caller that refuses a request in an organization
     * that is disabled or deleted instead of failing on it.
     *
     * @param organizationId the organization ID
     * @return true when the organization exists, is not deleted and is ACTIVE
     */
    boolean isOrganizationActive(UUID organizationId);

    /**
     * Retrieves all organization IDs.
     * Used by scheduled tasks that need to iterate over all organizations.
     *
     * @return list of all organization UUIDs
     */
    List<UUID> getAllOrganizationIds();
}
