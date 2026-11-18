package com.korur.oba.apps.kaizen.ticket.helper;

// 1. Framework-level
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 4. Internal
import com.korur.oba.apps.kaizen.sprint.entity.Sprint;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.apps.kaizen.ticket.enums.TicketStatus;
import com.korur.oba.apps.kaizen.ticket.enums.TicketType;
import com.korur.oba.dojo.logging.service.LoggingService;

class TicketStatusNormalizerUnitTest {

    private final TicketStatusNormalizer ticketStatusNormalizer = new TicketStatusNormalizer(new LoggingService());

    @Test
    void normalizeBacklogStatus_backlogTicketWaitingForThirdParty_resetsToTodo() {
        // Arrange
        Ticket ticket = Ticket.builder().title("T").key("B-1").type(TicketType.FEATURE)
                .status(TicketStatus.WAITING_FOR_THIRD_PARTY).build();

        // Act
        ticketStatusNormalizer.normalizeBacklogStatus(ticket);

        // Assert
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.TODO);
    }

    @Test
    void normalizeBacklogStatus_sprintTicketWaitingForThirdParty_keepsStatus() {
        // Arrange
        Ticket ticket = Ticket.builder().title("T").key("B-1").type(TicketType.FEATURE)
                .status(TicketStatus.WAITING_FOR_THIRD_PARTY).sprint(Sprint.builder().name("S").build()).build();

        // Act
        ticketStatusNormalizer.normalizeBacklogStatus(ticket);

        // Assert
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.WAITING_FOR_THIRD_PARTY);
    }

    @Test
    void normalizeBacklogStatus_backlogSubTicketWaitingForThirdParty_keepsStatus() {
        // Arrange
        Ticket parent = Ticket.builder().title("P").key("B-1").type(TicketType.FEATURE).status(TicketStatus.TODO).build();
        Ticket child = Ticket.builder().title("C").key("B-2").type(TicketType.FEATURE)
                .status(TicketStatus.WAITING_FOR_THIRD_PARTY).parentTicket(parent).build();

        // Act
        ticketStatusNormalizer.normalizeBacklogStatus(child);

        // Assert
        assertThat(child.getStatus()).isEqualTo(TicketStatus.WAITING_FOR_THIRD_PARTY);
    }
}
