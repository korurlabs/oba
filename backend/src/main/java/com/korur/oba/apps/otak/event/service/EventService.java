package com.korur.oba.apps.otak.event.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.event.dto.request.EventCreateRequest;
import com.korur.oba.apps.otak.event.dto.request.EventUpdateRequest;
import com.korur.oba.apps.otak.event.dto.response.EventDetailResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Write service interface for an organization's events. The caller's role in the
 * organization is checked before these methods run; every method works within that
 * organization, so an event of another organization is a missing one.
 */
public interface EventService {

    /**
     * Creates an event.
     *
     * @param organizationId the organization that owns the event
     * @param user           the authenticated user
     * @param request        the event payload
     * @return the created event
     */
    EventDetailResponse create(UUID organizationId, User user, EventCreateRequest request);

    /**
     * Replaces every field of an event; null clears an optional field.
     *
     * @param organizationId the organization that owns the event
     * @param eventId        the event identifier
     * @param request        the event payload
     * @return the updated event
     */
    EventDetailResponse update(UUID organizationId, UUID eventId, EventUpdateRequest request);

    /**
     * Deletes an event; the database clears the link of every relation that pointed to it
     * and keeps the relations.
     *
     * @param organizationId the organization that owns the event
     * @param eventId        the event identifier
     */
    void delete(UUID organizationId, UUID eventId);
}
