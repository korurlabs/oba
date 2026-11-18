package com.korur.oba.apps.kaya.agentsfile.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.agentsfile.constant.service.AgentsFileServiceLogConstant;
import com.korur.oba.apps.kaya.agentsfile.dto.embedded.AgentsFileIndex;
import com.korur.oba.apps.kaya.agentsfile.entity.AgentsFile;
import com.korur.oba.apps.kaya.agentsfile.enums.AgentsFilePlacement;
import com.korur.oba.apps.kaya.agentsfile.repository.AgentsFileRepository;
import com.korur.oba.apps.kaya.agentsfile.service.AgentsFileService;

/**
 * The {@link AgentsFileService} implementation.
 */
@Service
@RequiredArgsConstructor
public class AgentsFileServiceImpl implements AgentsFileService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AgentsFileServiceImpl.class);

    private final AgentsFileRepository agentsFileRepository;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public List<AgentsFile> replaceVersion(UUID organizationId, UUID versionId, List<AgentsFileIndex> agentsFiles) {
        // The old rows are deleted and flushed first, so an agents file saved again at the same placement
        // does not collide with the old row on the version and placement unique index.
        int removed = agentsFileRepository.deleteByVersion(organizationId, versionId);
        List<AgentsFile> entities = agentsFiles.stream()
                .map(index -> toEntity(organizationId, versionId, index))
                .toList();
        List<AgentsFile> saved = agentsFileRepository.saveAll(entities);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, AgentsFileServiceLogConstant.AGENTS_FILES_REPLACED,
                versionId, organizationId, removed, saved.size());
        return saved;
    }

    @Override
    @Transactional
    public AgentsFile saveFile(UUID organizationId, UUID versionId, AgentsFileIndex index) {
        // The old row is deleted and flushed first, so the new row does not collide with it on the
        // version and placement unique key.
        int removed = agentsFileRepository.deleteByPlacement(organizationId, versionId, index.placement());
        AgentsFile saved = agentsFileRepository.save(toEntity(organizationId, versionId, index));
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, AgentsFileServiceLogConstant.AGENTS_FILE_SAVED,
                index.placement(), versionId, organizationId, removed);
        return saved;
    }

    @Override
    @Transactional
    public boolean deleteFile(UUID organizationId, UUID versionId, AgentsFilePlacement placement) {
        int removed = agentsFileRepository.deleteByPlacement(organizationId, versionId, placement);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, AgentsFileServiceLogConstant.AGENTS_FILE_DELETED,
                placement, versionId, organizationId, removed);
        return removed > 0;
    }

    private AgentsFile toEntity(UUID organizationId, UUID versionId, AgentsFileIndex index) {
        return AgentsFile.builder()
                .organizationId(organizationId)
                .versionId(versionId)
                .contentBlobId(index.contentBlobId())
                .placement(index.placement())
                .build();
    }
}
