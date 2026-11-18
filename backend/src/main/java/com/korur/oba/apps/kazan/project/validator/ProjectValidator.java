package com.korur.oba.apps.kazan.project.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;

import com.korur.oba.apps.kazan.project.constant.ProjectConstant;
import com.korur.oba.apps.kazan.project.constant.ProjectExceptionLogConstant;
import com.korur.oba.apps.kazan.project.entity.Project;
import com.korur.oba.apps.kazan.project.repository.ProjectRepository;
// 4. Internal
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Validator for project lookups.
 */
@Component
@RequiredArgsConstructor
public class ProjectValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ProjectValidator.class);

    private final ProjectRepository projectRepository;
    private final LoggingService loggingService;

    /**
     * Ensures the project exists on the specified board and returns it.
     * Used by project get/update/delete endpoints to enforce board-scoped access.
     *
     * @param boardId   ID of the board from the URL path
     * @param projectId ID of the project
     * @return the project entity
     * @throws NotFoundException if the project does not exist on the given board
     */
    public Project ensureProjectExists(UUID boardId, UUID projectId) {
        return projectRepository.findByBoard_IdAndId(boardId, projectId)
                .orElseThrow(() -> {
                    loggingService.error(
                            LOG_PREFIX,
                            LogLayer.VALIDATOR,
                            ProjectExceptionLogConstant.PROJECT_NOT_FOUND,
                            projectId);
                    return new NotFoundException(
                            ExceptionDomainKazanError.NOT_PROJECT_FOUND,
                            Map.of(ProjectConstant.FIELD_PROJECT_ID, projectId));
                });
    }

    /**
     * Ensures the project exists on a board of the organization and returns it.
     * Used by ticket create and update, where a project of a related board may be attached.
     * A project of another organization answers the same not found as a missing one.
     *
     * @param organizationId ID of the ticket board's organization
     * @param projectId      ID of the project
     * @return the project entity
     * @throws NotFoundException if the project does not exist on a board of the organization
     */
    public Project ensureProjectInOrganization(UUID organizationId, UUID projectId) {
        return projectRepository.findByIdAndBoard_OrganizationId(projectId, organizationId)
                .orElseThrow(() -> {
                    loggingService.error(
                            LOG_PREFIX,
                            LogLayer.VALIDATOR,
                            ProjectExceptionLogConstant.PROJECT_NOT_FOUND,
                            projectId);
                    return new NotFoundException(
                            ExceptionDomainKazanError.NOT_PROJECT_FOUND,
                            Map.of(ProjectConstant.FIELD_PROJECT_ID, projectId));
                });
    }
}
