package com.korur.oba.apps.kazan.ticket.helper;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

// 4. Internal
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.enums.TicketType;
import com.korur.oba.dojo.logging.service.LoggingService;

class TicketTypeNormalizerUnitTest {

    private final TicketTypeNormalizer ticketTypeNormalizer = new TicketTypeNormalizer(new LoggingService());

    @Test
    @SuppressWarnings("deprecation")
    void normalizeType_typeTask_convertsToFeature() {
        // Arrange
        Ticket ticket = Ticket.builder().title("T").key("B-1").type(TicketType.TASK).build();

        // Act
        ticketTypeNormalizer.normalizeType(ticket);

        // Assert
        assertThat(ticket.getType()).isEqualTo(TicketType.FEATURE);
    }

    @ParameterizedTest
    @EnumSource(value = TicketType.class, names = "TASK", mode = EnumSource.Mode.EXCLUDE)
    void normalizeType_storedType_keepsType(TicketType type) {
        // Arrange
        Ticket ticket = Ticket.builder().title("T").key("B-1").type(type).build();

        // Act
        ticketTypeNormalizer.normalizeType(ticket);

        // Assert
        assertThat(ticket.getType()).isEqualTo(type);
    }

    @Test
    void normalizeType_nullTicket_doesNothing() {
        assertThatCode(() -> ticketTypeNormalizer.normalizeType(null)).doesNotThrowAnyException();
    }

    @Test
    void normalizeType_nullType_keepsNull() {
        // Arrange
        Ticket ticket = Ticket.builder().title("T").key("B-1").build();

        // Act
        ticketTypeNormalizer.normalizeType(ticket);

        // Assert
        assertThat(ticket.getType()).isNull();
    }
}
