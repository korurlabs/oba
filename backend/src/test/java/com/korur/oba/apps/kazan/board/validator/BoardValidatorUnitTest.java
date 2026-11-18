package com.korur.oba.apps.kazan.board.validator;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.constant.BoardExceptionLogConstant;
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.repository.BoardRepository;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

@ExtendWith(MockitoExtension.class)
class BoardValidatorUnitTest {

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private LoggingService loggingService;

    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;

    @InjectMocks
    private BoardValidator boardValidator;

    @Nested
    class EnsureBoardInOrganization {

        private final UUID organizationId = UUID.randomUUID();
        private final UUID boardId = UUID.randomUUID();

        private Board board(UUID boardOrganizationId) {
            Board board = Board.builder().organizationId(boardOrganizationId).build();
            board.setId(boardId);
            return board;
        }

        @Test
        void boardOfOrganization_returnsBoard() {
            // Arrange
            Board board = board(organizationId);
            when(boardRepository.findById(boardId)).thenReturn(Optional.of(board));

            // Act
            Board result = boardValidator.ensureBoardInOrganization(organizationId, boardId);

            // Assert
            assertSame(board, result);
            verify(loggingService, never()).warn(any(), any(), any(), any(Object[].class));
        }

        @Test
        void boardOfAnotherOrganization_throwsBoardNotFoundAndLogsWarn() {
            // Arrange
            when(boardRepository.findById(boardId)).thenReturn(Optional.of(board(UUID.randomUUID())));

            // Act
            NotFoundException exception = assertThrows(NotFoundException.class,
                    () -> boardValidator.ensureBoardInOrganization(organizationId, boardId));

            // Assert
            assertEquals(ExceptionDomainKazanError.NOT_BOARD_FOUND.getCode(), exception.getErrorCode());
            verify(loggingService).warn(any(), eq(LogLayer.VALIDATOR),
                    eq(BoardExceptionLogConstant.BOARD_OF_OTHER_ORGANIZATION), eq(boardId), eq(organizationId));
        }

        @Test
        void missingBoard_throwsBoardNotFound() {
            // Arrange
            when(boardRepository.findById(boardId)).thenReturn(Optional.empty());

            // Act
            NotFoundException exception = assertThrows(NotFoundException.class,
                    () -> boardValidator.ensureBoardInOrganization(organizationId, boardId));

            // Assert
            assertEquals(ExceptionDomainKazanError.NOT_BOARD_FOUND.getCode(), exception.getErrorCode());
        }
    }

    @Nested
    class EnsurePrefixIsUnique {

        @Test
        void prefixNotExists_doesNotThrow() {
            // Arrange
            String prefix = "ABC";
            when(boardRepository.existsByPrefix(prefix)).thenReturn(false);

            // Act & Assert
            assertDoesNotThrow(() -> boardValidator.ensurePrefixIsUnique(prefix));
        }

        @Test
        void prefixAlreadyExists_throwsConflictException() {
            // Arrange
            String prefix = "ABC";
            when(boardRepository.existsByPrefix(prefix)).thenReturn(true);

            // Act
            ConflictException exception = assertThrows(
                    ConflictException.class,
                    () -> boardValidator.ensurePrefixIsUnique(prefix));

            // Assert
            assertEquals(ExceptionDomainKazanError.STA_BOARD_PREFIX_DUPLICATE.getCode(), exception.getErrorCode());
        }
    }

    @Nested
    class EnsurePrefixIsUniqueForUpdate {

        @Test
        void prefixBelongsToDifferentBoard_throwsConflictException() {
            // Arrange
            String prefix = "ABC";
            UUID boardId = UUID.randomUUID();
            when(boardRepository.existsByPrefixAndIdNot(prefix, boardId)).thenReturn(true);

            // Act
            ConflictException exception = assertThrows(
                    ConflictException.class,
                    () -> boardValidator.ensurePrefixIsUniqueForUpdate(prefix, boardId));

            // Assert
            assertEquals(ExceptionDomainKazanError.STA_BOARD_PREFIX_DUPLICATE.getCode(), exception.getErrorCode());
        }

        @Test
        void prefixBelongsToSameBoard_doesNotThrow() {
            // Arrange
            String prefix = "ABC";
            UUID boardId = UUID.randomUUID();
            when(boardRepository.existsByPrefixAndIdNot(prefix, boardId)).thenReturn(false);

            // Act & Assert
            assertDoesNotThrow(() -> boardValidator.ensurePrefixIsUniqueForUpdate(prefix, boardId));
        }

        @Test
        void prefixNotExists_doesNotThrow() {
            // Arrange
            String prefix = "XYZ";
            UUID boardId = UUID.randomUUID();
            when(boardRepository.existsByPrefixAndIdNot(prefix, boardId)).thenReturn(false);

            // Act & Assert
            assertDoesNotThrow(() -> boardValidator.ensurePrefixIsUniqueForUpdate(prefix, boardId));
        }
    }

    @Nested
    class EnsureNotDescendant {

        @Test
        void candidateIsDirectChild_throwsValidationException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID childId = UUID.randomUUID();

            Board child = Board.builder().build();
            child.setId(childId);

            when(boardRepository.findByParentBoard_Id(boardId)).thenReturn(List.of(child));
            when(boardRepository.findByParentBoard_Id(childId)).thenReturn(Collections.emptyList());

            // Act & Assert
            assertThrows(ValidationException.class, () ->
                    boardValidator.ensureNotDescendant(boardId, childId));
        }

        @Test
        void candidateIsGrandchild_throwsValidationException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID childId = UUID.randomUUID();
            UUID grandchildId = UUID.randomUUID();

            Board child = Board.builder().build();
            child.setId(childId);

            Board grandchild = Board.builder().build();
            grandchild.setId(grandchildId);

            when(boardRepository.findByParentBoard_Id(boardId)).thenReturn(List.of(child));
            when(boardRepository.findByParentBoard_Id(childId)).thenReturn(List.of(grandchild));
            when(boardRepository.findByParentBoard_Id(grandchildId)).thenReturn(Collections.emptyList());

            // Act & Assert
            assertThrows(ValidationException.class, () ->
                    boardValidator.ensureNotDescendant(boardId, grandchildId));
        }

        @Test
        void candidateIsNotDescendant_doesNotThrow() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID unrelatedBoardId = UUID.randomUUID();

            Board child = Board.builder().build();
            child.setId(UUID.randomUUID());

            when(boardRepository.findByParentBoard_Id(boardId)).thenReturn(List.of(child));
            when(boardRepository.findByParentBoard_Id(child.getId())).thenReturn(Collections.emptyList());

            // Act & Assert
            assertDoesNotThrow(() -> boardValidator.ensureNotDescendant(boardId, unrelatedBoardId));
        }

        @Test
        void noChildren_doesNotThrow() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID candidateId = UUID.randomUUID();

            when(boardRepository.findByParentBoard_Id(boardId)).thenReturn(Collections.emptyList());

            // Act & Assert
            assertDoesNotThrow(() -> boardValidator.ensureNotDescendant(boardId, candidateId));
        }
    }

    @Nested
    class EnsureNotAlreadyChild {

        @Test
        void boardHasDifferentParent_doesNotThrow() {
            // Arrange
            UUID parentBoardId = UUID.randomUUID();
            UUID existingParentId = UUID.randomUUID();

            Board existingParent = Board.builder().build();
            existingParent.setId(existingParentId);

            Board board = Board.builder().build();
            board.setId(UUID.randomUUID());
            board.setParentBoard(existingParent);

            // Act & Assert
            assertDoesNotThrow(() -> boardValidator.ensureNotAlreadyChild(board, parentBoardId));
        }

        @Test
        void boardHasNoParent_doesNotThrow() {
            // Arrange
            UUID parentBoardId = UUID.randomUUID();

            Board board = Board.builder().build();
            board.setId(UUID.randomUUID());

            // Act & Assert
            assertDoesNotThrow(() -> boardValidator.ensureNotAlreadyChild(board, parentBoardId));
        }

        @Test
        void boardAlreadyChildOfSameParent_throwsConflictException() {
            // Arrange
            UUID parentBoardId = UUID.randomUUID();

            Board parentBoard = Board.builder().build();
            parentBoard.setId(parentBoardId);

            Board board = Board.builder().build();
            board.setId(UUID.randomUUID());
            board.setParentBoard(parentBoard);

            // Act
            ConflictException exception = assertThrows(
                    ConflictException.class,
                    () -> boardValidator.ensureNotAlreadyChild(board, parentBoardId));

            // Assert
            assertEquals(ExceptionDomainKazanError.STA_BOARD_ALREADY_CHILD.getCode(), exception.getErrorCode());
        }
    }

    @Nested
    class EnsureNotDescendantForReparent {

        @Test
        void candidateIsDirectChild_throwsValidationException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID childId = UUID.randomUUID();

            Board child = Board.builder().build();
            child.setId(childId);

            when(boardRepository.findByParentBoard_Id(boardId)).thenReturn(List.of(child));
            when(boardRepository.findByParentBoard_Id(childId)).thenReturn(Collections.emptyList());

            // Act & Assert
            assertThrows(ValidationException.class, () ->
                    boardValidator.ensureNotDescendantForReparent(boardId, childId));
        }

        @Test
        void candidateIsNotDescendant_doesNotThrow() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID unrelatedId = UUID.randomUUID();

            when(boardRepository.findByParentBoard_Id(boardId)).thenReturn(Collections.emptyList());

            // Act & Assert
            assertDoesNotThrow(() -> boardValidator.ensureNotDescendantForReparent(boardId, unrelatedId));
        }

        @Test
        void candidateIsGrandchild_throwsValidationException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID childId = UUID.randomUUID();
            UUID grandchildId = UUID.randomUUID();

            Board child = Board.builder().build();
            child.setId(childId);

            Board grandchild = Board.builder().build();
            grandchild.setId(grandchildId);

            when(boardRepository.findByParentBoard_Id(boardId)).thenReturn(List.of(child));
            when(boardRepository.findByParentBoard_Id(childId)).thenReturn(List.of(grandchild));
            when(boardRepository.findByParentBoard_Id(grandchildId)).thenReturn(Collections.emptyList());

            // Act
            ValidationException exception = assertThrows(
                    ValidationException.class,
                    () -> boardValidator.ensureNotDescendantForReparent(boardId, grandchildId));

            // Assert
            assertEquals(ExceptionDomainKazanError.VAL_BOARD_CIRCULAR_PARENT.getCode(), exception.getErrorCode());
        }
    }


    @Nested
    class EnsureUserIsOrganizationMember {

        private final UUID organizationId = UUID.randomUUID();
        private final UUID userId = UUID.randomUUID();

        @Test
        void activeMember_doesNotThrow() {
            when(organizationMemberQueryService.findMember(organizationId, userId)).thenReturn(Optional.of(
                    OrganizationMember.builder().status(OrganizationMemberStatus.ACTIVE).build()));

            assertDoesNotThrow(() -> boardValidator.ensureUserIsOrganizationMember(organizationId, userId));
        }

        @Test
        void unknownUserOrNoMembership_throwsNotInOrganization() {
            when(organizationMemberQueryService.findMember(organizationId, userId)).thenReturn(Optional.empty());

            ConflictException exception = assertThrows(ConflictException.class, () ->
                    boardValidator.ensureUserIsOrganizationMember(organizationId, userId));

            assertEquals("KAZ_STA_019", exception.getErrorCode());
        }

        @Test
        void disabledMembership_throwsNotInOrganization() {
            when(organizationMemberQueryService.findMember(organizationId, userId)).thenReturn(Optional.of(
                    OrganizationMember.builder().status(OrganizationMemberStatus.DISABLED).build()));

            ConflictException exception = assertThrows(ConflictException.class, () ->
                    boardValidator.ensureUserIsOrganizationMember(organizationId, userId));

            assertEquals("KAZ_STA_019", exception.getErrorCode());
        }
    }
}
