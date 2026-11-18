package com.korur.oba.apps.kaizen.board.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.board.dto.response.BoardListResponse;
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.board.enums.BoardMemberRole;
import com.korur.oba.apps.kaizen.board.helper.BoardHelper;
import com.korur.oba.apps.kaizen.board.mapper.BoardMapper;
import com.korur.oba.apps.kaizen.board.repository.BoardRepository;
import com.korur.oba.apps.kaizen.board.service.BoardAccessService;
import com.korur.oba.apps.kaizen.board.service.BoardMemberQueryService;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class BoardQueryServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private BoardHelper boardHelper;

    @Mock
    private BoardAccessService boardAccessService;

    @Mock
    private BoardMapper boardMapper;

    @Mock
    private BoardMemberQueryService boardMemberQueryService;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private BoardQueryServiceImpl boardQueryServiceImpl;

    @Nested
    class LockForUpdate {

        @Test
        void boardLocked_returnsBoard() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);
            when(boardRepository.lockById(boardId)).thenReturn(Optional.of(boardId));
            when(boardRepository.findById(boardId)).thenReturn(Optional.of(board));

            // Act
            Board result = boardQueryServiceImpl.lockForUpdate(boardId);

            // Assert
            assertSame(board, result);
        }

        @Test
        void boardNotFound_throwsNotBoardFound() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            when(boardRepository.lockById(boardId)).thenReturn(Optional.empty());

            // Act & Assert
            NotFoundException exception = assertThrows(NotFoundException.class, () ->
                    boardQueryServiceImpl.lockForUpdate(boardId));
            assertEquals(ExceptionDomainKaizenError.NOT_BOARD_FOUND.getCode(), exception.getErrorCode());
            verify(boardRepository, never()).findById(any());
        }

        @Test
        void rowVanishesAfterLock_throwsNotBoardFound() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            when(boardRepository.lockById(boardId)).thenReturn(Optional.of(boardId));
            when(boardRepository.findById(boardId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(NotFoundException.class, () -> boardQueryServiceImpl.lockForUpdate(boardId));
        }
    }

    @Nested
    class ListRelatedBoards {

        @Test
        void userNotMember_throwsNotFoundException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER))
                    .thenThrow(new NotFoundException(
                            ExceptionDomainKaizenError.NOT_BOARD_MEMBER_FOUND,
                            java.util.Map.of("boardId", boardId)));

            // Act & Assert
            assertThrows(NotFoundException.class, () ->
                    boardQueryServiceImpl.listRelatedBoards(user, organizationId, boardId));
            verify(boardRepository, never()).findAllById(any());
        }

        @Test
        void boardWithNoParentNoChildren_returnsEmptyList() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER)).thenReturn(board);
            // resolveRelatedBoardEntities returns only the board itself; after filtering self out, list is empty.
            when(boardHelper.resolveRelatedBoardEntities(board, user.getId())).thenReturn(List.of(board));

            // Act
            List<BoardListResponse> result = boardQueryServiceImpl.listRelatedBoards(user, organizationId, boardId);

            // Assert
            assertTrue(result.isEmpty());
        }

        @Test
        void boardWithParentAndChildren_returnsOnlyMemberBoards() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID parentBoardId = UUID.randomUUID();
            UUID childBoardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board parentBoard = Board.builder().name("Parent").prefix("P").build();
            parentBoard.setId(parentBoardId);

            Board childBoard = Board.builder().name("Child").prefix("C").build();
            childBoard.setId(childBoardId);

            Board board = Board.builder()
                    .name("Board")
                    .prefix("B")
                    .parentBoard(parentBoard)
                    .childBoards(new ArrayList<>(List.of(childBoard)))
                    .build();
            board.setId(boardId);

            BoardListResponse parentResponse = new BoardListResponse(
                    parentBoardId, null, null, "Parent", null, "P", "parent", null, null);
            BoardListResponse childResponse = new BoardListResponse(
                    childBoardId, null, null, "Child", null, "C", "child", null, null);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER)).thenReturn(board);
            // Helper returns parent, self, and child — self is filtered out by listRelatedBoards.
            when(boardHelper.resolveRelatedBoardEntities(board, user.getId()))
                    .thenReturn(List.of(parentBoard, board, childBoard));
            when(boardMapper.toListResponse(parentBoard)).thenReturn(parentResponse);
            when(boardMapper.toListResponse(childBoard)).thenReturn(childResponse);

            // Act
            List<BoardListResponse> result = boardQueryServiceImpl.listRelatedBoards(user, organizationId, boardId);

            // Assert
            assertTrue(result.stream().anyMatch(r -> r.id().equals(parentBoardId)),
                    "Parent board should be included when user is a member");
            assertTrue(result.stream().anyMatch(r -> r.id().equals(childBoardId)),
                    "Child board should be included when user is a member");
        }

        @Test
        void selfExcludedFromResults() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER)).thenReturn(board);
            // Helper returns only the board itself.
            when(boardHelper.resolveRelatedBoardEntities(board, user.getId())).thenReturn(List.of(board));

            // Act
            List<BoardListResponse> result = boardQueryServiceImpl.listRelatedBoards(user, organizationId, boardId);

            // Assert — self (boardId) must never appear in results.
            assertTrue(result.stream().noneMatch(r -> r.id().equals(boardId)),
                    "The requesting board itself should not appear in related boards");
        }

        @Test
        void deletedChildBoard_notIncludedBecauseEntityNavigationExcludesIt() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            // The board's childBoards list is empty — soft-deleted children are
            // filtered by @SoftDelete and not loaded into the collection.
            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER)).thenReturn(board);
            // Soft-deleted child is not returned by helper.
            when(boardHelper.resolveRelatedBoardEntities(board, user.getId())).thenReturn(List.of(board));

            // Act
            List<BoardListResponse> result = boardQueryServiceImpl.listRelatedBoards(user, organizationId, boardId);

            // Assert — empty childBoards means no child boards are returned.
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class GetBoardBySlug {

        @Test
        void memberBoard_returnsMappedBoard() {
            // Arrange
            User user = userWithId();
            Board board = boardWithId();
            BoardListResponse response = org.mockito.Mockito.mock(BoardListResponse.class);
            when(boardMemberQueryService.getMemberBoardIds(user.getId(), organizationId)).thenReturn(List.of(board.getId()));
            when(boardRepository.findBySlug("my-board")).thenReturn(Optional.of(board));
            when(boardMapper.toListResponse(board)).thenReturn(response);

            // Act
            BoardListResponse result = boardQueryServiceImpl.getBoardBySlug(user, organizationId, "my-board");

            // Assert
            assertSame(response, result);
        }

        @Test
        void slugOfNonMemberBoard_throwsNotBoardFound() {
            // Arrange
            User user = userWithId();
            Board board = boardWithId();
            when(boardMemberQueryService.getMemberBoardIds(user.getId(), organizationId)).thenReturn(List.of(UUID.randomUUID()));
            when(boardRepository.findBySlug("secret")).thenReturn(Optional.of(board));

            // Act
            NotFoundException exception = assertThrows(NotFoundException.class,
                    () -> boardQueryServiceImpl.getBoardBySlug(user, organizationId, "secret"));

            // Assert
            assertEquals(ExceptionDomainKaizenError.NOT_BOARD_FOUND.getCode(), exception.getErrorCode());
            verify(boardMapper, never()).toListResponse(any());
        }

        @Test
        void unknownSlug_throwsSameNotBoardFound() {
            // Arrange
            User user = userWithId();
            when(boardMemberQueryService.getMemberBoardIds(user.getId(), organizationId)).thenReturn(List.of(UUID.randomUUID()));
            when(boardRepository.findBySlug("missing")).thenReturn(Optional.empty());

            // Act
            NotFoundException exception = assertThrows(NotFoundException.class,
                    () -> boardQueryServiceImpl.getBoardBySlug(user, organizationId, "missing"));

            // Assert
            assertEquals(ExceptionDomainKaizenError.NOT_BOARD_FOUND.getCode(), exception.getErrorCode());
        }
    }

    @Nested
    class GetBoard {

        @Test
        void memberBoard_returnsMappedBoard() {
            // Arrange
            User user = userWithId();
            Board board = boardWithId();
            BoardListResponse response = org.mockito.Mockito.mock(BoardListResponse.class);
            when(boardMemberQueryService.getMemberBoardIds(user.getId(), organizationId)).thenReturn(List.of(board.getId()));
            when(boardRepository.findById(board.getId())).thenReturn(Optional.of(board));
            when(boardMapper.toListResponse(board)).thenReturn(response);

            // Act
            BoardListResponse result = boardQueryServiceImpl.getBoard(user, organizationId, board.getId());

            // Assert
            assertSame(response, result);
        }

        @Test
        void nonMemberBoard_throwsNotBoardFoundWithoutLoadingTheBoard() {
            // Arrange
            User user = userWithId();
            UUID boardId = UUID.randomUUID();
            when(boardMemberQueryService.getMemberBoardIds(user.getId(), organizationId)).thenReturn(List.of(UUID.randomUUID()));

            // Act
            NotFoundException exception = assertThrows(NotFoundException.class,
                    () -> boardQueryServiceImpl.getBoard(user, organizationId, boardId));

            // Assert
            assertEquals(ExceptionDomainKaizenError.NOT_BOARD_FOUND.getCode(), exception.getErrorCode());
            verify(boardRepository, never()).findById(any());
        }

        @Test
        void memberOfDeletedBoard_throwsNotBoardFound() {
            // Arrange
            User user = userWithId();
            UUID boardId = UUID.randomUUID();
            when(boardMemberQueryService.getMemberBoardIds(user.getId(), organizationId)).thenReturn(List.of(boardId));
            when(boardRepository.findById(boardId)).thenReturn(Optional.empty());

            // Act
            NotFoundException exception = assertThrows(NotFoundException.class,
                    () -> boardQueryServiceImpl.getBoard(user, organizationId, boardId));

            // Assert
            assertEquals(ExceptionDomainKaizenError.NOT_BOARD_FOUND.getCode(), exception.getErrorCode());
        }
    }

    private static User userWithId() {
        User user = User.builder().build();
        user.setId(UUID.randomUUID());
        return user;
    }

    private static Board boardWithId() {
        Board board = Board.builder().name("Board").prefix("B").slug("my-board").build();
        board.setId(UUID.randomUUID());
        return board;
    }
}
