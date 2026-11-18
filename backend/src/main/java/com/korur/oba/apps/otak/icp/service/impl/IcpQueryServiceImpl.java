package com.korur.oba.apps.otak.icp.service.impl;

// 1. Framework-level
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import static com.korur.oba.apps.otak.icp.constant.service.IcpServiceLogConstant.*;

import com.korur.oba.apps.otak.icp.constant.IcpConstant;
import com.korur.oba.apps.otak.icp.dto.request.IcpQueryRequest;
import com.korur.oba.apps.otak.icp.dto.response.IcpDetailResponse;
import com.korur.oba.apps.otak.icp.dto.response.IcpListResponse;
import com.korur.oba.apps.otak.icp.mapper.IcpMapper;
import com.korur.oba.apps.otak.icp.repository.IcpRepository;
import com.korur.oba.apps.otak.icp.service.IcpQueryService;
import com.korur.oba.apps.otak.icp.specification.IcpSpecification;
import com.korur.oba.apps.otak.icp.validator.IcpValidator;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Implementation of ICP read operations.
 *
 * All methods run within a read-only transaction.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IcpQueryServiceImpl implements IcpQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(IcpQueryServiceImpl.class);

    // A fixed order, with the id as tie-breaker, keeps pages stable and keeps arbitrary sortBy
    // values from reaching the query.
    private static final Sort BY_NAME = Sort.by(Sort.Direction.ASC, IcpConstant.FIELD_NAME)
            .and(Sort.by(Sort.Direction.ASC, IcpConstant.FIELD_ID));

    private final IcpRepository icpRepository;
    private final IcpMapper icpMapper;
    private final IcpValidator icpValidator;
    private final LoggingService loggingService;

    @Override
    public Page<IcpListResponse> list(UUID organizationId, IcpQueryRequest request) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, LISTING_ICPS, organizationId, request);

        Pageable pageable = PageRequest.of(request.getPage() - 1, request.getAmount(), BY_NAME);
        return icpRepository.findAll(IcpSpecification.filterBy(organizationId, request), pageable)
                .map(icpMapper::toListResponse);
    }

    @Override
    public IcpDetailResponse get(UUID organizationId, UUID icpId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, READING_ICP, icpId, organizationId);

        return icpMapper.toDetailResponse(icpValidator.ensureIcpExists(organizationId, icpId));
    }

    @Override
    public void ensureExists(UUID organizationId, UUID icpId) {
        icpValidator.ensureIcpExists(organizationId, icpId);
    }
}
