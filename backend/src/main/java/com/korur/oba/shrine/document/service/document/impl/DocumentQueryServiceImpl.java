package com.korur.oba.shrine.document.service.document.impl;

// 1. Framework-level
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
import static com.korur.oba.shrine.document.constant.service.document.DocumentServiceLogConstant.*;

import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.ResourceAccessAction;
import com.korur.oba.shrine.access.enums.ResourceAccessType;
import com.korur.oba.shrine.access.service.ResourceAccessQueryService;
import com.korur.oba.shrine.document.constant.document.DocumentConstant;
import com.korur.oba.shrine.document.dto.request.document.DocumentQueryRequest;
import com.korur.oba.shrine.document.dto.response.document.DocumentDetailResponse;
import com.korur.oba.shrine.document.dto.response.document.DocumentListResponse;
import com.korur.oba.shrine.document.entity.document.Document;
import com.korur.oba.shrine.document.enums.document.DocumentEntityType;
import com.korur.oba.shrine.document.mapper.document.DocumentMapperHelper;
import com.korur.oba.shrine.document.repository.document.DocumentRepository;
import com.korur.oba.shrine.document.service.document.DocumentQueryService;
import com.korur.oba.shrine.document.specification.document.DocumentSpecification;
import com.korur.oba.shrine.document.validator.document.DocumentValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.service.OrganizationAccessService;

/**
 * Implements the reads of documents. The contract is on {@link DocumentQueryService}.
 *
 * @see com.korur.oba.shrine.document.service.document.DocumentService
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentQueryServiceImpl implements DocumentQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(DocumentQueryServiceImpl.class);

    // A fixed order, with the id as tie-breaker, keeps pages stable and keeps arbitrary sortBy
    // values from reaching the query.
    private static final Sort LAST_CHANGE_FIRST = Sort.by(Sort.Direction.DESC, DocumentConstant.FIELD_UPDATED_AT)
            .and(Sort.by(Sort.Direction.DESC, DocumentConstant.FIELD_ID));

    private final DocumentRepository documentRepository;
    private final DocumentValidator documentValidator;
    private final DocumentMapperHelper documentMapperHelper;
    private final ResourceAccessQueryService resourceAccessQueryService;
    private final OrganizationAccessService organizationAccessService;
    private final LoggingService loggingService;

    /**
     * {@inheritDoc}
     *
     * Relations are the only owner kind and have no per-user visibility, so one check covers
     * the page: READ on the relation filter, which refuses a relation of another organization, or
     * else the role and app of a relation read in the organization.
     */
    @Override
    public Page<DocumentListResponse> list(User user, UUID organizationId, DocumentQueryRequest request) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, LISTING_DOCUMENTS, organizationId, request);

        ResourceAccessType ownerAccess = DocumentEntityType.RELATION.getResourceAccessType();
        if (request.getRelationId() != null) {
            resourceAccessQueryService.verifyResourceAccess(user, organizationId, ownerAccess,
                    request.getRelationId(), ResourceAccessAction.READ);
        } else {
            organizationAccessService.verifyOrganizationAccess(organizationId, user.getId(),
                    ownerAccess.minimumRole(ResourceAccessAction.READ), ownerAccess.getApp());
        }

        Pageable pageable = PageRequest.of(request.getPage() - 1, request.getAmount(), LAST_CHANGE_FIRST);
        Page<Document> documents = documentRepository.findAll(DocumentSpecification.filterBy(organizationId, request),
                pageable);
        // Mapped as a whole page, so names and counts are read once per page, not per row.
        return new PageImpl<>(documentMapperHelper.toListResponses(organizationId, documents.getContent()),
                pageable, documents.getTotalElements());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DocumentDetailResponse get(User user, UUID organizationId, UUID documentId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, READING_DOCUMENT, documentId, organizationId);

        Document document = documentValidator.ensureDocumentExists(organizationId, documentId);
        resourceAccessQueryService.verifyResourceAccess(user, organizationId,
                document.getEntityType().getResourceAccessType(), document.getEntityId(), ResourceAccessAction.READ);
        return documentMapperHelper.toDetailResponse(organizationId, document);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Document getEntity(UUID organizationId, UUID documentId) {
        return documentValidator.ensureDocumentExists(organizationId, documentId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean existsByLegalEntity(UUID organizationId, UUID legalEntityId) {
        return documentRepository.existsByOrganizationIdAndLegalEntityId(organizationId, legalEntityId);
    }
}
