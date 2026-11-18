package com.korur.oba.apps.kazan.comment.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.enums.BoardMemberRole;
import com.korur.oba.apps.kazan.board.service.BoardAccessService;
import com.korur.oba.apps.kazan.comment.dto.request.CommentCreateRequest;
import com.korur.oba.apps.kazan.comment.dto.request.CommentUpdateRequest;
import com.korur.oba.apps.kazan.comment.dto.response.CommentDetailResponse;
import com.korur.oba.apps.kazan.comment.entity.Comment;
import com.korur.oba.apps.kazan.comment.mapper.CommentMapper;
import com.korur.oba.apps.kazan.comment.repository.CommentRepository;
import com.korur.oba.apps.kazan.comment.service.CommentService;
import com.korur.oba.apps.kazan.comment.validator.CommentValidator;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.validator.TicketValidator;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

import static com.korur.oba.apps.kazan.comment.constant.service.CommentServiceLogConstant.*;

@Service
@RequiredArgsConstructor
@Transactional
public class CommentServiceImpl implements CommentService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(CommentServiceImpl.class);

    private final CommentRepository commentRepository;
    private final CommentMapper commentMapper;
    private final CommentValidator commentValidator;
    private final TicketValidator ticketValidator;
    private final BoardAccessService boardAccessService;
    private final LoggingService loggingService;

    @Override
    public CommentDetailResponse createComment(
            User user,
            UUID organizationId,
            UUID boardId,
            UUID ticketId,
            CommentCreateRequest request) {
        // Validate board membership (contribute permission).
        boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER);

        // Validate ticket exists.
        Ticket ticket = ticketValidator.ensureTicketExists(boardId, ticketId);

        // Map request to entity and set relations.
        Comment comment = commentMapper.toEntity(request);
        comment.setTicket(ticket);
        comment.setAuthor(user);

        comment = commentRepository.save(comment);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, COMMENT_CREATED, comment.getId());

        return commentMapper.toDetailResponse(comment);
    }

    @Override
    public CommentDetailResponse updateComment(
            User user,
            UUID organizationId,
            UUID boardId,
            UUID ticketId,
            UUID commentId,
            CommentUpdateRequest request) {
        // Validate board membership (contribute permission).
        boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER);

        // Validate comment exists on this board and ticket.
        Comment comment = commentValidator.ensureCommentExists(boardId, ticketId, commentId);
        commentValidator.ensureCommentHasCorrectAuthor(comment, user);

        // Update content.
        comment.setContent(request.content());
        comment = commentRepository.save(comment);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, COMMENT_UPDATED, comment.getId());

        return commentMapper.toDetailResponse(comment);
    }

    @Override
    public void deleteComment(
            User user,
            UUID organizationId,
            UUID boardId,
            UUID ticketId,
            UUID commentId) {
        // Validate board membership (contribute permission).
        boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER);

        // Validate comment exists on this board and ticket.
        Comment comment = commentValidator.ensureCommentExists(boardId, ticketId, commentId);
        commentValidator.ensureCommentHasCorrectAuthor(comment, user);

        commentRepository.delete(comment);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, COMMENT_DELETED, commentId);
    }
}
