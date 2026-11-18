package com.korur.oba.apps.kaizen.backlog.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.backlog.dto.response.BacklogDetailResponse;
import com.korur.oba.apps.kaizen.backlog.entity.Backlog;
import com.korur.oba.apps.kaizen.backlog.mapper.BacklogMapper;
import com.korur.oba.apps.kaizen.backlog.repository.BacklogRepository;
import com.korur.oba.apps.kaizen.backlog.service.BacklogQueryService;
import com.korur.oba.apps.kaizen.board.enums.BoardMemberRole;
import com.korur.oba.apps.kaizen.board.service.BoardAccessService;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Implementation of backlog read operations.
 * <p>
 * All methods run within a read-only transaction for optimal database performance.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BacklogQueryServiceImpl implements BacklogQueryService {

    private final BacklogRepository backlogRepository;
    private final BoardAccessService boardAccessService;
    private final BacklogMapper backlogMapper;

    @Override
    public BacklogDetailResponse getBacklog(User user, UUID organizationId, UUID boardId) {
        // Validate board access and backlog existence.
        Backlog backlog = boardAccessService.verifyBacklogAccess(
                organizationId, boardId, user.getId(), BoardMemberRole.VIEWER);
        return backlogMapper.toDetailResponse(backlog);
    }

    @Override
    public Optional<Backlog> findBacklogEntityByBoardId(UUID boardId) {
        return backlogRepository.findByBoard_Id(boardId);
    }
}
