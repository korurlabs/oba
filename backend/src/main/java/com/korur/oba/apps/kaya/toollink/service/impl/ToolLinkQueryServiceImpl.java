package com.korur.oba.apps.kaya.toollink.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.toollink.constant.service.ToolLinkServiceLogConstant;
import com.korur.oba.apps.kaya.toollink.entity.ToolLink;
import com.korur.oba.apps.kaya.toollink.repository.ToolLinkRepository;
import com.korur.oba.apps.kaya.toollink.service.ToolLinkQueryService;

/**
 * The {@link ToolLinkQueryService} implementation.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ToolLinkQueryServiceImpl implements ToolLinkQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ToolLinkQueryServiceImpl.class);

    private final ToolLinkRepository toolLinkRepository;
    private final LoggingService loggingService;

    @Override
    public List<ToolLink> getEntitiesByVersionId(UUID organizationId, UUID versionId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, ToolLinkServiceLogConstant.LOADING_VERSION_TOOL_LINKS, versionId);
        return toolLinkRepository.findByOrganizationIdAndVersionIdOrderByNameAsc(organizationId, versionId);
    }

    @Override
    public List<ToolLink> getEntitiesByVersionIds(UUID organizationId, Collection<UUID> versionIds) {
        if (versionIds.isEmpty()) {
            return List.of();
        }
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, ToolLinkServiceLogConstant.LOADING_VERSIONS_TOOL_LINKS,
                versionIds.size(), organizationId);
        return toolLinkRepository.findByOrganizationIdAndVersionIdIn(organizationId, versionIds);
    }
}
