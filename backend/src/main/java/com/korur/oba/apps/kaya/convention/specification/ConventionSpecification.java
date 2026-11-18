package com.korur.oba.apps.kaya.convention.specification;

// 1. Framework-level
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.convention.constant.ConventionConstant;
import com.korur.oba.apps.kaya.convention.dto.request.ConventionQueryRequest;
import com.korur.oba.apps.kaya.convention.entity.Convention;

/**
 * Filters of the convention list. Every list is limited to one organization, so a version id of
 * another organization matches nothing.
 */
public final class ConventionSpecification {

    private ConventionSpecification() {
    }

    private static Specification<Convention> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    private static Specification<Convention> byOrganization(UUID organizationId) {
        if (organizationId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(ConventionConstant.FIELD_ORGANIZATION_ID), organizationId);
    }

    private static Specification<Convention> byVersionIds(List<UUID> versionIds) {
        if (versionIds == null || versionIds.isEmpty()) {
            return noOp();
        }
        return (root, query, cb) -> root.get(ConventionConstant.FIELD_VERSION_ID).in(versionIds);
    }

    /**
     * The conventions of the organization among the requested versions.
     *
     * @param organizationId the organization of the request
     * @param request        the version ids
     * @return the combined filter
     */
    public static Specification<Convention> filterBy(UUID organizationId, ConventionQueryRequest request) {
        return byOrganization(organizationId)
                .and(byVersionIds(request.getVersionId()));
    }
}
