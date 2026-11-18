package com.korur.oba.apps.kaya.template.service.impl;

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
import com.korur.oba.apps.kaya.template.constant.service.TemplateServiceLogConstant;
import com.korur.oba.apps.kaya.template.dto.embedded.TemplateIndex;
import com.korur.oba.apps.kaya.template.entity.Template;
import com.korur.oba.apps.kaya.template.repository.TemplateRepository;
import com.korur.oba.apps.kaya.template.service.TemplateService;

/**
 * The {@link TemplateService} implementation.
 */
@Service
@RequiredArgsConstructor
public class TemplateServiceImpl implements TemplateService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(TemplateServiceImpl.class);

    private final TemplateRepository templateRepository;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public List<Template> replaceVersion(UUID organizationId, UUID versionId, List<TemplateIndex> templates) {
        // The old rows are deleted and flushed first, so a template saved again at the same path does
        // not collide with the old row on the version and path unique index.
        int removed = templateRepository.deleteByVersion(organizationId, versionId);
        List<Template> entities = templates.stream()
                .map(index -> toEntity(organizationId, versionId, index))
                .toList();
        List<Template> saved = templateRepository.saveAll(entities);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, TemplateServiceLogConstant.TEMPLATES_REPLACED,
                versionId, organizationId, removed, saved.size());
        return saved;
    }

    @Override
    @Transactional
    public Template saveFile(UUID organizationId, UUID versionId, TemplateIndex index) {
        // The old row is deleted and flushed first, so the new row does not collide with it on the
        // version and path unique key.
        int removed = templateRepository.deleteByPath(organizationId, versionId, index.relativePath());
        Template saved = templateRepository.save(toEntity(organizationId, versionId, index));
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, TemplateServiceLogConstant.TEMPLATE_SAVED,
                index.relativePath(), versionId, organizationId, removed);
        return saved;
    }

    @Override
    @Transactional
    public boolean deleteFile(UUID organizationId, UUID versionId, String relativePath) {
        int removed = templateRepository.deleteByPath(organizationId, versionId, relativePath);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, TemplateServiceLogConstant.TEMPLATE_DELETED,
                relativePath, versionId, organizationId, removed);
        return removed > 0;
    }

    private Template toEntity(UUID organizationId, UUID versionId, TemplateIndex index) {
        return Template.builder()
                .organizationId(organizationId)
                .versionId(versionId)
                .contentBlobId(index.contentBlobId())
                .relativePath(index.relativePath())
                .title(index.title())
                .build();
    }
}
