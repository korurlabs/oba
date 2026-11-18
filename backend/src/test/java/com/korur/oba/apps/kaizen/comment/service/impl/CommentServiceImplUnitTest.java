package com.korur.oba.apps.kaizen.comment.service.impl;

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
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.board.enums.BoardMemberRole;
import com.korur.oba.apps.kaizen.board.service.BoardAccessService;
import com.korur.oba.apps.kaizen.comment.dto.request.CommentCreateRequest;
import com.korur.oba.apps.kaizen.comment.dto.request.CommentUpdateRequest;
import com.korur.oba.apps.kaizen.comment.dto.response.CommentDetailResponse;
import com.korur.oba.apps.kaizen.comment.entity.Comment;
import com.korur.oba.apps.kaizen.comment.mapper.CommentMapper;
import com.korur.oba.apps.kaizen.comment.repository.CommentRepository;
import com.korur.oba.apps.kaizen.comment.validator.CommentValidator;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.apps.kaizen.ticket.validator.TicketValidator;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class CommentServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private CommentValidator commentValidator;

    @Mock
    private TicketValidator ticketValidator;

    @Mock
    private BoardAccessService boardAccessService;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private CommentServiceImpl commentServiceImpl;

    @Nested
    class CreateCommentAuthorization {

        @Test
        void viewerMember_throwsForbiddenException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID ticketId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            CommentCreateRequest request = new CommentCreateRequest("This is a comment.");

            doThrow(new ForbiddenException(
                    ExceptionDomainKaizenError.SEC_INSUFFICIENT_BOARD_PERMISSIONS,
                    Map.of("boardId", boardId)))
                    .when(boardAccessService).verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER);

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    commentServiceImpl.createComment(user, organizationId, boardId, ticketId, request));
            verify(commentRepository, never()).save(any());
        }

        @Test
        void contributingMember_createsCommentSuccessfully() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID ticketId = UUID.randomUUID();
            UUID commentId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Ticket ticket = Ticket.builder().key("B-1").title("Fix bug").build();
            ticket.setId(ticketId);

            Comment comment = Comment.builder().content("This is a comment.").build();
            comment.setId(commentId);

            CommentCreateRequest request = new CommentCreateRequest("This is a comment.");
            CommentDetailResponse expectedResponse = new CommentDetailResponse(
                    commentId, null, null, "This is a comment.", ticketId,
                    user.getId(), "test@example.com", "John", "Doe");

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER)).thenReturn(board);
            when(ticketValidator.ensureTicketExists(boardId, ticketId)).thenReturn(ticket);
            when(commentMapper.toEntity(request)).thenReturn(comment);
            when(commentRepository.save(comment)).thenReturn(comment);
            when(commentMapper.toDetailResponse(comment)).thenReturn(expectedResponse);

            // Act
            CommentDetailResponse result = commentServiceImpl.createComment(user, organizationId, boardId, ticketId, request);

            // Assert
            assertNotNull(result);
            verify(boardAccessService).verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER);
            verify(commentRepository).save(comment);
        }
    }

    @Nested
    class UpdateCommentAuthorization {

        @Test
        void viewerMember_throwsForbiddenException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID ticketId = UUID.randomUUID();
            UUID commentId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            CommentUpdateRequest request = new CommentUpdateRequest("Updated comment.");

            doThrow(new ForbiddenException(
                    ExceptionDomainKaizenError.SEC_INSUFFICIENT_BOARD_PERMISSIONS,
                    Map.of("boardId", boardId)))
                    .when(boardAccessService).verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER);

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    commentServiceImpl.updateComment(user, organizationId, boardId, ticketId, commentId, request));
            verify(commentRepository, never()).save(any());
        }
    }

    @Nested
    class DeleteCommentAuthorization {

        @Test
        void viewerMember_throwsForbiddenException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID ticketId = UUID.randomUUID();
            UUID commentId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            doThrow(new ForbiddenException(
                    ExceptionDomainKaizenError.SEC_INSUFFICIENT_BOARD_PERMISSIONS,
                    Map.of("boardId", boardId)))
                    .when(boardAccessService).verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER);

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    commentServiceImpl.deleteComment(user, organizationId, boardId, ticketId, commentId));
            verify(commentRepository, never()).delete(any());
        }
    }
}
