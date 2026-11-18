package com.korur.oba.apps.kaya.toolparameter.service.impl;

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
import com.korur.oba.apps.kaya.toolparameter.constant.service.ToolParameterServiceLogConstant;
import com.korur.oba.apps.kaya.toolparameter.dto.embedded.ToolParameterIndex;
import com.korur.oba.apps.kaya.toolparameter.entity.ToolParameter;
import com.korur.oba.apps.kaya.toolparameter.repository.ToolParameterRepository;
import com.korur.oba.apps.kaya.toolparameter.service.ToolParameterService;

/**
 * The {@link ToolParameterService} implementation.
 */
@Service
@RequiredArgsConstructor
public class ToolParameterServiceImpl implements ToolParameterService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ToolParameterServiceImpl.class);

    private final ToolParameterRepository toolParameterRepository;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public List<ToolParameter> replaceVersion(UUID organizationId, UUID versionId, List<ToolParameterIndex> toolParameters) {
        // The old rows are deleted and flushed first, so the new rows never coexist with the ones they
        // replace within the version.
        int removed = toolParameterRepository.deleteByVersion(organizationId, versionId);
        List<ToolParameter> entities = toolParameters.stream()
                .map(index -> toEntity(organizationId, versionId, index))
                .toList();
        List<ToolParameter> saved = toolParameterRepository.saveAll(entities);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ToolParameterServiceLogConstant.TOOL_PARAMETERS_REPLACED,
                versionId, organizationId, removed, saved.size());
        return saved;
    }

    @Override
    @Transactional
    public ToolParameter savePart(UUID organizationId, UUID versionId, ToolParameterIndex index) {
        ToolParameter saved = toolParameterRepository.save(toEntity(organizationId, versionId, index));
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ToolParameterServiceLogConstant.TOOL_PARAMETER_PART_SAVED,
                saved.getId(), versionId, organizationId);
        return saved;
    }

    @Override
    @Transactional
    public int deleteParts(UUID organizationId, UUID versionId, Collection<UUID> partIds) {
        if (partIds.isEmpty()) {
            return 0;
        }
        int removed = toolParameterRepository.deleteParts(organizationId, versionId, partIds);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ToolParameterServiceLogConstant.TOOL_PARAMETER_PARTS_DELETED,
                removed, versionId, organizationId);
        return removed;
    }

    private ToolParameter toEntity(UUID organizationId, UUID versionId, ToolParameterIndex index) {
        return ToolParameter.builder()
                .organizationId(organizationId)
                .versionId(versionId)
                .contentBlobId(index.contentBlobId())
                .name(index.name())
                .format(index.format())
                .build();
    }
}
