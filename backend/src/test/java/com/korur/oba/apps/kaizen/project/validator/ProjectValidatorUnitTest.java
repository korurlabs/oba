package com.korur.oba.apps.kaizen.project.validator;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.apps.kaizen.project.entity.Project;
import com.korur.oba.apps.kaizen.project.repository.ProjectRepository;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class ProjectValidatorUnitTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private ProjectValidator projectValidator;

    @Nested
    class EnsureProjectExistsBoardScoped {

        @Test
        void ensureProjectExists_projectBelongsToBoard_returnsProject() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();

            Board board = Board.builder().id(boardId).build();
            Project project = Project.builder()
                    .name("Backend Cleanup")
                    .board(board)
                    .build();
            project.setId(projectId);

            when(projectRepository.findByBoard_IdAndId(boardId, projectId)).thenReturn(Optional.of(project));

            // Act
            Project result = projectValidator.ensureProjectExists(boardId, projectId);

            // Assert
            assertNotNull(result);
            assertEquals(projectId, result.getId());
        }

        @Test
        void ensureProjectExists_projectBelongsToDifferentBoard_throwsNotFoundException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();

            when(projectRepository.findByBoard_IdAndId(boardId, projectId)).thenReturn(Optional.empty());

            // Act
            NotFoundException exception = assertThrows(
                    NotFoundException.class,
                    () -> projectValidator.ensureProjectExists(boardId, projectId));

            // Assert
            assertEquals(ExceptionDomainKaizenError.NOT_PROJECT_FOUND.getCode(), exception.getErrorCode());
        }
    }

    @Nested
    class EnsureProjectInOrganization {

        @Test
        void projectOnBoardOfTheOrganization_returnsProject() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            Project project = Project.builder().build();
            project.setId(projectId);
            when(projectRepository.findByIdAndBoard_OrganizationId(projectId, organizationId)).thenReturn(Optional.of(project));

            // Act
            Project result = projectValidator.ensureProjectInOrganization(organizationId, projectId);

            // Assert
            assertEquals(project, result);
        }

        @Test
        void projectOnBoardOfAnotherOrganization_throwsNotFoundException() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            when(projectRepository.findByIdAndBoard_OrganizationId(projectId, organizationId)).thenReturn(Optional.empty());

            // Act & Assert
            NotFoundException exception = assertThrows(NotFoundException.class,
                    () -> projectValidator.ensureProjectInOrganization(organizationId, projectId));
            assertEquals(ExceptionDomainKaizenError.NOT_PROJECT_FOUND.getCode(), exception.getErrorCode());
        }
    }
}
