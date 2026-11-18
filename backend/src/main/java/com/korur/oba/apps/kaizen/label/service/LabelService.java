package com.korur.oba.apps.kaizen.label.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

import com.korur.oba.apps.kaizen.label.dto.request.LabelCreateRequest;
import com.korur.oba.apps.kaizen.label.dto.request.LabelUpdateRequest;
import com.korur.oba.apps.kaizen.label.dto.response.LabelResponse;
// 4. Internal
import com.korur.oba.shrine.identity.entity.User;

/**
 * Write service interface for label operations.
 * <p>
 * Handles label creation, updates, and deletion within a board.
 */
public interface LabelService {

    /**
     * Creates a new label within a board.
     *
     * @param user           the authenticated user creating the label
     * @param organizationId the organization of the request, already checked by the interceptor
     * @param boardId        the board identifier
     * @param request        the label creation payload
     * @return the created label
     */
    LabelResponse createLabel(User user, UUID organizationId, UUID boardId, LabelCreateRequest request);

    /**
     * Updates an existing label's metadata.
     *
     * @param user           the authenticated user performing the update
     * @param organizationId the organization of the request, already checked by the interceptor
     * @param boardId        the board identifier
     * @param labelId        the label identifier
     * @param request        the label update payload
     * @return the updated label
     */
    LabelResponse updateLabel(User user, UUID organizationId, UUID boardId, UUID labelId, LabelUpdateRequest request);

    /**
     * Deletes an existing label.
     *
     * @param user           the authenticated user performing the deletion
     * @param organizationId the organization of the request, already checked by the interceptor
     * @param boardId        the board identifier
     * @param labelId        the label identifier
     */
    void deleteLabel(User user, UUID organizationId, UUID boardId, UUID labelId);
}
