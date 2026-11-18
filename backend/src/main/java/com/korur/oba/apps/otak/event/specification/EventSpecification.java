package com.korur.oba.apps.otak.event.specification;

// 1. Framework-level
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party
import jakarta.persistence.criteria.Expression;

// 3. Java SDK
import java.time.LocalDate;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.event.constant.EventConstant;
import com.korur.oba.apps.otak.event.dto.request.EventQueryRequest;
import com.korur.oba.apps.otak.event.entity.Event;
import com.korur.oba.apps.otak.event.enums.EventPeriod;

/**
 * JPA Specification builder for Event entity queries.
 *
 * The optional filters return noOp() with cb.conjunction() for a null parameter, so
 * they chain correctly with AND. The organization filter has no no-op.
 */
public final class EventSpecification {

    private EventSpecification() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns a no-op specification that matches all records.
     */
    private static Specification<Event> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    /**
     * Filters events owned by the given organization. Unlike the optional filters it has no
     * no-op for null: the organization separates tenants, so a missing id must match
     * nothing rather than every organization's events.
     */
    public static Specification<Event> belongsToOrganization(UUID organizationId) {
        return (root, query, cb) -> cb.equal(root.get(EventConstant.FIELD_ORGANIZATION_ID), organizationId);
    }

    /**
     * Keeps the upcoming or the past events relative to the viewer's day. An event's last
     * day is its end date, or its start date when it has none; it is upcoming while that
     * day is today or later, so an event ending today is still upcoming. Returns no-op if
     * {@code period} or {@code today} is null; the service refuses a period without today
     * before this runs.
     */
    public static Specification<Event> inPeriod(EventPeriod period, LocalDate today) {
        if (period == null || today == null) {
            return noOp();
        }
        return (root, query, cb) -> {
            Expression<LocalDate> lastDay = cb.coalesce(
                    root.<LocalDate>get(EventConstant.FIELD_END_DATE),
                    root.<LocalDate>get(EventConstant.FIELD_START_DATE));
            return period == EventPeriod.UPCOMING
                    ? cb.greaterThanOrEqualTo(lastDay, today)
                    : cb.lessThan(lastDay, today);
        };
    }

    /**
     * Case-insensitive search on the event name. Returns no-op if {@code keyword} is null or blank.
     */
    private static Specification<Event> searchSpecification(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return noOp();
        }
        String pattern = "%" + keyword.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get(EventConstant.FIELD_NAME)), pattern);
    }

    /**
     * Builds the specification of the event list: one organization, the optional period
     * and the optional name filter.
     *
     * @param organizationId the organization whose events are listed
     * @param request        the list filters
     * @return combined specification with all applicable filters
     */
    public static Specification<Event> filterBy(UUID organizationId, EventQueryRequest request) {
        return belongsToOrganization(organizationId)
                .and(inPeriod(request.getPeriod(), request.getToday()))
                .and(searchSpecification(request.getSearch()));
    }
}
