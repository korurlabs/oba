package com.korur.oba.apps.kazan.dailyupdate.specification;

// 1. Framework-level
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;

// 3. Java SDK
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.dailyupdate.dto.request.DailyUpdateQueryRequest;
import com.korur.oba.apps.kazan.dailyupdate.entity.DailyUpdate;
import com.korur.oba.apps.kazan.dailyupdate.entity.DailyUpdateTicket;
import com.korur.oba.dojo.time.BusinessCalendar.DayRange;

/**
 * JPA Specification builder for DailyUpdate entity queries.
 *
 * Every public builder starts with visibleTo, so an update is only ever read
 * by its author or by a colleague in a shared organization. Uses noOp() with
 * cb.conjunction() for null parameters to ensure correct AND chaining.
 */
public final class DailyUpdateSpecification {

    private DailyUpdateSpecification() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns a no-op specification that matches all records.
     * Uses cb.conjunction() which translates to SQL TRUE (or 1=1).
     *
     * WARNING: Do NOT use Specification.anyOf() here -- it matches NOTHING!
     */
    private static Specification<DailyUpdate> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    /**
     * Keeps the updates written by one of the given users: the caller and the
     * users who share an active organization with the caller.
     * Returns a disjunction (SQL FALSE / match-nothing) if authorIds is null or
     * empty, so a missing colleague set never exposes every update.
     *
     * @param authorIds IDs of the users whose updates the caller may read
     * @return specification on the update's author
     */
    public static Specification<DailyUpdate> visibleTo(Collection<UUID> authorIds) {
        if (authorIds == null || authorIds.isEmpty()) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> root.get("author").get("id").in(authorIds);
    }

    /**
     * Filters by update ID.
     * Returns a disjunction (SQL FALSE / match-nothing) if updateId is null, so
     * a single-update read never matches every visible update.
     */
    private static Specification<DailyUpdate> byId(UUID updateId) {
        if (updateId == null) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> cb.equal(root.get("id"), updateId);
    }

    /**
     * Filters by update IDs.
     * Returns a disjunction (SQL FALSE / match-nothing) if updateIds is null or
     * empty, so a batched read of no updates never matches every visible update.
     */
    private static Specification<DailyUpdate> byIds(Collection<UUID> updateIds) {
        if (updateIds == null || updateIds.isEmpty()) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> root.get("id").in(updateIds);
    }

    /**
     * Filters by author IDs.
     * Returns no-op if authorIds is null or empty.
     */
    private static Specification<DailyUpdate> byAuthorIds(List<UUID> authorIds) {
        if (authorIds == null || authorIds.isEmpty()) {
            return noOp();
        }
        return (root, query, cb) -> root.get("author").get("id").in(authorIds);
    }

    /**
     * Keeps updates with at least one line on the given boards. Every line is
     * visible to every reader, so the boards are not intersected with the
     * caller's memberships.
     * Returns no-op if boardIds is null or empty.
     */
    private static Specification<DailyUpdate> byBoardIds(List<UUID> boardIds) {
        if (boardIds == null || boardIds.isEmpty()) {
            return noOp();
        }
        return (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            Join<DailyUpdate, DailyUpdateTicket> items = root.join("items");
            return items.get("boardId").in(boardIds);
        };
    }

    /**
     * Filters by creation time: on or after start and before end.
     * Returns no-op if range is null or both of its sides are null.
     *
     * @param range bounds of the report days, start inclusive and end exclusive; a null side is open
     */
    private static Specification<DailyUpdate> byCreatedAtRange(DayRange range) {
        if (range == null || (range.start() == null && range.end() == null)) {
            return noOp();
        }
        Instant start = range.start();
        Instant end = range.end();
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (start != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), start));
            }
            if (end != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), end));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Builds the specification that loads one update the caller may read.
     *
     * @param visibleAuthorIds IDs of the users whose updates the caller may read
     * @param updateId         ID of the update
     * @return specification matching at most one visible update
     */
    public static Specification<DailyUpdate> visibleById(Collection<UUID> visibleAuthorIds, UUID updateId) {
        return visibleTo(visibleAuthorIds).and(byId(updateId));
    }

    /**
     * Builds the specification that loads the requested updates the caller may
     * read; an invisible or deleted update does not match.
     *
     * @param visibleAuthorIds IDs of the users whose updates the caller may read
     * @param updateIds        IDs of the updates
     * @return specification matching the visible updates among the requested ones
     */
    public static Specification<DailyUpdate> visibleByIds(Collection<UUID> visibleAuthorIds,
                                                          Collection<UUID> updateIds) {
        return visibleTo(visibleAuthorIds).and(byIds(updateIds));
    }

    /**
     * Builds the composite specification of the feed: the updates the caller
     * may read, narrowed by the request's author and board filters and by the
     * report days of its date filter.
     *
     * @param visibleAuthorIds IDs of the users whose updates the caller may read
     * @param request          the query request containing filter criteria
     * @param createdAtRange   bounds of the request's report days on the business
     *                         calendar; null or a null side for no bound
     * @return combined specification with all applicable filters
     */
    public static Specification<DailyUpdate> filterBy(Collection<UUID> visibleAuthorIds,
                                                      DailyUpdateQueryRequest request,
                                                      DayRange createdAtRange) {
        Specification<DailyUpdate> spec = visibleTo(visibleAuthorIds);
        if (request == null) {
            return spec;
        }
        return spec
                .and(byAuthorIds(request.getAuthorIds()))
                .and(byBoardIds(request.getBoardIds()))
                .and(byCreatedAtRange(createdAtRange));
    }
}
