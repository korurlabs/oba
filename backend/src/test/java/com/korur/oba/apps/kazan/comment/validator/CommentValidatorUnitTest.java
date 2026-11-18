package com.korur.oba.apps.kazan.comment.validator;

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
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.comment.entity.Comment;
import com.korur.oba.apps.kazan.comment.repository.CommentRepository;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.enums.TicketType;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class CommentValidatorUnitTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private CommentValidator commentValidator;

    @Nested
    class EnsureCommentExistsBoardScoped {

        @Test
        void ensureCommentExists_commentBelongsToTicketAndBoard_returnsComment() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID ticketId = UUID.randomUUID();
            UUID commentId = UUID.randomUUID();

            Board board = Board.builder().id(boardId).build();
            User author = User.builder().build();
            author.setId(UUID.randomUUID());

            Ticket ticket = Ticket.builder()
                    .title("Fix bug")
                    .key("B-1")
                    .type(TicketType.FEATURE)
                    .board(board)
                    .createdBy(author)
                    .build();
            ticket.setId(ticketId);

            Comment comment = Comment.builder()
                    .content("This is a comment")
                    .ticket(ticket)
                    .author(author)
                    .build();
            comment.setId(commentId);

            when(commentRepository.findByIdAndTicket_IdAndTicket_Board_Id(commentId, ticketId, boardId))
                    .thenReturn(Optional.of(comment));

            // Act
            Comment result = commentValidator.ensureCommentExists(boardId, ticketId, commentId);

            // Assert
            assertNotNull(result);
            assertEquals(commentId, result.getId());
        }

        @Test
        void ensureCommentExists_commentBelongsToDifferentBoard_throwsNotFoundException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID ticketId = UUID.randomUUID();
            UUID commentId = UUID.randomUUID();

            when(commentRepository.findByIdAndTicket_IdAndTicket_Board_Id(commentId, ticketId, boardId))
                    .thenReturn(Optional.empty());

            // Act
            NotFoundException exception = assertThrows(
                    NotFoundException.class,
                    () -> commentValidator.ensureCommentExists(boardId, ticketId, commentId));

            // Assert
            assertEquals(ExceptionDomainKazanError.NOT_COMMENT_FOUND.getCode(), exception.getErrorCode());
        }
    }
}
