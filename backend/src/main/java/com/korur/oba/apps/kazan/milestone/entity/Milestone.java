package com.korur.oba.apps.kazan.milestone.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

// 3. Java SDK
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.milestone.enums.MilestoneStatus;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.dojo.entity.SoftDeleteBaseEntity;

/**
 * Entity representing a dated target owned by a board.
 *
 * Tickets of the board, or of a parent, child or sibling board, attach to a
 * milestone. Progress and health are derived from those tickets at read time
 * and are not stored. The name is not unique: two milestones of a board may
 * share a name, and only the id identifies a milestone.
 */
@Entity
@Table(name = "milestones", schema = "kazan")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
public class Milestone extends SoftDeleteBaseEntity {

    // -------------Atts-----------//

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "description", length = 2000, nullable = true)
    private String description;

    @Column(name = "start_date", nullable = true)
    private LocalDate startDate;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    /**
     * Bar colour on the roadmap, in the format of Board.color. Defaults to the
     * board's colour on create; null when the board had none.
     */
    @Column(name = "color", length = 7, nullable = true)
    private String color;

    // -------------Enums-----------//

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", nullable = false)
    private MilestoneStatus status = MilestoneStatus.OPEN;

    // -------------Relations-----------//

    /**
     * Board this milestone belongs to.
     *
     * No cascade -- board lifecycle is independent. The board service
     * soft-deletes and restores milestones explicitly.
     *
     * Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
     * LAZY is not supported in this configuration.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;

    /**
     * Tickets attached to this milestone.
     *
     * No cascade -- tickets are not owned by the milestone. Deleting a milestone
     * detaches its tickets first, so no ticket points at a deleted milestone.
     */
    @Builder.Default
    @OneToMany(mappedBy = "milestone")
    private List<Ticket> tickets = new ArrayList<>();
}
