package com.korur.oba.apps.kaizen.project.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

import static com.korur.oba.apps.kaizen.project.constant.service.ProjectServiceLogConstant.*;

// 3. Java SDK
import java.util.UUID;

import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.board.enums.BoardMemberRole;
import com.korur.oba.apps.kaizen.board.service.BoardAccessService;
import com.korur.oba.apps.kaizen.project.dto.request.ProjectCreateRequest;
import com.korur.oba.apps.kaizen.project.dto.request.ProjectUpdateRequest;
import com.korur.oba.apps.kaizen.project.dto.response.ProjectDetailResponse;
import com.korur.oba.apps.kaizen.project.entity.Project;
import com.korur.oba.apps.kaizen.project.mapper.ProjectMapper;
import com.korur.oba.apps.kaizen.project.repository.ProjectRepository;
import com.korur.oba.apps.kaizen.project.service.ProjectService;
import com.korur.oba.apps.kaizen.project.validator.ProjectValidator;
// 4. Internal
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectServiceImpl implements ProjectService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ProjectServiceImpl.class);

    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;
    private final ProjectValidator projectValidator;
    private final BoardAccessService boardAccessService;
    private final LoggingService loggingService;

    @Override
    public ProjectDetailResponse createProject(
            User user, UUID organizationId, UUID boardId, ProjectCreateRequest request) {
        // Validate user can manage the board, then fetch the board entity.
        Board board = boardAccessService.verifyBoardAccess(
                organizationId, boardId, user.getId(), BoardMemberRole.ADMIN);

        // Map request to entity and set the board relation.
        Project project = projectMapper.toEntity(request);
        project.setBoard(board);

        project = projectRepository.save(project);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, PROJECT_CREATED, project.getId());

        return projectMapper.toDetailResponse(project);
    }

    @Override
    public ProjectDetailResponse updateProject(
            User user, UUID organizationId, UUID boardId, UUID projectId, ProjectUpdateRequest request) {
        // Validate board and project exist, and user can manage the board.
        boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN);
        Project project = projectValidator.ensureProjectExists(boardId, projectId);

        // Apply partial update via mapper.
        projectMapper.updateEntity(project, request);
        project = projectRepository.save(project);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, PROJECT_UPDATED, project.getId());

        return projectMapper.toDetailResponse(project);
    }

    @Override
    public void deleteProject(User user, UUID organizationId, UUID boardId, UUID projectId) {
        // Validate board and project exist, and user can manage the board.
        boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN);
        Project project = projectValidator.ensureProjectExists(boardId, projectId);

        projectRepository.delete(project);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, PROJECT_DELETED, projectId);
    }
}
