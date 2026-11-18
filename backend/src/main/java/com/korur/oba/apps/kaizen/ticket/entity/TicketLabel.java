package com.korur.oba.apps.kaizen.ticket.entity;

// 1. Framework-level
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// 2. Third-party
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaizen.label.entity.Label;
import com.korur.oba.dojo.entity.SoftDeleteBaseEntity;

/**
 * Join entity linking a ticket to a label, replacing the previous ManyToMany join table.
 *
 * This entity-backed join table enables soft delete on the ticket-label relationship.
 * Uniqueness on (ticket_id, label_id) is enforced at DB level via a partial unique
 * index (WHERE deleted = false).
 */
@Entity
@Table(name = "ticket_labels", schema = "kaizen")
@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TicketLabel extends SoftDeleteBaseEntity {

    // -------------Relations-----------//

    /**
     * Ticket side of the ticket-label link.
     *
     * No cascade -- ticket lifecycle is independent. This entity is owned by
     * the Ticket.ticketLabels collection.
     * Re-add: service checks for soft-deleted record and restores instead of creating duplicate.
     *
     * Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
     * LAZY is not supported in this configuration.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    /**
     * Label side of the ticket-label link.
     *
     * No cascade -- label lifecycle is independent. When the label is soft-deleted,
     * this link record keeps the label_id reference.
     *
     * Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
     * LAZY is not supported in this configuration.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "label_id", nullable = false)
    private Label label;
}
