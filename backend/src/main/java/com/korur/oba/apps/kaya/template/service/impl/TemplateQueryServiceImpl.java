package com.korur.oba.apps.kaya.template.service.impl;

// 1. Framework-level
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.blob.entity.Blob;
import com.korur.oba.apps.kaya.blob.service.BlobQueryService;
import com.korur.oba.apps.kaya.template.constant.TemplateConstant;
import com.korur.oba.apps.kaya.template.constant.service.TemplateServiceLogConstant;
import com.korur.oba.apps.kaya.template.dto.request.TemplateQueryRequest;
import com.korur.oba.apps.kaya.template.dto.response.TemplateDetailResponse;
import com.korur.oba.apps.kaya.template.dto.response.TemplateListResponse;
import com.korur.oba.apps.kaya.template.entity.Template;
import com.korur.oba.apps.kaya.template.mapper.TemplateMapper;
import com.korur.oba.apps.kaya.template.repository.TemplateRepository;
import com.korur.oba.apps.kaya.template.service.TemplateQueryService;
import com.korur.oba.apps.kaya.template.specification.TemplateSpecification;

/**
 * The {@link TemplateQueryService} implementation.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TemplateQueryServiceImpl implements TemplateQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(TemplateQueryServiceImpl.class);

    // Version first, so the templates of one version stay together when a page lists several.
    private static final Sort LIST_SORT = Sort.by(TemplateConstant.FIELD_VERSION_ID,
            TemplateConstant.FIELD_RELATIVE_PATH, TemplateConstant.FIELD_ID);

    private final TemplateRepository templateRepository;
    private final BlobQueryService blobQueryService;
    private final TemplateMapper templateMapper;
    private final LoggingService loggingService;

    @Override
    public List<TemplateListResponse> getTemplates(UUID organizationId, TemplateQueryRequest request) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, TemplateServiceLogConstant.LISTING_TEMPLATES,
                organizationId, request);
        List<Template> templates = templateRepository.findAll(
                TemplateSpecification.filterBy(organizationId, request), LIST_SORT);
        if (templates.isEmpty()) {
            return List.of();
        }

        // The sizes are read in one query, without loading any content.
        Map<UUID, Integer> sizes = blobQueryService.getSizesByIds(organizationId,
                templates.stream().map(Template::getContentBlobId).collect(Collectors.toSet()));
        return templates.stream()
                .map(template -> templateMapper.toListResponse(template, sizes.get(template.getContentBlobId())))
                .toList();
    }

    @Override
    public TemplateDetailResponse getTemplate(UUID organizationId, UUID templateId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, TemplateServiceLogConstant.READING_TEMPLATE,
                templateId, organizationId);
        Template template = getEntityById(organizationId, templateId);
        Blob blob = blobQueryService.getEntityById(organizationId, template.getContentBlobId());
        return templateMapper.toDetailResponse(template, blob.getSize(),
                new String(blob.getContent(), StandardCharsets.UTF_8));
    }

    @Override
    public Template getEntityById(UUID organizationId, UUID templateId) {
        // Looked up within the organization, so a template of another organization answers the same
        // 404 as an unknown id.
        return templateRepository.findByIdAndOrganizationId(templateId, organizationId).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, TemplateServiceLogConstant.TEMPLATE_NOT_FOUND,
                    templateId, organizationId);
            return new NotFoundException(ExceptionDomainKayaError.NOT_VERSIONED_RESOURCE_FOUND,
                    Map.of(TemplateConstant.FIELD_TEMPLATE_ID, templateId.toString()));
        });
    }

    @Override
    public List<Template> getEntitiesByVersionId(UUID organizationId, UUID versionId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, TemplateServiceLogConstant.LOADING_VERSION_TEMPLATES,
                versionId);
        return templateRepository.findByOrganizationIdAndVersionIdOrderByRelativePathAsc(organizationId, versionId);
    }
}
