package com.korur.oba.apps.kaizen.project.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.Collections;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.board.enums.BoardMemberRole;
import com.korur.oba.apps.kaizen.board.helper.BoardHelper;
import com.korur.oba.apps.kaizen.board.service.BoardAccessService;
import com.korur.oba.apps.kaizen.project.dto.response.ProjectDetailResponse;
import com.korur.oba.apps.kaizen.project.dto.response.ProjectListResponse;
import com.korur.oba.apps.kaizen.project.entity.Project;
import com.korur.oba.apps.kaizen.project.mapper.ProjectMapper;
import com.korur.oba.apps.kaizen.project.repository.ProjectRepository;
import com.korur.oba.apps.kaizen.project.validator.ProjectValidator;
import com.korur.oba.dojo.exception.enums.ExceptionNotFoundError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class ProjectQueryServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectValidator projectValidator;

    @Mock
    private ProjectMapper projectMapper;

    @Mock
    private BoardAccessService boardAccessService;

    @Mock
    private BoardHelper boardHelper;

    @InjectMocks
    private ProjectQueryServiceImpl projectQueryServiceImpl;

    @Nested
    class ListProjects {

        @Test
        void userIsNotBoardMember_throwsNotFoundException() {
            // Arrange
            UUID boardId = UUID.randomUUID();

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER))
                    .thenThrow(new NotFoundException(ExceptionNotFoundError.RESOURCE_FOUND));

            // Act & Assert
            assertThrows(NotFoundException.class, () ->
                    projectQueryServiceImpl.listProjects(user, organizationId, boardId));
        }

        @Test
        void userIsBoardMember_returnsProjectList() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Project project = Project.builder().name("Project Alpha").color("#0000ff").build();
            project.setId(projectId);

            ProjectListResponse expectedResponse = new ProjectListResponse(projectId, "Project Alpha", "#0000ff", boardId, "Board");

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER)).thenReturn(board);
            when(boardHelper.resolveRelatedBoardEntities(board, user.getId())).thenReturn(Collections.emptyList());
            when(projectRepository.findByBoard_IdIn(anyList())).thenReturn(List.of(project));
            when(projectMapper.toListResponse(project)).thenReturn(expectedResponse);

            // Act
            List<ProjectListResponse> result = projectQueryServiceImpl.listProjects(user, organizationId, boardId);

            // Assert
            assertNotNull(result);
            verify(boardAccessService).verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER);
            verify(boardHelper).resolveRelatedBoardEntities(board, user.getId());
            verify(projectRepository).findByBoard_IdIn(anyList());
        }
    }

    @Nested
    class GetProject {

        @Test
        void userIsNotBoardMember_throwsNotFoundException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER))
                    .thenThrow(new NotFoundException(ExceptionNotFoundError.RESOURCE_FOUND));

            // Act & Assert
            assertThrows(NotFoundException.class, () ->
                    projectQueryServiceImpl.getProject(user, organizationId, boardId, projectId));
        }

        @Test
        void userIsBoardMember_returnsProjectDetail() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Project project = Project.builder().name("Project Alpha").color("#0000ff").build();
            project.setId(projectId);

            ProjectDetailResponse expectedResponse = new ProjectDetailResponse(projectId, null, null, "Project Alpha", "#0000ff", boardId);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER)).thenReturn(board);
            when(projectValidator.ensureProjectExists(boardId, projectId)).thenReturn(project);
            when(projectMapper.toDetailResponse(project)).thenReturn(expectedResponse);

            // Act
            ProjectDetailResponse result = projectQueryServiceImpl.getProject(user, organizationId, boardId, projectId);

            // Assert
            assertNotNull(result);
            verify(boardAccessService).verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER);
            verify(projectValidator).ensureProjectExists(boardId, projectId);
        }
    }
}
