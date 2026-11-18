package com.korur.oba.apps.kaizen.label.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

import com.korur.oba.apps.kaizen.label.dto.response.LabelResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Read service interface for label queries.
 * <p>
 * Provides read-only access to label data within a board.
 */
public interface LabelQueryService {

    /**
     * Lists all labels belonging to the specified board.
     *
     * @param user           the authenticated user
     * @param organizationId the organization of the request, already checked by the interceptor
     * @param boardId        the board identifier
     * @return list of labels
     */
    List<LabelResponse> listLabels(User user, UUID organizationId, UUID boardId);
}
