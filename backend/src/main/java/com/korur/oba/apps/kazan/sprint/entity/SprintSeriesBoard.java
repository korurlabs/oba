package com.korur.oba.apps.kazan.sprint.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * Join entity linking a sprint series to one of the boards its new sprints are
 * linked to, with the board's place in the link order.
 *
 * A board belongs to at most one series, enforced by a unique constraint on
 * board_id, so exactly one series creates the board's next sprints.
 */
@Entity
@Table(name = "sprint_series_boards", schema = "kazan")
@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SprintSeriesBoard extends BaseEntity {

    // -------------Atts-----------//

    /**
     * Place of the board in the link order of a new sprint, starting at 0.
     */
    @Column(name = "position", nullable = false)
    private int position;

    // -------------Relations-----------//

    /**
     * Series this board belongs to.
     *
     * No cascade -- the series owns these rows through its boards collection.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "series_id", nullable = false)
    private SprintSeries series;

    /**
     * Board new sprints of the series are linked to.
     *
     * No cascade -- board lifecycle is independent; the job skips a
     * soft-deleted board.
     *
     * Fetch: EAGER -- Hibernate 6 does not support a LAZY to-one association to
     * an entity that uses @SoftDelete.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;
}
