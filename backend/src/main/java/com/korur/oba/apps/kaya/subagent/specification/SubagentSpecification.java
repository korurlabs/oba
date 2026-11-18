package com.korur.oba.apps.kaya.subagent.specification;

// 1. Framework-level
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.subagent.constant.SubagentConstant;
import com.korur.oba.apps.kaya.subagent.dto.request.SubagentQueryRequest;
import com.korur.oba.apps.kaya.subagent.entity.Subagent;

/**
 * Filters of the subagent list. Every list is limited to one organization, so a version id of
 * another organization matches nothing.
 */
public final class SubagentSpecification {

    private SubagentSpecification() {
    }

    private static Specification<Subagent> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    private static Specification<Subagent> byOrganization(UUID organizationId) {
        if (organizationId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(SubagentConstant.FIELD_ORGANIZATION_ID), organizationId);
    }

    private static Specification<Subagent> byVersionIds(List<UUID> versionIds) {
        if (versionIds == null || versionIds.isEmpty()) {
            return noOp();
        }
        return (root, query, cb) -> root.get(SubagentConstant.FIELD_VERSION_ID).in(versionIds);
    }

    /**
     * The subagents of the organization among the requested versions.
     *
     * @param organizationId the organization of the request
     * @param request        the version ids
     * @return the combined filter
     */
    public static Specification<Subagent> filterBy(UUID organizationId, SubagentQueryRequest request) {
        return byOrganization(organizationId)
                .and(byVersionIds(request.getVersionId()));
    }
}
