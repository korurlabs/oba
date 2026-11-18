package com.korur.oba.apps.otak.icp.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import static com.korur.oba.apps.otak.icp.constant.service.IcpServiceLogConstant.*;

import com.korur.oba.apps.otak.icp.dto.request.IcpCreateRequest;
import com.korur.oba.apps.otak.icp.dto.request.IcpUpdateRequest;
import com.korur.oba.apps.otak.icp.dto.response.IcpDetailResponse;
import com.korur.oba.apps.otak.icp.entity.Icp;
import com.korur.oba.apps.otak.icp.mapper.IcpMapper;
import com.korur.oba.apps.otak.icp.repository.IcpRepository;
import com.korur.oba.apps.otak.icp.service.IcpService;
import com.korur.oba.apps.otak.icp.validator.IcpValidator;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Implements writes to an organization's ICPs.
 *
 * The organization role is checked once per request by the access interceptor. Every
 * ICP is read and written within the request's organization, so an ICP of another
 * organization is a missing one.
 */
@Service
@RequiredArgsConstructor
public class IcpServiceImpl implements IcpService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(IcpServiceImpl.class);

    private final IcpRepository icpRepository;
    private final IcpMapper icpMapper;
    private final IcpValidator icpValidator;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public IcpDetailResponse create(UUID organizationId, User user, IcpCreateRequest request) {
        String name = request.name().trim();
        icpValidator.ensureSizeRange(request.companySizeMin(), request.companySizeMax());
        icpValidator.ensureNameFree(organizationId, name, null);

        Icp icp = icpMapper.toEntity(request);
        // Stored trimmed, so the stored name is the one the name rule checked.
        icp.setName(name);
        icp.setOrganizationId(organizationId);
        icp = icpRepository.save(icp);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ICP_CREATED, icp.getId(), organizationId, user.getId());

        return icpMapper.toDetailResponse(icp);
    }

    @Override
    @Transactional
    public IcpDetailResponse update(UUID organizationId, UUID icpId, IcpUpdateRequest request) {
        Icp icp = icpValidator.ensureIcpExists(organizationId, icpId);

        String name = request.name().trim();
        icpValidator.ensureSizeRange(request.companySizeMin(), request.companySizeMax());
        icpValidator.ensureNameFree(organizationId, name, icpId);

        icpMapper.updateEntity(icp, request);
        icp.setName(name);
        // Flushed here so the response carries the new last-change time.
        icp = icpRepository.saveAndFlush(icp);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ICP_UPDATED, icpId, organizationId);

        return icpMapper.toDetailResponse(icp);
    }

    @Override
    @Transactional
    public void delete(UUID organizationId, UUID icpId) {
        Icp icp = icpValidator.ensureIcpExists(organizationId, icpId);

        icpRepository.delete(icp);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ICP_DELETED, icpId, organizationId);
    }
}
