package com.korur.oba.apps.kazan.comment.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.enums.BoardMemberRole;
import com.korur.oba.apps.kazan.board.service.BoardAccessService;
import com.korur.oba.apps.kazan.comment.dto.response.CommentDetailResponse;
import com.korur.oba.apps.kazan.comment.mapper.CommentMapper;
import com.korur.oba.apps.kazan.comment.repository.CommentRepository;
import com.korur.oba.apps.kazan.comment.service.CommentQueryService;
import com.korur.oba.shrine.identity.entity.User;
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentQueryServiceImpl implements CommentQueryService {

    private final CommentRepository commentRepository;
    private final CommentMapper commentMapper;
    private final BoardAccessService boardAccessService;

    @Override
    public List<CommentDetailResponse> listComments(User user, UUID organizationId, UUID boardId, UUID ticketId) {
        boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER);

        return commentRepository.findByTicket_IdAndTicket_Board_Id(ticketId, boardId).stream()
                .map(commentMapper::toDetailResponse)
                .toList();
    }
}
