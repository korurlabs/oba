package com.korur.oba.apps.otak.icp.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.icp.dto.request.IcpCreateRequest;
import com.korur.oba.apps.otak.icp.dto.request.IcpUpdateRequest;
import com.korur.oba.apps.otak.icp.dto.response.IcpDetailResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Write service interface for an organization's ICPs. The caller's role in the
 * organization is checked before these methods run; every method works within that
 * organization, so an ICP of another organization is a missing one.
 */
public interface IcpService {

    /**
     * Creates an ICP.
     *
     * @param organizationId the organization that owns the ICP
     * @param user           the authenticated user
     * @param request        the ICP payload
     * @return the created ICP
     */
    IcpDetailResponse create(UUID organizationId, User user, IcpCreateRequest request);

    /**
     * Replaces every field of an ICP; null clears an optional field.
     *
     * @param organizationId the organization that owns the ICP
     * @param icpId          the ICP identifier
     * @param request        the ICP payload
     * @return the updated ICP
     */
    IcpDetailResponse update(UUID organizationId, UUID icpId, IcpUpdateRequest request);

    /**
     * Deletes an ICP; the database clears the link of every relation that pointed to it
     * and keeps the relations.
     *
     * @param organizationId the organization that owns the ICP
     * @param icpId          the ICP identifier
     */
    void delete(UUID organizationId, UUID icpId);
}
