package com.korur.oba.apps.kaya.subagent.service.impl;

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
import com.korur.oba.apps.kaya.subagent.constant.service.SubagentServiceLogConstant;
import com.korur.oba.apps.kaya.subagent.dto.embedded.SubagentIndex;
import com.korur.oba.apps.kaya.subagent.entity.Subagent;
import com.korur.oba.apps.kaya.subagent.repository.SubagentRepository;
import com.korur.oba.apps.kaya.subagent.service.SubagentService;

/**
 * The {@link SubagentService} implementation.
 */
@Service
@RequiredArgsConstructor
public class SubagentServiceImpl implements SubagentService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(SubagentServiceImpl.class);

    private final SubagentRepository subagentRepository;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public List<Subagent> replaceVersion(UUID organizationId, UUID versionId, List<SubagentIndex> subagents) {
        // The old rows are deleted and flushed first, so a subagent saved again at the same path does
        // not collide with the old row on the version and path, or version and name, unique index.
        int removed = subagentRepository.deleteByVersion(organizationId, versionId);
        List<Subagent> entities = subagents.stream()
                .map(index -> toEntity(organizationId, versionId, index))
                .toList();
        List<Subagent> saved = subagentRepository.saveAll(entities);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, SubagentServiceLogConstant.SUBAGENTS_REPLACED,
                versionId, organizationId, removed, saved.size());
        return saved;
    }

    @Override
    @Transactional
    public Subagent saveFile(UUID organizationId, UUID versionId, SubagentIndex index) {
        // The old row is deleted and flushed first, so the new row does not collide with it on the
        // version and path unique key.
        int removed = subagentRepository.deleteByPath(organizationId, versionId, index.relativePath());
        Subagent saved = subagentRepository.save(toEntity(organizationId, versionId, index));
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, SubagentServiceLogConstant.SUBAGENT_SAVED,
                index.relativePath(), versionId, organizationId, removed);
        return saved;
    }

    @Override
    @Transactional
    public boolean deleteFile(UUID organizationId, UUID versionId, String relativePath) {
        int removed = subagentRepository.deleteByPath(organizationId, versionId, relativePath);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, SubagentServiceLogConstant.SUBAGENT_DELETED,
                relativePath, versionId, organizationId, removed);
        return removed > 0;
    }

    private Subagent toEntity(UUID organizationId, UUID versionId, SubagentIndex index) {
        return Subagent.builder()
                .organizationId(organizationId)
                .versionId(versionId)
                .contentBlobId(index.contentBlobId())
                .relativePath(index.relativePath())
                .name(index.name())
                .description(index.description())
                .model(index.model())
                .maxTurns(index.maxTurns())
                .tools(index.tools())
                .build();
    }
}
