package com.korur.oba.apps.kazan.ticket.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.milestone.dto.projection.MilestoneTicketCountProjection;
import com.korur.oba.apps.kazan.ticket.dto.projection.LeaderboardTicketProjection;
import com.korur.oba.apps.kazan.ticket.dto.projection.SprintListRootProjection;
import com.korur.oba.apps.kazan.ticket.dto.projection.StaleBacklogTicketProjection;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.enums.TicketStatus;
import com.korur.oba.apps.kazan.ticket.enums.TicketType;
import com.korur.oba.dojo.repository.SoftDeleteRepository;

/**
 * Repository interface for Ticket entity database operations.
 * <p>
 * Extends JpaRepository to provide standard CRUD operations and
 * JpaSpecificationExecutor to support dynamic query building via
 * specifications.
 * Defines custom query methods for ticket queries by backlog, sprint, board,
 * and parent ticket.
 * <p>
 * Query methods follow Spring Data JPA naming conventions for automatic
 * implementation generation.
 */
@Repository
public interface TicketRepository extends SoftDeleteRepository<Ticket, UUID>,
        JpaSpecificationExecutor<Ticket> {

    /**
     * Finds all tickets belonging to the specified backlog.
     *
     * @param backlogId ID of the backlog
     * @return list of tickets
     */
    List<Ticket> findByBacklog_Id(UUID backlogId);

    /**
     * Finds all tickets belonging to the specified sprint.
     *
     * @param sprintId ID of the sprint
     * @return list of tickets
     */
    List<Ticket> findBySprint_Id(UUID sprintId);

    /**
     * Finds all tickets belonging to the specified board.
     *
     * @param boardId ID of the board
     * @return list of tickets
     */
    List<Ticket> findByBoard_Id(UUID boardId);

    /**
     * Finds all tickets belonging to the specified board with IDs in the given list.
     *
     * @param boardId   ID of the board
     * @param ticketIds list of ticket IDs
     * @return list of matching tickets
     */
    List<Ticket> findByBoard_IdAndIdIn(UUID boardId, List<UUID> ticketIds);

    /**
     * Finds a ticket by board and ID.
     *
     * @param boardId  ID of the board
     * @param ticketId ID of the ticket
     * @return optional ticket if found
     */
    Optional<Ticket> findByBoardIdAndId(UUID boardId, UUID ticketId);

    /**
     * Finds the newest ticket of a board that carries the given incident key
     * and is not in the given status. Called with DONE to find the open ticket
     * of an incident; soft-deleted tickets are never returned.
     *
     * @param boardId     ID of the board
     * @param incidentKey key the external monitoring source gave the incident
     * @param status      status the ticket must not have
     * @return optional newest matching ticket
     */
    Optional<Ticket> findFirstByBoard_IdAndIncidentKeyAndStatusNotOrderByCreatedAtDesc(
            UUID boardId, String incidentKey, TicketStatus status);

    /**
     * Counts the number of tickets belonging to the specified board.
     *
     * @param boardId ID of the board
     * @return count of tickets
     */
    long countByBoard_Id(UUID boardId);

    /**
     * Finds the maximum display order for tickets in the specified board.
     *
     * @param boardId ID of the board
     * @return maximum display order
     */
    @Query("""
            SELECT COALESCE(MAX(t.displayOrder),0) FROM Ticket t WHERE t.board.id = :boardId
            """)
    int maxDisplayOrderByBoard_Id(UUID boardId);

    /**
     * Finds all sub-tickets of the specified parent ticket.
     *
     * @param parentTicketId ID of the parent ticket
     * @return list of child tickets
     */
    List<Ticket> findByParentTicket_Id(UUID parentTicketId);

    /**
     * Finds all sub-tickets whose parent ticket ID is in the given list.
     *
     * @param parentTicketIds list of parent ticket IDs
     * @return list of child tickets
     */
    List<Ticket> findByParentTicket_IdIn(List<UUID> parentTicketIds);

    /**
     * Finds all tickets in the specified sprint that do not have the given status.
     * <p>
     * Useful for finding incomplete tickets when completing a sprint.
     *
     * @param sprintId ID of the sprint
     * @param status   status to exclude
     * @return list of tickets not matching the given status
     */
    List<Ticket> findBySprint_IdAndStatusNot(UUID sprintId, TicketStatus status);

    /**
     * Counts tickets on a board assigned to a specific user excluding a given
     * status.
     * Used by the workload-balancing assignment algorithm.
     *
     * @param boardId the board identifier
     * @param userId  the assignee's user identifier
     * @param status  the status to exclude (typically DONE)
     * @return count of matching tickets
     */
    long countByBoard_IdAndAssignees_User_IdAndStatusNot(UUID boardId, UUID userId, TicketStatus status);

    /**
     * Counts the top-level tickets of a type on any of the given boards, excluding a given
     * status. Sub-tickets are left out, so the count matches the ticket list, which hides them.
     *
     * The parent is joined explicitly with LEFT JOIN. A derived {@code ParentTicketIsNull}
     * condition makes Hibernate inner-join the soft-deleted parent {@code Ticket} in a count
     * query, so "parent is null" could never hold and the home summary always showed zero
     * open incidents.
     *
     * @param boardIds the boards to count on; must not be empty
     * @param type     the ticket type to count
     * @param status   the status to exclude (typically DONE)
     * @return count of matching tickets
     */
    @Query("""
            SELECT COUNT(t) FROM Ticket t
            LEFT JOIN t.parentTicket p
            WHERE t.board.id IN :boardIds
              AND t.type = :type
              AND t.status <> :status
              AND p.id IS NULL
            """)
    long countRootsByBoardIdsAndTypeAndStatusNot(
            @Param("boardIds") Collection<UUID> boardIds,
            @Param("type") TicketType type,
            @Param("status") TicketStatus status);

    /**
     * Counts the list roots on any of the given boards that are in any of the given
     * sprints, excluding a given status. A list root is a ticket whose parent is null or
     * is not in the same sprint, so a sub-ticket planned without its parent counts as
     * sprint work and its parent does not, matching the sprint list.
     *
     * <p>The parent's sprint is joined explicitly with LEFT JOIN. A path such as
     * {@code p.sprint.id} in the WHERE clause makes Hibernate inner-join the soft-deleted
     * {@code Sprint} entity, which dropped every ticket without a parent and every ticket
     * whose parent has no sprint, so the home summary undercounted sprint work.
     *
     * @param boardIds  the boards to count on; must not be empty
     * @param sprintIds the sprints to count in; must not be empty
     * @param status    the status to exclude (typically DONE)
     * @return count of matching tickets
     */
    @Query("""
            SELECT COUNT(t) FROM Ticket t
            LEFT JOIN t.parentTicket p
            LEFT JOIN p.sprint ps
            WHERE t.board.id IN :boardIds
              AND t.sprint.id IN :sprintIds
              AND t.status <> :status
              AND (ps.id IS NULL OR ps.id <> t.sprint.id)
            """)
    long countSprintListRootsByBoardIdsAndSprintIdsAndStatusNot(
            @Param("boardIds") Collection<UUID> boardIds,
            @Param("sprintIds") Collection<UUID> sprintIds,
            @Param("status") TicketStatus status);

    /**
     * Shifts display orders up (+1) for tickets in the range [newOrder, oldOrder)
     * on the given board, excluding the ticket being moved.
     * Used when a ticket moves to a lower (earlier) position.
     *
     * @param boardId  ID of the board
     * @param newOrder the new (lower) display order position
     * @param oldOrder the old (higher) display order position
     * @param ticketId ID of the ticket being moved (excluded from shift)
     * @return number of tickets updated
     */
    @Modifying
    @Query("""
                UPDATE Ticket t
                SET t.displayOrder = t.displayOrder + 1
                WHERE t.board.id = :boardId
                  AND t.displayOrder >= :newOrder
                  AND t.displayOrder < :oldOrder
                  AND t.id <> :ticketId
            """)
    int shiftDisplayOrderUp(
            @Param("boardId") UUID boardId,
            @Param("newOrder") int newOrder,
            @Param("oldOrder") int oldOrder,
            @Param("ticketId") UUID ticketId);

    /**
     * Shifts display orders down (-1) for tickets in the range (oldOrder, newOrder]
     * on the given board, excluding the ticket being moved.
     * Used when a ticket moves to a higher (later) position.
     *
     * @param boardId  ID of the board
     * @param oldOrder the old (lower) display order position
     * @param newOrder the new (higher) display order position
     * @param ticketId ID of the ticket being moved (excluded from shift)
     * @return number of tickets updated
     */
    @Modifying
    @Query("""
                UPDATE Ticket t
                SET t.displayOrder = t.displayOrder - 1
                WHERE t.board.id = :boardId
                  AND t.displayOrder > :oldOrder
                  AND t.displayOrder <= :newOrder
                  AND t.id <> :ticketId
            """)
    int shiftDisplayOrderDown(
            @Param("boardId") UUID boardId,
            @Param("oldOrder") int oldOrder,
            @Param("newOrder") int newOrder,
            @Param("ticketId") UUID ticketId);

    /**
     * Finds all soft-deleted sub-tickets of a parent ticket.
     * Uses native query to bypass the Hibernate @SoftDelete filter.
     *
     * @param parentTicketId ID of the parent ticket
     * @return list of deleted child tickets
     */
    @Query(value = """
            SELECT * FROM kazan.tickets
            WHERE parent_ticket_id = :parentTicketId AND deleted = true
            """, nativeQuery = true)
    List<Ticket> findDeletedByParentTicketId(@Param("parentTicketId") UUID parentTicketId);

    /**
     * Finds all soft-deleted tickets for the specified board.
     * Uses native query to bypass the Hibernate @SoftDelete filter.
     *
     * @param boardId ID of the board
     * @return list of deleted tickets
     */
    @Query(value = """
            SELECT * FROM kazan.tickets
            WHERE board_id = :boardId AND deleted = true
            """, nativeQuery = true)
    List<Ticket> findDeletedByBoardId(@Param("boardId") UUID boardId);

    /**
     * Restores all soft-deleted tickets for the specified board.
     *
     * @param boardId ID of the board
     * @return number of rows updated
     */
    @Modifying
    @Query(value = """
            UPDATE kazan.tickets SET deleted = false
            WHERE board_id = :boardId AND deleted = true
            """, nativeQuery = true)
    int restoreAllByBoardId(@Param("boardId") UUID boardId);

    /**
     * Counts the non-deleted tickets of each milestone, sub-tickets included,
     * and how many of them are DONE. Milestones without tickets have no row.
     *
     * @param milestoneIds IDs of the milestones, not empty
     * @return one row per milestone that has tickets
     */
    @Query(value = """
            SELECT t.milestone_id AS milestoneId,
                   COUNT(*) AS totalCount,
                   COUNT(*) FILTER (WHERE t.status = 'DONE') AS doneCount
            FROM kazan.tickets t
            WHERE t.milestone_id IN (:milestoneIds)
              AND t.deleted = false
            GROUP BY t.milestone_id
            """, nativeQuery = true)
    List<MilestoneTicketCountProjection> countByMilestoneIds(@Param("milestoneIds") List<UUID> milestoneIds);

    /**
     * Detaches every ticket from the milestones, deleted tickets included, so
     * that no ticket points at a deleted milestone.
     *
     * @param milestoneIds IDs of the milestones, not empty
     * @return number of rows updated
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE kazan.tickets SET milestone_id = NULL
            WHERE milestone_id IN (:milestoneIds)
            """, nativeQuery = true)
    int clearMilestoneByMilestoneIds(@Param("milestoneIds") List<UUID> milestoneIds);

    /**
     * Lists the stale backlog tickets on the given boards, least recently active first.
     * A ticket is stale when it is in a backlog and in no sprint, is not DONE, and its last
     * activity, the later of its last edit and its newest live comment, is before the
     * cutoff. Sub-tickets are included, because each is a backlog item of its own.
     *
     * @param boardIds the boards to read; must not be empty
     * @param cutoff   the moment before which the last activity must lie
     * @param limit    the maximum number of rows
     * @return at most limit tickets, ordered by last activity, then key
     */
    @Query(value = """
            SELECT t.id AS id,
                   t.key AS key,
                   t.title AS title,
                   CAST(t.status AS text) AS status,
                   CAST(t.priority AS text) AS priority,
                   CAST(t.mission_rank AS text) AS missionRank,
                   b.id AS boardId,
                   b.name AS boardName,
                   b.slug AS boardSlug,
                   a.last_activity_at AS lastActivityAt
            FROM kazan.tickets t
            JOIN kazan.boards b ON b.id = t.board_id AND b.deleted = false
            CROSS JOIN LATERAL (
                SELECT GREATEST(t.updated_at, MAX(c.created_at)) AS last_activity_at
                FROM kazan.comments c
                WHERE c.ticket_id = t.id
                  AND c.deleted = false
            ) a
            WHERE t.deleted = false
              AND t.board_id IN (:boardIds)
              AND t.sprint_id IS NULL
              AND t.backlog_id IS NOT NULL
              AND t.status <> 'DONE'
              AND a.last_activity_at < :cutoff
            ORDER BY a.last_activity_at ASC, t.key ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<StaleBacklogTicketProjection> findStaleBacklogTickets(@Param("boardIds") Collection<UUID> boardIds,
                                                              @Param("cutoff") Instant cutoff,
                                                              @Param("limit") int limit);

    /**
     * Counts the stale backlog tickets on the given boards, by the same rule as
     * {@link #findStaleBacklogTickets}, without its row limit.
     *
     * @param boardIds the boards to read; must not be empty
     * @param cutoff   the moment before which the last activity must lie
     * @return the number of stale backlog tickets
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM kazan.tickets t
            JOIN kazan.boards b ON b.id = t.board_id AND b.deleted = false
            CROSS JOIN LATERAL (
                SELECT GREATEST(t.updated_at, MAX(c.created_at)) AS last_activity_at
                FROM kazan.comments c
                WHERE c.ticket_id = t.id
                  AND c.deleted = false
            ) a
            WHERE t.deleted = false
              AND t.board_id IN (:boardIds)
              AND t.sprint_id IS NULL
              AND t.backlog_id IS NOT NULL
              AND t.status <> 'DONE'
              AND a.last_activity_at < :cutoff
            """, nativeQuery = true)
    long countStaleBacklogTickets(@Param("boardIds") Collection<UUID> boardIds,
                                  @Param("cutoff") Instant cutoff);

    /**
     * Lists one row per live assignment of every ticket on the given boards that reached
     * DONE within [from, to), sub-tickets included, with the ticket's assignee count so its
     * points can be split. Deleted users and their assignments are left out, so a ticket
     * without a live assignee has no row.
     *
     * @param boardIds the boards to read; must not be empty
     * @param from     the start of the window, inclusive
     * @param to       the end of the window, exclusive
     * @return the assignments of the tickets finished in the window, in no particular order
     */
    @Query(value = """
            SELECT t.id AS ticketId,
                   CAST(t.mission_rank AS text) AS missionRank,
                   u.id AS userId,
                   u.first_name AS firstName,
                   u.last_name AS lastName,
                   u.email AS email,
                   COUNT(*) OVER (PARTITION BY t.id) AS assigneeCount
            FROM kazan.tickets t
            JOIN kazan.ticket_assignees ta ON ta.ticket_id = t.id AND ta.deleted = false
            JOIN shrine.users u ON u.id = ta.user_id AND u.deleted = false
            WHERE t.deleted = false
              AND t.board_id IN (:boardIds)
              AND t.status = 'DONE'
              AND t.completed_at >= :from
              AND t.completed_at < :to
            """, nativeQuery = true)
    List<LeaderboardTicketProjection> findCompletedTicketAssignments(@Param("boardIds") Collection<UUID> boardIds,
                                                                     @Param("from") Instant from,
                                                                     @Param("to") Instant to);

    /**
     * Lists the list roots of a sprint on the given boards with their status and completion
     * moment. A ticket is a list root unless its live parent is in the same sprint, as in
     * {@link #countSprintListRootsByBoardIdsAndSprintIdsAndStatusNot}. The parent test reads
     * the parent on any board, so a sub-ticket listed under a parent on a board the reader
     * cannot see is still left out.
     *
     * @param sprintId the sprint
     * @param boardIds the boards to read; must not be empty
     * @return the sprint's list roots on those boards, in no particular order
     */
    @Query(value = """
            SELECT t.id AS id,
                   CAST(t.status AS text) AS status,
                   t.completed_at AS completedAt
            FROM kazan.tickets t
            LEFT JOIN kazan.tickets p
                   ON p.id = t.parent_ticket_id
                  AND p.deleted = false
                  AND p.sprint_id = t.sprint_id
            WHERE t.deleted = false
              AND t.sprint_id = :sprintId
              AND t.board_id IN (:boardIds)
              AND p.id IS NULL
            """, nativeQuery = true)
    List<SprintListRootProjection> findSprintListRoots(@Param("sprintId") UUID sprintId,
                                                       @Param("boardIds") Collection<UUID> boardIds);
}
