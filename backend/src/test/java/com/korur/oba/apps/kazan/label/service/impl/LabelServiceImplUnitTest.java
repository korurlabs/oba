package com.korur.oba.apps.kazan.label.service.impl;

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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.enums.BoardMemberRole;
import com.korur.oba.apps.kazan.board.service.BoardAccessService;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.label.dto.request.LabelCreateRequest;
import com.korur.oba.apps.kazan.label.dto.request.LabelUpdateRequest;
import com.korur.oba.apps.kazan.label.dto.response.LabelResponse;
import com.korur.oba.apps.kazan.label.entity.Label;
import com.korur.oba.apps.kazan.label.mapper.LabelMapper;
import com.korur.oba.apps.kazan.label.repository.LabelRepository;
import com.korur.oba.apps.kazan.label.validator.LabelValidator;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class LabelServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private LabelRepository labelRepository;

    @Mock
    private LabelMapper labelMapper;

    @Mock
    private LabelValidator labelValidator;

    @Mock
    private BoardAccessService boardAccessService;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private LabelServiceImpl labelServiceImpl;

    @Nested
    class CreateLabelAuthorization {

        @Test
        void nonAdminMember_throwsForbiddenException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            LabelCreateRequest request = new LabelCreateRequest("Bug", "#ff0000");

            doThrow(new ForbiddenException(
                    ExceptionDomainKazanError.SEC_INSUFFICIENT_BOARD_PERMISSIONS,
                    Map.of("boardId", boardId)))
                    .when(boardAccessService).verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN);

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    labelServiceImpl.createLabel(user, organizationId, boardId, request));
            verify(labelRepository, never()).save(any());
        }
    }

    @Nested
    class UpdateLabelAuthorization {

        @Test
        void nonAdminMember_throwsForbiddenException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID labelId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            LabelUpdateRequest request = new LabelUpdateRequest("Bug", "#ff0000");

            doThrow(new ForbiddenException(
                    ExceptionDomainKazanError.SEC_INSUFFICIENT_BOARD_PERMISSIONS,
                    Map.of("boardId", boardId)))
                    .when(boardAccessService).verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN);

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    labelServiceImpl.updateLabel(user, organizationId, boardId, labelId, request));
            verify(labelRepository, never()).save(any());
        }
    }

    @Nested
    class DeleteLabelAuthorization {

        @Test
        void nonAdminMember_throwsForbiddenException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID labelId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            doThrow(new ForbiddenException(
                    ExceptionDomainKazanError.SEC_INSUFFICIENT_BOARD_PERMISSIONS,
                    Map.of("boardId", boardId)))
                    .when(boardAccessService).verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN);

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    labelServiceImpl.deleteLabel(user, organizationId, boardId, labelId));
            verify(labelRepository, never()).delete(any());
        }
    }

    @Nested
    class CreateLabel {

        @Test
        void softDeletedLabelExists_restoresAndUpdatesNameAndColor() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID labelId = UUID.randomUUID();

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Label deletedLabel = Label.builder().name("Bug").color("#ff0000").board(board).build();
            deletedLabel.setId(labelId);

            LabelCreateRequest request = new LabelCreateRequest("Bug", "#ff0000");
            LabelResponse expectedResponse = new LabelResponse(labelId, "Bug", "#ff0000", boardId, "Board");

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(board);
            when(labelRepository.findDeletedByBoardIdAndName(boardId, "Bug"))
                    .thenReturn(Optional.of(deletedLabel));
            doNothing().when(labelRepository).restoreById(labelId);
            when(labelRepository.save(deletedLabel)).thenReturn(deletedLabel);
            when(labelMapper.toResponse(deletedLabel)).thenReturn(expectedResponse);

            // Act
            LabelResponse result = labelServiceImpl.createLabel(user, organizationId, boardId, request);

            // Assert
            assertNotNull(result);
            verify(labelRepository).restoreById(labelId);
            verify(labelRepository).save(deletedLabel);
            verify(labelMapper, never()).toEntity(any());
        }

        @Test
        void noDeletedLabel_createsNewLabel() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID labelId = UUID.randomUUID();

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Label newLabel = Label.builder().name("Feature").color("#00ff00").board(board).build();
            newLabel.setId(labelId);

            LabelCreateRequest request = new LabelCreateRequest("Feature", "#00ff00");
            LabelResponse expectedResponse = new LabelResponse(labelId, "Feature", "#00ff00", boardId, "Board");

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(board);
            when(labelRepository.findDeletedByBoardIdAndName(boardId, "Feature"))
                    .thenReturn(Optional.empty());
            when(labelMapper.toEntity(request)).thenReturn(newLabel);
            when(labelRepository.save(newLabel)).thenReturn(newLabel);
            when(labelMapper.toResponse(newLabel)).thenReturn(expectedResponse);

            // Act
            LabelResponse result = labelServiceImpl.createLabel(user, organizationId, boardId, request);

            // Assert
            assertNotNull(result);
            verify(labelMapper).toEntity(request);
            verify(labelRepository).save(newLabel);
            verify(labelRepository, never()).restoreById(any());
        }
    }
}
