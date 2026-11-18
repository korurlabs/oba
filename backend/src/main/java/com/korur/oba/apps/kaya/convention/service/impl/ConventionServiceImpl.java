package com.korur.oba.apps.kaya.convention.service.impl;

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
import com.korur.oba.apps.kaya.convention.constant.service.ConventionServiceLogConstant;
import com.korur.oba.apps.kaya.convention.dto.embedded.ConventionIndex;
import com.korur.oba.apps.kaya.convention.entity.Convention;
import com.korur.oba.apps.kaya.convention.repository.ConventionRepository;
import com.korur.oba.apps.kaya.convention.service.ConventionService;

/**
 * The {@link ConventionService} implementation.
 */
@Service
@RequiredArgsConstructor
public class ConventionServiceImpl implements ConventionService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ConventionServiceImpl.class);

    private final ConventionRepository conventionRepository;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public List<Convention> replaceVersion(UUID organizationId, UUID versionId, List<ConventionIndex> conventions) {
        // The old rows are deleted and flushed first, so a convention saved again at the same path does
        // not collide with the old row on the version and path unique index.
        int removed = conventionRepository.deleteByVersion(organizationId, versionId);
        List<Convention> entities = conventions.stream()
                .map(index -> toEntity(organizationId, versionId, index))
                .toList();
        List<Convention> saved = conventionRepository.saveAll(entities);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ConventionServiceLogConstant.CONVENTIONS_REPLACED,
                versionId, organizationId, removed, saved.size());
        return saved;
    }

    @Override
    @Transactional
    public Convention saveFile(UUID organizationId, UUID versionId, ConventionIndex index) {
        // The old row is deleted and flushed first, so the new row does not collide with it on the
        // version and path unique key.
        int removed = conventionRepository.deleteByPath(organizationId, versionId, index.relativePath());
        Convention saved = conventionRepository.save(toEntity(organizationId, versionId, index));
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ConventionServiceLogConstant.CONVENTION_SAVED,
                index.relativePath(), versionId, organizationId, removed);
        return saved;
    }

    @Override
    @Transactional
    public boolean deleteFile(UUID organizationId, UUID versionId, String relativePath) {
        int removed = conventionRepository.deleteByPath(organizationId, versionId, relativePath);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ConventionServiceLogConstant.CONVENTION_DELETED,
                relativePath, versionId, organizationId, removed);
        return removed > 0;
    }

    @Override
    @Transactional
    public Convention savePart(UUID organizationId, UUID versionId, ConventionIndex index) {
        Convention saved = conventionRepository.save(toEntity(organizationId, versionId, index));
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ConventionServiceLogConstant.CONVENTION_PART_SAVED,
                saved.getId(), versionId, organizationId);
        return saved;
    }

    @Override
    @Transactional
    public int deleteParts(UUID organizationId, UUID versionId, Collection<UUID> partIds) {
        if (partIds.isEmpty()) {
            return 0;
        }
        int removed = conventionRepository.deleteParts(organizationId, versionId, partIds);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ConventionServiceLogConstant.CONVENTION_PARTS_DELETED,
                removed, versionId, organizationId);
        return removed;
    }

    private Convention toEntity(UUID organizationId, UUID versionId, ConventionIndex index) {
        return Convention.builder()
                .organizationId(organizationId)
                .versionId(versionId)
                .contentBlobId(index.contentBlobId())
                .relativePath(index.relativePath())
                .title(index.title())
                .overridesPath(index.overridesPath())
                .build();
    }
}
