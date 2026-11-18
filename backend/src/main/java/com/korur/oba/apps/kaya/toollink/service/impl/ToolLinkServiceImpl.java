package com.korur.oba.apps.kaya.toollink.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.toollink.constant.service.ToolLinkServiceLogConstant;
import com.korur.oba.apps.kaya.toollink.entity.ToolLink;
import com.korur.oba.apps.kaya.toollink.enums.ToolLinkName;
import com.korur.oba.apps.kaya.toollink.repository.ToolLinkRepository;
import com.korur.oba.apps.kaya.toollink.service.ToolLinkService;

/**
 * The {@link ToolLinkService} implementation.
 */
@Service
@RequiredArgsConstructor
public class ToolLinkServiceImpl implements ToolLinkService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ToolLinkServiceImpl.class);

    private final ToolLinkRepository toolLinkRepository;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public ToolLink saveLink(UUID organizationId, UUID versionId, ToolLinkName name) {
        // A link has nothing to update, so a repeated save keeps the row and its creation time.
        ToolLink link = toolLinkRepository.findByOrganizationIdAndVersionIdAndName(organizationId, versionId, name)
                .orElseGet(() -> toolLinkRepository.save(toEntity(organizationId, versionId, name)));
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ToolLinkServiceLogConstant.TOOL_LINK_SAVED, name, versionId,
                organizationId);
        return link;
    }

    @Override
    @Transactional
    public int deleteLink(UUID organizationId, UUID versionId, ToolLinkName name) {
        int removed = toolLinkRepository.deleteByName(organizationId, versionId, name);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ToolLinkServiceLogConstant.TOOL_LINK_DELETED, name, versionId,
                organizationId, removed);
        return removed;
    }

    @Override
    @Transactional
    public List<ToolLink> replaceVersion(UUID organizationId, UUID versionId, Collection<ToolLinkName> names) {
        // The old rows are deleted and flushed first, so the new rows do not collide with them on the
        // one-link-per-name unique key.
        int removed = toolLinkRepository.deleteByVersion(organizationId, versionId);
        EnumSet<ToolLinkName> distinct = names.isEmpty() ? EnumSet.noneOf(ToolLinkName.class) : EnumSet.copyOf(names);
        List<ToolLink> saved = toolLinkRepository.saveAll(distinct.stream()
                .map(name -> toEntity(organizationId, versionId, name))
                .toList());
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ToolLinkServiceLogConstant.TOOL_LINKS_REPLACED, versionId,
                organizationId, removed, saved.size());
        return saved;
    }

    private static ToolLink toEntity(UUID organizationId, UUID versionId, ToolLinkName name) {
        return ToolLink.builder()
                .organizationId(organizationId)
                .versionId(versionId)
                .name(name)
                .build();
    }
}
