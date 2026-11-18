package com.korur.oba.apps.kaizen.project.entity;

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
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.dojo.entity.SoftDeleteBaseEntity;

/**
 * Entity representing a project within a board.
 *
 * Projects provide an additional grouping layer for tickets beyond sprints.
 */
@Entity
@Table(name = "projects", schema = "kaizen")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
public class Project extends SoftDeleteBaseEntity {

    // -------------Atts-----------//

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "color", length = 7, nullable = true)
    private String color;

    // -------------Relations-----------//

    /**
     * Board this project belongs to.
     *
     * No cascade -- board lifecycle is independent. This entity is owned
     * by the board and managed via projectRepository.
     *
     * Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
     * LAZY is not supported in this configuration.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;

    /**
     * Tickets grouped under this project.
     *
     * No cascade -- tickets reference the project but are not owned by it.
     * When the project is soft-deleted, tickets keep the project_id reference
     * but Hibernate filters exclude the deleted project from queries.
     */
    @Builder.Default
    @OneToMany(mappedBy = "project")
    private List<Ticket> tickets = new ArrayList<>();
}
