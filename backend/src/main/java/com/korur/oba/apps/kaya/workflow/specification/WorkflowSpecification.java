package com.korur.oba.apps.kaya.workflow.specification;

// 1. Framework-level
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.workflow.constant.WorkflowConstant;
import com.korur.oba.apps.kaya.workflow.dto.request.WorkflowQueryRequest;
import com.korur.oba.apps.kaya.workflow.entity.Workflow;

/**
 * Filters of the workflow list. Every list is limited to one organization, so a version id of another
 * organization matches nothing.
 */
public final class WorkflowSpecification {

    private WorkflowSpecification() {
    }

    private static Specification<Workflow> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    private static Specification<Workflow> byOrganization(UUID organizationId) {
        if (organizationId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(WorkflowConstant.FIELD_ORGANIZATION_ID), organizationId);
    }

    private static Specification<Workflow> byVersionIds(List<UUID> versionIds) {
        if (versionIds == null || versionIds.isEmpty()) {
            return noOp();
        }
        return (root, query, cb) -> root.get(WorkflowConstant.FIELD_VERSION_ID).in(versionIds);
    }

    /**
     * The workflows of the organization among the requested versions.
     *
     * @param organizationId the organization of the request
     * @param request        the version ids
     * @return the combined filter
     */
    public static Specification<Workflow> filterBy(UUID organizationId, WorkflowQueryRequest request) {
        return byOrganization(organizationId)
                .and(byVersionIds(request.getVersionId()));
    }
}
