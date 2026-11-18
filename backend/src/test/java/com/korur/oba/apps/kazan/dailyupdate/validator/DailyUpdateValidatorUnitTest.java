package com.korur.oba.apps.kazan.dailyupdate.validator;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.springframework.data.jpa.domain.Specification;

// 3. Java SDK
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.dailyupdate.dto.request.DailyUpdateTicketRequest;
import com.korur.oba.apps.kazan.dailyupdate.entity.DailyUpdate;
import com.korur.oba.apps.kazan.dailyupdate.mock_data.DailyUpdateMocks;
import com.korur.oba.apps.kazan.dailyupdate.repository.DailyUpdateRepository;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class DailyUpdateValidatorUnitTest {

    @Mock
    private DailyUpdateRepository dailyUpdateRepository;

    private DailyUpdateValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DailyUpdateValidator(dailyUpdateRepository, new LoggingService());
    }

    private static List<DailyUpdateTicketRequest> lines(int count) {
        List<DailyUpdateTicketRequest> lines = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            lines.add(new DailyUpdateTicketRequest(UUID.randomUUID(), 10, null));
        }
        return lines;
    }

    @Nested
    class EnsureWithinLimits {

        @Test
        void oneTicketAtBoundaries_passes() {
            List<DailyUpdateTicketRequest> lines = List.of(
                    new DailyUpdateTicketRequest(UUID.randomUUID(), 0, "n".repeat(500)));

            assertThatCode(() -> validator.ensureWithinLimits(lines, "f".repeat(2000), "q".repeat(2000)))
                    .doesNotThrowAnyException();
        }

        @Test
        void thirtyTickets_passes() {
            assertThatCode(() -> validator.ensureWithinLimits(lines(30), null, null)).doesNotThrowAnyException();
        }

        @Test
        void zeroTickets_throwsValidationException() {
            assertThatThrownBy(() -> validator.ensureWithinLimits(List.of(), null, null))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void nullTickets_throwsValidationException() {
            assertThatThrownBy(() -> validator.ensureWithinLimits(null, null, null))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void thirtyOneTickets_throwsValidationException() {
            assertThatThrownBy(() -> validator.ensureWithinLimits(lines(31), null, null))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void noteOf501Characters_throwsValidationException() {
            List<DailyUpdateTicketRequest> lines = List.of(
                    new DailyUpdateTicketRequest(UUID.randomUUID(), 10, "n".repeat(501)));

            assertThatThrownBy(() -> validator.ensureWithinLimits(lines, null, null))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void percentageOutOfRange_throwsValidationException() {
            List<DailyUpdateTicketRequest> over = List.of(new DailyUpdateTicketRequest(UUID.randomUUID(), 101, null));
            List<DailyUpdateTicketRequest> under = List.of(new DailyUpdateTicketRequest(UUID.randomUUID(), -1, null));
            List<DailyUpdateTicketRequest> missing = List.of(
                    new DailyUpdateTicketRequest(UUID.randomUUID(), null, null));

            assertThatThrownBy(() -> validator.ensureWithinLimits(over, null, null))
                    .isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> validator.ensureWithinLimits(under, null, null))
                    .isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> validator.ensureWithinLimits(missing, null, null))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void lineWithoutTicketId_throwsValidationException() {
            List<DailyUpdateTicketRequest> lines = List.of(new DailyUpdateTicketRequest(null, 10, null));

            assertThatThrownBy(() -> validator.ensureWithinLimits(lines, null, null))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void focusOrQuestionsOf2001Characters_throwsValidationException() {
            List<DailyUpdateTicketRequest> lines = lines(1);

            assertThatThrownBy(() -> validator.ensureWithinLimits(lines, "f".repeat(2001), null))
                    .isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> validator.ensureWithinLimits(lines, null, "q".repeat(2001)))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class EnsureNoDuplicateTickets {

        @Test
        void duplicateTicket_throwsValidationExceptionWithDuplicateCode() {
            UUID ticketId = UUID.randomUUID();
            List<DailyUpdateTicketRequest> lines = List.of(
                    new DailyUpdateTicketRequest(ticketId, 10, null),
                    new DailyUpdateTicketRequest(UUID.randomUUID(), 10, null),
                    new DailyUpdateTicketRequest(ticketId, 20, "again"));

            assertThatThrownBy(() -> validator.ensureNoDuplicateTickets(lines))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainKazanError.VAL_DAILY_UPDATE_DUPLICATE_TICKET.getCode()));
        }

        @Test
        void distinctTickets_passes() {
            assertThatCode(() -> validator.ensureNoDuplicateTickets(lines(5))).doesNotThrowAnyException();
        }
    }

    @Nested
    class EnsureTicketsAccessible {

        @Test
        void everyTicketFound_passes() {
            Ticket ticket = DailyUpdateMocks.ticket(DailyUpdateMocks.board("B"), "B-1", "T", 0);

            assertThatCode(() -> validator.ensureTicketsAccessible(List.of(ticket.getId()), List.of(ticket)))
                    .doesNotThrowAnyException();
        }

        @Test
        void ticketMissing_throwsNotFoundWithTicketCode() {
            UUID missingId = UUID.randomUUID();

            assertThatThrownBy(() -> validator.ensureTicketsAccessible(List.of(missingId), List.of()))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainKazanError.NOT_TICKET_FOUND.getCode()));
        }
    }

    @Nested
    class EnsureAuthor {

        @Test
        void differentUser_throwsForbiddenWithSecurityCode() {
            User author = DailyUpdateMocks.user();
            DailyUpdate dailyUpdate = DailyUpdateMocks.dailyUpdate(author, null, null);

            assertThatThrownBy(() -> validator.ensureAuthor(dailyUpdate, DailyUpdateMocks.user()))
                    .isInstanceOfSatisfying(ForbiddenException.class, (ForbiddenException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainKazanError.SEC_CANNOT_MODIFY_OTHERS_UPDATE.getCode()));
        }

        @Test
        void sameUserWithEqualButDistinctIdInstance_passes() {
            User author = DailyUpdateMocks.user();
            User caller = DailyUpdateMocks.user();
            caller.setId(new UUID(author.getId().getMostSignificantBits(), author.getId().getLeastSignificantBits()));
            DailyUpdate dailyUpdate = DailyUpdateMocks.dailyUpdate(author, null, null);

            assertThatCode(() -> validator.ensureAuthor(dailyUpdate, caller)).doesNotThrowAnyException();
        }
    }

    @Nested
    class EnsureDailyUpdateVisible {

        @Test
        void updateVisible_returnsIt() {
            DailyUpdate dailyUpdate = DailyUpdateMocks.dailyUpdate(DailyUpdateMocks.user(), null, null);
            when(dailyUpdateRepository.findOne(any(Specification.class))).thenReturn(Optional.of(dailyUpdate));

            DailyUpdate result = validator.ensureDailyUpdateVisible(Set.of(UUID.randomUUID()), dailyUpdate.getId());

            assertThat(result).isSameAs(dailyUpdate);
        }

        @Test
        void updateInvisible_throwsNotFoundWithUpdateCode() {
            when(dailyUpdateRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> validator.ensureDailyUpdateVisible(Set.of(), UUID.randomUUID()))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainKazanError.NOT_DAILY_UPDATE_FOUND.getCode()));
        }
    }

    @Nested
    class EnsureDateRangeValid {

        @Test
        void fromAfterTo_throwsValidationException() {
            assertThatThrownBy(() -> validator.ensureDateRangeValid(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 19)))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainKazanError.VAL_DAILY_UPDATE_FROM_AFTER_TO.getCode()));
        }

        @Test
        void equalOrOpenEndedDates_pass() {
            LocalDate day = LocalDate.of(2026, 10, 19);

            assertThatCode(() -> {
                validator.ensureDateRangeValid(day, day);
                validator.ensureDateRangeValid(day, null);
                validator.ensureDateRangeValid(null, day);
                validator.ensureDateRangeValid(null, null);
            }).doesNotThrowAnyException();
        }
    }
}
