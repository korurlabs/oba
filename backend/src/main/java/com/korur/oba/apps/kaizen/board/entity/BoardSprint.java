package com.korur.oba.apps.kaizen.board.entity;

// 1. Framework-level
import jakarta.persistence.Column;
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
import com.korur.oba.apps.kaizen.sprint.entity.Sprint;
import com.korur.oba.dojo.entity.SoftDeleteBaseEntity;

/**
 * Join entity linking a board to a sprint, replacing the previous ManyToMany join table.
 *
 * This entity-backed join table enables soft delete on the board-sprint relationship.
 * Uniqueness on (board_id, sprint_id) is enforced at DB level via a partial unique
 * index (WHERE deleted = false).
 */
@Entity
@Table(name = "board_sprints", schema = "kaizen")
@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BoardSprint extends SoftDeleteBaseEntity {

    /**
     * URL segment of the sprint on this board, built from the sprint name and unique among
     * the live links of the board, so a sprint shared by two boards can have a different
     * segment on each. It never equals a fixed segment of the board pages. Only the server
     * sets it, when the link is created or restored and when a rename changes the name's slug.
     */
    @Column(name = "slug", length = 80, nullable = false)
    private String slug;

    // -------------Relations-----------//

    /**
     * Board side of the board-sprint link.
     *
     * No cascade -- board lifecycle is independent. This entity is owned by both
     * the Board.boardSprints and Sprint.boardSprints collections.
     * Re-add: service checks for soft-deleted record and restores instead of creating duplicate.
     *
     * Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
     * LAZY is not supported in this configuration.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;

    /**
     * Sprint side of the board-sprint link.
     *
     * No cascade -- sprint lifecycle is independent. This entity is owned by both
     * the Board.boardSprints and Sprint.boardSprints collections.
     *
     * Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
     * LAZY is not supported in this configuration.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "sprint_id", nullable = false)
    private Sprint sprint;
}
