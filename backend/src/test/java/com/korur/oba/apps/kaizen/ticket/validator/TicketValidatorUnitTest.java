package com.korur.oba.apps.kaizen.ticket.validator;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.apps.kaizen.sprint.entity.Sprint;
import com.korur.oba.apps.kaizen.ticket.constant.TicketConstant;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.apps.kaizen.ticket.enums.TicketStatus;
import com.korur.oba.apps.kaizen.ticket.enums.TicketType;
import com.korur.oba.apps.kaizen.ticket.repository.TicketRepository;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class TicketValidatorUnitTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private LoggingService loggingService;

    private TicketValidator ticketValidator;

    @BeforeEach
    void setUp() {
        ticketValidator = new TicketValidator(ticketRepository, loggingService);
    }

    private Ticket ticket(TicketType type, TicketStatus status) {
        return Ticket.builder().title("T").key("B-1").type(type).status(status).build();
    }

    @Nested
    class EnsureBacklogTicketStatusAllowed {

        @ParameterizedTest
        @EnumSource(value = TicketStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "TODO")
        void topLevelBacklogTicket_nonTodoStatus_throwsValidationException(TicketStatus status) {
            // Arrange
            Ticket ticket = ticket(TicketType.FEATURE, status);

            // Act and Assert
            assertThatThrownBy(() -> ticketValidator.ensureBacklogTicketStatusAllowed(ticket))
                    .isInstanceOfSatisfying(ValidationException.class, ex -> {
                        assertThat(ex.getErrorCode())
                                .isEqualTo(ExceptionDomainKaizenError.VAL_TICKET_BACKLOG_STATUS.getCode());
                        assertThat(ex.getErrorValues())
                                .containsEntry(TicketConstant.FIELD_TICKET_KEY, "B-1")
                                .containsEntry(TicketConstant.FIELD_STATUS, status.name());
                    });
        }

        @Test
        void topLevelBacklogTicket_todoStatus_passes() {
            Ticket ticket = ticket(TicketType.FEATURE, TicketStatus.TODO);

            assertThatCode(() -> ticketValidator.ensureBacklogTicketStatusAllowed(ticket)).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @EnumSource(TicketStatus.class)
        void sprintTicket_anyStatus_passes(TicketStatus status) {
            Ticket ticket = ticket(TicketType.FEATURE, status);
            ticket.setSprint(Sprint.builder().name("S").build());

            assertThatCode(() -> ticketValidator.ensureBacklogTicketStatusAllowed(ticket)).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @EnumSource(TicketStatus.class)
        void subTicketInBacklog_anyStatus_passes(TicketStatus status) {
            Ticket parent = ticket(TicketType.FEATURE, TicketStatus.TODO);
            Ticket subTicket = ticket(TicketType.FEATURE, status);
            subTicket.setParentTicket(parent);

            assertThatCode(() -> ticketValidator.ensureBacklogTicketStatusAllowed(subTicket)).doesNotThrowAnyException();
        }
    }

    @Nested
    class EnsureIncidentCanBeClosed {

        private Ticket incident(TicketStatus status, String rca, String lessonsLearned) {
            Ticket ticket = ticket(TicketType.INCIDENT, status);
            ticket.setRca(rca);
            ticket.setLessonsLearned(lessonsLearned);
            return ticket;
        }

        private void assertMissing(Ticket ticket, TicketStatus previous, List<String> expectedMissing) {
            assertThatThrownBy(() -> ticketValidator.ensureIncidentCanBeClosed(ticket, previous))
                    .isInstanceOfSatisfying(ValidationException.class, ex -> {
                        assertThat(ex.getErrorCode())
                                .isEqualTo(ExceptionDomainKaizenError.VAL_INCIDENT_CLOSE_REQUIRES_RCA.getCode());
                        assertThat(ex.getErrorValues())
                                .containsEntry(TicketConstant.FIELD_TICKET_KEY, "B-1")
                                .containsEntry(TicketConstant.FIELD_MISSING_FIELDS, expectedMissing);
                    });
        }

        @Test
        void closing_nullRca_throwsMissingRca() {
            assertMissing(incident(TicketStatus.DONE, null, "lessons"), TicketStatus.IN_PROGRESS,
                    List.of(TicketConstant.FIELD_RCA));
        }

        @Test
        void closing_blankLessons_throwsMissingLessons() {
            assertMissing(incident(TicketStatus.DONE, "cause", ""), TicketStatus.IN_PROGRESS,
                    List.of(TicketConstant.FIELD_LESSONS_LEARNED));
        }

        @ParameterizedTest
        @ValueSource(strings = {" ", "   ", "\n\t"})
        void closing_whitespaceOnlyTexts_throwsMissingBoth(String whitespace) {
            assertMissing(incident(TicketStatus.DONE, whitespace, whitespace), TicketStatus.TODO,
                    List.of(TicketConstant.FIELD_RCA, TicketConstant.FIELD_LESSONS_LEARNED));
        }

        @Test
        void creatingDone_nullPreviousStatusWithoutTexts_throwsMissingBoth() {
            assertMissing(incident(TicketStatus.DONE, null, null), null,
                    List.of(TicketConstant.FIELD_RCA, TicketConstant.FIELD_LESSONS_LEARNED));
        }

        @Test
        void closing_bothTextsSet_passes() {
            Ticket ticket = incident(TicketStatus.DONE, "cause", "lessons");

            assertThatCode(() -> ticketValidator.ensureIncidentCanBeClosed(ticket, TicketStatus.IN_REVIEW))
                    .doesNotThrowAnyException();
        }

        @Test
        void alreadyDone_noTexts_passes() {
            Ticket ticket = incident(TicketStatus.DONE, null, null);

            assertThatCode(() -> ticketValidator.ensureIncidentCanBeClosed(ticket, TicketStatus.DONE))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest
        @EnumSource(value = TicketStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "DONE")
        void statusOtherThanDone_noTexts_passes(TicketStatus status) {
            Ticket ticket = incident(status, null, null);

            assertThatCode(() -> ticketValidator.ensureIncidentCanBeClosed(ticket, TicketStatus.TODO))
                    .doesNotThrowAnyException();
        }
    }
}
