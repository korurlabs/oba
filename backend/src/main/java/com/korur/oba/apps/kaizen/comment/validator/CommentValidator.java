package com.korur.oba.apps.kaizen.comment.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.comment.constant.CommentConstant;
import com.korur.oba.apps.kaizen.comment.constant.CommentExceptionLogConstant;
import com.korur.oba.apps.kaizen.comment.entity.Comment;
import com.korur.oba.apps.kaizen.comment.repository.CommentRepository;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Validator for comment lookups.
 */
@Component
@RequiredArgsConstructor
public class CommentValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(CommentValidator.class);

    private final CommentRepository commentRepository;
    private final LoggingService loggingService;

    /**
     * Ensures the comment exists on the specified board and ticket, and returns it.
     * Scopes the lookup to both the ticket and its board to prevent cross-board access.
     *
     * @param boardId   ID of the board from the URL path
     * @param ticketId  ID of the ticket from the URL path
     * @param commentId ID of the comment
     * @return the comment entity
     * @throws NotFoundException if the comment does not exist on the given ticket/board
     */
    public Comment ensureCommentExists(UUID boardId, UUID ticketId, UUID commentId) {
        return commentRepository.findByIdAndTicket_IdAndTicket_Board_Id(commentId, ticketId, boardId)
                .orElseThrow(() -> {
                    loggingService.error(
                            LOG_PREFIX,
                            LogLayer.VALIDATOR,
                            CommentExceptionLogConstant.COMMENT_NOT_FOUND,
                            commentId);
                    return new NotFoundException(
                            ExceptionDomainKaizenError.NOT_COMMENT_FOUND,
                            Map.of(CommentConstant.FIELD_COMMENT_ID, commentId));
                });
    }

    /**
     * Ensures the comment has the correct author.
     */
    public void ensureCommentHasCorrectAuthor(Comment comment, User user) {
        if (!comment.getAuthor().getId().equals(user.getId())) {
            throw new NotFoundException(
                    ExceptionDomainKaizenError.VAL_CANNOT_EDIT_OTHERS_COMMENT,
                    Map.of(CommentConstant.FIELD_COMMENT_ID, comment.getId()));
        }
    }
}
