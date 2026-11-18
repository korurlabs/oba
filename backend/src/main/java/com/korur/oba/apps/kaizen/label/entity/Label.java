package com.korur.oba.apps.kaizen.label.entity;

// 1. Framework-level
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

// 2. Third-party
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.ticket.entity.TicketLabel;
import com.korur.oba.dojo.entity.SoftDeleteBaseEntity;

/**
 * Entity representing a label that can be applied to tickets.
 *
 * Labels are scoped to a board and provide a flexible tagging system for tickets.
 * Uniqueness on (board_id, name) is enforced at DB level via a partial unique
 * index (WHERE deleted = false).
 */
@Entity
@Table(name = "labels", schema = "kaizen")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
public class Label extends SoftDeleteBaseEntity {

    // -------------Atts-----------//

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "color", length = 7, nullable = true)
    private String color;

    // -------------Relations-----------//

    /**
     * Board this label is scoped to.
     *
     * No cascade -- board lifecycle is independent. This entity is owned by
     * the board via the Board.labels collection with REMOVE cascade.
     * Re-add: service checks for soft-deleted record and restores instead of creating duplicate.
     *
     * Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
     * LAZY is not supported in this configuration.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;

    /**
     * Ticket-label links referencing this label.
     *
     * No cascade -- ticket-label links are owned by the ticket side.
     * When this label is soft-deleted, existing TicketLabel records keep
     * the label_id reference but the label is filtered from queries.
     */
    @Builder.Default
    @OneToMany(mappedBy = "label")
    private List<TicketLabel> ticketLabels = new ArrayList<>();
}
