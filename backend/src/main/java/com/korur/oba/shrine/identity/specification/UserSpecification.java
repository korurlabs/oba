package com.korur.oba.shrine.identity.specification;

// 1. Framework-level
import org.springframework.data.jpa.domain.Specification;

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.dto.UserQueryRequest;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserGender;
import com.korur.oba.shrine.identity.enums.UserPosition;
import com.korur.oba.shrine.identity.enums.UserPreferredLanguage;
import com.korur.oba.shrine.identity.enums.UserStatus;

/**
 * JPA Specification builder for User entity queries.
 * <p>
 * Provides composable query predicates for filtering users based on various criteria.
 * Uses noOp() with cb.conjunction() for null parameters to ensure correct AND chaining.
 */
public class UserSpecification {

    private UserSpecification() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns a no-op specification that matches all records.
     * Uses cb.conjunction() which translates to SQL TRUE (or 1=1).
     *
     * WARNING: Do NOT use Specification.anyOf() here — it matches NOTHING!
     */
    private static Specification<User> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    /**
     * Filters by user statuses.
     * Returns no-op if {@code statuses} is null or empty.
     */
    private static Specification<User> byStatuses(List<UserStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return noOp();
        }
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    /**
     * Filters by position in profile block.
     * Returns no-op if {@code position} is null.
     */
    private static Specification<User> byPosition(UserPosition position) {
        if (position == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get("profile").get("userPosition"), position);
    }

    /**
     * Filters by preferred language in profile block.
     * Returns no-op if {@code language} is null.
     */
    private static Specification<User> byPreferredLanguage(UserPreferredLanguage language) {
        if (language == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get("profile").get("userPreferredLanguage"), language);
    }

    /**
     * Filters by gender in profile block.
     * Returns no-op if {@code gender} is null.
     */
    private static Specification<User> byGender(UserGender gender) {
        if (gender == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get("profile").get("userGender"), gender);
    }

    /**
     * Case-insensitive keyword search across email, firstName, lastName.
     * Returns no-op if {@code keyword} is null or blank.
     */
    private static Specification<User> searchSpecification(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return noOp();
        }
        String pattern = "%" + keyword.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("email")), pattern),
                cb.like(cb.lower(root.get("profile").get("firstName")), pattern),
                cb.like(cb.lower(root.get("profile").get("lastName")), pattern));
    }

    /**
     * Keeps the users the caller may see: the caller and the users who share an
     * active organization with the caller.
     * Returns a disjunction (SQL FALSE / match-nothing) if userIds is null or empty,
     * so a missing colleague set never exposes every user.
     *
     * @param userIds IDs of the users the caller may see
     * @return specification on the user's ID
     */
    public static Specification<User> visibleTo(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> root.get("id").in(userIds);
    }

    /**
     * Builds a composite {@link Specification} for filtering {@link User} entities:
     * the users the caller may see, narrowed by the request's filters. No filter
     * widens the visible set.
     *
     * @param visibleUserIds IDs of the users the caller may see
     * @param request        the query request containing filter criteria
     * @return combined specification with all applicable filters
     */
    public static Specification<User> filterBy(Collection<UUID> visibleUserIds, UserQueryRequest request) {
        Specification<User> spec = visibleTo(visibleUserIds);
        if (request == null) {
            return spec;
        }
        return spec
                .and(byStatuses(request.getUserStatuses()))
                .and(byPosition(request.getUserPosition()))
                .and(byGender(request.getUserGender()))
                .and(byPreferredLanguage(request.getUserPreferredLanguage()))
                .and(searchSpecification(request.getSearch()));
    }
}
