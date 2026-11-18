package com.korur.oba.apps.kazan.comment.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.comment.entity.Comment;
import com.korur.oba.dojo.repository.SoftDeleteRepository;

/**
 * Repository interface for Comment entity database operations.
 * <p>
 * Extends SoftDeleteRepository to provide soft-delete semantics including
 * findByIdWithDeleted and restoreById alongside standard CRUD operations.
 * Defines custom query methods for comment lookups by ticket.
 */
@Repository
public interface CommentRepository extends SoftDeleteRepository<Comment, UUID> {

    /**
     * Finds all comments belonging to the specified ticket and board.
     *
     * @param ticketId ID of the ticket
     * @param boardId  ID of the board
     * @return list of comments
     */
    List<Comment> findByTicket_IdAndTicket_Board_Id(UUID ticketId, UUID boardId);

    /**
     * Finds a single comment by its ID, ticket ID, and board ID.
     * Used for board-scoped validation on comment endpoints.
     *
     * @param id       ID of the comment
     * @param ticketId ID of the ticket
     * @param boardId  ID of the board
     * @return optional comment if found on the specified ticket and board
     */
    Optional<Comment> findByIdAndTicket_IdAndTicket_Board_Id(UUID id, UUID ticketId, UUID boardId);

    /**
     * Finds all soft-deleted comments for a ticket.
     * Uses native query to bypass the Hibernate @SoftDelete filter.
     *
     * @param ticketId ID of the ticket
     * @return list of deleted comments
     */
    @Query(value = """
            SELECT * FROM kazan.comments
            WHERE ticket_id = :ticketId AND deleted = true
            """, nativeQuery = true)
    List<Comment> findDeletedByTicketId(@Param("ticketId") UUID ticketId);

    /**
     * Restores all soft-deleted comments for the specified ticket.
     *
     * @param ticketId ID of the ticket
     * @return number of rows updated
     */
    @Modifying
    @Query(value = """
            UPDATE kazan.comments SET deleted = false
            WHERE ticket_id = :ticketId AND deleted = true
            """, nativeQuery = true)
    int restoreAllByTicketId(@Param("ticketId") UUID ticketId);
}
