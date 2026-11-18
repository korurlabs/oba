package com.korur.oba.apps.kazan.project.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.enums.BoardMemberRole;
import com.korur.oba.apps.kazan.board.service.BoardAccessService;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.project.dto.request.ProjectCreateRequest;
import com.korur.oba.apps.kazan.project.dto.request.ProjectUpdateRequest;
import com.korur.oba.apps.kazan.project.dto.response.ProjectDetailResponse;
import com.korur.oba.apps.kazan.project.entity.Project;
import com.korur.oba.apps.kazan.project.mapper.ProjectMapper;
import com.korur.oba.apps.kazan.project.repository.ProjectRepository;
import com.korur.oba.apps.kazan.project.validator.ProjectValidator;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class ProjectServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMapper projectMapper;

    @Mock
    private ProjectValidator projectValidator;

    @Mock
    private BoardAccessService boardAccessService;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private ProjectServiceImpl projectServiceImpl;

    @Nested
    class CreateProjectAuthorization {

        @Test
        void nonAdminMember_throwsForbiddenException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            ProjectCreateRequest request = new ProjectCreateRequest("Sprint 1", "#3b82f6");

            doThrow(new ForbiddenException(
                    ExceptionDomainKazanError.SEC_INSUFFICIENT_BOARD_PERMISSIONS,
                    Map.of("boardId", boardId)))
                    .when(boardAccessService).verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN);

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    projectServiceImpl.createProject(user, organizationId, boardId, request));
            verify(projectRepository, never()).save(any());
        }

        @Test
        void adminMember_createsProjectSuccessfully() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Project project = Project.builder().name("Sprint 1").build();
            project.setId(projectId);

            ProjectCreateRequest request = new ProjectCreateRequest("Sprint 1", "#3b82f6");
            ProjectDetailResponse expectedResponse = new ProjectDetailResponse(
                    projectId, null, null, "Sprint 1", "#3b82f6", boardId);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(board);
            when(projectMapper.toEntity(request)).thenReturn(project);
            when(projectRepository.save(project)).thenReturn(project);
            when(projectMapper.toDetailResponse(project)).thenReturn(expectedResponse);

            // Act
            ProjectDetailResponse result = projectServiceImpl.createProject(user, organizationId, boardId, request);

            // Assert
            assertNotNull(result);
            verify(boardAccessService).verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN);
            verify(projectRepository).save(project);
        }
    }

    @Nested
    class UpdateProjectAuthorization {

        @Test
        void nonAdminMember_throwsForbiddenException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            ProjectUpdateRequest request = new ProjectUpdateRequest("Updated Name", null);

            doThrow(new ForbiddenException(
                    ExceptionDomainKazanError.SEC_INSUFFICIENT_BOARD_PERMISSIONS,
                    Map.of("boardId", boardId)))
                    .when(boardAccessService).verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN);

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    projectServiceImpl.updateProject(user, organizationId, boardId, projectId, request));
            verify(projectRepository, never()).save(any());
        }

        @Test
        void adminMember_updatesProjectSuccessfully() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Project project = Project.builder().name("Sprint 1").build();
            project.setId(projectId);

            ProjectUpdateRequest request = new ProjectUpdateRequest("Updated Name", null);
            ProjectDetailResponse expectedResponse = new ProjectDetailResponse(
                    projectId, null, null, "Updated Name", null, boardId);

            when(projectValidator.ensureProjectExists(boardId, projectId)).thenReturn(project);
            when(projectRepository.save(project)).thenReturn(project);
            when(projectMapper.toDetailResponse(project)).thenReturn(expectedResponse);

            // Act
            ProjectDetailResponse result = projectServiceImpl.updateProject(user, organizationId, boardId, projectId, request);

            // Assert
            assertNotNull(result);
            verify(boardAccessService).verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN);
            verify(projectRepository).save(project);
        }
    }

    @Nested
    class DeleteProjectAuthorization {

        @Test
        void nonAdminMember_throwsForbiddenException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            doThrow(new ForbiddenException(
                    ExceptionDomainKazanError.SEC_INSUFFICIENT_BOARD_PERMISSIONS,
                    Map.of("boardId", boardId)))
                    .when(boardAccessService).verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN);

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    projectServiceImpl.deleteProject(user, organizationId, boardId, projectId));
            verify(projectRepository, never()).delete(any());
        }

        @Test
        void adminMember_deletesProjectSuccessfully() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Project project = Project.builder().name("Sprint 1").build();
            project.setId(projectId);

            when(projectValidator.ensureProjectExists(boardId, projectId)).thenReturn(project);

            // Act
            projectServiceImpl.deleteProject(user, organizationId, boardId, projectId);

            // Assert
            verify(boardAccessService).verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN);
            verify(projectRepository).delete(project);
        }
    }
}
