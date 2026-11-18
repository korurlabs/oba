package com.korur.oba.apps.kaizen.ticket.enums.unit;

// 1. Framework-level

// 2. Third-party
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import org.junit.jupiter.api.Test;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaizen.ticket.enums.TicketPriority;

/**
 * Guards the declaration order of {@link TicketPriority}, which is the urgency
 * order that the PostgreSQL enum kaizen_ticket_priority_enum must share.
 */
class TicketPriorityUnitTest {

    @Test
    void values_declarationOrder_matchesUrgencyAscending() {
        TicketPriority[] expected = {
                TicketPriority.LOWEST,
                TicketPriority.LOW,
                TicketPriority.MEDIUM,
                TicketPriority.P3,
                TicketPriority.HIGH,
                TicketPriority.HIGHEST,
                TicketPriority.P2,
                TicketPriority.P1
        };

        TicketPriority[] actual = TicketPriority.values();

        assertArrayEquals(expected, actual);
    }
}
