package com.korur.oba.apps.kaya.reference.service.impl;

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
import com.korur.oba.apps.kaya.reference.constant.service.ReferenceServiceLogConstant;
import com.korur.oba.apps.kaya.reference.dto.embedded.ReferenceIndex;
import com.korur.oba.apps.kaya.reference.entity.Reference;
import com.korur.oba.apps.kaya.reference.repository.ReferenceRepository;
import com.korur.oba.apps.kaya.reference.service.ReferenceService;

/**
 * The {@link ReferenceService} implementation.
 */
@Service
@RequiredArgsConstructor
public class ReferenceServiceImpl implements ReferenceService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ReferenceServiceImpl.class);

    private final ReferenceRepository referenceRepository;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public List<Reference> replaceVersion(UUID organizationId, UUID versionId, List<ReferenceIndex> references) {
        // The old rows are deleted and flushed first, so the new rows never coexist with the ones they
        // replace within the version.
        int removed = referenceRepository.deleteByVersion(organizationId, versionId);
        List<Reference> entities = references.stream()
                .map(index -> toEntity(organizationId, versionId, index))
                .toList();
        List<Reference> saved = referenceRepository.saveAll(entities);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ReferenceServiceLogConstant.REFERENCES_REPLACED,
                versionId, organizationId, removed, saved.size());
        return saved;
    }

    @Override
    @Transactional
    public Reference savePart(UUID organizationId, UUID versionId, ReferenceIndex index) {
        Reference saved = referenceRepository.save(toEntity(organizationId, versionId, index));
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ReferenceServiceLogConstant.REFERENCE_PART_SAVED,
                saved.getId(), versionId, organizationId);
        return saved;
    }

    @Override
    @Transactional
    public int deleteParts(UUID organizationId, UUID versionId, Collection<UUID> partIds) {
        if (partIds.isEmpty()) {
            return 0;
        }
        int removed = referenceRepository.deleteParts(organizationId, versionId, partIds);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ReferenceServiceLogConstant.REFERENCE_PARTS_DELETED,
                removed, versionId, organizationId);
        return removed;
    }

    private Reference toEntity(UUID organizationId, UUID versionId, ReferenceIndex index) {
        return Reference.builder()
                .organizationId(organizationId)
                .versionId(versionId)
                .contentBlobId(index.contentBlobId())
                .name(index.name())
                .mediaType(index.mediaType())
                .build();
    }
}
