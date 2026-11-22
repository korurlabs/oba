package com.korur.oba.shrine.document.specification.template;

// 1. Framework-level
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.document.constant.template.DocumentTemplateConstant;
import com.korur.oba.shrine.document.dto.request.template.DocumentTemplateQueryRequest;
import com.korur.oba.shrine.document.entity.template.DocumentTemplate;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;
import com.korur.oba.shrine.document.enums.common.DocumentType;

/**
 * JPA Specification builder for the {@link DocumentTemplate} list.
 *
 * {@link #filterBy} combines one mandatory filter, the organization, with four optional
 * ones from the {@link DocumentTemplateQueryRequest}: a search on the name, a
 * {@link DocumentType}, a {@link DocumentLanguage} and a legal entity. The optional filters return
 * {@link #noOp()}, which is cb.conjunction() (always true), for a null or blank value, so
 * they chain with AND without null checks in the caller. The organization filter has no
 * no-op, so it can never be left out.
 *
 * Used by
 * {@link com.korur.oba.shrine.document.service.template.impl.DocumentTemplateQueryServiceImpl#list},
 * which passes the result to
 * {@link com.korur.oba.shrine.document.repository.template.DocumentTemplateRepository}.
 *
 * Final with a private constructor: it only holds static factory methods.
 *
 * @see com.korur.oba.shrine.document.repository.template.DocumentTemplateRepository
 * @see com.korur.oba.shrine.document.service.template.impl.DocumentTemplateQueryServiceImpl
 */
public final class DocumentTemplateSpecification {

    /**
     * Not instantiable.
     *
     * @throws UnsupportedOperationException always
     */
    private DocumentTemplateSpecification() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns a no-op specification that matches all records. cb.conjunction() is used
     * rather than a null specification, so the result chains with and() like any other.
     *
     * @return a specification that is always true
     */
    private static Specification<DocumentTemplate> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    /**
     * Filters templates owned by the given organization. Unlike the optional filters it
     * has no no-op for null: the organization separates tenants, so a missing id must
     * match nothing rather than every organization's templates. With a null id the
     * predicate compares with null, which is never true in SQL.
     *
     * @param organizationId the organization the caller acts in
     * @return a specification on {@link DocumentTemplate#getOrganizationId()}
     */
    public static Specification<DocumentTemplate> belongsToOrganization(UUID organizationId) {
        return (root, query, cb) -> cb.equal(root.get(DocumentTemplateConstant.FIELD_ORGANIZATION_ID),
                organizationId);
    }

    /**
     * Case-insensitive search on the name: the name contains the keyword anywhere. Returns
     * {@link #noOp()} if keyword is null or blank, so an empty search box lists everything.
     *
     * @param keyword the text typed in the search box, may be null
     * @return a LIKE on the lower-case name, or a no-op
     */
    private static Specification<DocumentTemplate> searchSpecification(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return noOp();
        }
        // Both sides are lower-cased, so the match ignores case on every database collation.
        String pattern = "%" + keyword.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get(DocumentTemplateConstant.FIELD_NAME)), pattern);
    }

    /**
     * Filters templates of one type, for example only the proposals. Returns
     * {@link #noOp()} if type is null.
     *
     * @param type the type to keep, may be null for every type
     * @return an equality on {@link DocumentTemplate#getType()}, or a no-op
     */
    private static Specification<DocumentTemplate> typeSpecification(DocumentType type) {
        if (type == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(DocumentTemplateConstant.FIELD_TYPE), type);
    }

    /**
     * Filters templates in one language. Returns {@link #noOp()} if language is null.
     *
     * @param language the language to keep, may be null for every language
     * @return an equality on {@link DocumentTemplate#getLanguage()}, or a no-op
     */
    private static Specification<DocumentTemplate> languageSpecification(DocumentLanguage language) {
        if (language == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(DocumentTemplateConstant.FIELD_LANGUAGE), language);
    }

    /**
     * Filters templates the given legal entity may use: those it issues itself and those
     * without a legal entity, which every legal entity of the organization may use. Returns
     * {@link #noOp()} if legalEntityId is null.
     *
     * @param legalEntityId the legal entity to keep templates for, may be null for every template
     * @return an equality on {@link DocumentTemplate#getLegalEntityId()} or a null check, or a no-op
     */
    private static Specification<DocumentTemplate> usableByLegalEntitySpecification(UUID legalEntityId) {
        if (legalEntityId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.or(
                cb.equal(root.get(DocumentTemplateConstant.FIELD_LEGAL_ENTITY_ID), legalEntityId),
                cb.isNull(root.get(DocumentTemplateConstant.FIELD_LEGAL_ENTITY_ID)));
    }

    /**
     * Builds the specification of the template list: one organization and the optional
     * search, type, language and legal entity filters, joined with AND. The order of the rows is not
     * part of the specification; the caller sets it on the page request.
     *
     * @param organizationId the organization whose templates are listed
     * @param request        the list filters; its search, type, language and legal entity may each be null
     * @return combined specification with all applicable filters
     */
    public static Specification<DocumentTemplate> filterBy(UUID organizationId, DocumentTemplateQueryRequest request) {
        return belongsToOrganization(organizationId)
                .and(searchSpecification(request.getSearch()))
                .and(typeSpecification(request.getType()))
                .and(languageSpecification(request.getLanguage()))
                .and(usableByLegalEntitySpecification(request.getLegalEntityId()));
    }
}
