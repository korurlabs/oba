package com.korur.oba.apps.kaizen.pullrequest.specification;

// 1. Framework-level
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party
import jakarta.persistence.criteria.Join;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.pullrequest.dto.request.PullRequestShareQueryRequest;
import com.korur.oba.apps.kaizen.pullrequest.entity.PullRequestShare;
import com.korur.oba.apps.kaizen.pullrequest.entity.PullRequestShareReviewer;
import com.korur.oba.apps.kaizen.pullrequest.enums.PullRequestShareStatus;

/**
 * JPA Specification builder for PullRequestShare entity queries.
 *
 * Every public builder starts with byOrganization, so a share is only ever
 * listed within the organization whose pull requests channel shows it. Uses
 * noOp() with cb.conjunction() for null parameters to ensure correct AND
 * chaining.
 */
public final class PullRequestShareSpecification {

    private PullRequestShareSpecification() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns a no-op specification that matches all records.
     * Uses cb.conjunction() which translates to SQL TRUE (or 1=1).
     *
     * WARNING: Do NOT use Specification.anyOf() here -- it matches NOTHING!
     */
    private static Specification<PullRequestShare> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    /**
     * Keeps the shares of one organization.
     * Returns a disjunction (SQL FALSE / match-nothing) if organizationId is
     * null, so a missing organization never lists the shares of every one.
     */
    private static Specification<PullRequestShare> byOrganization(UUID organizationId) {
        if (organizationId == null) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> cb.equal(root.get("organizationId"), organizationId);
    }

    /**
     * Filters by status.
     * Returns no-op if statuses is null or empty.
     */
    private static Specification<PullRequestShare> byStatuses(List<PullRequestShareStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return noOp();
        }
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    /**
     * Filters by author IDs.
     * Returns no-op if authorIds is null or empty.
     */
    private static Specification<PullRequestShare> byAuthorIds(List<UUID> authorIds) {
        if (authorIds == null || authorIds.isEmpty()) {
            return noOp();
        }
        return (root, query, cb) -> root.get("author").get("id").in(authorIds);
    }

    /**
     * Keeps the shares waiting for one reviewer: the reviewer has a row
     * without an approval, and the pull request is not merged, since a merged
     * pull request needs no more review.
     * Returns no-op unless awaitingReview is true; a disjunction when it is
     * true without a reviewer, as nothing can wait for nobody.
     */
    private static Specification<PullRequestShare> awaitingReviewBy(UUID reviewerId, Boolean awaitingReview) {
        if (!Boolean.TRUE.equals(awaitingReview)) {
            return noOp();
        }
        if (reviewerId == null) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            Join<PullRequestShare, PullRequestShareReviewer> reviewers = root.join("reviewers");
            return cb.and(
                    cb.equal(reviewers.get("user").get("id"), reviewerId),
                    cb.isNull(reviewers.get("approvedAt")),
                    cb.notEqual(root.get("status"), PullRequestShareStatus.MERGED));
        };
    }

    /**
     * Builds the composite specification of the share list: the live shares
     * of the organization, narrowed by the request's status, author and
     * awaiting-review filters.
     *
     * @param organizationId the request's organization
     * @param readerId       ID of the caller, the reviewer of the awaiting-review filter
     * @param request        the query request containing filter criteria
     * @return combined specification with all applicable filters
     */
    public static Specification<PullRequestShare> filterBy(UUID organizationId, UUID readerId,
                                                           PullRequestShareQueryRequest request) {
        Specification<PullRequestShare> spec = byOrganization(organizationId);
        if (request == null) {
            return spec;
        }
        return spec
                .and(byStatuses(request.getStatuses()))
                .and(byAuthorIds(request.getAuthorIds()))
                .and(awaitingReviewBy(readerId, request.getAwaitingMyReview()));
    }
}
