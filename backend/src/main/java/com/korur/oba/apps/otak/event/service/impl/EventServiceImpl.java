package com.korur.oba.apps.otak.event.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import static com.korur.oba.apps.otak.event.constant.service.EventServiceLogConstant.*;

import com.korur.oba.apps.otak.event.dto.request.EventCreateRequest;
import com.korur.oba.apps.otak.event.dto.request.EventUpdateRequest;
import com.korur.oba.apps.otak.event.dto.response.EventDetailResponse;
import com.korur.oba.apps.otak.event.entity.Event;
import com.korur.oba.apps.otak.event.mapper.EventMapper;
import com.korur.oba.apps.otak.event.repository.EventRepository;
import com.korur.oba.apps.otak.event.service.EventService;
import com.korur.oba.apps.otak.event.validator.EventValidator;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Implements writes to an organization's events.
 *
 * The organization role is checked once per request by the access interceptor. Every
 * event is read and written within the request's organization, so an event of another
 * organization is a missing one.
 */
@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(EventServiceImpl.class);

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final EventValidator eventValidator;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public EventDetailResponse create(UUID organizationId, User user, EventCreateRequest request) {
        String name = request.name().trim();
        eventValidator.ensureDateRange(request.startDate(), request.endDate());
        eventValidator.ensureNameFree(organizationId, name, null);

        Event event = eventMapper.toEntity(request);
        // Stored trimmed, so the stored name is the one the name rule checked.
        event.setName(name);
        event.setOrganizationId(organizationId);
        event = eventRepository.save(event);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, EVENT_CREATED, event.getId(), organizationId, user.getId());

        return eventMapper.toDetailResponse(event);
    }

    @Override
    @Transactional
    public EventDetailResponse update(UUID organizationId, UUID eventId, EventUpdateRequest request) {
        Event event = eventValidator.ensureEventExists(organizationId, eventId);

        String name = request.name().trim();
        eventValidator.ensureDateRange(request.startDate(), request.endDate());
        eventValidator.ensureNameFree(organizationId, name, eventId);

        eventMapper.updateEntity(event, request);
        event.setName(name);
        // Flushed here so the response carries the new last-change time.
        event = eventRepository.saveAndFlush(event);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, EVENT_UPDATED, eventId, organizationId);

        return eventMapper.toDetailResponse(event);
    }

    @Override
    @Transactional
    public void delete(UUID organizationId, UUID eventId) {
        Event event = eventValidator.ensureEventExists(organizationId, eventId);

        eventRepository.delete(event);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, EVENT_DELETED, eventId, organizationId);
    }
}
