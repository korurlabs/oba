package com.korur.oba.apps.kaizen.ticket.entity;

// 1. Framework-level
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 3. Java SDK
import java.time.Instant;

// 4. Internal
import com.korur.oba.apps.kaizen.ticket.enums.TicketStatus;

/**
 * Unit tests for the callback that keeps Ticket.completedAt and Ticket.percentage in step
 * with the status.
 */
class TicketCompletedAtUnitTest {

    @Test
    void syncCompletedAt_statusDoneWithoutValue_setsCurrentMoment() {
        // Arrange
        Ticket ticket = Ticket.builder().status(TicketStatus.DONE).build();
        Instant before = Instant.now();

        // Act
        ticket.syncCompletedAt();

        // Assert
        assertThat(ticket.getCompletedAt()).isBetween(before, Instant.now());
    }

    @Test
    void syncCompletedAt_statusDoneWithValue_keepsValue() {
        // Arrange
        Instant finishedAt = Instant.parse("2026-10-01T10:00:00Z");
        Ticket ticket = Ticket.builder().status(TicketStatus.DONE).completedAt(finishedAt).build();

        // Act
        ticket.syncCompletedAt();

        // Assert
        assertThat(ticket.getCompletedAt()).isEqualTo(finishedAt);
    }

    @Test
    void syncCompletedAt_reopenedTicket_clearsValue() {
        // Arrange
        Ticket ticket = Ticket.builder()
                .status(TicketStatus.IN_PROGRESS)
                .completedAt(Instant.parse("2026-10-01T10:00:00Z"))
                .build();

        // Act
        ticket.syncCompletedAt();

        // Assert
        assertThat(ticket.getCompletedAt()).isNull();
    }

    @Test
    void syncCompletedAt_openTicketWithoutValue_staysNull() {
        // Arrange
        Ticket ticket = Ticket.builder().status(TicketStatus.TODO).build();

        // Act
        ticket.syncCompletedAt();

        // Assert
        assertThat(ticket.getCompletedAt()).isNull();
    }

    @Test
    void syncCompletedAt_doneThenReopenedThenDoneAgain_setsFreshMoment() {
        // Arrange
        Instant firstFinish = Instant.parse("2026-01-01T00:00:00Z");
        Ticket ticket = Ticket.builder().status(TicketStatus.DONE).completedAt(firstFinish).build();

        // Act
        ticket.setStatus(TicketStatus.TODO);
        ticket.syncCompletedAt();
        ticket.setStatus(TicketStatus.DONE);
        ticket.syncCompletedAt();

        // Assert
        assertThat(ticket.getCompletedAt()).isAfter(firstFinish);
    }

    @Test
    void syncCompletedAt_statusDone_setsPercentageToFull() {
        // Arrange
        Ticket ticket = Ticket.builder().status(TicketStatus.DONE).percentage(40).build();

        // Act
        ticket.syncCompletedAt();

        // Assert
        assertThat(ticket.getPercentage()).isEqualTo(100);
    }

    @Test
    void syncCompletedAt_openTicket_keepsPercentage() {
        // Arrange
        Ticket ticket = Ticket.builder().status(TicketStatus.IN_PROGRESS).percentage(40).build();

        // Act
        ticket.syncCompletedAt();

        // Assert
        assertThat(ticket.getPercentage()).isEqualTo(40);
    }
}
