package com.korur.oba.shrine.document.service.template.impl;

// 1. Framework-level
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import static com.korur.oba.shrine.document.constant.service.template.DocumentTemplateServiceLogConstant.*;

import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.document.constant.template.DocumentTemplateConstant;
import com.korur.oba.shrine.document.dto.request.template.DocumentTemplateQueryRequest;
import com.korur.oba.shrine.document.dto.response.template.DocumentTemplateDetailResponse;
import com.korur.oba.shrine.document.dto.response.template.DocumentTemplateListResponse;
import com.korur.oba.shrine.document.entity.template.DocumentTemplate;
import com.korur.oba.shrine.document.mapper.template.DocumentTemplateMapper;
import com.korur.oba.shrine.document.repository.template.DocumentTemplateRepository;
import com.korur.oba.shrine.document.service.template.DocumentTemplateQueryService;
import com.korur.oba.shrine.document.specification.template.DocumentTemplateSpecification;
import com.korur.oba.shrine.document.validator.template.DocumentTemplateValidator;
import com.korur.oba.shrine.legalentity.dto.response.LegalEntityListResponse;
import com.korur.oba.shrine.legalentity.service.LegalEntityQueryService;

/**
 * Implementation of the document template reads declared by
 * {@link DocumentTemplateQueryService}.
 *
 * Every method runs in a read-only transaction (the class-level {@link Transactional}),
 * so Hibernate skips dirty checking and no read can write by accident. When a caller
 * already holds a read-write transaction, the read joins that transaction.
 *
 * How the parts fit:
 * - {@link DocumentTemplateRepository} runs the queries; the list goes through
 *   {@link DocumentTemplateSpecification#filterBy}, which always adds the organization
 *   filter.
 * - {@link DocumentTemplateValidator#ensureTemplateExists} is the single lookup of one
 *   template by id and organization, shared with the command side
 *   {@link DocumentTemplateServiceImpl}, so both sides answer a foreign or missing id with
 *   the same not found.
 * - {@link DocumentTemplateMapper} turns entities into responses and signs the file URLs
 *   of the detail.
 * - {@link LegalEntityQueryService} gives the short name of each template's legal entity;
 *   the legal entity domain owns that table, so it is never read here directly.
 * - {@link LoggingService} writes a debug line for each read, with the organization.
 *
 * @see DocumentTemplateQueryService
 * @see DocumentTemplateServiceImpl
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentTemplateQueryServiceImpl implements DocumentTemplateQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(DocumentTemplateQueryServiceImpl.class);

    // A fixed order, with the id as tie-breaker, keeps pages stable and keeps arbitrary sortBy
    // values from reaching the query. The language groups the translations of one name.
    private static final Sort BY_NAME = Sort.by(Sort.Direction.ASC, DocumentTemplateConstant.FIELD_NAME)
            .and(Sort.by(Sort.Direction.ASC, DocumentTemplateConstant.FIELD_LANGUAGE))
            .and(Sort.by(Sort.Direction.ASC, DocumentTemplateConstant.FIELD_ID));

    private final DocumentTemplateRepository documentTemplateRepository;
    private final DocumentTemplateMapper documentTemplateMapper;
    private final DocumentTemplateValidator documentTemplateValidator;
    private final LegalEntityQueryService legalEntityQueryService;
    private final LoggingService loggingService;

    /**
     * {@inheritDoc}
     *
     * Builds a page request from the one-based page of the request and the fixed BY_NAME
     * order, runs {@link DocumentTemplateRepository#findAll} with
     * {@link DocumentTemplateSpecification#filterBy}, and maps each row with
     * {@link DocumentTemplateMapper#toListResponse}. The short names of the legal entities
     * come from one read of the organization's legal entities, made only when a row of the
     * page names one. The list rows carry no file URL, so no URL is signed here.
     */
    @Override
    public Page<DocumentTemplateListResponse> list(UUID organizationId, DocumentTemplateQueryRequest request) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, LISTING_TEMPLATES, organizationId, request);

        // The request counts pages from 1, Spring Data from 0. The request's own sortBy is
        // replaced by the fixed order, so a client can not sort by an arbitrary column.
        Pageable pageable = PageRequest.of(request.getPage() - 1, request.getAmount(), BY_NAME);
        // The specification always holds the organization filter, so a page never mixes
        // tenants; the mapper then drops the content and file URLs from each row.
        Page<DocumentTemplate> templates = documentTemplateRepository
                .findAll(DocumentTemplateSpecification.filterBy(organizationId, request), pageable);
        Map<UUID, String> shortNames = legalEntityShortNames(organizationId, templates);
        return templates.map(template -> documentTemplateMapper.toListResponse(template,
                template.getLegalEntityId() == null ? null : shortNames.get(template.getLegalEntityId())));
    }

    /**
     * {@inheritDoc}
     *
     * Looks the template up through {@link DocumentTemplateValidator#ensureTemplateExists},
     * reads the short name of its legal entity through
     * {@link LegalEntityQueryService#getEntity} when it has one, and maps it with
     * {@link DocumentTemplateMapper#toDetailResponse}, which signs the HTML and PDF URLs through
     * {@link com.korur.oba.connect.azure.service.BlobUrlRewriterMapper}.
     */
    @Override
    public DocumentTemplateDetailResponse get(UUID organizationId, UUID templateId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, READING_TEMPLATE, templateId, organizationId);

        // The lookup is scoped to the organization and throws the not found itself; the
        // mapper signs the stored URLs, which stay unsigned in the database.
        DocumentTemplate template = documentTemplateValidator.ensureTemplateExists(organizationId, templateId);
        String legalEntityShortName = template.getLegalEntityId() == null
                ? null
                : legalEntityQueryService.getEntity(organizationId, template.getLegalEntityId()).getShortName();
        return documentTemplateMapper.toDetailResponse(template, legalEntityShortName);
    }

    /**
     * {@inheritDoc}
     *
     * Delegates to {@link DocumentTemplateValidator#ensureTemplateExists}. No debug line is
     * written; the calling service logs what it does with the template.
     */
    @Override
    public DocumentTemplate getEntity(UUID organizationId, UUID templateId) {
        return documentTemplateValidator.ensureTemplateExists(organizationId, templateId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean existsByLegalEntity(UUID organizationId, UUID legalEntityId) {
        return documentTemplateRepository.existsByOrganizationIdAndLegalEntityId(organizationId, legalEntityId);
    }

    /**
     * Maps each legal entity id of the organization to its short name, for the rows of one
     * page. The organization holds a handful of legal entities, so one unpaged read serves
     * the whole page; it is skipped when no row names a legal entity.
     *
     * @param organizationId the organization the caller acts in
     * @param templates      the page of templates about to be mapped
     * @return the short name per legal entity id; empty when no row names a legal entity
     */
    private Map<UUID, String> legalEntityShortNames(UUID organizationId, Page<DocumentTemplate> templates) {
        boolean anyLegalEntity = templates.stream().anyMatch(template -> template.getLegalEntityId() != null);
        if (!anyLegalEntity) {
            return Map.of();
        }
        return legalEntityQueryService.list(organizationId).stream()
                .collect(Collectors.toMap(LegalEntityListResponse::id, LegalEntityListResponse::shortName));
    }
}
