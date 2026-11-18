package com.korur.oba.apps.otak.battlefield.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import static com.korur.oba.apps.otak.battlefield.constant.service.BattlefieldServiceLogConstant.*;

import com.korur.oba.apps.otak.battlefield.service.BattlefieldQueryService;
import com.korur.oba.apps.otak.relation.dto.response.RelationListResponse;
import com.korur.oba.apps.otak.relation.service.RelationQueryService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Implementation of battlefield read operations. The battlefield owns no data: the leads
 * and customers come from the relation domain through {@link RelationQueryService}.
 *
 * All methods run within a read-only transaction.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BattlefieldQueryServiceImpl implements BattlefieldQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(BattlefieldQueryServiceImpl.class);

    private final RelationQueryService relationQueryService;
    private final LoggingService loggingService;

    @Override
    public List<RelationListResponse> getBattlefield(UUID organizationId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, READING_BATTLEFIELD, organizationId);

        return relationQueryService.listPipeline(organizationId);
    }
}
