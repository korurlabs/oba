package com.korur.oba.shrine.organization.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.dto.request.OrganizationCreateRequest;
import com.korur.oba.shrine.organization.dto.request.OrganizationUpdateRequest;
import com.korur.oba.shrine.organization.dto.response.OrganizationDetailResponse;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;

/**
 * Write operations for organization lifecycle management.
 */
public interface OrganizationService {

    /**
     * Creates a new organization and owner membership for the current user.
     */
    OrganizationDetailResponse createOrganization(User user, OrganizationCreateRequest request);

    /**
     * Updates organization metadata.
     */
    OrganizationDetailResponse updateOrganization(User user, UUID organizationId, OrganizationUpdateRequest request);

    /**
     * Updates an organization's lifecycle status (enable/disable).
     * DISABLED: suspends the organization.
     * ACTIVE: reactivates a disabled organization.
     * Requires OWNER role.
     */
    void updateOrganizationStatus(User user, UUID organizationId, OrganizationStatus newStatus);

    /**
     * Permanently deletes an organization by anonymizing data and soft-deleting.
     * Anonymizes name and clears description/website, sets DISABLED status,
     * then soft-deletes. JPA cascades soft-delete to members. Requires OWNER role in an
     * enabled organization, which callers check through RequiredUserAccess.
     */
    void deleteOrganization(User user, UUID organizationId);
}
