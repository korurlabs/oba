package com.korur.oba.shrine.document.specification.document;

// 1. Framework-level
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.document.constant.document.DocumentConstant;
import com.korur.oba.shrine.document.dto.request.document.DocumentQueryRequest;
import com.korur.oba.shrine.document.entity.document.Document;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;
import com.korur.oba.shrine.document.enums.common.DocumentType;
import com.korur.oba.shrine.document.enums.document.DocumentEntityType;

/**
 * JPA specifications of the document list. {@link #filterBy} always holds the organization
 * filter, so a page never mixes tenants; every other filter is a no-op when its value is null.
 */
public final class DocumentSpecification {

    private DocumentSpecification() {
        throw new UnsupportedOperationException("Utility class");
    }

    private static Specification<Document> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    /**
     * @param organizationId the organization of the request
     * @return the documents of that organization
     */
    public static Specification<Document> belongsToOrganization(UUID organizationId) {
        return (root, query, cb) -> cb.equal(root.get(DocumentConstant.FIELD_ORGANIZATION_ID), organizationId);
    }

    private static Specification<Document> relationSpecification(UUID relationId) {
        if (relationId == null) {
            return noOp();
        }
        // The owner kind is part of the match, so a future owner kind with the same id never matches.
        return (root, query, cb) -> cb.and(
                cb.equal(root.get(DocumentConstant.FIELD_ENTITY_TYPE), DocumentEntityType.RELATION),
                cb.equal(root.get(DocumentConstant.FIELD_ENTITY_ID), relationId));
    }

    private static Specification<Document> legalEntitySpecification(UUID legalEntityId) {
        if (legalEntityId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(DocumentConstant.FIELD_LEGAL_ENTITY_ID), legalEntityId);
    }

    private static Specification<Document> languageSpecification(DocumentLanguage language) {
        if (language == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(DocumentConstant.FIELD_LANGUAGE), language);
    }

    private static Specification<Document> typeSpecification(DocumentType type) {
        if (type == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(DocumentConstant.FIELD_TYPE), type);
    }

    private static Specification<Document> searchSpecification(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return noOp();
        }
        // Both sides are lower-cased, so the match ignores case on every database collation.
        String pattern = "%" + keyword.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get(DocumentConstant.FIELD_TITLE)), pattern);
    }

    /**
     * @param organizationId the organization of the request
     * @param request        the optional filters
     * @return the organization filter and every given filter, joined with and
     */
    public static Specification<Document> filterBy(UUID organizationId, DocumentQueryRequest request) {
        return belongsToOrganization(organizationId)
                .and(relationSpecification(request.getRelationId()))
                .and(legalEntitySpecification(request.getLegalEntityId()))
                .and(languageSpecification(request.getLanguage()))
                .and(typeSpecification(request.getType()))
                .and(searchSpecification(request.getSearch()));
    }
}
