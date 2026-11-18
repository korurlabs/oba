package com.korur.oba.apps.otak.documenttemplate.service.impl;

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
import java.util.UUID;

// 4. Internal
import static com.korur.oba.apps.otak.documenttemplate.constant.service.DocumentTemplateServiceLogConstant.*;

import com.korur.oba.apps.otak.documenttemplate.constant.DocumentTemplateConstant;
import com.korur.oba.apps.otak.documenttemplate.dto.request.DocumentTemplateQueryRequest;
import com.korur.oba.apps.otak.documenttemplate.dto.response.DocumentTemplateDetailResponse;
import com.korur.oba.apps.otak.documenttemplate.dto.response.DocumentTemplateListResponse;
import com.korur.oba.apps.otak.documenttemplate.entity.DocumentTemplate;
import com.korur.oba.apps.otak.documenttemplate.mapper.DocumentTemplateMapper;
import com.korur.oba.apps.otak.documenttemplate.repository.DocumentTemplateRepository;
import com.korur.oba.apps.otak.documenttemplate.service.DocumentTemplateQueryService;
import com.korur.oba.apps.otak.documenttemplate.specification.DocumentTemplateSpecification;
import com.korur.oba.apps.otak.documenttemplate.validator.DocumentTemplateValidator;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

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
    private final LoggingService loggingService;

    /**
     * {@inheritDoc}
     *
     * Builds a page request from the one-based page of the request and the fixed BY_NAME
     * order, runs {@link DocumentTemplateRepository#findAll} with
     * {@link DocumentTemplateSpecification#filterBy}, and maps each row with
     * {@link DocumentTemplateMapper#toListResponse}. The list rows carry no file URL, so
     * no URL is signed here.
     */
    @Override
    public Page<DocumentTemplateListResponse> list(UUID organizationId, DocumentTemplateQueryRequest request) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, LISTING_TEMPLATES, organizationId, request);

        // The request counts pages from 1, Spring Data from 0. The request's own sortBy is
        // replaced by the fixed order, so a client can not sort by an arbitrary column.
        Pageable pageable = PageRequest.of(request.getPage() - 1, request.getAmount(), BY_NAME);
        // The specification always holds the organization filter, so a page never mixes
        // tenants; the mapper then drops the content and file URLs from each row.
        return documentTemplateRepository
                .findAll(DocumentTemplateSpecification.filterBy(organizationId, request), pageable)
                .map(documentTemplateMapper::toListResponse);
    }

    /**
     * {@inheritDoc}
     *
     * Looks the template up through {@link DocumentTemplateValidator#ensureTemplateExists}
     * and maps it with {@link DocumentTemplateMapper#toDetailResponse}, which signs the
     * HTML and PDF URLs through
     * {@link com.korur.oba.connect.azure.service.BlobUrlRewriterMapper}.
     */
    @Override
    public DocumentTemplateDetailResponse get(UUID organizationId, UUID templateId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, READING_TEMPLATE, templateId, organizationId);

        // The lookup is scoped to the organization and throws the not found itself; the
        // mapper signs the stored URLs, which stay unsigned in the database.
        return documentTemplateMapper.toDetailResponse(
                documentTemplateValidator.ensureTemplateExists(organizationId, templateId));
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
}
