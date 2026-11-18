package com.korur.oba.apps.kaya.agentsfile.specification;

// 1. Framework-level
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.agentsfile.constant.AgentsFileConstant;
import com.korur.oba.apps.kaya.agentsfile.dto.request.AgentsFileQueryRequest;
import com.korur.oba.apps.kaya.agentsfile.entity.AgentsFile;

/**
 * Filters of the agents file list. Every list is limited to one organization, so a version id of
 * another organization matches nothing.
 */
public final class AgentsFileSpecification {

    private AgentsFileSpecification() {
    }

    private static Specification<AgentsFile> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    private static Specification<AgentsFile> byOrganization(UUID organizationId) {
        if (organizationId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(AgentsFileConstant.FIELD_ORGANIZATION_ID), organizationId);
    }

    private static Specification<AgentsFile> byVersionIds(List<UUID> versionIds) {
        if (versionIds == null || versionIds.isEmpty()) {
            return noOp();
        }
        return (root, query, cb) -> root.get(AgentsFileConstant.FIELD_VERSION_ID).in(versionIds);
    }

    /**
     * The agents files of the organization among the requested versions.
     *
     * @param organizationId the organization of the request
     * @param request        the version ids
     * @return the combined filter
     */
    public static Specification<AgentsFile> filterBy(UUID organizationId, AgentsFileQueryRequest request) {
        return byOrganization(organizationId)
                .and(byVersionIds(request.getVersionId()));
    }
}
