package com.korur.oba.apps.otak.event.service;

// 1. Framework-level
import org.springframework.data.domain.Page;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.event.dto.request.EventQueryRequest;
import com.korur.oba.apps.otak.event.dto.response.EventDetailResponse;
import com.korur.oba.apps.otak.event.dto.response.EventListResponse;

/**
 * Read service interface for an organization's events.
 */
public interface EventQueryService {

    /**
     * Lists one page of the organization's events. Upcoming events come soonest first,
     * past events and the unfiltered list most recent start first.
     *
     * @param organizationId the organization that owns the events
     * @param request        the paging, the period with the viewer's day and the name search
     * @return the page of events
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException OTK_VAL_011
     *         when a period is given without today
     */
    Page<EventListResponse> list(UUID organizationId, EventQueryRequest request);

    /**
     * Reads one event of the organization.
     *
     * @param organizationId the organization that owns the event
     * @param eventId        the event identifier
     * @return the event
     */
    EventDetailResponse get(UUID organizationId, UUID eventId);

    /**
     * Ensures the organization has the event, for the relation domain before it links a
     * relation to one. An event of another organization is not found.
     *
     * @param organizationId the organization that owns the event
     * @param eventId        the event identifier
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException OTK_NOT_009
     *         when the organization has no event with the id
     */
    void ensureExists(UUID organizationId, UUID eventId);
}
