package com.korur.oba.apps.kaizen.comment.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.comment.dto.response.CommentDetailResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Read service interface for comment queries.
 * <p>
 * Provides read-only access to comment data on tickets.
 */
public interface CommentQueryService {

    /**
     * Lists all comments on the specified ticket.
     *
     * @param organizationId the organization of the request, already checked by the interceptor
     * @param ticketId       the ticket identifier
     * @return list of comment details
     */
    List<CommentDetailResponse> listComments(User user, UUID organizationId, UUID boardId, UUID ticketId);
}
