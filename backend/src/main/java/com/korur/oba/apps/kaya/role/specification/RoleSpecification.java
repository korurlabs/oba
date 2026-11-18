package com.korur.oba.apps.kaya.role.specification;

// 1. Framework-level
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.role.constant.RoleConstant;
import com.korur.oba.apps.kaya.role.dto.request.RoleQueryRequest;
import com.korur.oba.apps.kaya.role.entity.Role;

/**
 * Filters of the role list. Every list is limited to one organization, so a version id of
 * another organization matches nothing.
 */
public final class RoleSpecification {

    private RoleSpecification() {
    }

    private static Specification<Role> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    private static Specification<Role> byOrganization(UUID organizationId) {
        if (organizationId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(RoleConstant.FIELD_ORGANIZATION_ID), organizationId);
    }

    private static Specification<Role> byVersionIds(List<UUID> versionIds) {
        if (versionIds == null || versionIds.isEmpty()) {
            return noOp();
        }
        return (root, query, cb) -> root.get(RoleConstant.FIELD_VERSION_ID).in(versionIds);
    }

    /**
     * The roles of the organization among the requested versions.
     *
     * @param organizationId the organization of the request
     * @param request        the version ids
     * @return the combined filter
     */
    public static Specification<Role> filterBy(UUID organizationId, RoleQueryRequest request) {
        return byOrganization(organizationId)
                .and(byVersionIds(request.getVersionId()));
    }
}
