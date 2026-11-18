package com.korur.oba.apps.kaya.lesson.specification;

// 1. Framework-level
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party

// 3. Java SDK
import java.util.Locale;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.lesson.constant.LessonConstant;
import com.korur.oba.apps.kaya.lesson.dto.request.LessonQueryRequest;
import com.korur.oba.apps.kaya.lesson.entity.Lesson;

/**
 * Filters of the lessons list and of the machine delivery. Every result is limited to one
 * organization.
 */
public final class LessonSpecification {

    private LessonSpecification() {
    }

    private static Specification<Lesson> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    private static Specification<Lesson> byOrganization(UUID organizationId) {
        if (organizationId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(LessonConstant.FIELD_ORGANIZATION_ID), organizationId);
    }

    private static Specification<Lesson> byRepository(UUID repositoryId) {
        if (repositoryId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(LessonConstant.FIELD_REPOSITORY_ID), repositoryId);
    }

    /** Lessons of no repository; false and null leave the list unfiltered. */
    private static Specification<Lesson> byOrganizationWide(Boolean organizationWide) {
        if (!Boolean.TRUE.equals(organizationWide)) {
            return noOp();
        }
        return (root, query, cb) -> cb.isNull(root.get(LessonConstant.FIELD_REPOSITORY_ID));
    }

    private static Specification<Lesson> byUser(UUID userId) {
        if (userId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(LessonConstant.FIELD_USER_ID), userId);
    }

    private static Specification<Lesson> byClaudeSession(String claudeSessionId) {
        if (claudeSessionId == null || claudeSessionId.isBlank()) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(LessonConstant.FIELD_CLAUDE_SESSION_ID), claudeSessionId);
    }

    /**
     * Matches the title or the body, case ignored. The wildcards of LIKE are escaped, so a search
     * for "100%" matches only that text.
     */
    public static Specification<Lesson> searchSpecification(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return noOp();
        }
        String escaped = keyword.trim().toLowerCase(Locale.ROOT)
                .replaceAll(LessonConstant.LIKE_SPECIAL_CHARACTERS_PATTERN, LessonConstant.LIKE_ESCAPED_REPLACEMENT);
        String pattern = LessonConstant.LIKE_WILDCARD + escaped + LessonConstant.LIKE_WILDCARD;
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get(LessonConstant.FIELD_TITLE)), pattern, LessonConstant.LIKE_ESCAPE),
                cb.like(cb.lower(root.get(LessonConstant.FIELD_BODY)), pattern, LessonConstant.LIKE_ESCAPE));
    }

    /**
     * The lessons a checkout reads: those of its repository plus the organization-wide ones. A
     * null repository is a remote Kaya does not know, so only the organization-wide lessons apply;
     * here null narrows the result, unlike the optional filters of the list.
     *
     * @param organizationId the organization of the machine token
     * @param repositoryId   the repository of the checkout, or null when the remote is unknown
     * @return the specification
     */
    public static Specification<Lesson> applicableTo(UUID organizationId, UUID repositoryId) {
        return byOrganization(organizationId).and((root, query, cb) -> repositoryId == null
                ? cb.isNull(root.get(LessonConstant.FIELD_REPOSITORY_ID))
                : cb.or(cb.isNull(root.get(LessonConstant.FIELD_REPOSITORY_ID)),
                        cb.equal(root.get(LessonConstant.FIELD_REPOSITORY_ID), repositoryId)));
    }

    /**
     * The lessons of the organization that pass every filter of the list.
     *
     * @param organizationId the organization of the request
     * @param request        the filters
     * @return the specification
     */
    public static Specification<Lesson> filterBy(UUID organizationId, LessonQueryRequest request) {
        return byOrganization(organizationId)
                .and(byRepository(request.getRepositoryId()))
                .and(byOrganizationWide(request.getOrganizationWide()))
                .and(byUser(request.getUserId()))
                .and(byClaudeSession(request.getSessionId()))
                .and(searchSpecification(request.getSearch()));
    }
}
