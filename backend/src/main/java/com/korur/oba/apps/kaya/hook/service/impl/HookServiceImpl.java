package com.korur.oba.apps.kaya.hook.service.impl;

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
import com.korur.oba.apps.kaya.hook.constant.service.HookServiceLogConstant;
import com.korur.oba.apps.kaya.hook.dto.embedded.HookIndex;
import com.korur.oba.apps.kaya.hook.entity.Hook;
import com.korur.oba.apps.kaya.hook.repository.HookRepository;
import com.korur.oba.apps.kaya.hook.service.HookService;

/**
 * The {@link HookService} implementation.
 */
@Service
@RequiredArgsConstructor
public class HookServiceImpl implements HookService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(HookServiceImpl.class);

    private final HookRepository hookRepository;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public List<Hook> replaceVersion(UUID organizationId, UUID versionId, List<HookIndex> hooks) {
        // The old rows are deleted and flushed first, so a hook saved again at the same path does not
        // collide with the old row on the version and path unique index.
        int removed = hookRepository.deleteByVersion(organizationId, versionId);
        List<Hook> entities = hooks.stream()
                .map(index -> toEntity(organizationId, versionId, index))
                .toList();
        List<Hook> saved = hookRepository.saveAll(entities);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, HookServiceLogConstant.HOOKS_REPLACED,
                versionId, organizationId, removed, saved.size());
        return saved;
    }

    @Override
    @Transactional
    public Hook saveFile(UUID organizationId, UUID versionId, HookIndex index) {
        // The old row is deleted and flushed first, so the new row does not collide with it on the
        // version and path unique key.
        int removed = hookRepository.deleteByPath(organizationId, versionId, index.relativePath());
        Hook saved = hookRepository.save(toEntity(organizationId, versionId, index));
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, HookServiceLogConstant.HOOK_SAVED,
                index.relativePath(), versionId, organizationId, removed);
        return saved;
    }

    @Override
    @Transactional
    public boolean deleteFile(UUID organizationId, UUID versionId, String relativePath) {
        int removed = hookRepository.deleteByPath(organizationId, versionId, relativePath);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, HookServiceLogConstant.HOOK_DELETED,
                relativePath, versionId, organizationId, removed);
        return removed > 0;
    }

    private Hook toEntity(UUID organizationId, UUID versionId, HookIndex index) {
        return Hook.builder()
                .organizationId(organizationId)
                .versionId(versionId)
                .contentBlobId(index.contentBlobId())
                .relativePath(index.relativePath())
                .executable(Boolean.TRUE.equals(index.executable()))
                .build();
    }
}
