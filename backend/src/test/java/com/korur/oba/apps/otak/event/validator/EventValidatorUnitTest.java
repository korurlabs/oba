package com.korur.oba.apps.otak.event.validator;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.event.entity.Event;
import com.korur.oba.apps.otak.event.enums.EventPeriod;
import com.korur.oba.apps.otak.event.repository.EventRepository;
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class EventValidatorUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private EventRepository eventRepository;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private EventValidator eventValidator;

    @Nested
    class EnsureEventExists {

        @Test
        void eventOfOrganization_returnsIt() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            Event event = Event.builder().name("IGEF").build();
            when(eventRepository.findByIdAndOrganizationId(eventId, organizationId)).thenReturn(Optional.of(event));

            // Act
            Event result = eventValidator.ensureEventExists(organizationId, eventId);

            // Assert
            assertThat(result).isSameAs(event);
        }

        @Test
        void eventMissingOrOfAnotherOrganization_throwsNotFound() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            when(eventRepository.findByIdAndOrganizationId(eventId, organizationId)).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> eventValidator.ensureEventExists(organizationId, eventId))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.NOT_EVENT_FOUND.getCode()));
        }
    }

    @Nested
    class EnsureDateRange {

        private final LocalDate start = LocalDate.of(2026, 11, 19);

        @Test
        void endBeforeStart_throwsValidation() {
            assertThatThrownBy(() -> eventValidator.ensureDateRange(start, start.minusDays(1)))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.VAL_EVENT_DATE_RANGE.getCode()));
        }

        @Test
        void endOnStart_passes() {
            assertThatCode(() -> eventValidator.ensureDateRange(start, start)).doesNotThrowAnyException();
        }

        @Test
        void endAfterStart_passes() {
            assertThatCode(() -> eventValidator.ensureDateRange(start, start.plusDays(3)))
                    .doesNotThrowAnyException();
        }

        @Test
        void noEndDate_passes() {
            assertThatCode(() -> eventValidator.ensureDateRange(start, null)).doesNotThrowAnyException();
        }
    }

    @Nested
    class EnsureNameFree {

        @Test
        void createWithFreeName_passes() {
            // Arrange
            when(eventRepository.existsByOrganizationIdAndNameIgnoreCase(organizationId, "IGEF")).thenReturn(false);

            // Act and assert
            assertThatCode(() -> eventValidator.ensureNameFree(organizationId, "IGEF", null))
                    .doesNotThrowAnyException();
        }

        @Test
        void createWithTakenName_throwsConflict() {
            // Arrange
            when(eventRepository.existsByOrganizationIdAndNameIgnoreCase(organizationId, "igef")).thenReturn(true);

            // Act and assert
            assertThatThrownBy(() -> eventValidator.ensureNameFree(organizationId, "igef", null))
                    .isInstanceOfSatisfying(ConflictException.class, (ConflictException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.STA_EVENT_NAME_TAKEN.getCode()));
        }

        @Test
        void updateExcludesTheEventItself() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            when(eventRepository.existsByOrganizationIdAndNameIgnoreCaseAndIdNot(organizationId, "IGEF", eventId))
                    .thenReturn(false);

            // Act
            eventValidator.ensureNameFree(organizationId, "IGEF", eventId);

            // Assert
            verify(eventRepository).existsByOrganizationIdAndNameIgnoreCaseAndIdNot(organizationId, "IGEF", eventId);
        }

        @Test
        void updateWithNameOfAnotherEvent_throwsConflict() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            when(eventRepository.existsByOrganizationIdAndNameIgnoreCaseAndIdNot(organizationId, "IGEF", eventId))
                    .thenReturn(true);

            // Act and assert
            assertThatThrownBy(() -> eventValidator.ensureNameFree(organizationId, "IGEF", eventId))
                    .isInstanceOf(ConflictException.class);
        }
    }

    @Nested
    class EnsurePeriodHasToday {

        @Test
        void periodWithoutToday_throwsValidation() {
            assertThatThrownBy(() -> eventValidator.ensurePeriodHasToday(EventPeriod.UPCOMING, null))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.VAL_EVENT_PERIOD_WITHOUT_TODAY.getCode()));
        }

        @Test
        void periodWithToday_passes() {
            assertThatCode(() -> eventValidator.ensurePeriodHasToday(EventPeriod.PAST, LocalDate.of(2026, 11, 14)))
                    .doesNotThrowAnyException();
        }

        @Test
        void noPeriod_passesWithOrWithoutToday() {
            assertThatCode(() -> {
                eventValidator.ensurePeriodHasToday(null, null);
                eventValidator.ensurePeriodHasToday(null, LocalDate.of(2026, 11, 14));
            }).doesNotThrowAnyException();
            verifyNoInteractions(eventRepository);
        }
    }
}
