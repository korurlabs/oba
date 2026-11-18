package com.korur.oba.apps.otak.event.service.impl;

// 1. Framework-level
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import static com.korur.oba.apps.otak.event.constant.service.EventServiceLogConstant.*;

import com.korur.oba.apps.otak.event.constant.EventConstant;
import com.korur.oba.apps.otak.event.dto.request.EventQueryRequest;
import com.korur.oba.apps.otak.event.dto.response.EventDetailResponse;
import com.korur.oba.apps.otak.event.dto.response.EventListResponse;
import com.korur.oba.apps.otak.event.enums.EventPeriod;
import com.korur.oba.apps.otak.event.mapper.EventMapper;
import com.korur.oba.apps.otak.event.repository.EventRepository;
import com.korur.oba.apps.otak.event.service.EventQueryService;
import com.korur.oba.apps.otak.event.specification.EventSpecification;
import com.korur.oba.apps.otak.event.validator.EventValidator;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Implementation of event read operations.
 *
 * All methods run within a read-only transaction.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventQueryServiceImpl implements EventQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(EventQueryServiceImpl.class);

    // Fixed orders, with the id as tie-breaker, keep pages stable and keep arbitrary sortBy
    // values from reaching the query. Upcoming events read as an agenda, soonest first.
    private static final Sort SOONEST_FIRST = Sort.by(Sort.Direction.ASC, EventConstant.FIELD_START_DATE)
            .and(Sort.by(Sort.Direction.ASC, EventConstant.FIELD_NAME))
            .and(Sort.by(Sort.Direction.ASC, EventConstant.FIELD_ID));

    // Past events and the unfiltered picker list show the most recent start first.
    private static final Sort MOST_RECENT_FIRST = Sort.by(Sort.Direction.DESC, EventConstant.FIELD_START_DATE)
            .and(Sort.by(Sort.Direction.DESC, EventConstant.FIELD_ID));

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final EventValidator eventValidator;
    private final LoggingService loggingService;

    @Override
    public Page<EventListResponse> list(UUID organizationId, EventQueryRequest request) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, LISTING_EVENTS, organizationId, request);

        eventValidator.ensurePeriodHasToday(request.getPeriod(), request.getToday());

        Sort sort = request.getPeriod() == EventPeriod.UPCOMING ? SOONEST_FIRST : MOST_RECENT_FIRST;
        Pageable pageable = PageRequest.of(request.getPage() - 1, request.getAmount(), sort);
        return eventRepository.findAll(EventSpecification.filterBy(organizationId, request), pageable)
                .map(eventMapper::toListResponse);
    }

    @Override
    public EventDetailResponse get(UUID organizationId, UUID eventId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, READING_EVENT, eventId, organizationId);

        return eventMapper.toDetailResponse(eventValidator.ensureEventExists(organizationId, eventId));
    }

    @Override
    public void ensureExists(UUID organizationId, UUID eventId) {
        eventValidator.ensureEventExists(organizationId, eventId);
    }
}
