package com.korur.oba.apps.kazan.board.entity;

// 1. Framework-level
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.backlog.entity.Backlog;
import com.korur.oba.apps.kazan.label.entity.Label;
import com.korur.oba.apps.kazan.project.entity.Project;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.dojo.entity.SoftDeleteBaseEntity;

/**
 * Entity representing a board that organizes tickets, sprints, and projects.
 *
 * Boards can be organized in a hierarchy using the parent-child self-reference.
 * Each board has a unique prefix used for generating ticket keys.
 */
@Entity
@Table(name = "boards", schema = "kazan")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
public class Board extends SoftDeleteBaseEntity {

    // -------------Atts-----------//

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "description", length = 512, nullable = true)
    private String description;

    @Column(name = "prefix", length = 10, nullable = false)
    private String prefix;

    /**
     * URL segment of the board in the web app, built from the name and unique among live
     * boards. Only the server sets it, on create, on a rename that changes the name's slug
     * and on a restore whose slug a live board took meanwhile.
     */
    @Column(name = "slug", length = 80, nullable = false)
    private String slug;

    @Column(name = "color", length = 7, nullable = true)
    private String color;

    @Column(name = "icon", length = 50, nullable = true)
    private String icon;

    @Column(name = "ticket_counter", nullable = false)
    @Builder.Default
    private long ticketCounter = 0L;

    /**
     * Organization that owns the board and everything under it. Set once on create from the
     * request's organization, never from a request body; a child board always has its
     * parent's organization, which the database enforces.
     */
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    // -------------Relations-----------//

    /**
     * Parent board in the board hierarchy.
     *
     * No cascade -- parent lifecycle is independent. Service handles
     * child board actions (promote/move/delete) when the parent is deleted.
     *
     * Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
     * LAZY is not supported in this configuration.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "parent_board_id", nullable = true)
    private Board parentBoard;

    /**
     * Child boards in the board hierarchy.
     *
     * No cascade -- service handles child board actions (promote/move/delete)
     * when this board is deleted. Children are not automatically soft-deleted.
     * Restore: not applicable, children are handled before parent deletion.
     */
    @Builder.Default
    @OneToMany(mappedBy = "parentBoard")
    private List<Board> childBoards = new ArrayList<>();

    /**
     * Members of this board with their assigned roles.
     *
     * Cascade: {PERSIST, MERGE, REMOVE} + orphanRemoval -- members are soft-deleted
     * when the board is soft-deleted or when removed from the collection.
     * Restore: members (with specialties) are restored when the board is restored.
     * Re-add: service checks for soft-deleted record and restores instead of creating duplicate.
     */
    @Builder.Default
    @OneToMany(mappedBy = "board",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE},
            orphanRemoval = true)
    private List<BoardMember> members = new ArrayList<>();

    /**
     * Links between this board and its sprints.
     *
     * Cascade: {PERSIST, MERGE, REMOVE} + orphanRemoval -- board-sprint links are
     * soft-deleted when the board is soft-deleted or when a sprint is unlinked.
     * Restore: board-sprint links are restored when the board is restored.
     * Re-add: service checks for soft-deleted record and restores instead of creating duplicate.
     */
    @Builder.Default
    @OneToMany(mappedBy = "board",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE},
            orphanRemoval = true)
    private List<BoardSprint> boardSprints = new ArrayList<>();

    /**
     * Backlog container for unassigned tickets.
     *
     * Cascade: {PERSIST, MERGE, REMOVE} -- backlog is soft-deleted when the board
     * is soft-deleted. One-to-one lifecycle fully tied to the board.
     * Restore: backlog is restored when the board is restored.
     */
    @OneToOne(mappedBy = "board",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE})
    private Backlog backlog;

    /**
     * Tickets belonging to this board.
     *
     * Cascade: {PERSIST, MERGE} -- tickets are managed via ticketRepository.
     * Service handles ticket move/delete actions before board deletion.
     * Restore: tickets are explicitly restored by the service when the board is restored.
     */
    @Builder.Default
    @OneToMany(mappedBy = "board",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<Ticket> tickets = new ArrayList<>();

    /**
     * Projects providing additional grouping for tickets within this board.
     *
     * Cascade: {PERSIST, MERGE} -- projects are managed via projectRepository.
     * Service explicitly soft-deletes projects before board deletion.
     * Restore: projects are explicitly restored by the service when the board is restored.
     */
    @Builder.Default
    @OneToMany(mappedBy = "board",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<Project> projects = new ArrayList<>();

    /**
     * Labels scoped to this board for tagging tickets.
     *
     * Cascade: {REMOVE} -- labels are created via labelRepository; only REMOVE is
     * needed to cascade soft-delete when the board is soft-deleted.
     * Restore: labels are explicitly restored by the service when the board is restored.
     * Re-add: service checks for soft-deleted record and restores instead of creating duplicate.
     */
    @Builder.Default
    @OneToMany(mappedBy = "board",
            cascade = {CascadeType.REMOVE})
    private List<Label> labels = new ArrayList<>();
}
