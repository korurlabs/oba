package com.korur.oba.apps.kaizen.backlog.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;

import com.korur.oba.apps.kaizen.backlog.constant.BacklogConstant;
import com.korur.oba.apps.kaizen.backlog.constant.BacklogExceptionLogConstant;
import com.korur.oba.apps.kaizen.backlog.entity.Backlog;
import com.korur.oba.apps.kaizen.backlog.repository.BacklogRepository;
// 4. Internal
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Validator for backlog lookups.
 */
@Component
@RequiredArgsConstructor
public class BacklogValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(BacklogValidator.class);

    private final BacklogRepository backlogRepository;
    private final LoggingService loggingService;

    /**
     * Ensures the backlog exists for the specified board and returns it.
     */
    public Backlog ensureBacklogExistsByBoard(UUID backlogId, UUID boardId) {
        // Fetch the backlog or raise a domain-specific not-found error.
        return backlogRepository.findByBoard_IdAndId(boardId, backlogId)
                .orElseThrow(() -> {
                    // Log for observability before throwing the exception.
                    loggingService.error(
                            LOG_PREFIX,
                            LogLayer.VALIDATOR,
                            BacklogExceptionLogConstant.BACKLOG_NOT_FOUND,
                            backlogId);
                    // Propagate a NotFoundException with field context.
                    return new NotFoundException(
                            ExceptionDomainKaizenError.NOT_BACKLOG_FOUND,
                            Map.of(BacklogConstant.FIELD_BACKLOG_ID, backlogId));
                });
    }

    /**
     * Ensures a backlog exists for the specified board and returns it.
     */
    public Backlog ensureBacklogExistsByBoardId(UUID boardId) {
        // Fetch the backlog by board or raise a domain-specific not-found error.
        return backlogRepository.findByBoard_Id(boardId)
                .orElseThrow(() -> {
                    // Log for observability before throwing the exception.
                    loggingService.error(
                            LOG_PREFIX,
                            LogLayer.VALIDATOR,
                            BacklogExceptionLogConstant.BACKLOG_NOT_FOUND,
                            boardId);
                    // Propagate a NotFoundException with field context.
                    return new NotFoundException(
                            ExceptionDomainKaizenError.NOT_BACKLOG_FOUND,
                            Map.of(BacklogConstant.FIELD_BOARD_ID, boardId));
                });
    }
}
