package com.korur.oba.apps.kaya.rule.specification;

// 1. Framework-level
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.rule.constant.RuleConstant;
import com.korur.oba.apps.kaya.rule.dto.request.RuleQueryRequest;
import com.korur.oba.apps.kaya.rule.entity.Rule;

/**
 * Filters of the rule list. Every list is limited to one organization, so a version id of another
 * organization matches nothing.
 */
public final class RuleSpecification {

    private RuleSpecification() {
    }

    private static Specification<Rule> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    private static Specification<Rule> byOrganization(UUID organizationId) {
        if (organizationId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(RuleConstant.FIELD_ORGANIZATION_ID), organizationId);
    }

    private static Specification<Rule> byVersionIds(List<UUID> versionIds) {
        if (versionIds == null || versionIds.isEmpty()) {
            return noOp();
        }
        return (root, query, cb) -> root.get(RuleConstant.FIELD_VERSION_ID).in(versionIds);
    }

    /**
     * The rules of the organization among the requested versions.
     *
     * @param organizationId the organization of the request
     * @param request        the version ids
     * @return the combined filter
     */
    public static Specification<Rule> filterBy(UUID organizationId, RuleQueryRequest request) {
        return byOrganization(organizationId)
                .and(byVersionIds(request.getVersionId()));
    }
}
