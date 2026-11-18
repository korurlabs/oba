package com.korur.oba.apps.kaizen.ticket.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.ticket.entity.TicketReviewer;
import com.korur.oba.dojo.repository.SoftDeleteRepository;

/**
 * Repository interface for TicketReviewer entity database operations.
 * <p>
 * Extends SoftDeleteRepository to provide soft-delete semantics including
 * findByIdWithDeleted and restoreById alongside standard CRUD operations.
 * Defines custom query methods for ticket reviewer lookups and deletions.
 */
@Repository
public interface TicketReviewerRepository extends SoftDeleteRepository<TicketReviewer, UUID> {

    /**
     * Finds all reviewers for the specified ticket.
     *
     * @param ticketId ID of the ticket
     * @return list of ticket reviewers
     */
    List<TicketReviewer> findByTicket_Id(UUID ticketId);

    /**
     * Deletes all reviewers for the specified ticket.
     *
     * @param ticketId ID of the ticket
     */
    void deleteByTicket_Id(UUID ticketId);

    /**
     * Finds all soft-deleted reviewers for a ticket.
     * Uses native query to bypass the Hibernate @SoftDelete filter.
     *
     * @param ticketId ID of the ticket
     * @return list of deleted ticket reviewers
     */
    @Query(value = """
            SELECT * FROM kaizen.ticket_reviewers
            WHERE ticket_id = :ticketId AND deleted = true
            """, nativeQuery = true)
    List<TicketReviewer> findDeletedByTicketId(@Param("ticketId") UUID ticketId);

    /**
     * Restores all soft-deleted reviewers for the specified ticket.
     *
     * @param ticketId ID of the ticket
     * @return number of rows updated
     */
    @Modifying
    @Query(value = """
            UPDATE kaizen.ticket_reviewers SET deleted = false
            WHERE ticket_id = :ticketId AND deleted = true
            """, nativeQuery = true)
    int restoreAllByTicketId(@Param("ticketId") UUID ticketId);
}
