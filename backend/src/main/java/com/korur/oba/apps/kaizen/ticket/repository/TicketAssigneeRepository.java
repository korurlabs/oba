package com.korur.oba.apps.kaizen.ticket.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.ticket.entity.TicketAssignee;
import com.korur.oba.apps.kaizen.ticket.enums.TicketStatus;
import com.korur.oba.dojo.repository.SoftDeleteRepository;

/**
 * Repository interface for TicketAssignee entity database operations.
 * <p>
 * Extends SoftDeleteRepository to provide soft-delete semantics including
 * findByIdWithDeleted and restoreById alongside standard CRUD operations.
 * Defines custom query methods for ticket assignee lookups and deletions.
 */
@Repository
public interface TicketAssigneeRepository extends SoftDeleteRepository<TicketAssignee, UUID> {

    /**
     * Finds all assignees for the specified ticket.
     *
     * @param ticketId ID of the ticket
     * @return list of ticket assignees
     */
    List<TicketAssignee> findByTicket_Id(UUID ticketId);

    /**
     * Deletes all assignees for the specified ticket.
     *
     * @param ticketId ID of the ticket
     */
    void deleteByTicket_Id(UUID ticketId);

    /**
     * Finds all soft-deleted assignees for a ticket.
     * Uses native query to bypass the Hibernate @SoftDelete filter.
     *
     * @param ticketId ID of the ticket
     * @return list of deleted ticket assignees
     */
    @Query(value = """
            SELECT * FROM kaizen.ticket_assignees
            WHERE ticket_id = :ticketId AND deleted = true
            """, nativeQuery = true)
    List<TicketAssignee> findDeletedByTicketId(@Param("ticketId") UUID ticketId);

    /**
     * Finds a soft-deleted assignee for a ticket and user combination.
     * Uses native query to bypass the Hibernate @SoftDelete filter.
     *
     * @param ticketId ID of the ticket
     * @param userId   ID of the user
     * @return optional deleted ticket assignee
     */
    @Query(value = """
            SELECT * FROM kaizen.ticket_assignees
            WHERE ticket_id = :ticketId AND user_id = :userId AND deleted = true
            """, nativeQuery = true)
    Optional<TicketAssignee> findDeletedByTicketIdAndUserId(
            @Param("ticketId") UUID ticketId,
            @Param("userId") UUID userId);

    /**
     * Restores all soft-deleted assignees for the specified ticket.
     *
     * @param ticketId ID of the ticket
     * @return number of rows updated
     */
    @Modifying
    @Query(value = """
            UPDATE kaizen.ticket_assignees SET deleted = false
            WHERE ticket_id = :ticketId AND deleted = true
            """, nativeQuery = true)
    int restoreAllByTicketId(@Param("ticketId") UUID ticketId);

    /**
     * Finds the assignments of the given users to tickets with the given status in the
     * given sprints, on the given boards, sub-tickets included, ordered by ticket key.
     * A ticket assigned to several of the users comes back once per user, so callers
     * group by assignee without loading each ticket's assignee collection. Ticket and
     * board are fetched in the same select.
     *
     * @param assigneeIds IDs of the assigned users
     * @param boardIds    IDs of the boards the tickets must be on
     * @param sprintIds   IDs of the sprints the tickets must be in
     * @param status      status the tickets must have
     * @return the matching assignments ordered by ticket key
     */
    @Query("""
            SELECT ta FROM TicketAssignee ta JOIN FETCH ta.ticket t JOIN FETCH t.board
            WHERE ta.user.id IN :assigneeIds AND t.board.id IN :boardIds
              AND t.sprint.id IN :sprintIds AND t.status = :status
            ORDER BY t.key ASC
            """)
    List<TicketAssignee> findByAssigneesBoardsSprintsAndStatus(
            @Param("assigneeIds") Collection<UUID> assigneeIds,
            @Param("boardIds") Collection<UUID> boardIds,
            @Param("sprintIds") Collection<UUID> sprintIds,
            @Param("status") TicketStatus status);
}
