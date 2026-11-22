package com.korur.oba.shrine.document.service.document;

// 1. Framework-level
import org.springframework.data.domain.Page;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.document.dto.request.document.DocumentQueryRequest;
import com.korur.oba.shrine.document.dto.response.document.DocumentDetailResponse;
import com.korur.oba.shrine.document.dto.response.document.DocumentListResponse;
import com.korur.oba.shrine.document.entity.document.Document;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Read side of the document domain. Every method runs in a read-only transaction.
 *
 * @see DocumentService
 */
public interface DocumentQueryService {

    /**
     * Lists one page of the organization's documents, newest change first. Relations have no
     * per-user visibility, so access is checked once: READ on the given relation, or, without
     * one, the role and app that reading any relation needs.
     *
     * @param user           the caller
     * @param organizationId the organization from the checked request header
     * @param request        paging and the optional filters
     * @return the page of list items
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException APP_SEC_001 without Otak access
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException OTK_NOT_001 for a relation
     *         filter that does not exist
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException APP_SEC_002 for a relation filter
     *         of another organization
     */
    Page<DocumentListResponse> list(User user, UUID organizationId, DocumentQueryRequest request);

    /**
     * Reads one document with its snapshots, after READ on its owner.
     *
     * @param user           the caller
     * @param organizationId the organization from the checked request header
     * @param documentId     the document
     * @return the detail
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException DOC_NOT_002
     */
    DocumentDetailResponse get(User user, UUID organizationId, UUID documentId);

    /**
     * Reads one document entity of the organization, without an access check, for other
     * services of the domain.
     *
     * @param organizationId the organization
     * @param documentId     the document
     * @return the entity
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException DOC_NOT_002
     */
    Document getEntity(UUID organizationId, UUID documentId);

    /**
     * Tells whether a document of the organization names the legal entity.
     *
     * Called by {@link com.korur.oba.shrine.legalentity.service.impl.LegalEntityServiceImpl#delete},
     * which refuses to delete a legal entity in use.
     *
     * @param organizationId the organization
     * @param legalEntityId  the legal entity
     * @return true when at least one document names it
     */
    boolean existsByLegalEntity(UUID organizationId, UUID legalEntityId);
}
