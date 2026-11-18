package com.korur.oba.apps.otak.mailtemplate.specification;

// 1. Framework-level
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.documenttemplate.enums.DocumentLanguage;
import com.korur.oba.apps.otak.mailtemplate.constant.MailTemplateConstant;
import com.korur.oba.apps.otak.mailtemplate.dto.request.MailTemplateQueryRequest;
import com.korur.oba.apps.otak.mailtemplate.entity.MailTemplate;

/**
 * JPA Specification builder for the {@link MailTemplate} list.
 *
 * {@link #filterBy} combines one mandatory filter, the organization, with two optional ones
 * from the {@link MailTemplateQueryRequest}: a search on the name and a
 * {@link DocumentLanguage}. The optional filters return {@link #noOp()}, which is
 * cb.conjunction() (always true), for a null or blank value, so they chain with AND without
 * null checks in the caller. The organization filter has no no-op, so it can never be left
 * out.
 *
 * Used by
 * {@link com.korur.oba.apps.otak.mailtemplate.service.impl.MailTemplateQueryServiceImpl#list}.
 *
 * Final with a private constructor: it only holds static factory methods.
 */
public final class MailTemplateSpecification {

    /**
     * Not instantiable.
     *
     * @throws UnsupportedOperationException always
     */
    private MailTemplateSpecification() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns a no-op specification that matches all records. cb.conjunction() is used
     * rather than a null specification, so the result chains with and() like any other.
     *
     * @return a specification that is always true
     */
    private static Specification<MailTemplate> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    /**
     * Filters mail templates owned by the given organization. Unlike the optional filters it
     * has no no-op for null: the organization separates tenants, so a missing id must match
     * nothing rather than every organization's mail templates.
     *
     * @param organizationId the organization the caller acts in
     * @return a specification on {@link MailTemplate#getOrganizationId()}
     */
    public static Specification<MailTemplate> belongsToOrganization(UUID organizationId) {
        return (root, query, cb) -> cb.equal(root.get(MailTemplateConstant.FIELD_ORGANIZATION_ID), organizationId);
    }

    /**
     * Case-insensitive search on the name: the name contains the keyword anywhere. Returns
     * {@link #noOp()} if keyword is null or blank.
     *
     * @param keyword the text typed in the search box, may be null
     * @return a LIKE on the lower-case name, or a no-op
     */
    private static Specification<MailTemplate> searchSpecification(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return noOp();
        }
        // Both sides are lower-cased, so the match ignores case on every database collation.
        String pattern = "%" + keyword.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get(MailTemplateConstant.FIELD_NAME)), pattern);
    }

    /**
     * Filters mail templates in one language. Returns {@link #noOp()} if language is null.
     *
     * @param language the language to keep, may be null for every language
     * @return an equality on {@link MailTemplate#getLanguage()}, or a no-op
     */
    private static Specification<MailTemplate> languageSpecification(DocumentLanguage language) {
        if (language == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(MailTemplateConstant.FIELD_LANGUAGE), language);
    }

    /**
     * Builds the specification of the mail template list: one organization and the optional
     * search and language filters, joined with AND. The order of the rows is set by the
     * caller on the page request.
     *
     * @param organizationId the organization whose mail templates are listed
     * @param request        the list filters; its search and language may each be null
     * @return combined specification with all applicable filters
     */
    public static Specification<MailTemplate> filterBy(UUID organizationId, MailTemplateQueryRequest request) {
        return belongsToOrganization(organizationId)
                .and(searchSpecification(request.getSearch()))
                .and(languageSpecification(request.getLanguage()));
    }
}
