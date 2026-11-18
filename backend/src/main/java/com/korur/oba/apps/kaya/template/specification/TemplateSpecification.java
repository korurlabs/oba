package com.korur.oba.apps.kaya.template.specification;

// 1. Framework-level
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.template.constant.TemplateConstant;
import com.korur.oba.apps.kaya.template.dto.request.TemplateQueryRequest;
import com.korur.oba.apps.kaya.template.entity.Template;

/**
 * Filters of the template list. Every list is limited to one organization, so a version id of
 * another organization matches nothing.
 */
public final class TemplateSpecification {

    private TemplateSpecification() {
    }

    private static Specification<Template> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    private static Specification<Template> byOrganization(UUID organizationId) {
        if (organizationId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(TemplateConstant.FIELD_ORGANIZATION_ID), organizationId);
    }

    private static Specification<Template> byVersionIds(List<UUID> versionIds) {
        if (versionIds == null || versionIds.isEmpty()) {
            return noOp();
        }
        return (root, query, cb) -> root.get(TemplateConstant.FIELD_VERSION_ID).in(versionIds);
    }

    /**
     * The templates of the organization among the requested versions.
     *
     * @param organizationId the organization of the request
     * @param request        the version ids
     * @return the combined filter
     */
    public static Specification<Template> filterBy(UUID organizationId, TemplateQueryRequest request) {
        return byOrganization(organizationId)
                .and(byVersionIds(request.getVersionId()));
    }
}
