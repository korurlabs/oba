package com.korur.oba.apps.kaizen.ticket.entity;

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
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

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
import com.korur.oba.apps.kaizen.backlog.entity.Backlog;
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.comment.entity.Comment;
import com.korur.oba.apps.kaizen.milestone.entity.Milestone;
import com.korur.oba.apps.kaizen.project.entity.Project;
import com.korur.oba.apps.kaizen.sprint.entity.Sprint;
import com.korur.oba.apps.kaizen.systemactor.entity.SystemActor;
import com.korur.oba.apps.kaizen.ticket.enums.MissionRank;
import com.korur.oba.apps.kaizen.ticket.enums.TicketPriority;
import com.korur.oba.apps.kaizen.ticket.enums.TicketStatus;
import com.korur.oba.apps.kaizen.ticket.enums.TicketType;
import com.korur.oba.dojo.entity.SoftDeleteBaseEntity;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Entity representing a work item within a board.
 *
 * Tickets are the primary unit of work and can be organized into sprints,
 * backlogs, and projects. They support hierarchical sub-tickets, assignees,
 * reviewers, comments, and labels.
 *
 * Uniqueness on key is enforced at DB level via a partial unique index
 * (WHERE deleted = false).
 */
@Entity
@Table(name = "tickets", schema = "kaizen")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
public class Ticket extends SoftDeleteBaseEntity {

    // -------------Atts-----------//

    @Column(name = "title", length = 500, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT", nullable = true)
    private String description;

    @Column(name = "key", length = 50, nullable = false)
    private String key;

    @Builder.Default
    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "percentage", nullable = false)
    @Builder.Default
    private int percentage = 0;

    @Column(name = "weight", nullable = true)
    private Integer weight;

    // Root Cause Analysis
    // This explains the root cause of the issue and why it happened.
    @Column(name = "rca", columnDefinition = "TEXT", nullable = true)
    private String rca;

    // Lessons Learned
    // "What will we change to prevent this from happening again?"
    @Column(name = "lessons_learned", columnDefinition = "TEXT", nullable = true)
    private String lessonsLearned;

    @Column(name = "due_date", nullable = true)
    private Instant dueDate;

    // The key an external monitoring source gives an incident, used to find
    // the incident's open ticket.
    @Column(name = "incident_key", length = 64, nullable = true)
    private String incidentKey;

    /**
     * The moment the ticket last reached DONE, or null while it is not DONE. The home
     * dashboard reads it to place finished work on a day, so it is kept by
     * {@link #syncCompletedAt()} on every write of the entity. A bulk UPDATE of status
     * bypasses that callback and must set this column itself.
     */
    @Column(name = "completed_at", nullable = true)
    private Instant completedAt;

    // -------------Enums-----------//

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "mission_rank", nullable = false)
    private MissionRank missionRank;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "type", nullable = false)
    private TicketType type;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", nullable = false)
    private TicketStatus status = TicketStatus.TODO;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "priority", nullable = false)
    private TicketPriority priority = TicketPriority.MEDIUM;

    // -------------Relations-----------//

    /**
     * Board this ticket belongs to.
     *
     * No cascade -- board lifecycle is independent. Ticket references the
     * board but does not own it.
     *
     * Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
     * LAZY is not supported in this configuration.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;

    /**
     * Sprint this ticket is assigned to, or null if in the backlog.
     *
     * No cascade -- sprint lifecycle is independent. Service manages
     * ticket-sprint assignment.
     *
     * Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
     * LAZY is not supported in this configuration.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sprint_id", nullable = true)
    private Sprint sprint;

    /**
     * Backlog this ticket belongs to, or null if assigned to a sprint.
     *
     * No cascade -- backlog lifecycle is tied to the board, not the ticket.
     *
     * Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
     * LAZY is not supported in this configuration.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "backlog_id", nullable = true)
    private Backlog backlog;

    /**
     * Parent ticket for sub-ticket hierarchy.
     *
     * No cascade -- parent ticket lifecycle is independent.
     * Deleting a parent cascades through the subTickets collection, not this reference.
     *
     * Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
     * LAZY is not supported in this configuration.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "parent_ticket_id", nullable = true)
    private Ticket parentTicket;

    /**
     * User who created this ticket; null when a system actor created it.
     * Exactly one of createdBy and createdBySystemActor is set.
     *
     * No cascade -- user lifecycle is independent of the ticket.
     *
     * Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
     * LAZY is not supported in this configuration.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by_id", nullable = true)
    private User createdBy;

    /**
     * The system actor that created this ticket; null when a user created it.
     * Exactly one of createdBy and createdBySystemActor is set.
     *
     * No cascade -- a system actor outlives the tickets it creates.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by_system_actor_id", nullable = true)
    private SystemActor createdBySystemActor;

    /**
     * Sub-tickets forming a hierarchical work breakdown.
     *
     * Cascade: {REMOVE} -- sub-tickets are recursively soft-deleted when the
     * parent ticket is soft-deleted. No orphanRemoval to allow promoting sub-tickets.
     * Restore: sub-tickets (and their children) are recursively restored by the service.
     *
     * Order: the board display order, then creation time, then id. Display order is one
     * sequence per board and a new ticket is appended at the end, so sub-tickets that were
     * never reordered come back in ticket number order. Creation time breaks ties because
     * display order is not unique, and the id breaks the remaining ties because two rows
     * can share a creation time, which makes the order total.
     */
    @Builder.Default
    @OneToMany(mappedBy = "parentTicket",
            cascade = {CascadeType.REMOVE})
    @OrderBy("displayOrder ASC, createdAt ASC, id ASC")
    private List<Ticket> subTickets = new ArrayList<>();

    /**
     * Users assigned to work on this ticket.
     *
     * Cascade: {PERSIST, MERGE, REMOVE} + orphanRemoval -- assignees are soft-deleted
     * when the ticket is soft-deleted or when removed from the collection.
     * Restore: assignees are restored when the ticket is restored.
     * Re-add: service checks for soft-deleted record and restores instead of creating duplicate.
     */
    @Builder.Default
    @OneToMany(mappedBy = "ticket",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE},
            orphanRemoval = true)
    private List<TicketAssignee> assignees = new ArrayList<>();

    /**
     * Users assigned to review this ticket.
     *
     * Cascade: {PERSIST, MERGE, REMOVE} + orphanRemoval -- reviewers are soft-deleted
     * when the ticket is soft-deleted or when removed from the collection.
     * Restore: reviewers are restored when the ticket is restored.
     * Re-add: service checks for soft-deleted record and restores instead of creating duplicate.
     */
    @Builder.Default
    @OneToMany(mappedBy = "ticket",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE},
            orphanRemoval = true)
    private List<TicketReviewer> reviewers = new ArrayList<>();

    /**
     * Project this ticket is grouped under, or null if ungrouped.
     *
     * No cascade -- project lifecycle is managed by the board, not the ticket.
     *
     * Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
     * LAZY is not supported in this configuration.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "project_id", nullable = true)
    private Project project;

    /**
     * Milestone this ticket counts towards, or null if none.
     *
     * No cascade -- the milestone lifecycle is managed by its board. The milestone
     * must live on this ticket's board or on a parent, child or sibling board;
     * a move to an unrelated board clears it.
     *
     * Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
     * LAZY is not supported in this configuration.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "milestone_id", nullable = true)
    private Milestone milestone;

    /**
     * Discussion comments on this ticket.
     *
     * Cascade: {PERSIST, MERGE, REMOVE} + orphanRemoval -- comments are soft-deleted
     * when the ticket is soft-deleted or when removed from the collection.
     * Restore: comments are restored when the ticket is restored.
     */
    @Builder.Default
    @OneToMany(mappedBy = "ticket",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE},
            orphanRemoval = true)
    private List<Comment> comments = new ArrayList<>();

    /**
     * Labels applied to this ticket via the TicketLabel join entity.
     *
     * Cascade: {PERSIST, MERGE, REMOVE} + orphanRemoval -- ticket-label links are
     * soft-deleted when the ticket is soft-deleted or when a label is unlinked.
     * Restore: ticket-label links are restored when the ticket is restored.
     * Re-add: service checks for soft-deleted record and restores instead of creating duplicate.
     */
    @Builder.Default
    @OneToMany(mappedBy = "ticket",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE},
            orphanRemoval = true)
    private List<TicketLabel> ticketLabels = new ArrayList<>();

    // -------------Callbacks-----------//

    /**
     * Keeps {@link #completedAt} and {@link #percentage} in step with the status on insert
     * and update, so every status path (update, move, bulk move, backlog normalizer, sprint
     * takeover) records the finish moment and full progress without code of its own. A
     * DONE ticket is always at 100 percent. A ticket that stays DONE keeps its first finish
     * moment; a reopened ticket loses it and gets a new one when it is finished again, and
     * keeps its percentage until someone changes it.
     */
    @PrePersist
    @PreUpdate
    void syncCompletedAt() {
        if (status == TicketStatus.DONE) {
            percentage = 100;
            if (completedAt == null) {
                completedAt = Instant.now();
            }
        } else {
            completedAt = null;
        }
    }
}
