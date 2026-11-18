package com.korur.oba.apps.kazan.ticket.entity;

// 1. Framework-level
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// 2. Third-party
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.entity.SoftDeleteBaseEntity;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Join entity representing a user assigned to work on a ticket.
 *
 * Uniqueness on (ticket_id, user_id) is enforced at DB level
 * via a partial unique index (WHERE deleted = false).
 */
@Entity
@Table(name = "ticket_assignees", schema = "kazan")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
public class TicketAssignee extends SoftDeleteBaseEntity {

    // -------------Relations-----------//

    /**
     * Ticket this assignment belongs to.
     *
     * No cascade -- ticket lifecycle is independent. This entity is owned by
     * the ticket via the Ticket.assignees collection.
     * Re-add: service checks for soft-deleted record and restores instead of creating duplicate.
     *
     * Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
     * LAZY is not supported in this configuration.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    /**
     * User assigned to the ticket.
     *
     * No cascade -- user lifecycle is independent of the assignment.
     *
     * Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
     * LAZY is not supported in this configuration.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
