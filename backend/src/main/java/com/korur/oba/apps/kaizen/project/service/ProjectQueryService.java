package com.korur.oba.apps.kaizen.project.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

import com.korur.oba.apps.kaizen.project.dto.response.ProjectDetailResponse;
import com.korur.oba.apps.kaizen.project.dto.response.ProjectListResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Read service interface for project queries.
 * <p>
 * Provides read-only access to project data within a board.
 */
public interface ProjectQueryService {

    /**
     * Retrieves a project's detail by its identifier.
     *
     * @param user           the authenticated user
     * @param organizationId the organization of the request
     * @param boardId        the board identifier
     * @param projectId      the project identifier
     * @return the project detail
     */
    ProjectDetailResponse getProject(User user, UUID organizationId, UUID boardId, UUID projectId);

    /**
     * Lists all projects belonging to the specified board.
     *
     * @param user           the authenticated user
     * @param organizationId the organization of the request
     * @param boardId        the board identifier
     * @return list of projects
     */
    List<ProjectListResponse> listProjects(User user, UUID organizationId, UUID boardId);
}
