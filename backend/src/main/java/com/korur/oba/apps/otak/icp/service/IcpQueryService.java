package com.korur.oba.apps.otak.icp.service;

// 1. Framework-level
import org.springframework.data.domain.Page;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.icp.dto.request.IcpQueryRequest;
import com.korur.oba.apps.otak.icp.dto.response.IcpDetailResponse;
import com.korur.oba.apps.otak.icp.dto.response.IcpListResponse;

/**
 * Read service interface for an organization's ICPs.
 */
public interface IcpQueryService {

    /**
     * Lists one page of the organization's ICPs by name.
     *
     * @param organizationId the organization that owns the ICPs
     * @param request        the paging and the name search
     * @return the page of ICPs
     */
    Page<IcpListResponse> list(UUID organizationId, IcpQueryRequest request);

    /**
     * Reads one ICP of the organization.
     *
     * @param organizationId the organization that owns the ICP
     * @param icpId          the ICP identifier
     * @return the ICP
     */
    IcpDetailResponse get(UUID organizationId, UUID icpId);

    /**
     * Ensures the organization has the ICP, for the relation domain before it links a
     * relation to one. An ICP of another organization is not found.
     *
     * @param organizationId the organization that owns the ICP
     * @param icpId          the ICP identifier
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException OTK_NOT_004
     *         when the organization has no ICP with the id
     */
    void ensureExists(UUID organizationId, UUID icpId);
}
