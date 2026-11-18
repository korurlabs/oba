package com.korur.oba.apps.kazan.label.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

import static com.korur.oba.apps.kazan.label.constant.service.LabelServiceLogConstant.*;

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.enums.BoardMemberRole;
import com.korur.oba.apps.kazan.board.service.BoardAccessService;
import com.korur.oba.apps.kazan.label.dto.request.LabelCreateRequest;
import com.korur.oba.apps.kazan.label.dto.request.LabelUpdateRequest;
import com.korur.oba.apps.kazan.label.dto.response.LabelResponse;
import com.korur.oba.apps.kazan.label.entity.Label;
import com.korur.oba.apps.kazan.label.mapper.LabelMapper;
import com.korur.oba.apps.kazan.label.repository.LabelRepository;
import com.korur.oba.apps.kazan.label.service.LabelService;
import com.korur.oba.apps.kazan.label.validator.LabelValidator;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

@Service
@RequiredArgsConstructor
@Transactional
public class LabelServiceImpl implements LabelService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(LabelServiceImpl.class);

    private final LabelRepository labelRepository;
    private final LabelMapper labelMapper;
    private final LabelValidator labelValidator;
    private final BoardAccessService boardAccessService;
    private final LoggingService loggingService;

    @Override
    public LabelResponse createLabel(User user, UUID organizationId, UUID boardId, LabelCreateRequest request) {
        // Validate user can manage the board, then fetch the board entity.
        Board board = boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN);

        // Check for soft-deleted label with same (board_id, name) and restore instead of creating new.
        Optional<Label> deletedLabel = labelRepository.findDeletedByBoardIdAndName(boardId, request.name());
        if (deletedLabel.isPresent()) {
            Label restored = deletedLabel.get();
            labelRepository.restoreById(restored.getId());

            // Update with the new request values (color may have changed).
            restored.setName(request.name());
            restored.setColor(request.color());
            restored = labelRepository.save(restored);

            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, LABEL_RESTORED, restored.getId());
            return labelMapper.toResponse(restored);
        }

        // Map request to entity and set the board relation.
        Label label = labelMapper.toEntity(request);
        label.setBoard(board);

        label = labelRepository.save(label);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, LABEL_CREATED, label.getId());

        return labelMapper.toResponse(label);
    }

    @Override
    public LabelResponse updateLabel(User user, UUID organizationId, UUID boardId, UUID labelId, LabelUpdateRequest request) {
        // Validate board and label exist, and user can manage the board.
        boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN);
        Label label = labelValidator.ensureLabelExists(boardId, labelId);

        // Apply partial update via mapper.
        labelMapper.updateEntity(label, request);
        label = labelRepository.save(label);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, LABEL_UPDATED, label.getId());

        return labelMapper.toResponse(label);
    }

    @Override
    public void deleteLabel(User user, UUID organizationId, UUID boardId, UUID labelId) {
        // Validate board and label exist, and user can manage the board.
        boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN);
        Label label = labelValidator.ensureLabelExists(boardId, labelId);

        labelRepository.delete(label);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, LABEL_DELETED, labelId);
    }
}
