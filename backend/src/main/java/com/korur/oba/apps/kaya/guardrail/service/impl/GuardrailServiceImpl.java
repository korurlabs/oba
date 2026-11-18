package com.korur.oba.apps.kaya.guardrail.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.guardrail.constant.service.GuardrailServiceLogConstant;
import com.korur.oba.apps.kaya.guardrail.dto.embedded.GuardrailIndex;
import com.korur.oba.apps.kaya.guardrail.entity.Guardrail;
import com.korur.oba.apps.kaya.guardrail.repository.GuardrailRepository;
import com.korur.oba.apps.kaya.guardrail.service.GuardrailService;

/**
 * The {@link GuardrailService} implementation.
 */
@Service
@RequiredArgsConstructor
public class GuardrailServiceImpl implements GuardrailService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(GuardrailServiceImpl.class);

    private final GuardrailRepository guardrailRepository;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public List<Guardrail> replaceVersion(UUID organizationId, UUID versionId, List<GuardrailIndex> guardrails) {
        // The old rows are deleted and flushed first, so a guardrail saved again under the same name does
        // not collide with the old row on the version and name unique key.
        int removed = guardrailRepository.deleteByVersion(organizationId, versionId);
        List<Guardrail> entities = guardrails.stream()
                .map(index -> toEntity(organizationId, versionId, index))
                .toList();
        List<Guardrail> saved = guardrailRepository.saveAll(entities);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, GuardrailServiceLogConstant.GUARDRAILS_REPLACED,
                versionId, organizationId, removed, saved.size());
        return saved;
    }

    @Override
    @Transactional
    public Guardrail saveFile(UUID organizationId, UUID versionId, GuardrailIndex index) {
        // The old row is deleted and flushed first, so the new row does not collide with it on the
        // version and name unique key.
        int removed = guardrailRepository.deleteByName(organizationId, versionId, index.name());
        Guardrail saved = guardrailRepository.save(toEntity(organizationId, versionId, index));
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, GuardrailServiceLogConstant.GUARDRAIL_SAVED,
                index.name(), versionId, organizationId, removed);
        return saved;
    }

    @Override
    @Transactional
    public boolean deleteFile(UUID organizationId, UUID versionId, String name) {
        int removed = guardrailRepository.deleteByName(organizationId, versionId, name);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, GuardrailServiceLogConstant.GUARDRAIL_DELETED,
                name, versionId, organizationId, removed);
        return removed > 0;
    }

    private Guardrail toEntity(UUID organizationId, UUID versionId, GuardrailIndex index) {
        return Guardrail.builder()
                .organizationId(organizationId)
                .versionId(versionId)
                .contentBlobId(index.contentBlobId())
                .name(index.name())
                .category(index.category())
                .description(index.description())
                .deny(index.deny() == null ? new ArrayList<>() : new ArrayList<>(index.deny()))
                .build();
    }
}
