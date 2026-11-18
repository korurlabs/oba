package com.korur.oba.apps.kaya.skill.specification;

// 1. Framework-level
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.skill.constant.SkillConstant;
import com.korur.oba.apps.kaya.skill.dto.request.SkillQueryRequest;
import com.korur.oba.apps.kaya.skill.entity.Skill;

/**
 * Filters of the skill list. Every list is limited to one organization, so a version id of another
 * organization matches nothing.
 */
public final class SkillSpecification {

    private SkillSpecification() {
    }

    private static Specification<Skill> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    private static Specification<Skill> byOrganization(UUID organizationId) {
        if (organizationId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(SkillConstant.FIELD_ORGANIZATION_ID), organizationId);
    }

    private static Specification<Skill> byVersionIds(List<UUID> versionIds) {
        if (versionIds == null || versionIds.isEmpty()) {
            return noOp();
        }
        return (root, query, cb) -> root.get(SkillConstant.FIELD_VERSION_ID).in(versionIds);
    }

    /**
     * The skills of the organization among the requested versions.
     *
     * @param organizationId the organization of the request
     * @param request        the version ids
     * @return the combined filter
     */
    public static Specification<Skill> filterBy(UUID organizationId, SkillQueryRequest request) {
        return byOrganization(organizationId)
                .and(byVersionIds(request.getVersionId()));
    }
}
