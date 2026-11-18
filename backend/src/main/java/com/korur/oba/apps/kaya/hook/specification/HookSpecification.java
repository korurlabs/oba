package com.korur.oba.apps.kaya.hook.specification;

// 1. Framework-level
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.hook.constant.HookConstant;
import com.korur.oba.apps.kaya.hook.dto.request.HookQueryRequest;
import com.korur.oba.apps.kaya.hook.entity.Hook;

/**
 * Filters of the hook list. Every list is limited to one organization, so a version id of another
 * organization matches nothing.
 */
public final class HookSpecification {

    private HookSpecification() {
    }

    private static Specification<Hook> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    private static Specification<Hook> byOrganization(UUID organizationId) {
        if (organizationId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(HookConstant.FIELD_ORGANIZATION_ID), organizationId);
    }

    private static Specification<Hook> byVersionIds(List<UUID> versionIds) {
        if (versionIds == null || versionIds.isEmpty()) {
            return noOp();
        }
        return (root, query, cb) -> root.get(HookConstant.FIELD_VERSION_ID).in(versionIds);
    }

    /**
     * The hooks of the organization among the requested versions.
     *
     * @param organizationId the organization of the request
     * @param request        the version ids
     * @return the combined filter
     */
    public static Specification<Hook> filterBy(UUID organizationId, HookQueryRequest request) {
        return byOrganization(organizationId)
                .and(byVersionIds(request.getVersionId()));
    }
}
