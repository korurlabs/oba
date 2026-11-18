package com.korur.oba.apps.kazan.comment.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.enums.BoardMemberRole;
import com.korur.oba.apps.kazan.board.service.BoardAccessService;
import com.korur.oba.apps.kazan.comment.dto.response.CommentDetailResponse;
import com.korur.oba.apps.kazan.comment.entity.Comment;
import com.korur.oba.apps.kazan.comment.mapper.CommentMapper;
import com.korur.oba.apps.kazan.comment.repository.CommentRepository;
import com.korur.oba.dojo.exception.enums.ExceptionNotFoundError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class CommentQueryServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private BoardAccessService boardAccessService;

    @InjectMocks
    private CommentQueryServiceImpl commentQueryServiceImpl;

    @Nested
    class ListComments {

        @Test
        void userIsNotBoardMember_throwsNotFoundException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID ticketId = UUID.randomUUID();

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER))
                    .thenThrow(new NotFoundException(ExceptionNotFoundError.RESOURCE_FOUND));

            // Act & Assert
            assertThrows(NotFoundException.class, () ->
                    commentQueryServiceImpl.listComments(user, organizationId, boardId, ticketId));
        }

        @Test
        void userIsBoardMember_returnsCommentList() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID ticketId = UUID.randomUUID();
            UUID commentId = UUID.randomUUID();

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Comment comment = Comment.builder().content("A comment").build();
            comment.setId(commentId);

            CommentDetailResponse expectedResponse = new CommentDetailResponse(
                    commentId, null, null, "A comment", ticketId, user.getId(), null, null, null);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER)).thenReturn(board);
            when(commentRepository.findByTicket_IdAndTicket_Board_Id(ticketId, boardId))
                    .thenReturn(List.of(comment));
            when(commentMapper.toDetailResponse(comment)).thenReturn(expectedResponse);

            // Act
            List<CommentDetailResponse> result = commentQueryServiceImpl.listComments(user, organizationId, boardId, ticketId);

            // Assert
            assertNotNull(result);
            verify(boardAccessService).verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER);
            verify(commentRepository).findByTicket_IdAndTicket_Board_Id(ticketId, boardId);
        }
    }
}
