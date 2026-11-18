package com.korur.oba.apps.kaizen.project.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.board.enums.BoardMemberRole;
import com.korur.oba.apps.kaizen.board.helper.BoardHelper;
import com.korur.oba.apps.kaizen.board.service.BoardAccessService;
import com.korur.oba.apps.kaizen.project.dto.response.ProjectDetailResponse;
import com.korur.oba.apps.kaizen.project.dto.response.ProjectListResponse;
import com.korur.oba.apps.kaizen.project.entity.Project;
import com.korur.oba.apps.kaizen.project.mapper.ProjectMapper;
import com.korur.oba.apps.kaizen.project.repository.ProjectRepository;
import com.korur.oba.apps.kaizen.project.service.ProjectQueryService;
import com.korur.oba.apps.kaizen.project.validator.ProjectValidator;
import com.korur.oba.shrine.identity.entity.User;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectQueryServiceImpl implements ProjectQueryService {

    private final ProjectRepository projectRepository;
    private final ProjectValidator projectValidator;
    private final ProjectMapper projectMapper;
    private final BoardAccessService boardAccessService;
    private final BoardHelper boardHelper;

    @Override
    public ProjectDetailResponse getProject(User user, UUID organizationId, UUID boardId, UUID projectId) {
        // Validate user is a board member and the project exists.
        boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER);
        Project project = projectValidator.ensureProjectExists(boardId, projectId);
        return projectMapper.toDetailResponse(project);
    }

    @Override
    public List<ProjectListResponse> listProjects(User user, UUID organizationId, UUID boardId) {
        Board board = boardAccessService.verifyBoardAccess(
                organizationId, boardId, user.getId(), BoardMemberRole.VIEWER);

        List<Board> relatedBoards = boardHelper.resolveRelatedBoardEntities(board, user.getId());
        List<UUID> allBoardIds = new ArrayList<>(relatedBoards.stream().map(Board::getId).toList());
        allBoardIds.add(boardId);

        return projectRepository.findByBoard_IdIn(allBoardIds).stream()
                .map(projectMapper::toListResponse)
                .toList();
    }
}
