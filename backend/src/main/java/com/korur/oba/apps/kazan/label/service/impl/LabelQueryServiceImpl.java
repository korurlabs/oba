package com.korur.oba.apps.kazan.label.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.enums.BoardMemberRole;
import com.korur.oba.apps.kazan.board.helper.BoardHelper;
import com.korur.oba.apps.kazan.board.service.BoardAccessService;
import com.korur.oba.apps.kazan.label.dto.response.LabelResponse;
import com.korur.oba.apps.kazan.label.mapper.LabelMapper;
import com.korur.oba.apps.kazan.label.repository.LabelRepository;
import com.korur.oba.apps.kazan.label.service.LabelQueryService;
import com.korur.oba.shrine.identity.entity.User;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LabelQueryServiceImpl implements LabelQueryService {

    private final LabelRepository labelRepository;
    private final LabelMapper labelMapper;
    private final BoardAccessService boardAccessService;
    private final BoardHelper boardHelper;

    @Override
    public List<LabelResponse> listLabels(User user, UUID organizationId, UUID boardId) {
        Board board = boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER);

        List<Board> relatedBoards = boardHelper.resolveRelatedBoardEntities(board, user.getId());
        List<UUID> allBoardIds = new ArrayList<>(relatedBoards.stream().map(Board::getId).toList());
        allBoardIds.add(boardId);

        return labelRepository.findByBoard_IdIn(allBoardIds).stream()
                .map(labelMapper::toResponse)
                .toList();
    }
}
