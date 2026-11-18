package com.korur.oba.apps.kaya.plan.specification;

// 1. Framework-level
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

// 3. Java SDK
import java.util.Locale;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.plan.constant.PlanConstant;
import com.korur.oba.apps.kaya.plan.dto.request.PlanQueryRequest;
import com.korur.oba.apps.kaya.plan.entity.Plan;
import com.korur.oba.apps.kaya.plan.entity.PlanVersion;
import com.korur.oba.apps.kaya.plan.enums.PlanKind;
import com.korur.oba.apps.kaya.plan.enums.PlanReviewState;

/**
 * Filters of the plans list. Every list is limited to one organization.
 */
public final class PlanSpecification {

    private PlanSpecification() {
    }

    private static Specification<Plan> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    private static Specification<Plan> byOrganization(UUID organizationId) {
        if (organizationId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(PlanConstant.FIELD_ORGANIZATION_ID), organizationId);
    }

    private static Specification<Plan> byRepository(UUID repositoryId) {
        if (repositoryId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(PlanConstant.FIELD_REPOSITORY_ID), repositoryId);
    }

    private static Specification<Plan> byPack(UUID packId) {
        if (packId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(PlanConstant.FIELD_PACK_ID), packId);
    }

    private static Specification<Plan> byKind(PlanKind kind) {
        if (kind == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(PlanConstant.FIELD_KIND), kind);
    }

    private static Specification<Plan> byReviewState(PlanReviewState reviewState) {
        if (reviewState == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(PlanConstant.FIELD_REVIEW_STATE), reviewState);
    }

    private static Specification<Plan> byUser(UUID userId) {
        if (userId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(PlanConstant.FIELD_USER_ID), userId);
    }

    /**
     * Plans with a version the session wrote, so the session page lists every plan the session
     * touched, also one another session has written to since.
     */
    private static Specification<Plan> byWritingSession(UUID sessionRowId) {
        if (sessionRowId == null) {
            return noOp();
        }
        return (root, query, cb) -> {
            Subquery<UUID> written = query.subquery(UUID.class);
            Root<PlanVersion> version = written.from(PlanVersion.class);
            written.select(version.get(PlanConstant.FIELD_ID)).where(
                    cb.equal(version.get(PlanConstant.FIELD_PLAN_ID), root.get(PlanConstant.FIELD_ID)),
                    cb.equal(version.get(PlanConstant.FIELD_SESSION_ID), sessionRowId));
            return cb.exists(written);
        };
    }

    /**
     * Matches the title or the path, case ignored. The wildcards of LIKE are escaped, so a search
     * for "04_KAYA" matches only that text.
     */
    public static Specification<Plan> searchSpecification(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return noOp();
        }
        String escaped = keyword.trim().toLowerCase(Locale.ROOT)
                .replaceAll(PlanConstant.LIKE_SPECIAL_CHARACTERS_PATTERN, PlanConstant.LIKE_ESCAPED_REPLACEMENT);
        String pattern = PlanConstant.LIKE_WILDCARD + escaped + PlanConstant.LIKE_WILDCARD;
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get(PlanConstant.FIELD_TITLE)), pattern, PlanConstant.LIKE_ESCAPE),
                cb.like(cb.lower(root.get(PlanConstant.FIELD_PATH)), pattern, PlanConstant.LIKE_ESCAPE));
    }

    /**
     * The plans of the organization that pass every filter of the request.
     *
     * @param organizationId the organization of the request
     * @param sessionRowId   the row id of the session named by the request's sessionId, or null
     * @param request        the filters
     * @return the specification
     */
    public static Specification<Plan> filterBy(UUID organizationId, UUID sessionRowId, PlanQueryRequest request) {
        return byOrganization(organizationId)
                .and(byRepository(request.getRepositoryId()))
                .and(byPack(request.getPackId()))
                .and(byKind(request.getKind()))
                .and(byReviewState(request.getReviewState()))
                .and(byUser(request.getUserId()))
                .and(byWritingSession(sessionRowId))
                .and(searchSpecification(request.getSearch()));
    }
}
