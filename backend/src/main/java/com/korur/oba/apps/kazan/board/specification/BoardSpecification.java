package com.korur.oba.apps.kazan.board.specification;

// 1. Framework-level
import org.springframework.data.jpa.domain.Specification;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.dto.request.BoardQueryRequest;
import com.korur.oba.apps.kazan.board.entity.Board;

/**
 * JPA Specification builder for Board entity queries.
 * <p>
 * Provides composable query predicates for filtering boards based on various
 * criteria.
 * Uses noOp() with cb.conjunction() for null parameters to ensure correct AND
 * chaining.
 */
public class BoardSpecification {

    private BoardSpecification() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns a no-op specification that matches all records.
     * Uses cb.conjunction() which translates to SQL TRUE (or 1=1).
     *
     * WARNING: Do NOT use Specification.anyOf() here — it matches NOTHING!
     */
    private static Specification<Board> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    /**
     * Case-insensitive keyword search across board name, description, and prefix.
     * Returns no-op if {@code keyword} is null or blank.
     */
    private static Specification<Board> searchSpecification(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return noOp();
        }
        String pattern = "%" + keyword.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("description")), pattern),
                cb.like(cb.lower(root.get("prefix")), pattern));
    }

    /**
     * Filters boards where the given user is a member.
     * Returns no-op if {@code userId} is null.
     */
    public static Specification<Board> hasMember(UUID userId) {
        if (userId == null) {
            return noOp();
        }
        return (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            var members = root.join("members");
            return cb.equal(members.get("user").get("id"), userId);
        };
    }

    /**
     * Filters boards owned by the given organization. Unlike the optional filters it has
     * no no-op for null: the organization separates tenants, so a missing id must match
     * nothing rather than every organization's boards.
     */
    public static Specification<Board> belongsToOrganization(UUID organizationId) {
        return (root, query, cb) -> cb.equal(root.get("organizationId"), organizationId);
    }

    /**
     * Filters boards that have no parent board.
     */
    public static Specification<Board> getOnlyParentBoards() {
        return (root, query, cb) -> cb.isNull(root.get("parentBoard"));
    }

    /**
     * Builds a composite {@link Specification} for filtering {@link Board}
     * entities.
     * <p>
     * Returns all boards of the organization the user is a member of (both parent and
     * child boards).
     * The parent-only filter is NOT applied here so that child boards assigned
     * directly to the user are included in the results.
     *
     * @param request the query request containing filter criteria
     * @param userId         the user id to filter boards the user is a member of
     * @param organizationId the organization whose boards are returned
     * @return combined specification with all applicable filters
     */
    public static Specification<Board> filterBy(BoardQueryRequest request, UUID userId, UUID organizationId) {
        if (request == null) {
            return hasMember(userId)
                    .and(belongsToOrganization(organizationId));
        }
        return hasMember(userId)
                .and(belongsToOrganization(organizationId))
                .and(searchSpecification(request.getSearch()));
    }
}