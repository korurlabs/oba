package com.korur.oba.apps.kazan.project.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

import com.korur.oba.apps.kazan.project.dto.request.ProjectCreateRequest;
import com.korur.oba.apps.kazan.project.dto.request.ProjectUpdateRequest;
import com.korur.oba.apps.kazan.project.dto.response.ProjectDetailResponse;
// 4. Internal
import com.korur.oba.shrine.identity.entity.User;

/**
 * Write service interface for project operations.
 * <p>
 * Handles project creation, updates, and deletion within a board.
 */
public interface ProjectService {

    /**
     * Creates a new project within a board.
     *
     * @param user           the authenticated user creating the project
     * @param organizationId the organization of the request
     * @param boardId        the board identifier
     * @param request        the project creation payload
     * @return the created project detail
     */
    ProjectDetailResponse createProject(User user, UUID organizationId, UUID boardId, ProjectCreateRequest request);

    /**
     * Updates an existing project's metadata.
     *
     * @param user           the authenticated user performing the update
     * @param organizationId the organization of the request
     * @param boardId        the board identifier
     * @param projectId      the project identifier
     * @param request        the project update payload
     * @return the updated project detail
     */
    ProjectDetailResponse updateProject(
            User user, UUID organizationId, UUID boardId, UUID projectId, ProjectUpdateRequest request);

    /**
     * Deletes an existing project.
     *
     * @param user           the authenticated user performing the deletion
     * @param organizationId the organization of the request
     * @param boardId        the board identifier
     * @param projectId      the project identifier
     */
    void deleteProject(User user, UUID organizationId, UUID boardId, UUID projectId);
}
