package com.korur.oba.apps.kaya.script.service.impl;

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
import com.korur.oba.apps.kaya.script.constant.service.ScriptServiceLogConstant;
import com.korur.oba.apps.kaya.script.dto.embedded.ScriptIndex;
import com.korur.oba.apps.kaya.script.entity.Script;
import com.korur.oba.apps.kaya.script.repository.ScriptRepository;
import com.korur.oba.apps.kaya.script.service.ScriptService;

/**
 * The {@link ScriptService} implementation.
 */
@Service
@RequiredArgsConstructor
public class ScriptServiceImpl implements ScriptService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ScriptServiceImpl.class);

    private final ScriptRepository scriptRepository;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public List<Script> replaceVersion(UUID organizationId, UUID versionId, List<ScriptIndex> scripts) {
        // The old rows are deleted and flushed first, so the new rows never coexist with the ones they
        // replace within the version.
        int removed = scriptRepository.deleteByVersion(organizationId, versionId);
        List<Script> entities = scripts.stream()
                .map(index -> toEntity(organizationId, versionId, index))
                .toList();
        List<Script> saved = scriptRepository.saveAll(entities);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ScriptServiceLogConstant.SCRIPTS_REPLACED,
                versionId, organizationId, removed, saved.size());
        return saved;
    }

    @Override
    @Transactional
    public Script savePart(UUID organizationId, UUID versionId, ScriptIndex index) {
        Script saved = scriptRepository.save(toEntity(organizationId, versionId, index));
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ScriptServiceLogConstant.SCRIPT_PART_SAVED,
                saved.getId(), versionId, organizationId);
        return saved;
    }

    @Override
    @Transactional
    public int deleteParts(UUID organizationId, UUID versionId, Collection<UUID> partIds) {
        if (partIds.isEmpty()) {
            return 0;
        }
        int removed = scriptRepository.deleteParts(organizationId, versionId, partIds);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ScriptServiceLogConstant.SCRIPT_PARTS_DELETED,
                removed, versionId, organizationId);
        return removed;
    }

    private Script toEntity(UUID organizationId, UUID versionId, ScriptIndex index) {
        return Script.builder()
                .organizationId(organizationId)
                .versionId(versionId)
                .contentBlobId(index.contentBlobId())
                .name(index.name())
                .executable(index.executable())
                .build();
    }
}
