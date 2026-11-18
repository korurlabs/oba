package com.korur.oba.apps.kazan.board.helper;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 2. Third-party
import org.hibernate.exception.ConstraintViolationException;

// 3. Java SDK
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.backlog.repository.BacklogRepository;
import com.korur.oba.apps.kazan.board.constant.BoardConstant;
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.repository.BoardMemberRepository;
import com.korur.oba.apps.kazan.board.repository.BoardMemberSpecialtyRepository;
import com.korur.oba.apps.kazan.board.repository.BoardRepository;
import com.korur.oba.apps.kazan.board.service.BoardMemberQueryService;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.label.repository.LabelRepository;
import com.korur.oba.apps.kazan.milestone.service.MilestoneService;
import com.korur.oba.apps.kazan.project.repository.ProjectRepository;
import com.korur.oba.apps.kazan.sprint.helper.SprintLifecycleHelper;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.enums.TicketType;
import com.korur.oba.apps.kazan.ticket.helper.TicketHelper;
import com.korur.oba.apps.kazan.ticket.repository.TicketRepository;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class BoardHelperUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private BacklogRepository backlogRepository;

    @Mock
    private LabelRepository labelRepository;

    @Mock
    private BoardMemberRepository boardMemberRepository;

    @Mock
    private BoardMemberSpecialtyRepository boardMemberSpecialtyRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private SprintLifecycleHelper sprintLifecycleHelper;

    @Mock
    private TicketHelper ticketHelper;

    @Mock
    private BoardMemberQueryService boardMemberQueryService;

    @Mock
    private LoggingService loggingService;

    @Mock
    private MilestoneService milestoneService;

    @InjectMocks
    private BoardHelper boardHelper;

    @Nested
    class RestoreBoardChildren {

        @Test
        void callsAllRestoreMethods() {
            // Arrange
            UUID boardId = UUID.randomUUID();

            when(backlogRepository.restoreByBoardId(boardId)).thenReturn(1);
            when(labelRepository.restoreAllByBoardId(boardId)).thenReturn(0);
            when(boardMemberRepository.findDeletedIdsByBoardId(boardId)).thenReturn(Collections.emptyList());
            when(boardMemberRepository.restoreAllByBoardId(boardId)).thenReturn(0);
            when(projectRepository.restoreAllByBoardId(boardId)).thenReturn(0);
            when(ticketRepository.findDeletedByBoardId(boardId)).thenReturn(Collections.emptyList());
            when(ticketRepository.restoreAllByBoardId(boardId)).thenReturn(0);

            // Act
            boardHelper.restoreBoardChildren(boardId);

            // Assert
            verify(backlogRepository).restoreByBoardId(boardId);
            verify(labelRepository).restoreAllByBoardId(boardId);
            verify(boardMemberRepository).restoreAllByBoardId(boardId);
            verify(projectRepository).restoreAllByBoardId(boardId);
            verify(milestoneService).restoreBoardMilestones(boardId);
            verify(sprintLifecycleHelper).restoreLinksOfBoard(boardId);
            verify(ticketRepository).restoreAllByBoardId(boardId);
        }

        @Test
        void withDeletedTickets_delegatesRestoreTicketChildrenToTicketHelper() {
            // Arrange
            UUID boardId = UUID.randomUUID();

            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").build();
            board.setId(boardId);

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            UUID ticketId = UUID.randomUUID();
            Ticket ticket = Ticket.builder().title("T").key("B-1").type(TicketType.FEATURE).board(board).createdBy(user).build();
            ticket.setId(ticketId);

            when(backlogRepository.restoreByBoardId(boardId)).thenReturn(1);
            when(labelRepository.restoreAllByBoardId(boardId)).thenReturn(0);
            when(boardMemberRepository.findDeletedIdsByBoardId(boardId)).thenReturn(Collections.emptyList());
            when(boardMemberRepository.restoreAllByBoardId(boardId)).thenReturn(0);
            when(projectRepository.restoreAllByBoardId(boardId)).thenReturn(0);
            when(ticketRepository.findDeletedByBoardId(boardId)).thenReturn(List.of(ticket));
            when(ticketRepository.restoreAllByBoardId(boardId)).thenReturn(1);

            // Act
            boardHelper.restoreBoardChildren(boardId);

            // Assert
            verify(ticketHelper).restoreTicketChildren(ticketId);
        }
    }

    @Nested
    class ResolveRelatedBoardEntities {

        @Test
        void boardWithNoParentNoChildren_returnsSelfIfMember() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID boardId = UUID.randomUUID();

            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").build();
            board.setId(boardId);

            when(boardRepository.findByParentBoard_Id(boardId)).thenReturn(Collections.emptyList());
            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(List.of(boardId));
            when(boardRepository.findAllById(Collections.emptySet())).thenReturn(Collections.emptyList());

            // Act
            List<Board> result = boardHelper.resolveRelatedBoardEntities(board, userId);

            // Assert
            assertTrue(result.isEmpty(), "Self is excluded from relatedIds — relatedIds is empty before findAllById");
        }

        @Test
        void boardWithNoParentNoChildren_notMember_returnsEmpty() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID boardId = UUID.randomUUID();

            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").build();
            board.setId(boardId);

            when(boardRepository.findByParentBoard_Id(boardId)).thenReturn(Collections.emptyList());
            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(Collections.emptyList());
            when(boardRepository.findAllById(any())).thenReturn(Collections.emptyList());

            // Act
            List<Board> result = boardHelper.resolveRelatedBoardEntities(board, userId);

            // Assert
            assertTrue(result.isEmpty());
        }

        @Test
        void boardWithParent_parentIncludedIfMember() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID boardId = UUID.randomUUID();
            UUID parentBoardId = UUID.randomUUID();

            Board parentBoard = Board.builder().organizationId(organizationId).name("Parent").prefix("P").build();
            parentBoard.setId(parentBoardId);

            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").parentBoard(parentBoard).build();
            board.setId(boardId);

            // When querying siblings (children of parent), return only self.
            when(boardRepository.findByParentBoard_Id(parentBoardId)).thenReturn(List.of(board));
            // No children of the board itself.
            when(boardRepository.findByParentBoard_Id(boardId)).thenReturn(Collections.emptyList());
            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(List.of(boardId, parentBoardId));
            // relatedIds after removing self = {parentBoardId}
            when(boardRepository.findAllById(any())).thenReturn(List.of(parentBoard));

            // Act
            List<Board> result = boardHelper.resolveRelatedBoardEntities(board, userId);

            // Assert
            assertTrue(result.contains(parentBoard), "Parent board should be included when user is a member");
        }

        @Test
        void boardWithSiblings_siblingsIncludedIfMember() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID boardId = UUID.randomUUID();
            UUID parentBoardId = UUID.randomUUID();
            UUID siblingBoardId = UUID.randomUUID();

            Board parentBoard = Board.builder().organizationId(organizationId).name("Parent").prefix("P").build();
            parentBoard.setId(parentBoardId);

            Board siblingBoard = Board.builder().organizationId(organizationId).name("Sibling").prefix("S").build();
            siblingBoard.setId(siblingBoardId);

            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").parentBoard(parentBoard).build();
            board.setId(boardId);

            // Siblings = other children of the parent.
            when(boardRepository.findByParentBoard_Id(parentBoardId)).thenReturn(List.of(board, siblingBoard));
            when(boardRepository.findByParentBoard_Id(boardId)).thenReturn(Collections.emptyList());
            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId))
                    .thenReturn(List.of(boardId, siblingBoardId));
            when(boardRepository.findAllById(any())).thenReturn(List.of(siblingBoard));

            // Act
            List<Board> result = boardHelper.resolveRelatedBoardEntities(board, userId);

            // Assert
            assertTrue(result.contains(siblingBoard), "Sibling board should be included when user is a member");
        }

        @Test
        void boardWithChildren_childrenIncludedIfMember() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID boardId = UUID.randomUUID();
            UUID childBoardId = UUID.randomUUID();

            Board childBoard = Board.builder().organizationId(organizationId).name("Child").prefix("C").build();
            childBoard.setId(childBoardId);

            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").build();
            board.setId(boardId);

            when(boardRepository.findByParentBoard_Id(boardId)).thenReturn(List.of(childBoard));
            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId))
                    .thenReturn(List.of(boardId, childBoardId));
            when(boardRepository.findAllById(any())).thenReturn(List.of(childBoard));

            // Act
            List<Board> result = boardHelper.resolveRelatedBoardEntities(board, userId);

            // Assert
            assertTrue(result.contains(childBoard), "Child board should be included when user is a member");
        }

        @Test
        void boardWithGrandchildren_descendantsAtEveryDepthIncludedIfMember() {
            // Arrange: Korur > Clients > Fabrikam, Korur > Platform.
            UUID userId = UUID.randomUUID();
            UUID rootId = UUID.randomUUID();
            UUID clientsId = UUID.randomUUID();
            UUID fabrikamId = UUID.randomUUID();
            UUID platformId = UUID.randomUUID();

            Board root = Board.builder().organizationId(organizationId).name("Korur").prefix("KOR").build();
            root.setId(rootId);
            Board clients = Board.builder().organizationId(organizationId).name("Clients").prefix("CLI").parentBoard(root).build();
            clients.setId(clientsId);
            Board fabrikam = Board.builder().organizationId(organizationId).name("Fabrikam").prefix("ACME").parentBoard(clients).build();
            fabrikam.setId(fabrikamId);
            Board platform = Board.builder().organizationId(organizationId).name("Platform").prefix("PLT").parentBoard(root).build();
            platform.setId(platformId);

            when(boardRepository.findByParentBoard_Id(rootId)).thenReturn(List.of(clients, platform));
            when(boardRepository.findByParentBoard_Id(clientsId)).thenReturn(List.of(fabrikam));
            when(boardRepository.findByParentBoard_Id(fabrikamId)).thenReturn(Collections.emptyList());
            when(boardRepository.findByParentBoard_Id(platformId)).thenReturn(Collections.emptyList());
            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId))
                    .thenReturn(List.of(rootId, clientsId, fabrikamId, platformId));
            when(boardRepository.findAllById(any())).thenReturn(List.of(clients, fabrikam, platform));

            // Act
            boardHelper.resolveRelatedBoardEntities(root, userId);

            // Assert
            ArgumentCaptor<Iterable<UUID>> idsCaptor = ArgumentCaptor.captor();
            verify(boardRepository).findAllById(idsCaptor.capture());
            Set<UUID> requestedIds = new HashSet<>();
            idsCaptor.getValue().forEach(requestedIds::add);
            assertEquals(Set.of(clientsId, fabrikamId, platformId), requestedIds,
                    "The grandchild board must be resolved with the direct children");
        }

        @Test
        void grandchildUnderNonMemberChild_stillIncluded() {
            // Arrange: the user is a member of the grandchild but not of its parent.
            UUID userId = UUID.randomUUID();
            UUID rootId = UUID.randomUUID();
            UUID clientsId = UUID.randomUUID();
            UUID fabrikamId = UUID.randomUUID();

            Board root = Board.builder().organizationId(organizationId).name("Korur").prefix("KOR").build();
            root.setId(rootId);
            Board clients = Board.builder().organizationId(organizationId).name("Clients").prefix("CLI").parentBoard(root).build();
            clients.setId(clientsId);
            Board fabrikam = Board.builder().organizationId(organizationId).name("Fabrikam").prefix("ACME").parentBoard(clients).build();
            fabrikam.setId(fabrikamId);

            when(boardRepository.findByParentBoard_Id(rootId)).thenReturn(List.of(clients));
            when(boardRepository.findByParentBoard_Id(clientsId)).thenReturn(List.of(fabrikam));
            when(boardRepository.findByParentBoard_Id(fabrikamId)).thenReturn(Collections.emptyList());
            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(List.of(rootId, fabrikamId));
            when(boardRepository.findAllById(any())).thenReturn(List.of(fabrikam));

            // Act
            boardHelper.resolveRelatedBoardEntities(root, userId);

            // Assert
            ArgumentCaptor<Iterable<UUID>> idsCaptor = ArgumentCaptor.captor();
            verify(boardRepository).findAllById(idsCaptor.capture());
            Set<UUID> requestedIds = new HashSet<>();
            idsCaptor.getValue().forEach(requestedIds::add);
            assertEquals(Set.of(fabrikamId), requestedIds,
                    "A member grandchild stays included when its parent is not a member board");
        }

        @Test
        void nonMemberBoards_excluded() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID boardId = UUID.randomUUID();
            UUID parentBoardId = UUID.randomUUID();
            UUID siblingBoardId = UUID.randomUUID();

            Board parentBoard = Board.builder().organizationId(organizationId).name("Parent").prefix("P").build();
            parentBoard.setId(parentBoardId);

            Board siblingBoard = Board.builder().organizationId(organizationId).name("Sibling").prefix("S").build();
            siblingBoard.setId(siblingBoardId);

            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").parentBoard(parentBoard).build();
            board.setId(boardId);

            when(boardRepository.findByParentBoard_Id(parentBoardId)).thenReturn(List.of(board, siblingBoard));
            when(boardRepository.findByParentBoard_Id(boardId)).thenReturn(Collections.emptyList());
            // User is only a member of self — not the sibling or parent.
            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(List.of(boardId));
            // After retainAll, relatedIds is empty (sibling and parent not in memberBoardIds).
            when(boardRepository.findAllById(any())).thenReturn(Collections.emptyList());

            // Act
            List<Board> result = boardHelper.resolveRelatedBoardEntities(board, userId);

            // Assert
            assertTrue(result.stream().noneMatch(b -> b.getId().equals(siblingBoardId)),
                    "Sibling board should be excluded when user is not a member");
        }

        @Test
        void nullParent_handledGracefully() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID boardId = UUID.randomUUID();

            // Board with no parent (parentBoard == null).
            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").build();
            board.setId(boardId);

            when(boardRepository.findByParentBoard_Id(boardId)).thenReturn(Collections.emptyList());
            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(List.of(boardId));
            when(boardRepository.findAllById(any())).thenReturn(Collections.emptyList());

            // Act — should not throw NullPointerException.
            List<Board> result = boardHelper.resolveRelatedBoardEntities(board, userId);

            // Assert
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class NextFreeSlug {

        @Test
        void createWithFreeBase_returnsBaseSlug() {
            // Arrange
            when(boardRepository.existsBySlug("my-board")).thenReturn(false);

            // Act
            String slug = boardHelper.nextFreeSlug("My Board", null);

            // Assert
            assertEquals("my-board", slug);
        }

        @Test
        void createWithTakenBase_returnsFirstFreeSuffix() {
            // Arrange
            when(boardRepository.existsBySlug("my-board")).thenReturn(true);
            when(boardRepository.existsBySlug("my-board-1")).thenReturn(true);
            when(boardRepository.existsBySlug("my-board-2")).thenReturn(false);

            // Act
            String slug = boardHelper.nextFreeSlug("My Board", null);

            // Assert
            assertEquals("my-board-2", slug);
        }

        @Test
        void renameIgnoresTheBoardsOwnSlug() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            when(boardRepository.existsBySlugAndIdNot("my-board", boardId)).thenReturn(false);

            // Act
            String slug = boardHelper.nextFreeSlug("My Board", boardId);

            // Assert
            assertEquals("my-board", slug);
            verify(boardRepository, never()).existsBySlug(any());
        }

        @Test
        void nameWithoutLettersOrDigits_usesFallbackSlug() {
            // Arrange
            when(boardRepository.existsBySlug("board")).thenReturn(false);

            // Act
            String slug = boardHelper.nextFreeSlug("🚀", null);

            // Assert
            assertEquals(BoardConstant.SLUG_FALLBACK, slug);
        }

        @Test
        void turkishName_isTransliterated() {
            // Act
            String slug = boardHelper.nextFreeSlug("Çalışma Panosu", null);

            // Assert
            assertEquals("calisma-panosu", slug);
        }

        @Test
        void uuidShapedName_getsASuffix() {
            // Arrange
            String uuidShaped = "123e4567-e89b-12d3-a456-426614174000";

            // Act
            String slug = boardHelper.nextFreeSlug(uuidShaped, null);

            // Assert
            assertEquals(uuidShaped + "-1", slug);
        }
    }

    @Nested
    class SyncSlugWithName {

        @Test
        void renameKeepingTheBase_keepsTheSlug() {
            // Arrange
            Board board = Board.builder().organizationId(organizationId).name("MY BOARD!").prefix("B").slug("my-board-2").build();
            board.setId(UUID.randomUUID());

            // Act
            boardHelper.syncSlugWithName(board);

            // Assert
            assertEquals("my-board-2", board.getSlug());
            verifyNoInteractions(boardRepository);
        }

        @Test
        void renameToNewBase_getsFreeSlugIgnoringOwnId() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            Board board = Board.builder().organizationId(organizationId).name("Roadmap Q4").prefix("B").slug("my-board").build();
            board.setId(boardId);
            when(boardRepository.existsBySlugAndIdNot("roadmap-q4", boardId)).thenReturn(true);
            when(boardRepository.existsBySlugAndIdNot("roadmap-q4-1", boardId)).thenReturn(false);

            // Act
            boardHelper.syncSlugWithName(board);

            // Assert
            assertEquals("roadmap-q4-1", board.getSlug());
        }
    }

    @Nested
    class SaveWithSlug {

        @Test
        void success_returnsTheFlushedBoard() {
            // Arrange
            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").slug("board").build();
            when(boardRepository.saveAndFlush(board)).thenReturn(board);

            // Act
            Board result = boardHelper.saveWithSlug(board);

            // Assert
            assertSame(board, result);
        }

        @Test
        void slugIndexViolation_throwsSlugTakenConflictNamingTheBoard() {
            // Arrange
            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").slug("board").build();
            board.setId(UUID.randomUUID());
            when(boardRepository.saveAndFlush(board)).thenThrow(violation(BoardConstant.SLUG_UNIQUE_INDEX));

            // Act
            ConflictException exception = assertThrows(ConflictException.class,
                    () -> boardHelper.saveWithSlug(board));

            // Assert
            assertEquals(ExceptionDomainKazanError.STA_SLUG_TAKEN.getCode(), exception.getErrorCode());
            assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
            assertEquals("Board", exception.getErrorValues().get(BoardConstant.FIELD_NAME));
        }

        @Test
        void otherIntegrityViolation_isRethrownUnchanged() {
            // Arrange
            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").slug("board").build();
            DataIntegrityViolationException original = violation("boards_prefix_uk");
            when(boardRepository.saveAndFlush(board)).thenThrow(original);

            // Act
            DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                    () -> boardHelper.saveWithSlug(board));

            // Assert
            assertSame(original, thrown);
        }
    }

    @Nested
    class RestoreWithFreeSlug {

        @Test
        void slugStillFree_restoresWithoutChangingTheSlug() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").slug("board").build();
            board.setId(boardId);
            when(boardRepository.existsBySlugAndIdNot("board", boardId)).thenReturn(false);

            // Act
            boardHelper.restoreWithFreeSlug(board);

            // Assert
            verify(boardRepository, never()).updateSlug(any(), any());
            verify(boardRepository).restoreById(boardId);
        }

        @Test
        void slugTakenMeanwhile_setsNextFreeSlugBeforeRestoring() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").slug("board").build();
            board.setId(boardId);
            when(boardRepository.existsBySlugAndIdNot("board", boardId)).thenReturn(true);
            when(boardRepository.existsBySlugAndIdNot("board-1", boardId)).thenReturn(false);

            // Act
            boardHelper.restoreWithFreeSlug(board);

            // Assert
            InOrder order = inOrder(boardRepository);
            order.verify(boardRepository).updateSlug(boardId, "board-1");
            order.verify(boardRepository).restoreById(boardId);
        }

        @Test
        void slugIndexViolationOnRestore_throwsSlugTakenConflict() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").slug("board").build();
            board.setId(boardId);
            when(boardRepository.existsBySlugAndIdNot("board", boardId)).thenReturn(false);
            doThrow(violation(BoardConstant.SLUG_UNIQUE_INDEX)).when(boardRepository).restoreById(boardId);

            // Act
            ConflictException exception = assertThrows(ConflictException.class,
                    () -> boardHelper.restoreWithFreeSlug(board));

            // Assert
            assertEquals(ExceptionDomainKazanError.STA_SLUG_TAKEN.getCode(), exception.getErrorCode());
            assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
            assertEquals("Board", exception.getErrorValues().get(BoardConstant.FIELD_NAME));
        }

        @Test
        void otherIntegrityViolationOnRestore_isRethrownUnchanged() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").slug("board").build();
            board.setId(boardId);
            DataIntegrityViolationException original = violation("boards_prefix_uk");
            doThrow(original).when(boardRepository).restoreById(boardId);

            // Act
            DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                    () -> boardHelper.restoreWithFreeSlug(board));

            // Assert
            assertSame(original, thrown);
        }
    }

    private static DataIntegrityViolationException violation(String constraintName) {
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("duplicate key", new SQLException("duplicate key"),
                        constraintName));
    }
}
