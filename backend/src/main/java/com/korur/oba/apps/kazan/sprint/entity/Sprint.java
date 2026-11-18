package com.korur.oba.apps.kazan.sprint.entity;

// 1. Framework-level
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

// 2. Third-party
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

// 3. Java SDK
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kazan.board.entity.BoardSprint;
import com.korur.oba.apps.kazan.sprint.enums.SprintStatus;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.dojo.entity.SoftDeleteBaseEntity;

/**
 * Entity representing a time-boxed iteration within a board.
 *
 * Sprints group tickets into focused work periods with a defined goal. A
 * sprint belongs to one or more boards through BoardSprint links. A sprint
 * that repeats automatically belongs to a SprintSeries and has a round, its
 * place in that series; series and round are both set or both null, and the
 * database rejects two sprints with the same series and round.
 */
@Entity
@Table(name = "sprints", schema = "kazan")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
public class Sprint extends SoftDeleteBaseEntity {

    // -------------Atts-----------//

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "goal", length = 512, nullable = true)
    private String goal;

    @Column(name = "start_date", nullable = true)
    private Instant startDate;

    @Column(name = "end_date", nullable = true)
    private Instant endDate;

    /**
     * Place of the sprint in its series, from 1; null when the sprint has no series.
     */
    @Column(name = "round", nullable = true)
    private Integer round;

    /**
     * Optimistic locking counter. A user's edit and the recurrence job can load
     * the same sprint and save it one after the other; without the counter the
     * later save would silently put back the dates or status it read before the
     * earlier one committed. With it the later save fails and is retried or
     * reported as a conflict. Not part of any request or response.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    // -------------Enums-----------//

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", nullable = false)
    private SprintStatus status = SprintStatus.PLANNED;

    // -------------Relations-----------//

    /**
     * Series of repeating sprints this sprint belongs to; null when it does not repeat.
     *
     * No cascade -- the series is created and changed by the sprint service.
     * Fetch: LAZY -- SprintSeries has no soft delete, so Hibernate allows it,
     * and sprint lists do not load the series.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id", nullable = true)
    private SprintSeries series;

    /**
     * Links between this sprint and its boards.
     *
     * Cascade: {PERSIST, MERGE, REMOVE} + orphanRemoval -- board-sprint links are
     * soft-deleted when the sprint is soft-deleted or when a board is unlinked.
     * Restore: board-sprint links are restored when the sprint is restored.
     * Re-add: service checks for soft-deleted record and restores instead of creating duplicate.
     */
    @Builder.Default
    @OneToMany(mappedBy = "sprint",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE},
            orphanRemoval = true)
    private List<BoardSprint> boardSprints = new ArrayList<>();

    /**
     * Tickets assigned to this sprint.
     *
     * No cascade -- service handles ticket actions (move/delete) before sprint
     * deletion. Tickets are not automatically soft-deleted with the sprint.
     */
    @Builder.Default
    @OneToMany(mappedBy = "sprint")
    private List<Ticket> tickets = new ArrayList<>();
}
