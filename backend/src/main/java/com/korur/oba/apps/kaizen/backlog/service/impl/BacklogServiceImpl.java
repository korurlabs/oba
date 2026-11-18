package com.korur.oba.apps.kaizen.backlog.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.backlog.constant.service.BacklogServiceLogConstant;
import com.korur.oba.apps.kaizen.backlog.entity.Backlog;
import com.korur.oba.apps.kaizen.backlog.repository.BacklogRepository;
import com.korur.oba.apps.kaizen.backlog.service.BacklogService;
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.board.enums.BoardMemberRole;
import com.korur.oba.apps.kaizen.board.service.BoardAccessService;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.apps.kaizen.ticket.enums.SubTicketMoveMode;
import com.korur.oba.apps.kaizen.ticket.enums.TicketRelocationAction;
import com.korur.oba.apps.kaizen.ticket.helper.TicketHelper;
import com.korur.oba.apps.kaizen.ticket.validator.TicketValidator;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Implementation of backlog write operations.
 * <p>
 * Manages backlog lifecycle including default backlog creation for boards
 * and moving tickets to the backlog.
 */
@Service
@RequiredArgsConstructor
public class BacklogServiceImpl implements BacklogService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(BacklogServiceImpl.class);

    private final BacklogRepository backlogRepository;
    private final BoardAccessService boardAccessService;
    private final TicketValidator ticketValidator;
    private final TicketHelper ticketHelper;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public void addTicketToBacklog(User user, UUID organizationId, UUID boardId, UUID ticketId) {
        // Validate board access and backlog existence.
        Backlog backlog = boardAccessService.verifyBacklogAccess(
                organizationId, boardId, user.getId(), BoardMemberRole.MEMBER);

        // Validate ticket exists and move it with the rule shared by every user move: the
        // ticket takes its unfinished sub-tickets placed with it along, every parent is kept,
        // and a top-level backlog ticket is reset to TODO.
        Ticket ticket = ticketValidator.ensureTicketExists(boardId, ticketId);
        ticketHelper.moveTickets(
                List.of(ticket), SubTicketMoveMode.UNFINISHED, null,
                TicketRelocationAction.MOVE_TO_BACKLOG, null, null, backlog, boardId);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                BacklogServiceLogConstant.BACKLOG_UPDATED, backlog.getId());
    }

    @Override
    @Transactional
    public void createDefaultBacklog(Board board) {
        // Build and persist the default backlog for the board.
        Backlog backlog = Backlog.builder()
                .board(board)
                .build();
        backlog = backlogRepository.save(backlog);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                BacklogServiceLogConstant.BACKLOG_CREATED, backlog.getId());
    }
}
