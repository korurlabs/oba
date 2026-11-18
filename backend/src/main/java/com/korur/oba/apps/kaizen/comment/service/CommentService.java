package com.korur.oba.apps.kaizen.comment.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.comment.dto.request.CommentCreateRequest;
import com.korur.oba.apps.kaizen.comment.dto.request.CommentUpdateRequest;
import com.korur.oba.apps.kaizen.comment.dto.response.CommentDetailResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Write service interface for comment operations.
 * <p>
 * Handles comment creation, updates, and deletion on tickets.
 */
public interface CommentService {

    /**
     * Creates a new comment on a ticket.
     *
     * @param user           the authenticated user creating the comment
     * @param organizationId the organization of the request, already checked by the interceptor
     * @param ticketId       the ticket identifier
     * @param request        the comment creation payload
     * @return the created comment detail
     */
    CommentDetailResponse createComment(
            User user,
            UUID organizationId,
            UUID boardId,
            UUID ticketId,
            CommentCreateRequest request);

    /**
     * Updates an existing comment's content.
     *
     * @param user           the authenticated user performing the update
     * @param organizationId the organization of the request, already checked by the interceptor
     * @param commentId      the comment identifier
     * @param request        the comment update payload
     * @return the updated comment detail
     */
    CommentDetailResponse updateComment(
            User user,
            UUID organizationId,
            UUID boardId,
            UUID ticketId,
            UUID commentId,
            CommentUpdateRequest request);

    /**
     * Deletes an existing comment.
     *
     * @param user           the authenticated user performing the deletion
     * @param organizationId the organization of the request, already checked by the interceptor
     * @param commentId      the comment identifier
     */
    void deleteComment(
            User user,
            UUID organizationId,
            UUID boardId,
            UUID ticketId,
            UUID commentId);
}
