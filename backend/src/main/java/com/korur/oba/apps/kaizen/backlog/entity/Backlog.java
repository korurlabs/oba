package com.korur.oba.apps.kaizen.backlog.entity;

// 1. Framework-level
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
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
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.dojo.entity.SoftDeleteBaseEntity;

/**
 * Entity representing the backlog of a board.
 *
 * The backlog holds tickets that are not yet assigned to any sprint.
 * Each board has exactly one backlog (one-to-one relationship).
 */
@Entity
@Table(name = "backlogs", schema = "kaizen")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
public class Backlog extends SoftDeleteBaseEntity {

    // -------------Relations-----------//

    /**
     * Board this backlog belongs to.
     *
     * No cascade -- board lifecycle is independent. The backlog is owned by
     * the board via the Board.backlog field with REMOVE cascade.
     *
     * Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
     * LAZY is not supported in this configuration.
     */
    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;

    /**
     * Tickets in this backlog that are not yet assigned to a sprint.
     *
     * No cascade -- tickets reference the backlog but are not owned by it.
     * Service manages ticket placement between backlog and sprints.
     */
    @Builder.Default
    @OneToMany(mappedBy = "backlog")
    private List<Ticket> tickets = new ArrayList<>();
}
