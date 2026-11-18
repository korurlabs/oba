package com.korur.oba.apps.kazan.sprint.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

// 3. Java SDK
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kazan.sprint.enums.SprintRecurrenceCreationMode;
import com.korur.oba.apps.kazan.sprint.enums.SprintRecurrenceRunStatus;
import com.korur.oba.apps.kazan.sprint.enums.SprintRecurrenceTicketAction;
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * Entity for a series of repeating sprints: its name, the round the next
 * sprint gets, the "repeat automatically" settings, the state of the job that
 * rolls it over, and the boards its new sprints are linked to.
 *
 * The series is the single source of these settings, so the sprints of one
 * series never disagree. A sprint of an enabled series is named
 * "{name} {round}". The series has no soft delete: it is never deleted, and a
 * soft-deleted sprint keeps its round, because nextRound only grows and the
 * database rejects a second sprint with the same series and round.
 */
@Entity
@Table(name = "sprint_series", schema = "kazan")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SprintSeries extends BaseEntity {

    // -------------Atts-----------//

    @Column(name = "name", length = 240, nullable = false)
    private String name;

    /**
     * Round the next sprint of the series gets; at least 1.
     */
    @Column(name = "next_round", nullable = false)
    private int nextRound;

    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private boolean enabled = false;

    /**
     * Length of every sprint the job creates, in whole days, 1 to 365.
     */
    @Column(name = "length_days", nullable = false)
    private int lengthDays;

    /**
     * Days before the current sprint's end date at which the next sprint is
     * created; set only when creationMode is DAYS_BEFORE.
     */
    @Column(name = "create_days_before", nullable = true)
    private Integer createDaysBefore;

    /**
     * When the job creates the next sprint ahead of the rollover; null when
     * creationMode is AT_END, the series is off, or the next sprint exists.
     */
    @Column(name = "next_creation_at", nullable = true)
    private Instant nextCreationAt;

    @Column(name = "last_run_at", nullable = true)
    private Instant lastRunAt;

    @Column(name = "last_run_message", length = 500, nullable = true)
    private String lastRunMessage;

    // -------------Enums-----------//

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "unfinished_ticket_action", nullable = false)
    private SprintRecurrenceTicketAction unfinishedTicketAction = SprintRecurrenceTicketAction.MOVE_TO_NEXT_SPRINT;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "creation_mode", nullable = false)
    private SprintRecurrenceCreationMode creationMode = SprintRecurrenceCreationMode.AT_END;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "last_run_status", nullable = true)
    private SprintRecurrenceRunStatus lastRunStatus;

    // -------------Relations-----------//

    /**
     * Boards the job links each new sprint of the series to, in position order.
     *
     * Cascade: ALL + orphanRemoval -- the rows exist only for this series and
     * are kept in step with the links of its unfinished sprints.
     *
     * A board is in at most one series. Only an enabled series holds its boards
     * against another series; a disabled one gives a board up when another
     * series claims it.
     */
    @Builder.Default
    @OneToMany(mappedBy = "series", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<SprintSeriesBoard> boards = new ArrayList<>();
}
