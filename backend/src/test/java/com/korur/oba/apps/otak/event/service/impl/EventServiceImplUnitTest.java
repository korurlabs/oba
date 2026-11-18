package com.korur.oba.apps.otak.event.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.event.dto.request.EventCreateRequest;
import com.korur.oba.apps.otak.event.dto.request.EventUpdateRequest;
import com.korur.oba.apps.otak.event.dto.response.EventDetailResponse;
import com.korur.oba.apps.otak.event.entity.Event;
import com.korur.oba.apps.otak.event.enums.EventType;
import com.korur.oba.apps.otak.event.mapper.EventMapperImpl;
import com.korur.oba.apps.otak.event.repository.EventRepository;
import com.korur.oba.apps.otak.event.validator.EventValidator;
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.apps.otak.relation.enums.Country;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class EventServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private EventRepository eventRepository;

    @Mock
    private LoggingService loggingService;

    private EventServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        // The real validator and mapper run over the mocked repository, so the error codes and
        // the mapped fields are the production ones.
        service = new EventServiceImpl(eventRepository, new EventMapperImpl(),
                new EventValidator(eventRepository, loggingService), loggingService);
        user = User.builder().build();
        user.setId(UUID.randomUUID());
    }

    private static EventCreateRequest request(String name, LocalDate start, LocalDate end) {
        return new EventCreateRequest(name, EventType.EXPO, start, end, "Ankara", Country.TR,
                "https://igef.com.tr/", "Booth in hall 4.");
    }

    private static EventUpdateRequest updateRequest(String name, LocalDate start, LocalDate end) {
        return new EventUpdateRequest(name, EventType.EXPO, start, end, "Ankara", Country.TR,
                "https://igef.com.tr/", "Booth in hall 4.");
    }

    private Event stored(String name) {
        Event event = Event.builder().name(name).type(EventType.MEETUP).startDate(LocalDate.of(2026, 1, 1))
                .organizationId(organizationId).build();
        event.setId(UUID.randomUUID());
        when(eventRepository.findByIdAndOrganizationId(event.getId(), organizationId)).thenReturn(Optional.of(event));
        return event;
    }

    @Nested
    class Create {

        @Test
        void validRequest_savesTrimmedNameInTheHeaderOrganization() {
            // Arrange
            when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            EventDetailResponse result = service.create(organizationId, user,
                    request("  IGEF 2026  ", LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 22)));

            // Assert
            ArgumentCaptor<Event> saved = ArgumentCaptor.forClass(Event.class);
            verify(eventRepository).save(saved.capture());
            assertThat(saved.getValue().getName()).isEqualTo("IGEF 2026");
            assertThat(saved.getValue().getOrganizationId()).isEqualTo(organizationId);
            assertThat(result.name()).isEqualTo("IGEF 2026");
            assertThat(result.type()).isEqualTo(EventType.EXPO);
            assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 11, 22));
        }

        @Test
        void trimmedNameTaken_throwsConflictAndSavesNothing() {
            // Arrange
            when(eventRepository.existsByOrganizationIdAndNameIgnoreCase(organizationId, "IGEF 2026"))
                    .thenReturn(true);

            // Act and assert
            assertThatThrownBy(() -> service.create(organizationId, user,
                    request(" IGEF 2026 ", LocalDate.of(2026, 11, 19), null)))
                    .isInstanceOfSatisfying(ConflictException.class, (ConflictException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.STA_EVENT_NAME_TAKEN.getCode()));
            verify(eventRepository, never()).save(any(Event.class));
        }

        @Test
        void endBeforeStart_throwsValidationAndSavesNothing() {
            assertThatThrownBy(() -> service.create(organizationId, user,
                    request("IGEF", LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 18))))
                    .isInstanceOf(ValidationException.class);
            verify(eventRepository, never()).save(any(Event.class));
        }
    }

    @Nested
    class Update {

        @Test
        void ownEvent_replacesEveryFieldAndKeepsItsOwnName() {
            // Arrange
            Event event = stored("Old");
            when(eventRepository.saveAndFlush(event)).thenReturn(event);

            // Act
            EventDetailResponse result = service.update(organizationId, event.getId(),
                    new EventUpdateRequest(" New ", EventType.WEBINAR, LocalDate.of(2026, 5, 1), null, null, null, null, null));

            // Assert
            assertThat(event.getName()).isEqualTo("New");
            assertThat(event.getType()).isEqualTo(EventType.WEBINAR);
            assertThat(event.getCity()).isNull();
            assertThat(event.getCountry()).isNull();
            assertThat(event.getOrganizationId()).isEqualTo(organizationId);
            assertThat(result.name()).isEqualTo("New");
            verify(eventRepository).existsByOrganizationIdAndNameIgnoreCaseAndIdNot(organizationId, "New", event.getId());
        }

        @Test
        void eventMissing_throwsNotFound() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            when(eventRepository.findByIdAndOrganizationId(eventId, organizationId)).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, eventId,
                    updateRequest("IGEF", LocalDate.of(2026, 11, 19), null)))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.NOT_EVENT_FOUND.getCode()));
        }

        @Test
        void nameOfAnotherEvent_throwsConflict() {
            // Arrange
            Event event = stored("Old");
            when(eventRepository.existsByOrganizationIdAndNameIgnoreCaseAndIdNot(organizationId, "IGEF", event.getId()))
                    .thenReturn(true);

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, event.getId(),
                    updateRequest("IGEF", LocalDate.of(2026, 11, 19), null)))
                    .isInstanceOf(ConflictException.class);
            verify(eventRepository, never()).saveAndFlush(any(Event.class));
        }
    }

    @Nested
    class Delete {

        @Test
        void ownEvent_deletesIt() {
            // Arrange
            Event event = stored("IGEF");

            // Act
            service.delete(organizationId, event.getId());

            // Assert
            verify(eventRepository).delete(event);
        }

        @Test
        void eventMissing_throwsNotFoundAndDeletesNothing() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            when(eventRepository.findByIdAndOrganizationId(eventId, organizationId)).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.delete(organizationId, eventId)).isInstanceOf(NotFoundException.class);
            verify(eventRepository, never()).delete(any(Event.class));
        }
    }
}
