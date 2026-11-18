package com.korur.oba.apps.otak.event.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.event.dto.request.EventQueryRequest;
import com.korur.oba.apps.otak.event.dto.response.EventDetailResponse;
import com.korur.oba.apps.otak.event.dto.response.EventListResponse;
import com.korur.oba.apps.otak.event.entity.Event;
import com.korur.oba.apps.otak.event.enums.EventPeriod;
import com.korur.oba.apps.otak.event.enums.EventType;
import com.korur.oba.apps.otak.event.mapper.EventMapperImpl;
import com.korur.oba.apps.otak.event.repository.EventRepository;
import com.korur.oba.apps.otak.event.validator.EventValidator;
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class EventQueryServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();
    private final LocalDate today = LocalDate.of(2026, 11, 14);

    @Mock
    private EventRepository eventRepository;

    @Mock
    private LoggingService loggingService;

    private EventQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EventQueryServiceImpl(eventRepository, new EventMapperImpl(),
                new EventValidator(eventRepository, loggingService), loggingService);
    }

    private Event event(String name) {
        Event event = Event.builder().name(name).type(EventType.EXPO).startDate(today).organizationId(organizationId)
                .build();
        event.setId(UUID.randomUUID());
        return event;
    }

    @SuppressWarnings("unchecked")
    private Pageable listAndCapturePageable(EventQueryRequest request) {
        when(eventRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event("IGEF"))));
        service.list(organizationId, request);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(eventRepository).findAll(any(Specification.class), pageable.capture());
        return pageable.getValue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void list_upcoming_ordersSoonestStartThenNameThenIdAndMapsTheItems() {
        // Arrange
        Event stored = event("IGEF");
        EventQueryRequest request = new EventQueryRequest();
        request.setPage(2);
        request.setAmount(10);
        request.setPeriod(EventPeriod.UPCOMING);
        request.setToday(today);
        when(eventRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(stored)));

        // Act
        Page<EventListResponse> result = service.list(organizationId, request);

        // Assert
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(eventRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(
                Sort.Order.asc("startDate"), Sort.Order.asc("name"), Sort.Order.asc("id")));
        assertThat(result.getContent()).extracting(EventListResponse::name).containsExactly("IGEF");
    }

    @Test
    void list_past_ordersMostRecentStartThenIdDescending() {
        // Arrange
        EventQueryRequest request = new EventQueryRequest();
        request.setPeriod(EventPeriod.PAST);
        request.setToday(today);

        // Act
        Pageable pageable = listAndCapturePageable(request);

        // Assert
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Order.desc("startDate"), Sort.Order.desc("id")));
    }

    @Test
    void list_noPeriodAndNoToday_ordersMostRecentStartFirst() {
        // Arrange
        EventQueryRequest request = new EventQueryRequest();
        request.setSortBy("name");

        // Act
        Pageable pageable = listAndCapturePageable(request);

        // Assert
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Order.desc("startDate"), Sort.Order.desc("id")));
    }

    @SuppressWarnings("unchecked")
    @Test
    void list_periodWithoutToday_throwsValidationAndDoesNotQuery() {
        // Arrange
        EventQueryRequest request = new EventQueryRequest();
        request.setPeriod(EventPeriod.PAST);

        // Act and assert
        assertThatThrownBy(() -> service.list(organizationId, request))
                .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ExceptionDomainOtakError.VAL_EVENT_PERIOD_WITHOUT_TODAY.getCode()));
        verify(eventRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void get_eventOfOrganization_returnsDetail() {
        // Arrange
        Event stored = event("IGEF");
        when(eventRepository.findByIdAndOrganizationId(stored.getId(), organizationId))
                .thenReturn(Optional.of(stored));

        // Act
        EventDetailResponse result = service.get(organizationId, stored.getId());

        // Assert
        assertThat(result.id()).isEqualTo(stored.getId());
        assertThat(result.name()).isEqualTo("IGEF");
    }

    @Test
    void get_eventMissing_throwsNotFound() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndOrganizationId(eventId, organizationId)).thenReturn(Optional.empty());

        // Act and assert
        assertThatThrownBy(() -> service.get(organizationId, eventId))
                .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ExceptionDomainOtakError.NOT_EVENT_FOUND.getCode()));
    }

    @Test
    void ensureExists_eventOfOrganization_passes() {
        // Arrange
        Event stored = event("IGEF");
        when(eventRepository.findByIdAndOrganizationId(stored.getId(), organizationId))
                .thenReturn(Optional.of(stored));

        // Act and assert
        assertThatCode(() -> service.ensureExists(organizationId, stored.getId())).doesNotThrowAnyException();
    }

    @Test
    void ensureExists_foreignEvent_throwsNotFound() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndOrganizationId(eventId, organizationId)).thenReturn(Optional.empty());

        // Act and assert
        assertThatThrownBy(() -> service.ensureExists(organizationId, eventId)).isInstanceOf(NotFoundException.class);
    }
}
