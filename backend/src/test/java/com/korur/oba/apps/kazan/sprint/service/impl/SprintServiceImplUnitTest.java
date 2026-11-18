package com.korur.oba.apps.kazan.sprint.service.impl;

// 1. Framework-level
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.korur.oba.apps.kazan.sprint.mock_data.SprintRecurrenceMocks.inSeries;
import static com.korur.oba.apps.kazan.sprint.mock_data.SprintRecurrenceMocks.linkedTo;
import static com.korur.oba.apps.kazan.sprint.mock_data.SprintRecurrenceMocks.series;
import static com.korur.oba.apps.kazan.sprint.mock_data.SprintRecurrenceMocks.utc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 2. Third-party
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;

// 3. Java SDK
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.backlog.entity.Backlog;
import com.korur.oba.apps.kazan.backlog.validator.BacklogValidator;
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.entity.BoardSprint;
import com.korur.oba.apps.kazan.board.enums.BoardMemberRole;
import com.korur.oba.apps.kazan.board.repository.BoardSprintRepository;
import com.korur.oba.apps.kazan.board.service.BoardAccessService;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintBulkLinkRequest;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintCompleteRequest;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintCreateRequest;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintDeleteRequest;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintSeriesRequest;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintStartRequest;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintUnlinkRequest;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintUnlinkTransferRequest;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintUpdateRequest;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.sprint.constant.SprintConstant;
import com.korur.oba.apps.kazan.sprint.constant.SprintRecurrenceConstant;
import com.korur.oba.apps.kazan.sprint.entity.Sprint;
import com.korur.oba.apps.kazan.sprint.entity.SprintSeries;
import com.korur.oba.apps.kazan.sprint.entity.SprintSeriesBoard;
import com.korur.oba.apps.kazan.sprint.enums.SprintRecurrenceCreationMode;
import com.korur.oba.apps.kazan.sprint.enums.SprintRecurrenceRunStatus;
import com.korur.oba.apps.kazan.sprint.enums.SprintRecurrenceTicketAction;
import com.korur.oba.apps.kazan.sprint.enums.SprintStatus;
import com.korur.oba.apps.kazan.sprint.helper.SprintLifecycleHelper;
import com.korur.oba.apps.kazan.sprint.helper.SprintRecurrenceCalculator;
import com.korur.oba.apps.kazan.sprint.mapper.SprintMapper;
import com.korur.oba.apps.kazan.sprint.mapper.SprintSeriesMapper;
import com.korur.oba.apps.kazan.sprint.repository.SprintRepository;
import com.korur.oba.apps.kazan.sprint.repository.SprintSeriesBoardRepository;
import com.korur.oba.apps.kazan.sprint.repository.SprintSeriesRepository;
import com.korur.oba.apps.kazan.sprint.service.SprintSeriesService;
import com.korur.oba.apps.kazan.sprint.validator.SprintValidator;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.enums.TicketRelocationAction;
import com.korur.oba.apps.kazan.ticket.enums.TicketStatus;
import com.korur.oba.apps.kazan.ticket.enums.TicketType;
import com.korur.oba.apps.kazan.ticket.helper.TicketHelper;
import com.korur.oba.apps.kazan.ticket.repository.TicketRepository;
import com.korur.oba.apps.kazan.ticket.validator.TicketValidator;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class SprintServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private SprintRepository sprintRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private BoardSprintRepository boardSprintRepository;

    @Mock
    private BoardAccessService boardAccessService;

    @Mock
    private SprintValidator sprintValidator;

    @Mock
    private BacklogValidator backlogValidator;

    @Mock
    private TicketValidator ticketValidator;

    @Mock
    private SprintMapper sprintMapper;

    @Mock
    private TicketHelper ticketHelper;

    @Mock
    private SprintSeriesService sprintSeriesService;

    @Mock
    private SprintSeriesRepository sprintSeriesRepository;

    @Mock
    private SprintSeriesBoardRepository sprintSeriesBoardRepository;

    @Mock
    private LoggingService loggingService;

    @Mock
    private EntityManager entityManager;

    /**
     * 2026-11-10 at 15:00 UTC, during the day a series sprint is completed by hand.
     */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZoneOffset.UTC);

    private SprintServiceImpl sprintServiceImpl;

    /**
     * Builds the service on a real lifecycle helper over the same mocks, so the
     * tests check the repository and ticket calls a sprint change makes, wherever
     * the rule lives.
     */
    @BeforeEach
    void setUp() {
        SprintLifecycleHelper sprintLifecycleHelper = new SprintLifecycleHelper(
                sprintRepository, sprintSeriesRepository, boardSprintRepository, ticketRepository,
                sprintValidator, backlogValidator, ticketHelper, loggingService);
        sprintServiceImpl = new SprintServiceImpl(
                sprintRepository, ticketRepository, boardSprintRepository, boardAccessService,
                sprintValidator, backlogValidator, ticketValidator, sprintMapper, ticketHelper,
                sprintLifecycleHelper, sprintSeriesService, loggingService, entityManager, CLOCK);
    }

    @Nested
    class DeleteSprint {

        @Test
        void noTickets_deletesSuccessfully() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID sprintId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Sprint sprint = Sprint.builder().name("Sprint 1").build();
            sprint.setId(sprintId);

            when(boardAccessService.verifySprintAccess(organizationId, boardId, sprintId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(sprint);
            when(ticketRepository.findBySprint_Id(sprintId)).thenReturn(Collections.emptyList());

            // Act
            sprintServiceImpl.deleteSprint(user, organizationId, boardId, sprintId, new SprintDeleteRequest(null, null));

            // Assert
            verify(sprintRepository).delete(sprint);
            verify(ticketHelper, never()).relocateTickets(any(), any(), any(), any(), any(), any());
            InOrder seriesOrder = inOrder(sprintSeriesService, sprintRepository);
            seriesOrder.verify(sprintSeriesService).lockSeries(sprint);
            seriesOrder.verify(sprintRepository).delete(sprint);
            seriesOrder.verify(sprintSeriesService).rescheduleSeries(sprint);
        }

        @Test
        void withTickets_nullAction_throwsValidationException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID sprintId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Sprint sprint = Sprint.builder().name("Sprint 1").build();
            sprint.setId(sprintId);

            Ticket ticket = Ticket.builder().title("Ticket").key("B-1").type(TicketType.FEATURE).board(board).createdBy(user).build();
            ticket.setId(UUID.randomUUID());

            when(boardAccessService.verifySprintAccess(organizationId, boardId, sprintId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(sprint);
            when(ticketRepository.findBySprint_Id(sprintId)).thenReturn(List.of(ticket));
            doThrow(new ValidationException(
                    com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError.VAL_SPRINT_TICKET_ACTION_REQUIRED,
                    java.util.Map.of()))
                    .when(ticketValidator).ensureTicketActionProvided(null, sprintId);

            // Act & Assert
            assertThrows(ValidationException.class, () ->
                    sprintServiceImpl.deleteSprint(user, organizationId, boardId, sprintId, new SprintDeleteRequest(null, null)));
        }

        @Test
        void withTickets_validAction_delegatesToSprintHelper() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID sprintId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Sprint sprint = Sprint.builder().name("Sprint 1").build();
            sprint.setId(sprintId);

            Backlog backlog = Backlog.builder().board(board).build();
            backlog.setId(UUID.randomUUID());

            Ticket ticket = Ticket.builder().title("Ticket").key("B-1").type(TicketType.FEATURE).board(board).createdBy(user).build();
            ticket.setId(UUID.randomUUID());
            List<Ticket> tickets = List.of(ticket);

            when(boardAccessService.verifySprintAccess(organizationId, boardId, sprintId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(sprint);
            when(ticketRepository.findBySprint_Id(sprintId)).thenReturn(tickets);
            when(backlogValidator.ensureBacklogExistsByBoardId(boardId)).thenReturn(backlog);

            // Act
            sprintServiceImpl.deleteSprint(user, organizationId, boardId, sprintId, new SprintDeleteRequest(TicketRelocationAction.MOVE_TO_BACKLOG, null));

            // Assert
            verify(backlogValidator).ensureBacklogExistsByBoardId(boardId);
            verify(ticketHelper).relocateTickets(
                    eq(tickets),
                    eq(TicketRelocationAction.MOVE_TO_BACKLOG),
                    eq(null),
                    eq(null),
                    eq(backlog),
                    eq(sprintId));
            verify(sprintRepository).delete(sprint);
        }

        @Test
        void withTickets_moveToSameSprint_throwsValidationException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID sprintId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Sprint sprint = Sprint.builder().name("Sprint 1").build();
            sprint.setId(sprintId);

            Ticket ticket = Ticket.builder().title("Ticket").key("B-1").type(TicketType.FEATURE).board(board).createdBy(user).build();
            ticket.setId(UUID.randomUUID());

            when(boardAccessService.verifySprintAccess(organizationId, boardId, sprintId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(sprint);
            when(ticketRepository.findBySprint_Id(sprintId)).thenReturn(List.of(ticket));
            doThrow(new ValidationException(
                    com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError.VAL_SPRINT_TARGET_SPRINT_SELF_REFERENCE,
                    java.util.Map.of()))
                    .when(sprintValidator).ensureTargetSprintNotSelf(sprintId, sprintId);

            // Act & Assert — target sprint is the same as the sprint being deleted.
            assertThrows(ValidationException.class, () ->
                    sprintServiceImpl.deleteSprint(user, organizationId, boardId, sprintId,
                            new SprintDeleteRequest(TicketRelocationAction.MOVE_TO_SPRINT, sprintId)));
        }

        @Test
        void withTickets_deleteAction_delegatesToSprintHelperAndDeletesSprint() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID sprintId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Sprint sprint = Sprint.builder().name("Sprint 1").build();
            sprint.setId(sprintId);

            Ticket ticket = Ticket.builder().title("Ticket").key("B-1").type(TicketType.FEATURE).board(board).createdBy(user).build();
            ticket.setId(UUID.randomUUID());
            List<Ticket> tickets = List.of(ticket);

            when(boardAccessService.verifySprintAccess(organizationId, boardId, sprintId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(sprint);
            when(ticketRepository.findBySprint_Id(sprintId)).thenReturn(tickets);

            // Act
            sprintServiceImpl.deleteSprint(user, organizationId, boardId, sprintId, new SprintDeleteRequest(TicketRelocationAction.DELETE, null));

            // Assert
            verify(ticketHelper).relocateTickets(
                    eq(tickets),
                    eq(TicketRelocationAction.DELETE),
                    eq(null),
                    eq(null),
                    eq(null),
                    eq(sprintId));
            verify(sprintRepository).delete(sprint);
        }
    }

    @Nested
    class CompleteSprint {

        @Test
        void sprintNotActive_throwsValidationException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID sprintId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Sprint sprint = Sprint.builder().name("Sprint 1").build();
            sprint.setId(sprintId);
            sprint.setStatus(SprintStatus.PLANNED);

            when(boardAccessService.verifySprintAccess(organizationId, boardId, sprintId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(sprint);
            doThrow(new ValidationException(
                    com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError.VAL_SPRINT_NOT_ACTIVE,
                    java.util.Map.of()))
                    .when(sprintValidator).ensureSprintIsActive(sprint);

            // Act & Assert
            assertThrows(ValidationException.class, () ->
                    sprintServiceImpl.completeSprint(user, organizationId, boardId, sprintId,
                            new SprintCompleteRequest(TicketRelocationAction.MOVE_TO_BACKLOG, null, null, null)));
        }

        @Test
        void noIncompleteTickets_setsStatusToCompletedWithoutRelocation() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID sprintId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Sprint sprint = Sprint.builder().name("Sprint 1").build();
            sprint.setId(sprintId);
            sprint.setStatus(SprintStatus.ACTIVE);

            when(boardAccessService.verifySprintAccess(organizationId, boardId, sprintId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(sprint);
            when(ticketRepository.findBySprint_IdAndStatusNot(sprintId, TicketStatus.DONE))
                    .thenReturn(Collections.emptyList());
            when(sprintRepository.save(sprint)).thenReturn(sprint);
            when(sprintMapper.toDetailResponse(sprint)).thenReturn(null);

            // Act
            sprintServiceImpl.completeSprint(user, organizationId, boardId, sprintId,
                    new SprintCompleteRequest(TicketRelocationAction.MOVE_TO_BACKLOG, null, null, null));

            // Assert
            verify(ticketHelper, never()).relocateTickets(any(), any(), any(), any(), any(), any());
            verify(sprintRepository).save(sprint);
            InOrder seriesOrder = inOrder(sprintSeriesService, sprintRepository);
            seriesOrder.verify(sprintSeriesService).lockSeries(sprint);
            seriesOrder.verify(sprintRepository).save(sprint);
            seriesOrder.verify(sprintSeriesService).rescheduleSeries(sprint);
        }

        @Test
        void withIncompleteTickets_moveToBacklog_relocatesAndCompletes() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID sprintId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Sprint sprint = Sprint.builder().name("Sprint 1").build();
            sprint.setId(sprintId);
            sprint.setStatus(SprintStatus.ACTIVE);

            Backlog backlog = Backlog.builder().board(board).build();
            backlog.setId(UUID.randomUUID());

            Ticket ticket = Ticket.builder().title("T").key("B-1").type(TicketType.FEATURE).board(board).createdBy(user).build();
            ticket.setId(UUID.randomUUID());
            List<Ticket> incompleteTickets = List.of(ticket);

            when(boardAccessService.verifySprintAccess(organizationId, boardId, sprintId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(sprint);
            when(ticketRepository.findBySprint_IdAndStatusNot(sprintId, TicketStatus.DONE))
                    .thenReturn(incompleteTickets);
            when(backlogValidator.ensureBacklogExistsByBoardId(boardId)).thenReturn(backlog);
            when(sprintRepository.save(sprint)).thenReturn(sprint);
            when(sprintMapper.toDetailResponse(sprint)).thenReturn(null);

            // Act
            sprintServiceImpl.completeSprint(user, organizationId, boardId, sprintId,
                    new SprintCompleteRequest(TicketRelocationAction.MOVE_TO_BACKLOG, null, null, null));

            // Assert
            verify(backlogValidator).ensureBacklogExistsByBoardId(boardId);
            verify(ticketHelper).relocateTickets(
                    eq(incompleteTickets),
                    eq(TicketRelocationAction.MOVE_TO_BACKLOG),
                    eq(null),
                    eq(null),
                    eq(backlog),
                    eq(sprintId));
            verify(sprintRepository).save(sprint);
        }

        @Test
        void waitingForThirdPartyTicket_moveToBacklog_isCarriedOverLikeAnyNotDoneTicket() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID sprintId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Sprint sprint = Sprint.builder().name("Sprint 1").build();
            sprint.setId(sprintId);
            sprint.setStatus(SprintStatus.ACTIVE);

            Backlog backlog = Backlog.builder().board(board).build();
            backlog.setId(UUID.randomUUID());

            Ticket waiting = Ticket.builder().title("T").key("B-1").type(TicketType.FEATURE)
                    .status(TicketStatus.WAITING_FOR_THIRD_PARTY).board(board).createdBy(user).build();
            waiting.setId(UUID.randomUUID());
            List<Ticket> incompleteTickets = List.of(waiting);

            when(boardAccessService.verifySprintAccess(organizationId, boardId, sprintId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(sprint);
            when(ticketRepository.findBySprint_IdAndStatusNot(sprintId, TicketStatus.DONE))
                    .thenReturn(incompleteTickets);
            when(backlogValidator.ensureBacklogExistsByBoardId(boardId)).thenReturn(backlog);
            when(sprintRepository.save(sprint)).thenReturn(sprint);
            when(sprintMapper.toDetailResponse(sprint)).thenReturn(null);

            // Act
            sprintServiceImpl.completeSprint(user, organizationId, boardId, sprintId,
                    new SprintCompleteRequest(TicketRelocationAction.MOVE_TO_BACKLOG, null, null, null));

            // Assert
            verify(ticketRepository).findBySprint_IdAndStatusNot(sprintId, TicketStatus.DONE);
            verify(ticketHelper).relocateTickets(
                    eq(incompleteTickets),
                    eq(TicketRelocationAction.MOVE_TO_BACKLOG),
                    eq(null),
                    eq(null),
                    eq(backlog),
                    eq(sprintId));
        }

        @Test
        void withIncompleteTickets_moveToSprint_relocatesAndCompletes() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID sprintId = UUID.randomUUID();
            UUID targetSprintId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Sprint sprint = Sprint.builder().name("Sprint 1").build();
            sprint.setId(sprintId);
            sprint.setStatus(SprintStatus.ACTIVE);

            Sprint targetSprint = Sprint.builder().name("Sprint 2").build();
            targetSprint.setId(targetSprintId);
            // Link target sprint to the same board so the same-board validation passes.
            BoardSprint boardSprint = BoardSprint.builder().board(board).sprint(targetSprint).build();
            boardSprint.setId(UUID.randomUUID());
            targetSprint.getBoardSprints().add(boardSprint);

            Ticket ticket = Ticket.builder().title("T").key("B-1").type(TicketType.FEATURE).board(board).createdBy(user).build();
            ticket.setId(UUID.randomUUID());
            List<Ticket> incompleteTickets = List.of(ticket);

            when(boardAccessService.verifySprintAccess(organizationId, boardId, sprintId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(sprint);
            when(ticketRepository.findBySprint_IdAndStatusNot(sprintId, TicketStatus.DONE))
                    .thenReturn(incompleteTickets);
            when(sprintValidator.ensureSprintExists(targetSprintId)).thenReturn(targetSprint);
            when(sprintRepository.save(sprint)).thenReturn(sprint);
            when(sprintMapper.toDetailResponse(sprint)).thenReturn(null);

            // Act
            sprintServiceImpl.completeSprint(user, organizationId, boardId, sprintId,
                    new SprintCompleteRequest(TicketRelocationAction.MOVE_TO_SPRINT, targetSprintId, null, null));

            // Assert
            verify(sprintValidator).ensureSprintExists(targetSprintId);
            verify(ticketHelper).relocateTickets(
                    eq(incompleteTickets),
                    eq(TicketRelocationAction.MOVE_TO_SPRINT),
                    eq(null),
                    eq(targetSprint),
                    eq(null),
                    eq(sprintId));
            verify(sprintRepository).save(sprint);
        }

        @Test
        void withIncompleteTickets_deleteAction_deletesAndCompletes() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID sprintId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Sprint sprint = Sprint.builder().name("Sprint 1").build();
            sprint.setId(sprintId);
            sprint.setStatus(SprintStatus.ACTIVE);

            Ticket ticket = Ticket.builder().title("T").key("B-1").type(TicketType.FEATURE).board(board).createdBy(user).build();
            ticket.setId(UUID.randomUUID());
            List<Ticket> incompleteTickets = List.of(ticket);

            when(boardAccessService.verifySprintAccess(organizationId, boardId, sprintId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(sprint);
            when(ticketRepository.findBySprint_IdAndStatusNot(sprintId, TicketStatus.DONE))
                    .thenReturn(incompleteTickets);
            when(sprintRepository.save(sprint)).thenReturn(sprint);
            when(sprintMapper.toDetailResponse(sprint)).thenReturn(null);

            // Act
            sprintServiceImpl.completeSprint(user, organizationId, boardId, sprintId,
                    new SprintCompleteRequest(TicketRelocationAction.DELETE, null, null, null));

            // Assert
            verify(ticketHelper).relocateTickets(
                    eq(incompleteTickets),
                    eq(TicketRelocationAction.DELETE),
                    eq(null),
                    eq(null),
                    eq(null),
                    eq(sprintId));
            verify(sprintRepository).save(sprint);
        }

        @Test
        void withIncompleteTickets_crossBoardMoveToBacklog_resolvesTargetBoard() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID sprintId = UUID.randomUUID();
            UUID targetBoardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Source Board").prefix("S").build();
            board.setId(boardId);

            Board targetBoard = Board.builder().name("Target Board").prefix("T").build();
            targetBoard.setId(targetBoardId);

            Sprint sprint = Sprint.builder().name("Sprint 1").build();
            sprint.setId(sprintId);
            sprint.setStatus(SprintStatus.ACTIVE);

            Backlog targetBacklog = Backlog.builder().board(targetBoard).build();
            targetBacklog.setId(UUID.randomUUID());

            Ticket ticket = Ticket.builder().title("T").key("S-1").type(TicketType.FEATURE).board(board).createdBy(user).build();
            ticket.setId(UUID.randomUUID());
            List<Ticket> incompleteTickets = List.of(ticket);

            when(boardAccessService.verifySprintAccess(organizationId, boardId, sprintId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(sprint);
            when(ticketRepository.findBySprint_IdAndStatusNot(sprintId, TicketStatus.DONE))
                    .thenReturn(incompleteTickets);
            when(boardAccessService.verifyBoardAccess(organizationId, targetBoardId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(targetBoard);
            when(backlogValidator.ensureBacklogExistsByBoardId(targetBoardId)).thenReturn(targetBacklog);
            when(sprintRepository.save(sprint)).thenReturn(sprint);
            when(sprintMapper.toDetailResponse(sprint)).thenReturn(null);

            // Act
            sprintServiceImpl.completeSprint(user, organizationId, boardId, sprintId,
                    new SprintCompleteRequest(TicketRelocationAction.MOVE_TO_BACKLOG, null, targetBoardId, null));

            // Assert
            verify(boardAccessService).verifyBoardAccess(organizationId, targetBoardId, user.getId(), BoardMemberRole.ADMIN);
            verify(backlogValidator).ensureBacklogExistsByBoardId(targetBoardId);
            verify(ticketHelper).relocateTickets(
                    eq(incompleteTickets),
                    eq(TicketRelocationAction.MOVE_TO_BACKLOG),
                    eq(targetBoard),
                    eq(null),
                    eq(targetBacklog),
                    eq(sprintId));
        }

        @Test
        void withIncompleteTickets_moveToSameSprint_throwsValidationException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID sprintId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Sprint sprint = Sprint.builder().name("Sprint 1").build();
            sprint.setId(sprintId);
            sprint.setStatus(SprintStatus.ACTIVE);

            Ticket ticket = Ticket.builder().title("T").key("B-1").type(TicketType.FEATURE).board(board).createdBy(user).build();
            ticket.setId(UUID.randomUUID());
            List<Ticket> incompleteTickets = List.of(ticket);

            when(boardAccessService.verifySprintAccess(organizationId, boardId, sprintId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(sprint);
            when(ticketRepository.findBySprint_IdAndStatusNot(sprintId, TicketStatus.DONE))
                    .thenReturn(incompleteTickets);
            doThrow(new ValidationException(
                    com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError.VAL_SPRINT_TARGET_SPRINT_SELF_REFERENCE,
                    java.util.Map.of()))
                    .when(sprintValidator).ensureTargetSprintNotSelf(sprintId, sprintId);

            // Act & Assert — target sprint is the same as the sprint being completed.
            assertThrows(ValidationException.class, () ->
                    sprintServiceImpl.completeSprint(user, organizationId, boardId, sprintId,
                            new SprintCompleteRequest(TicketRelocationAction.MOVE_TO_SPRINT, sprintId, null, null)));
        }

        @Test
        void crossBoardMove_userLacksPermissionOnTargetBoard_throwsForbiddenException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID sprintId = UUID.randomUUID();
            UUID targetBoardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Source Board").prefix("S").build();
            board.setId(boardId);

            Sprint sprint = Sprint.builder().name("Sprint 1").build();
            sprint.setId(sprintId);
            sprint.setStatus(SprintStatus.ACTIVE);

            Ticket ticket = Ticket.builder().title("T").key("S-1").type(TicketType.FEATURE).board(board).createdBy(user).build();
            ticket.setId(UUID.randomUUID());
            List<Ticket> incompleteTickets = List.of(ticket);

            when(boardAccessService.verifySprintAccess(organizationId, boardId, sprintId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(sprint);
            when(ticketRepository.findBySprint_IdAndStatusNot(sprintId, TicketStatus.DONE))
                    .thenReturn(incompleteTickets);
            // Target board permission check fails.
            doThrow(new ForbiddenException(
                    com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError.SEC_INSUFFICIENT_BOARD_PERMISSIONS,
                    java.util.Map.of()))
                    .when(boardAccessService).verifyBoardAccess(organizationId, targetBoardId, user.getId(), BoardMemberRole.ADMIN);

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    sprintServiceImpl.completeSprint(user, organizationId, boardId, sprintId,
                            new SprintCompleteRequest(TicketRelocationAction.MOVE_TO_BACKLOG, null, targetBoardId, null)));
        }
    }

    @Nested
    class RestoreSprint {

        @Test
        void sprintNotFound_throwsNotFoundException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID sprintId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(board);
            when(sprintRepository.findByIdWithDeleted(sprintId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(NotFoundException.class, () ->
                    sprintServiceImpl.restoreSprint(user, organizationId, boardId, sprintId));
        }

        @Test
        void sprintNotDeleted_throwsValidationException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID sprintId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Sprint sprint = Sprint.builder().name("Sprint 1").build();
            sprint.setId(sprintId);
            // sprint.isDeleted() is false by default

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(board);
            when(sprintRepository.findByIdWithDeleted(sprintId)).thenReturn(Optional.of(sprint));
            doThrow(new ValidationException(
                    com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError.VAL_SPRINT_NOT_DELETED,
                    java.util.Map.of()))
                    .when(sprintValidator).ensureSprintIsDeleted(sprint);

            // Act & Assert
            assertThrows(ValidationException.class, () ->
                    sprintServiceImpl.restoreSprint(user, organizationId, boardId, sprintId));
        }

        @Test
        void deletedSprint_restoresSuccessfully() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID sprintId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Sprint sprint = Sprint.builder().name("Sprint 1").build();
            sprint.setId(sprintId);
            sprint.setDeleted(true);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(board);
            when(sprintRepository.findByIdWithDeleted(sprintId)).thenReturn(Optional.of(sprint));
            doNothing().when(sprintRepository).restoreById(sprintId);

            // Act
            sprintServiceImpl.restoreSprint(user, organizationId, boardId, sprintId);

            // Assert
            verify(sprintRepository).restoreById(sprintId);
        }
    }

    @Nested
    class LinkSprintToBoardIfNeeded {

        @Test
        void alreadyLinked_noOp() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID sprintId = UUID.randomUUID();

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Sprint sprint = Sprint.builder().name("Sprint 1").build();
            sprint.setId(sprintId);

            // Sprint already has a BoardSprint link for this board.
            BoardSprint existingLink = BoardSprint.builder().board(board).sprint(sprint).build();
            existingLink.setId(UUID.randomUUID());
            sprint.getBoardSprints().add(existingLink);

            // Act
            sprintServiceImpl.linkSprintToBoardIfNeeded(sprint, board);

            // Assert — no repository calls should happen.
            verify(boardSprintRepository, never()).findDeletedByBoardIdAndSprintId(any(), any());
            verify(sprintRepository, never()).save(any());
        }

        @Test
        void softDeletedLinkExists_restoresLinkInsteadOfCreatingNew() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID sprintId = UUID.randomUUID();
            UUID boardSprintId = UUID.randomUUID();

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Sprint sprint = Sprint.builder().name("Sprint 1").build();
            sprint.setId(sprintId);
            // Sprint has no active link for this board.

            BoardSprint deletedBoardSprint = BoardSprint.builder().board(board).sprint(sprint).build();
            deletedBoardSprint.setId(boardSprintId);

            when(boardSprintRepository.findDeletedByBoardIdAndSprintId(boardId, sprintId))
                    .thenReturn(Optional.of(deletedBoardSprint));

            // Act
            sprintServiceImpl.linkSprintToBoardIfNeeded(sprint, board);

            // Assert — restores the soft-deleted link with a slug free on the board, does not create a new one.
            verify(boardSprintRepository).restoreByIdWithSlug(boardSprintId, "sprint-1");
            verify(sprintValidator, never()).ensureSprintCanBeLinked(any(), any());
            verify(sprintRepository, never()).saveAndFlush(any());
        }

        @Test
        void noExistingLink_validBoard_createsNewLink() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID sprintId = UUID.randomUUID();

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Sprint sprint = Sprint.builder().name("Sprint 1").build();
            sprint.setId(sprintId);
            // Sprint has no link for this board.

            when(boardSprintRepository.findDeletedByBoardIdAndSprintId(boardId, sprintId))
                    .thenReturn(Optional.empty());
            doNothing().when(sprintValidator).ensureSprintCanBeLinked(sprint, board);
            when(sprintRepository.saveAndFlush(sprint)).thenReturn(sprint);

            // Act
            sprintServiceImpl.linkSprintToBoardIfNeeded(sprint, board);

            // Assert — validates and creates the new link.
            verify(sprintValidator).ensureSprintCanBeLinked(sprint, board);
            verify(sprintRepository).saveAndFlush(sprint);
        }

        @Test
        void noExistingLink_unrelatedBoard_throwsValidationException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID sprintId = UUID.randomUUID();

            Board board = Board.builder().name("Unrelated Board").prefix("U").build();
            board.setId(boardId);

            Sprint sprint = Sprint.builder().name("Sprint 1").build();
            sprint.setId(sprintId);
            // Sprint has no link for this board.

            when(boardSprintRepository.findDeletedByBoardIdAndSprintId(boardId, sprintId))
                    .thenReturn(Optional.empty());
            doThrow(new ValidationException(
                    com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError.VAL_SPRINT_NOT_LINKABLE,
                    java.util.Map.of()))
                    .when(sprintValidator).ensureSprintCanBeLinked(sprint, board);

            // Act & Assert
            assertThrows(ValidationException.class, () ->
                    sprintServiceImpl.linkSprintToBoardIfNeeded(sprint, board));
            verify(sprintRepository, never()).save(any());
        }
    }

    @Nested
    class SprintOutsideThePathBoardOrOrganization {

        private final UUID boardId = UUID.randomUUID();
        private final UUID sprintId = UUID.randomUUID();
        private final User user = User.builder().build();

        private NotFoundException sprintNotFound() {
            return new NotFoundException(
                    com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError.NOT_SPRINT_FOUND,
                    java.util.Map.of());
        }

        private void givenSprintNotOnBoard() {
            user.setId(UUID.randomUUID());
            when(boardAccessService.verifySprintAccess(
                    organizationId, boardId, sprintId, user.getId(), BoardMemberRole.ADMIN))
                    .thenThrow(sprintNotFound());
        }

        @Test
        void deleteSprint_sprintNotOnPathBoard_throwsNotFoundAndWritesNothing() {
            // Arrange
            givenSprintNotOnBoard();

            // Act & Assert
            assertThrows(NotFoundException.class, () -> sprintServiceImpl.deleteSprint(
                    user, organizationId, boardId, sprintId, new SprintDeleteRequest(null, null)));
            verifyNoInteractions(sprintSeriesService, ticketHelper);
            verify(sprintRepository, never()).delete(any(Sprint.class));
        }

        @Test
        void bulkLinkSprint_sprintNotOnPathBoard_throwsNotFoundAndLinksNothing() {
            // Arrange
            givenSprintNotOnBoard();

            // Act & Assert
            assertThrows(NotFoundException.class, () -> sprintServiceImpl.bulkLinkSprint(
                    user, organizationId, boardId, sprintId, null));
            verifyNoInteractions(sprintSeriesService, boardSprintRepository);
        }

        @Test
        void unlinkSprint_sprintNotOnPathBoard_throwsNotFoundAndWritesNothing() {
            // Arrange
            user.setId(UUID.randomUUID());
            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);
            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN))
                    .thenReturn(board);
            when(boardAccessService.verifySprintAccess(
                    organizationId, boardId, sprintId, user.getId(), BoardMemberRole.ADMIN))
                    .thenThrow(sprintNotFound());

            // Act & Assert
            assertThrows(NotFoundException.class, () -> sprintServiceImpl.unlinkSprint(
                    user, organizationId, boardId, sprintId, new SprintUnlinkRequest(null, null)));
            verifyNoInteractions(sprintSeriesService, ticketHelper);
        }

        @Test
        void restoreSprint_sprintNeverLinkedToPathBoard_throwsNotFoundAndRestoresNothing() {
            // Arrange
            user.setId(UUID.randomUUID());
            Sprint sprint = Sprint.builder().name("Sprint 1").build();
            sprint.setId(sprintId);
            sprint.setDeleted(true);
            when(sprintRepository.findByIdWithDeleted(sprintId)).thenReturn(Optional.of(sprint));
            doThrow(sprintNotFound()).when(sprintValidator).ensureSprintWasOnBoard(sprint, boardId);

            // Act & Assert
            assertThrows(NotFoundException.class, () ->
                    sprintServiceImpl.restoreSprint(user, organizationId, boardId, sprintId));
            verify(sprintRepository, never()).restoreById(any());
        }

        @Test
        void linkSprint_sprintOfAnotherOrganizationOrUnlinked_throwsNotFoundAndLinksNothing() {
            // Arrange
            user.setId(UUID.randomUUID());
            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);
            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN))
                    .thenReturn(board);
            when(sprintValidator.ensureSprintExistsInOrganization(organizationId, sprintId))
                    .thenThrow(sprintNotFound());

            // Act & Assert
            assertThrows(NotFoundException.class, () ->
                    sprintServiceImpl.linkSprint(user, organizationId, boardId, sprintId));
            verifyNoInteractions(sprintSeriesService, boardSprintRepository);
        }

        @Test
        void linkSprintToBoardIfNeeded_sprintOfAnotherOrganization_throwsNotFoundAndAddsNoLink() {
            // Arrange
            UUID boardOrganizationId = UUID.randomUUID();
            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);
            board.setOrganizationId(boardOrganizationId);
            Sprint sprint = Sprint.builder().name("Sprint 1").build();
            sprint.setId(sprintId);
            doThrow(sprintNotFound()).when(sprintValidator).ensureSprintInOrganization(sprint, boardOrganizationId);

            // Act & Assert
            assertThrows(NotFoundException.class, () -> sprintServiceImpl.linkSprintToBoardIfNeeded(sprint, board));
            verifyNoInteractions(boardSprintRepository);
            verify(sprintRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    class UnlinkSprint {

        @Test
        void transferToSprint_targetSprintNotOnBoard_throwsValidationException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID sprintId = UUID.randomUUID();
            UUID targetSprintId = UUID.randomUUID();
            UUID otherBoardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Sprint sprint = Sprint.builder().name("Sprint 1").build();
            sprint.setId(sprintId);

            // Target sprint belongs to a different board, not the source board.
            Board otherBoard = Board.builder().name("Other Board").prefix("O").build();
            otherBoard.setId(otherBoardId);

            Sprint targetSprint = Sprint.builder().name("Sprint 2").build();
            targetSprint.setId(targetSprintId);
            BoardSprint otherBoardSprint = BoardSprint.builder().board(otherBoard).sprint(targetSprint).build();
            otherBoardSprint.setId(UUID.randomUUID());
            targetSprint.getBoardSprints().add(otherBoardSprint);

            Ticket ticket = Ticket.builder().title("T").key("B-1").type(TicketType.FEATURE).board(board).createdBy(user).build();
            ticket.setId(UUID.randomUUID());

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(board);
            when(boardAccessService.verifySprintAccess(organizationId, boardId, sprintId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(sprint);
            when(ticketRepository.findBySprint_Id(sprintId)).thenReturn(List.of(ticket));
            when(sprintValidator.ensureSprintExists(targetSprintId)).thenReturn(targetSprint);
            doThrow(new ValidationException(
                    com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError.VAL_SPRINT_TARGET_SPRINT_SAME_BOARD,
                    java.util.Map.of()))
                    .when(sprintValidator).ensureSprintBelongsToBoard(targetSprint, boardId);

            // Transfer to same board but target sprint is not linked to it.
            SprintUnlinkTransferRequest transfer = new SprintUnlinkTransferRequest(boardId, targetSprintId, null);
            SprintUnlinkRequest request = new SprintUnlinkRequest(transfer, null);

            // Act & Assert
            assertThrows(ValidationException.class, () ->
                    sprintServiceImpl.unlinkSprint(user, organizationId, boardId, sprintId, request));
        }

        @Test
        void crossBoardTransfer_userLacksPermissionOnTargetBoard_throwsForbiddenException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID sprintId = UUID.randomUUID();
            UUID targetBoardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Source Board").prefix("S").build();
            board.setId(boardId);

            Board targetBoard = Board.builder().name("Target Board").prefix("T").build();
            targetBoard.setId(targetBoardId);

            Sprint sprint = Sprint.builder().name("Sprint 1").build();
            sprint.setId(sprintId);

            Ticket ticket = Ticket.builder().title("T").key("S-1").type(TicketType.FEATURE).board(board).createdBy(user).build();
            ticket.setId(UUID.randomUUID());

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(board);
            when(boardAccessService.verifySprintAccess(organizationId, boardId, sprintId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(sprint);
            when(ticketRepository.findBySprint_Id(sprintId)).thenReturn(List.of(ticket));
            // Target board permission check fails.
            doThrow(new ForbiddenException(
                    com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError.SEC_INSUFFICIENT_BOARD_PERMISSIONS,
                    java.util.Map.of()))
                    .when(boardAccessService).verifyBoardAccess(organizationId, targetBoardId, user.getId(), BoardMemberRole.ADMIN);

            SprintUnlinkTransferRequest transfer = new SprintUnlinkTransferRequest(targetBoardId, null, null);
            SprintUnlinkRequest request = new SprintUnlinkRequest(transfer, null);

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    sprintServiceImpl.unlinkSprint(user, organizationId, boardId, sprintId, request));
        }

        @Test
        void transferToSprint_validTargetOnSameBoard_relocatesSuccessfully() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID sprintId = UUID.randomUUID();
            UUID targetSprintId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Sprint sprint = Sprint.builder().name("Sprint 1").build();
            sprint.setId(sprintId);
            BoardSprint sourceBoardSprint = BoardSprint.builder().board(board).sprint(sprint).build();
            sourceBoardSprint.setId(UUID.randomUUID());
            sprint.getBoardSprints().add(sourceBoardSprint);

            Sprint targetSprint = Sprint.builder().name("Sprint 2").build();
            targetSprint.setId(targetSprintId);
            BoardSprint targetBoardSprint = BoardSprint.builder().board(board).sprint(targetSprint).build();
            targetBoardSprint.setId(UUID.randomUUID());
            targetSprint.getBoardSprints().add(targetBoardSprint);

            Ticket ticket = Ticket.builder().title("T").key("B-1").type(TicketType.FEATURE).board(board).createdBy(user).build();
            ticket.setId(UUID.randomUUID());

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(board);
            when(boardAccessService.verifySprintAccess(organizationId, boardId, sprintId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(sprint);
            when(ticketRepository.findBySprint_Id(sprintId)).thenReturn(List.of(ticket));
            when(sprintValidator.ensureSprintExists(targetSprintId)).thenReturn(targetSprint);

            SprintUnlinkTransferRequest transfer = new SprintUnlinkTransferRequest(boardId, targetSprintId, null);
            SprintUnlinkRequest request = new SprintUnlinkRequest(transfer, null);

            // Act
            sprintServiceImpl.unlinkSprint(user, organizationId, boardId, sprintId, request);

            // Assert
            verify(ticketHelper).relocateTickets(
                    eq(List.of(ticket)),
                    eq(TicketRelocationAction.MOVE_TO_SPRINT),
                    eq(null),
                    eq(targetSprint),
                    eq(null),
                    eq(sprintId));
        }
    }

    @Nested
    class CreateSprint {

        @Test
        void savesPlannedSprint() {
            UUID boardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());
            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);
            SprintCreateRequest request = new SprintCreateRequest("Sprint", null, null, null, null, null, null);
            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(board);
            when(sprintMapper.toEntity(request)).thenReturn(Sprint.builder().name("Sprint").build());
            when(sprintRepository.saveAndFlush(any(Sprint.class))).thenAnswer(invocation -> invocation.getArgument(0));

            sprintServiceImpl.createSprint(user, organizationId, boardId, request);

            org.mockito.ArgumentCaptor<Sprint> saved = org.mockito.ArgumentCaptor.forClass(Sprint.class);
            verify(sprintRepository).saveAndFlush(saved.capture());
            org.junit.jupiter.api.Assertions.assertEquals(SprintStatus.PLANNED, saved.getValue().getStatus());
        }

        @Test
        void withSeries_namesTheSprintRoundOneAndStartsTheSeries() {
            UUID boardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());
            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);
            SprintSeriesRequest recurrence = new SprintSeriesRequest(true, 14,
                    SprintRecurrenceTicketAction.MOVE_TO_NEXT_SPRINT, SprintRecurrenceCreationMode.AT_END, null);
            SprintCreateRequest request = new SprintCreateRequest("Sprint", null, null, null, recurrence, null, null);
            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(board);
            when(sprintMapper.toEntity(request)).thenReturn(Sprint.builder().name("Sprint").build());
            when(sprintRepository.saveAndFlush(any(Sprint.class))).thenAnswer(invocation -> invocation.getArgument(0));

            sprintServiceImpl.createSprint(user, organizationId, boardId, request);

            org.mockito.ArgumentCaptor<Sprint> saved = org.mockito.ArgumentCaptor.forClass(Sprint.class);
            verify(sprintSeriesService).applyOnCreate(saved.capture(), eq("Sprint"), eq(recurrence));
            org.junit.jupiter.api.Assertions.assertEquals("Sprint 1", saved.getValue().getName());
        }

        @Test
        void recurrenceRejected_exceptionPropagates() {
            UUID boardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());
            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);
            SprintSeriesRequest recurrence = new SprintSeriesRequest(true, 14,
                    SprintRecurrenceTicketAction.MOVE_TO_NEXT_SPRINT, SprintRecurrenceCreationMode.AT_END, null);
            SprintCreateRequest request = new SprintCreateRequest("Sprint", null, null, null, recurrence, null, null);
            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN)).thenReturn(board);
            when(sprintMapper.toEntity(request)).thenReturn(Sprint.builder().name("Sprint").build());
            when(sprintRepository.saveAndFlush(any(Sprint.class))).thenAnswer(invocation -> invocation.getArgument(0));
            doThrow(new ValidationException(
                    com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError.VAL_SPRINT_RECURRENCE_DATES_INVALID,
                    java.util.Map.of()))
                    .when(sprintSeriesService).applyOnCreate(any(Sprint.class), eq("Sprint"), eq(recurrence));

            assertThrows(ValidationException.class, () -> sprintServiceImpl.createSprint(user, organizationId, boardId, request));
            verify(sprintMapper, never()).toDetailResponse(any());
        }
    }

    @Nested
    class UpdateSprint {

        private Sprint arrangeSprint(UUID boardId, UUID sprintId, User user) {
            Sprint sprint = Sprint.builder().name("Sprint").build();
            sprint.setId(sprintId);
            when(boardAccessService.verifySprintAccess(organizationId, boardId, sprintId, user.getId(), BoardMemberRole.ADMIN))
                    .thenReturn(sprint);
            when(sprintRepository.saveAndFlush(sprint)).thenReturn(sprint);
            return sprint;
        }

        @Test
        void dateInRequest_notifiesSeriesService() {
            UUID boardId = UUID.randomUUID();
            UUID sprintId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());
            Sprint sprint = arrangeSprint(boardId, sprintId, user);

            sprintServiceImpl.updateSprint(user, organizationId, boardId, sprintId,
                    new SprintUpdateRequest(null, null, null, java.time.Instant.parse("2026-01-08T00:00:00Z"), null));

            verify(sprintSeriesService).onSprintDatesChanged(sprint);
        }

        @Test
        void nameOnlyInRequest_doesNotNotifySeriesService() {
            UUID boardId = UUID.randomUUID();
            UUID sprintId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());
            arrangeSprint(boardId, sprintId, user);

            sprintServiceImpl.updateSprint(user, organizationId, boardId, sprintId,
                    new SprintUpdateRequest("Renamed", null, null, null, null));

            verify(sprintSeriesService, never()).onSprintDatesChanged(any());
        }

        @Test
        void dateInRequest_locksSeriesRowBeforeSprintSave() {
            UUID boardId = UUID.randomUUID();
            UUID sprintId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());
            Sprint sprint = arrangeSprint(boardId, sprintId, user);

            sprintServiceImpl.updateSprint(user, organizationId, boardId, sprintId,
                    new SprintUpdateRequest(null, null, null, java.time.Instant.parse("2026-01-08T00:00:00Z"), null));

            InOrder inOrder = inOrder(sprintMapper, sprintRepository, sprintSeriesService);
            inOrder.verify(sprintSeriesService).lockSeries(sprint);
            inOrder.verify(sprintMapper).updateEntity(eq(sprint), any());
            inOrder.verify(sprintRepository).saveAndFlush(sprint);
            inOrder.verify(sprintSeriesService).onSprintDatesChanged(sprint);
        }
    }

    @Nested
    class CreateSprintWithChildBoards {

        private final User user = userWithId();
        private final Board parent = board("Parent");
        private final Board childA = board("ChildA");
        private final Board childB = board("ChildB");

        private User userWithId() {
            User created = User.builder().build();
            created.setId(UUID.randomUUID());
            return created;
        }

        private Board board(String name) {
            Board created = Board.builder().name(name).prefix(name.substring(0, 1)).build();
            created.setId(UUID.randomUUID());
            return created;
        }

        private void arrangeAccess(Board... boards) {
            for (Board board : boards) {
                when(boardAccessService.verifyBoardAccess(organizationId, board.getId(), user.getId(), BoardMemberRole.ADMIN))
                        .thenReturn(board);
            }
        }

        private void arrangeSave(SprintCreateRequest request) {
            when(sprintMapper.toEntity(request)).thenReturn(Sprint.builder().name("Sprint").build());
            when(sprintRepository.saveAndFlush(any(Sprint.class))).thenAnswer(invocation -> invocation.getArgument(0));
        }

        private Sprint savedSprint() {
            org.mockito.ArgumentCaptor<Sprint> saved = org.mockito.ArgumentCaptor.forClass(Sprint.class);
            verify(sprintRepository).saveAndFlush(saved.capture());
            return saved.getValue();
        }

        @Test
        void noChildBoardIds_linksOnlyThePathBoardAndSkipsChildChecks() {
            SprintCreateRequest request = new SprintCreateRequest("Sprint", null, null, null, null, null, null);
            arrangeAccess(parent);
            arrangeSave(request);

            sprintServiceImpl.createSprint(user, organizationId, parent.getId(), request);

            org.junit.jupiter.api.Assertions.assertEquals(List.of(parent),
                    savedSprint().getBoardSprints().stream().map(BoardSprint::getBoard).toList());
            verify(sprintValidator, never()).ensureBoardIsDescendant(any(), any());
        }

        @Test
        void emptyChildBoardIds_linksOnlyThePathBoard() {
            SprintCreateRequest request = new SprintCreateRequest("Sprint", null, null, null, null, List.of(), null);
            arrangeAccess(parent);
            arrangeSave(request);

            sprintServiceImpl.createSprint(user, organizationId, parent.getId(), request);

            org.junit.jupiter.api.Assertions.assertEquals(1, savedSprint().getBoardSprints().size());
            verify(sprintValidator, never()).ensureBoardIsDescendant(any(), any());
        }

        @Test
        void twoChildren_linksPathBoardThenChildrenInRequestOrder() {
            SprintCreateRequest request = new SprintCreateRequest("Sprint", null, null, null, null,
                    List.of(childB.getId(), childA.getId()), null);
            arrangeAccess(parent, childA, childB);
            arrangeSave(request);

            sprintServiceImpl.createSprint(user, organizationId, parent.getId(), request);

            Sprint saved = savedSprint();
            org.junit.jupiter.api.Assertions.assertEquals(List.of(parent, childB, childA),
                    saved.getBoardSprints().stream().map(BoardSprint::getBoard).toList());
            verify(sprintValidator).ensureBoardIsDescendant(parent, childA);
            verify(sprintValidator).ensureBoardIsDescendant(parent, childB);
            verify(sprintSeriesService).applyOnCreate(saved, null, null);
        }

        @Test
        void duplicateAndPathBoardIds_areIgnored() {
            SprintCreateRequest request = new SprintCreateRequest("Sprint", null, null, null, null,
                    List.of(childA.getId(), parent.getId(), childA.getId()), null);
            arrangeAccess(parent, childA);
            arrangeSave(request);

            sprintServiceImpl.createSprint(user, organizationId, parent.getId(), request);

            org.junit.jupiter.api.Assertions.assertEquals(List.of(parent, childA),
                    savedSprint().getBoardSprints().stream().map(BoardSprint::getBoard).toList());
            verify(boardAccessService, org.mockito.Mockito.times(1))
                    .verifyBoardAccess(organizationId, childA.getId(), user.getId(), BoardMemberRole.ADMIN);
            verify(boardAccessService, org.mockito.Mockito.times(1))
                    .verifyBoardAccess(organizationId, parent.getId(), user.getId(), BoardMemberRole.ADMIN);
        }

        @Test
        void grandchild_isAcceptedWhenValidatorAccepts() {
            Board grandchild = board("Grandchild");
            SprintCreateRequest request = new SprintCreateRequest("Sprint", null, null, null, null,
                    List.of(grandchild.getId()), null);
            arrangeAccess(parent, grandchild);
            arrangeSave(request);

            sprintServiceImpl.createSprint(user, organizationId, parent.getId(), request);

            verify(sprintValidator).ensureBoardIsDescendant(parent, grandchild);
            org.junit.jupiter.api.Assertions.assertEquals(2, savedSprint().getBoardSprints().size());
        }

        @Test
        void childWithoutAdminRole_throwsForbiddenAndSavesNothing() {
            SprintCreateRequest request = new SprintCreateRequest("Sprint", null, null, null, null,
                    List.of(childA.getId()), null);
            arrangeAccess(parent);
            when(boardAccessService.verifyBoardAccess(organizationId, childA.getId(), user.getId(), BoardMemberRole.ADMIN))
                    .thenThrow(new ForbiddenException(
                            com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError.SEC_INSUFFICIENT_BOARD_PERMISSIONS));

            assertThrows(ForbiddenException.class, () -> sprintServiceImpl.createSprint(user, organizationId, parent.getId(), request));

            verify(sprintRepository, never()).saveAndFlush(any(Sprint.class));
            verify(sprintSeriesService, never()).applyOnCreate(any(), any(), any());
        }

        @Test
        void childNotAMember_throwsNotFoundAndSavesNothing() {
            SprintCreateRequest request = new SprintCreateRequest("Sprint", null, null, null, null,
                    List.of(childA.getId()), null);
            arrangeAccess(parent);
            when(boardAccessService.verifyBoardAccess(organizationId, childA.getId(), user.getId(), BoardMemberRole.ADMIN))
                    .thenThrow(new NotFoundException(
                            com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError.NOT_BOARD_MEMBER_FOUND));

            assertThrows(NotFoundException.class, () -> sprintServiceImpl.createSprint(user, organizationId, parent.getId(), request));

            verify(sprintRepository, never()).saveAndFlush(any(Sprint.class));
        }

        @Test
        void childNotADescendant_throwsValidationAndSavesNothing() {
            SprintCreateRequest request = new SprintCreateRequest("Sprint", null, null, null, null,
                    List.of(childA.getId()), null);
            arrangeAccess(parent, childA);
            doThrow(new ValidationException(
                    com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError.VAL_SPRINT_NOT_LINKABLE,
                    java.util.Map.of()))
                    .when(sprintValidator).ensureBoardIsDescendant(parent, childA);

            assertThrows(ValidationException.class, () -> sprintServiceImpl.createSprint(user, organizationId, parent.getId(), request));

            verify(sprintRepository, never()).saveAndFlush(any(Sprint.class));
            verify(sprintSeriesService, never()).applyOnCreate(any(), any(), any());
        }

        @Test
        void secondChildRefused_firstChildChecksRanButNothingIsSaved() {
            SprintCreateRequest request = new SprintCreateRequest("Sprint", null, null, null, null,
                    List.of(childA.getId(), childB.getId()), null);
            arrangeAccess(parent, childA);
            when(boardAccessService.verifyBoardAccess(organizationId, childB.getId(), user.getId(), BoardMemberRole.ADMIN))
                    .thenThrow(new ForbiddenException(
                            com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError.SEC_INSUFFICIENT_BOARD_PERMISSIONS));

            assertThrows(ForbiddenException.class, () -> sprintServiceImpl.createSprint(user, organizationId, parent.getId(), request));

            verify(sprintValidator).ensureBoardIsDescendant(parent, childA);
            verify(sprintRepository, never()).saveAndFlush(any(Sprint.class));
        }
    }

    /**
     * Shared arrangement for the takeover tests: a user, a parent board with a PLANNED sprint
     * linked to it, and helpers that stub the ACTIVE sprints blocking it.
     */
    abstract class TakeoverFixture {

        final User user = userWithId();
        final Board parent = board("Parent");
        final Board child = board("Child");

        private User userWithId() {
            User created = User.builder().build();
            created.setId(UUID.randomUUID());
            return created;
        }

        Board board(String name) {
            Board created = Board.builder().name(name).prefix(name.substring(0, 1)).build();
            created.setId(UUID.randomUUID());
            return created;
        }

        Sprint sprintOn(SprintStatus status, Board... boards) {
            Sprint created = Sprint.builder().name("Sprint").status(status).build();
            created.setId(UUID.randomUUID());
            for (Board board : boards) {
                created.getBoardSprints().add(BoardSprint.builder().board(board).sprint(created).build());
            }
            return created;
        }

        void stubBlocking(List<UUID> linkedBoardIds, Sprint... blocking) {
            when(sprintRepository.findIdsByBoardIdsAndStatus(linkedBoardIds, SprintStatus.ACTIVE))
                    .thenReturn(java.util.Arrays.stream(blocking).map(Sprint::getId).toList());
            when(sprintRepository.findAllById(java.util.Arrays.stream(blocking).map(Sprint::getId).toList()))
                    .thenReturn(List.of(blocking));
        }

        void stubAdmin(Board... boards) {
            for (Board board : boards) {
                when(boardAccessService.verifyBoardAccess(organizationId, board.getId(), user.getId(), BoardMemberRole.ADMIN))
                        .thenReturn(board);
            }
        }
    }

    @Nested
    class CreateSprintTakeover extends TakeoverFixture {

        private SprintCreateRequest request(Boolean takeOver) {
            return new SprintCreateRequest("Sprint", null, null, null, null, null, takeOver);
        }

        private void stubCreate(SprintCreateRequest request) {
            Sprint entity = Sprint.builder().name("Sprint").build();
            entity.setId(UUID.randomUUID());
            when(sprintMapper.toEntity(request)).thenReturn(entity);
            when(sprintRepository.saveAndFlush(any(Sprint.class))).thenAnswer(invocation -> invocation.getArgument(0));
            org.mockito.Mockito.lenient().when(sprintRepository.save(any(Sprint.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            stubAdmin(parent);
        }

        @Test
        void takeOverNull_staysPlannedAndRunsNoTakeover() {
            SprintCreateRequest request = request(null);
            stubCreate(request);

            sprintServiceImpl.createSprint(user, organizationId, parent.getId(), request);

            org.mockito.ArgumentCaptor<Sprint> saved = org.mockito.ArgumentCaptor.forClass(Sprint.class);
            verify(sprintMapper).toDetailResponse(saved.capture());
            org.junit.jupiter.api.Assertions.assertEquals(SprintStatus.PLANNED, saved.getValue().getStatus());
            verify(sprintRepository, never()).findIdsByBoardIdsAndStatus(any(), any());
            verify(sprintSeriesRepository, never()).findByIdForUpdate(any());
            verify(sprintRepository, never()).save(any(Sprint.class));
        }

        @Test
        void takeOverFalse_staysPlannedAndRunsNoTakeover() {
            SprintCreateRequest request = request(false);
            stubCreate(request);

            sprintServiceImpl.createSprint(user, organizationId, parent.getId(), request);

            org.mockito.ArgumentCaptor<Sprint> saved = org.mockito.ArgumentCaptor.forClass(Sprint.class);
            verify(sprintMapper).toDetailResponse(saved.capture());
            org.junit.jupiter.api.Assertions.assertEquals(SprintStatus.PLANNED, saved.getValue().getStatus());
            verify(sprintRepository, never()).findIdsByBoardIdsAndStatus(any(), any());
        }

        @Test
        void takeOverTrueNothingBlocks_startsTheSprint() {
            SprintCreateRequest request = request(true);
            stubCreate(request);
            when(sprintRepository.findIdsByBoardIdsAndStatus(List.of(parent.getId()), SprintStatus.ACTIVE))
                    .thenReturn(List.of());

            sprintServiceImpl.createSprint(user, organizationId, parent.getId(), request);

            org.mockito.ArgumentCaptor<Sprint> mapped = org.mockito.ArgumentCaptor.forClass(Sprint.class);
            verify(sprintMapper).toDetailResponse(mapped.capture());
            org.junit.jupiter.api.Assertions.assertEquals(SprintStatus.ACTIVE, mapped.getValue().getStatus());
            verify(ticketHelper, never()).relocateTickets(any(), any(), any(), any(), any(), any());
        }

        @Test
        void takeOverTrueWithBlockingSprint_closesItAndStartsTheNewSprint() {
            SprintCreateRequest request = request(true);
            stubCreate(request);
            Sprint blocking = sprintOn(SprintStatus.ACTIVE, parent);
            stubBlocking(List.of(parent.getId()), blocking);
            Ticket open = Ticket.builder().board(parent).sprint(blocking).status(TicketStatus.IN_PROGRESS).build();
            when(ticketRepository.findBySprint_IdAndStatusNot(blocking.getId(), TicketStatus.DONE))
                    .thenReturn(List.of(open));

            sprintServiceImpl.createSprint(user, organizationId, parent.getId(), request);

            org.mockito.ArgumentCaptor<Sprint> mapped = org.mockito.ArgumentCaptor.forClass(Sprint.class);
            verify(sprintMapper).toDetailResponse(mapped.capture());
            org.junit.jupiter.api.Assertions.assertEquals(SprintStatus.ACTIVE, mapped.getValue().getStatus());
            org.junit.jupiter.api.Assertions.assertEquals(SprintStatus.COMPLETED, blocking.getStatus());
            verify(ticketHelper).relocateTickets(List.of(open), TicketRelocationAction.MOVE_TO_SPRINT,
                    null, mapped.getValue(), null, blocking.getId());
        }

        @Test
        void takeOverTrueBlockingSprintOnBoardWithoutAdminRole_throwsForbiddenAndWritesNothing() {
            SprintCreateRequest request = request(true);
            stubCreate(request);
            Sprint blocking = sprintOn(SprintStatus.ACTIVE, parent, child);
            stubBlocking(List.of(parent.getId()), blocking);
            when(boardAccessService.verifyBoardAccess(organizationId, child.getId(), user.getId(), BoardMemberRole.ADMIN))
                    .thenThrow(new ForbiddenException(
                            com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError.SEC_INSUFFICIENT_BOARD_PERMISSIONS));

            assertThrows(ForbiddenException.class, () -> sprintServiceImpl.createSprint(user, organizationId, parent.getId(), request));

            org.junit.jupiter.api.Assertions.assertEquals(SprintStatus.ACTIVE, blocking.getStatus());
            verify(ticketHelper, never()).relocateTickets(any(), any(), any(), any(), any(), any());
            verify(ticketRepository, never()).findBySprint_IdAndStatusNot(any(), any());
            verify(sprintSeriesRepository, never()).findByIdForUpdate(any());
            verify(sprintRepository, never()).save(any(Sprint.class));
            verify(sprintMapper, never()).toDetailResponse(any());
        }
    }

    @Nested
    class StartSprintTakeover extends TakeoverFixture {

        private Sprint planned;

        @BeforeEach
        void arrange() {
            planned = sprintOn(SprintStatus.PLANNED, parent, child);
            when(boardAccessService.verifySprintAccess(organizationId, parent.getId(), planned.getId(), user.getId(),
                    BoardMemberRole.ADMIN)).thenReturn(planned);
            org.mockito.Mockito.lenient().when(sprintRepository.save(any(Sprint.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
        }

        @Test
        void conflictOnChildBoard_throwsConflictCarryingTheBlockingBoards() {
            when(sprintRepository.findIdsByBoardIdsAndStatus(List.of(parent.getId()), SprintStatus.ACTIVE))
                    .thenReturn(List.of());
            when(sprintRepository.findIdsByBoardIdsAndStatus(List.of(child.getId()), SprintStatus.ACTIVE))
                    .thenReturn(List.of(UUID.randomUUID()));
            doThrow(new com.korur.oba.dojo.exception.internal.business.ConflictException(
                    com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError.STA_ACTIVE_SPRINT_EXISTS,
                    java.util.Map.of()))
                    .when(sprintValidator).ensureNoBlockingBoards(planned.getId(), List.of(child));

            assertThrows(com.korur.oba.dojo.exception.internal.business.ConflictException.class,
                    () -> sprintServiceImpl.startSprint(user, organizationId, parent.getId(), planned.getId(),
                            new SprintStartRequest(false)));

            verify(sprintValidator).ensureNoBlockingBoards(planned.getId(), List.of(child));
            org.junit.jupiter.api.Assertions.assertEquals(SprintStatus.PLANNED, planned.getStatus());
            verify(sprintRepository, never()).save(any(Sprint.class));
        }

        @Test
        void nullRequest_behavesAsTakeOverFalse() {
            when(sprintRepository.findIdsByBoardIdsAndStatus(any(), eq(SprintStatus.ACTIVE))).thenReturn(List.of());

            sprintServiceImpl.startSprint(user, organizationId, parent.getId(), planned.getId(), null);

            verify(sprintValidator).ensureNoBlockingBoards(planned.getId(), List.of());
            verify(sprintRepository, never()).findAllById(any());
            verify(sprintSeriesRepository, never()).findByIdForUpdate(any());
            org.junit.jupiter.api.Assertions.assertEquals(SprintStatus.ACTIVE, planned.getStatus());
            InOrder seriesOrder = inOrder(sprintSeriesService);
            seriesOrder.verify(sprintSeriesService).lockSeries(planned);
            seriesOrder.verify(sprintSeriesService).rescheduleSeries(planned);
        }

        @Test
        void takeOverNull_behavesAsTakeOverFalse() {
            when(sprintRepository.findIdsByBoardIdsAndStatus(any(), eq(SprintStatus.ACTIVE))).thenReturn(List.of());

            sprintServiceImpl.startSprint(user, organizationId, parent.getId(), planned.getId(), new SprintStartRequest(null));

            verify(sprintValidator).ensureNoBlockingBoards(planned.getId(), List.of());
            org.junit.jupiter.api.Assertions.assertEquals(SprintStatus.ACTIVE, planned.getStatus());
        }

        @Test
        void takeOverTrue_closesBlockingSprintsAfterCheckingEveryAffectedBoard() {
            Board outside = board("Outside");
            Sprint blocking = sprintOn(SprintStatus.ACTIVE, child, outside);
            com.korur.oba.apps.kazan.sprint.entity.SprintSeries series =
                    com.korur.oba.apps.kazan.sprint.entity.SprintSeries.builder().name("Atasprint").nextRound(2).build();
            series.setId(UUID.randomUUID());
            blocking.setSeries(series);
            blocking.setRound(1);
            stubBlocking(List.of(parent.getId(), child.getId()), blocking);
            stubAdmin(child, outside);
            Ticket onOutside = Ticket.builder().board(outside).sprint(blocking).status(TicketStatus.TODO).build();
            Backlog outsideBacklog = Backlog.builder().board(outside).build();
            when(ticketRepository.findBySprint_IdAndStatusNot(blocking.getId(), TicketStatus.DONE))
                    .thenReturn(List.of(onOutside));
            when(backlogValidator.ensureBacklogExistsByBoardId(outside.getId())).thenReturn(outsideBacklog);

            sprintServiceImpl.startSprint(user, organizationId, parent.getId(), planned.getId(), new SprintStartRequest(true));

            InOrder order = inOrder(boardAccessService, sprintSeriesRepository, ticketHelper);
            order.verify(boardAccessService).verifyBoardAccess(organizationId, child.getId(), user.getId(), BoardMemberRole.ADMIN);
            order.verify(boardAccessService).verifyBoardAccess(organizationId, outside.getId(), user.getId(), BoardMemberRole.ADMIN);
            order.verify(sprintSeriesRepository).findByIdForUpdate(series.getId());
            order.verify(ticketHelper).relocateTickets(List.of(onOutside), TicketRelocationAction.MOVE_TO_BACKLOG,
                    null, null, outsideBacklog, blocking.getId());
            org.junit.jupiter.api.Assertions.assertEquals(SprintStatus.COMPLETED, blocking.getStatus());
            org.junit.jupiter.api.Assertions.assertEquals(SprintStatus.ACTIVE, planned.getStatus());
            verify(sprintValidator, never()).ensureNoBlockingBoards(any(), any());
            verify(sprintSeriesService).rescheduleSeries(planned);
            verify(sprintSeriesService).rescheduleSeries(blocking);
            // The taking sprint now holds the boards, so the closed series sprint gets no next round.
            verify(sprintSeriesService, never()).advanceOnComplete(any(), any());
        }

        @Test
        void takeOverTrueWithoutAdminOnAffectedBoard_throwsForbiddenAndWritesNothing() {
            Sprint blocking = sprintOn(SprintStatus.ACTIVE, child);
            stubBlocking(List.of(parent.getId(), child.getId()), blocking);
            when(boardAccessService.verifyBoardAccess(organizationId, child.getId(), user.getId(), BoardMemberRole.ADMIN))
                    .thenThrow(new ForbiddenException(
                            com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError.SEC_INSUFFICIENT_BOARD_PERMISSIONS));

            assertThrows(ForbiddenException.class, () -> sprintServiceImpl.startSprint(
                    user, organizationId, parent.getId(), planned.getId(), new SprintStartRequest(true)));

            org.junit.jupiter.api.Assertions.assertEquals(SprintStatus.ACTIVE, blocking.getStatus());
            org.junit.jupiter.api.Assertions.assertEquals(SprintStatus.PLANNED, planned.getStatus());
            verify(sprintRepository, never()).save(any(Sprint.class));
            verify(ticketHelper, never()).relocateTickets(any(), any(), any(), any(), any(), any());
        }

        @Test
        void sprintNotPlanned_throwsValidationAndChecksNothingElse() {
            planned.setStatus(SprintStatus.ACTIVE);
            doThrow(new ValidationException(
                    com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError.VAL_SPRINT_NOT_PLANNED,
                    java.util.Map.of()))
                    .when(sprintValidator).ensureSprintIsPlanned(planned);

            assertThrows(ValidationException.class, () -> sprintServiceImpl.startSprint(
                    user, organizationId, parent.getId(), planned.getId(), new SprintStartRequest(true)));

            verify(sprintRepository, never()).findIdsByBoardIdsAndStatus(any(), any());
            verify(sprintRepository, never()).save(any(Sprint.class));
        }
    }

    /**
     * Completes a sprint of a series by hand on 2026-11-10 at 15:00 UTC, with
     * the real series service and lifecycle helper over the repository mocks, so
     * each test sees the series, the next round and the ticket moves the
     * completion leaves. The series "Atasprint" runs 14 days on boards Alpha and
     * Beta, and its ACTIVE "Atasprint 2" is completed from Alpha.
     */
    @Nested
    class CompleteSeriesSprint {

        @Mock
        private SprintSeriesMapper sprintSeriesMapper;

        private final SprintRecurrenceCalculator calculator = new SprintRecurrenceCalculator();
        private final Board alpha = com.korur.oba.apps.kazan.sprint.mock_data.SprintRecurrenceMocks.board("Alpha");
        private final Board beta = com.korur.oba.apps.kazan.sprint.mock_data.SprintRecurrenceMocks.board("Beta");
        private final List<Sprint> seriesSprints = new ArrayList<>();
        private final User user = User.builder().build();

        private SprintSeries series;
        private Sprint closing;

        @BeforeEach
        void setUp() {
            SprintLifecycleHelper lifecycleHelper = new SprintLifecycleHelper(
                    sprintRepository, sprintSeriesRepository, boardSprintRepository, ticketRepository,
                    sprintValidator, backlogValidator, ticketHelper, loggingService);
            SprintSeriesServiceImpl seriesService = new SprintSeriesServiceImpl(sprintSeriesRepository,
                    sprintSeriesBoardRepository, sprintRepository, boardAccessService, sprintValidator, lifecycleHelper, calculator,
                    sprintSeriesMapper, sprintMapper, loggingService, entityManager, CLOCK);
            sprintServiceImpl = new SprintServiceImpl(
                    sprintRepository, ticketRepository, boardSprintRepository, boardAccessService,
                    sprintValidator, backlogValidator, ticketValidator, sprintMapper, ticketHelper,
                    lifecycleHelper, seriesService, loggingService, entityManager, CLOCK);

            user.setId(UUID.randomUUID());
            series = series(SprintRecurrenceTicketAction.MOVE_TO_NEXT_SPRINT,
                    SprintRecurrenceCreationMode.AT_END, null, 14, alpha, beta);
            series.setNextRound(3);
            closing = withSeriesSprint(linkedTo(inSeries(
                    sprint(SprintStatus.ACTIVE, utc("2026-11-03"), utc("2026-11-17")), series, 2), alpha, beta));

            when(boardAccessService.verifySprintAccess(organizationId, alpha.getId(), closing.getId(), user.getId(),
                    BoardMemberRole.ADMIN)).thenReturn(closing);
            lenient().when(sprintSeriesRepository.findByIdForUpdate(series.getId())).thenReturn(Optional.of(series));
            lenient().when(sprintSeriesRepository.save(any(SprintSeries.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            lenient().when(sprintRepository.save(any(Sprint.class))).thenAnswer(invocation -> invocation.getArgument(0));
            lenient().when(sprintRepository.saveAndFlush(any(Sprint.class))).thenAnswer(invocation -> {
                Sprint saved = invocation.getArgument(0);
                if (saved.getId() == null) {
                    saved.setId(UUID.randomUUID());
                }
                if (saved.getSeries() != null && !seriesSprints.contains(saved)) {
                    seriesSprints.add(saved);
                }
                return saved;
            });
            lenient().when(sprintRepository.findBySeries_IdOrderByRoundDesc(series.getId())).thenAnswer(invocation ->
                    seriesSprints.stream().sorted(Comparator.comparing(Sprint::getRound).reversed()).toList());
        }

        private Sprint sprint(SprintStatus status, Instant startDate, Instant endDate) {
            return com.korur.oba.apps.kazan.sprint.mock_data.SprintRecurrenceMocks.sprint(status, startDate, endDate);
        }

        /**
         * Adds the sprint to the series' live sprints the repository returns.
         */
        private Sprint withSeriesSprint(Sprint seriesSprint) {
            seriesSprints.add(seriesSprint);
            return seriesSprint;
        }

        private SprintCompleteRequest moveToNextSprint() {
            return new SprintCompleteRequest(TicketRelocationAction.MOVE_TO_SPRINT, null, null, true);
        }

        private Sprint nextRound() {
            return seriesSprints.stream()
                    .filter(candidate -> candidate.getRound() == 3)
                    .findFirst()
                    .orElseThrow();
        }

        private List<UUID> boardIdsOf(Sprint linked) {
            return linked.getBoardSprints().stream().map(link -> link.getBoard().getId()).toList();
        }

        @Test
        void noSprintCreatedAhead_createsAndStartsTheNextRound() {
            // Act
            sprintServiceImpl.completeSprint(user, organizationId, alpha.getId(), closing.getId(),
                    new SprintCompleteRequest(TicketRelocationAction.MOVE_TO_BACKLOG, null, null, null));

            // Assert
            Sprint next = nextRound();
            assertEquals("Atasprint 3", next.getName());
            assertSame(series, next.getSeries());
            assertEquals(SprintStatus.ACTIVE, next.getStatus());
            assertEquals(utc("2026-11-10"), next.getStartDate());
            assertEquals(utc("2026-11-24"), next.getEndDate());
            assertEquals(List.of(alpha.getId(), beta.getId()), boardIdsOf(next));
            assertEquals(4, series.getNextRound());
            assertEquals(SprintStatus.COMPLETED, closing.getStatus());
            assertEquals(SprintRecurrenceRunStatus.OK, series.getLastRunStatus());
            assertEquals(CLOCK.instant(), series.getLastRunAt());
            assertNull(series.getNextCreationAt());
        }

        @Test
        void sprintCreatedAhead_isStartedOnTheCompletionDayWithTheSeriesLength() {
            // Arrange
            Sprint ahead = withSeriesSprint(linkedTo(inSeries(
                    sprint(SprintStatus.PLANNED, utc("2026-11-17"), utc("2026-12-01")), series, 3), alpha));
            series.setNextRound(4);

            // Act
            sprintServiceImpl.completeSprint(user, organizationId, alpha.getId(), closing.getId(),
                    new SprintCompleteRequest(TicketRelocationAction.MOVE_TO_BACKLOG, null, null, null));

            // Assert
            assertEquals(2, seriesSprints.size());
            assertEquals(SprintStatus.ACTIVE, ahead.getStatus());
            assertEquals(utc("2026-11-10"), ahead.getStartDate());
            assertEquals(utc("2026-11-24"), ahead.getEndDate());
            assertEquals(List.of(alpha.getId(), beta.getId()), boardIdsOf(ahead));
            assertEquals(4, series.getNextRound());
        }

        @Test
        void sprintCreatedAhead_completedOnTheClosingEndDate_keepsItsOwnEnd() {
            // Arrange
            closing.setStartDate(utc("2026-10-27"));
            closing.setEndDate(utc("2026-11-10"));
            Sprint ahead = withSeriesSprint(linkedTo(inSeries(
                    sprint(SprintStatus.PLANNED, utc("2026-11-10"), utc("2026-11-26")), series, 3), alpha, beta));
            series.setNextRound(4);

            // Act
            sprintServiceImpl.completeSprint(user, organizationId, alpha.getId(), closing.getId(),
                    new SprintCompleteRequest(TicketRelocationAction.MOVE_TO_BACKLOG, null, null, null));

            // Assert
            assertEquals(SprintStatus.ACTIVE, ahead.getStatus());
            assertEquals(utc("2026-11-10"), ahead.getStartDate());
            assertEquals(utc("2026-11-26"), ahead.getEndDate());
        }

        @Test
        void moveToNextSprint_movesUnfinishedTicketsIntoTheNextRoundOnTheirBoards() {
            // Arrange
            Ticket onAlpha = Ticket.builder().board(alpha).sprint(closing).status(TicketStatus.TODO).build();
            Ticket onBeta = Ticket.builder().board(beta).sprint(closing).status(TicketStatus.IN_PROGRESS).build();
            when(ticketRepository.findBySprint_IdAndStatusNot(closing.getId(), TicketStatus.DONE))
                    .thenReturn(List.of(onAlpha, onBeta));

            // Act
            sprintServiceImpl.completeSprint(user, organizationId, alpha.getId(), closing.getId(), moveToNextSprint());

            // Assert
            Sprint next = nextRound();
            verify(ticketHelper).relocateTickets(List.of(onAlpha, onBeta), TicketRelocationAction.MOVE_TO_SPRINT,
                    null, next, null, closing.getId());
            verify(sprintValidator, never()).ensureSprintExists(any());
            verify(ticketValidator, never()).ensureTargetSprintProvided(any(), any());
            assertEquals(SprintStatus.ACTIVE, next.getStatus());
        }

        @Test
        void moveToBacklog_keepsTodaysTicketHandlingAndStillAdvances() {
            // Arrange
            Ticket open = Ticket.builder().board(alpha).sprint(closing).status(TicketStatus.TODO).build();
            Backlog backlog = Backlog.builder().board(alpha).build();
            when(ticketRepository.findBySprint_IdAndStatusNot(closing.getId(), TicketStatus.DONE))
                    .thenReturn(List.of(open));
            when(backlogValidator.ensureBacklogExistsByBoardId(alpha.getId())).thenReturn(backlog);

            // Act
            sprintServiceImpl.completeSprint(user, organizationId, alpha.getId(), closing.getId(),
                    new SprintCompleteRequest(TicketRelocationAction.MOVE_TO_BACKLOG, null, null, null));

            // Assert
            verify(ticketHelper).relocateTickets(List.of(open), TicketRelocationAction.MOVE_TO_BACKLOG,
                    null, null, backlog, closing.getId());
            assertEquals(SprintStatus.ACTIVE, nextRound().getStatus());
        }

        @Test
        void delete_keepsTodaysTicketHandlingAndStillAdvances() {
            // Arrange
            Ticket open = Ticket.builder().board(alpha).sprint(closing).status(TicketStatus.TODO).build();
            when(ticketRepository.findBySprint_IdAndStatusNot(closing.getId(), TicketStatus.DONE))
                    .thenReturn(List.of(open));

            // Act
            sprintServiceImpl.completeSprint(user, organizationId, alpha.getId(), closing.getId(),
                    new SprintCompleteRequest(TicketRelocationAction.DELETE, null, null, null));

            // Assert
            verify(ticketHelper).relocateTickets(List.of(open), TicketRelocationAction.DELETE,
                    null, null, null, closing.getId());
            assertEquals(SprintStatus.ACTIVE, nextRound().getStatus());
        }

        @Test
        void seriesBoardWithAnotherActiveSprint_leavesTheNextRoundPlannedAndRecordsBlocked() {
            // Arrange
            lenient().when(sprintRepository.findIdsByBoardIdsAndStatus(List.of(beta.getId()), SprintStatus.ACTIVE))
                    .thenReturn(List.of(UUID.randomUUID()));

            // Act
            sprintServiceImpl.completeSprint(user, organizationId, alpha.getId(), closing.getId(), moveToNextSprint());

            // Assert
            Sprint next = nextRound();
            assertEquals(SprintStatus.COMPLETED, closing.getStatus());
            assertEquals(SprintStatus.PLANNED, next.getStatus());
            assertEquals(utc("2026-11-10"), next.getStartDate());
            assertEquals(utc("2026-11-24"), next.getEndDate());
            assertEquals(SprintRecurrenceRunStatus.BLOCKED, series.getLastRunStatus());
            assertEquals("Another sprint is active on: Beta", series.getLastRunMessage());
        }

        @Test
        void daysBefore_nextCreationFollowsTheNewRound() {
            // Arrange
            series.setCreationMode(SprintRecurrenceCreationMode.DAYS_BEFORE);
            series.setCreateDaysBefore(2);

            // Act
            sprintServiceImpl.completeSprint(user, organizationId, alpha.getId(), closing.getId(),
                    new SprintCompleteRequest(TicketRelocationAction.MOVE_TO_BACKLOG, null, null, null));

            // Assert
            assertEquals(utc("2026-11-22"), series.getNextCreationAt());
        }

        @Test
        void disabledSeries_createsNothing() {
            // Arrange
            series.setEnabled(false);

            // Act
            sprintServiceImpl.completeSprint(user, organizationId, alpha.getId(), closing.getId(),
                    new SprintCompleteRequest(TicketRelocationAction.MOVE_TO_BACKLOG, null, null, null));

            // Assert
            assertEquals(SprintStatus.COMPLETED, closing.getStatus());
            assertEquals(1, seriesSprints.size());
            assertEquals(3, series.getNextRound());
            assertNull(series.getLastRunStatus());
        }

        @Test
        void sprintOutsideSeries_createsNothing() {
            // Arrange
            closing.setSeries(null);
            closing.setRound(null);

            // Act
            sprintServiceImpl.completeSprint(user, organizationId, alpha.getId(), closing.getId(),
                    new SprintCompleteRequest(TicketRelocationAction.MOVE_TO_BACKLOG, null, null, null));

            // Assert
            assertEquals(SprintStatus.COMPLETED, closing.getStatus());
            verify(sprintRepository, never()).saveAndFlush(any());
            verify(entityManager, never()).refresh(any());
        }

        @Test
        void noLiveSeriesBoard_completesAndSwitchesTheSeriesOff() {
            // Arrange
            series.getBoards().clear();

            // Act
            sprintServiceImpl.completeSprint(user, organizationId, alpha.getId(), closing.getId(),
                    new SprintCompleteRequest(TicketRelocationAction.MOVE_TO_BACKLOG, null, null, null));

            // Assert
            assertEquals(SprintStatus.COMPLETED, closing.getStatus());
            assertEquals(1, seriesSprints.size());
            assertEquals(false, series.isEnabled());
            assertEquals(SprintRecurrenceRunStatus.FAILED, series.getLastRunStatus());
            assertEquals(SprintRecurrenceConstant.RUN_MESSAGE_NO_BOARDS, series.getLastRunMessage());
        }

        @Test
        void sprintCompletedByTheJobDuringTheLockWait_throwsNotActiveAndCreatesNothing() {
            // Arrange
            doAnswer(invocation -> {
                Sprint checked = invocation.getArgument(0);
                if (checked.getStatus() != SprintStatus.ACTIVE) {
                    throw new ValidationException(ExceptionDomainKazanError.VAL_SPRINT_NOT_ACTIVE,
                            java.util.Map.of());
                }
                return null;
            }).when(sprintValidator).ensureSprintIsActive(closing);
            // The job rolled the sprint over while the save waited for the series lock.
            lenient().doAnswer(invocation -> {
                if (invocation.getArgument(0) == closing) {
                    closing.setStatus(SprintStatus.COMPLETED);
                }
                return null;
            }).when(entityManager).refresh(any());

            // Act
            ValidationException thrown = assertThrows(ValidationException.class, () ->
                    sprintServiceImpl.completeSprint(user, organizationId, alpha.getId(), closing.getId(), moveToNextSprint()));

            // Assert
            assertEquals("KAZ_VAL_004", thrown.getErrorCode());
            assertEquals(1, seriesSprints.size());
            assertEquals(3, series.getNextRound());
            verify(sprintRepository, never()).save(any());
            verify(sprintRepository, never()).saveAndFlush(any());
        }

        @Test
        void moveToNextSprintRefused_changesNothing() {
            // Arrange
            doThrow(new ValidationException(ExceptionDomainKazanError.VAL_SPRINT_NEXT_SPRINT_UNAVAILABLE,
                    java.util.Map.of()))
                    .when(sprintValidator).ensureMoveToNextSprintAllowed(closing, moveToNextSprint(), true);

            // Act & Assert
            assertThrows(ValidationException.class, () ->
                    sprintServiceImpl.completeSprint(user, organizationId, alpha.getId(), closing.getId(), moveToNextSprint()));
            assertEquals(SprintStatus.ACTIVE, closing.getStatus());
            assertEquals(1, seriesSprints.size());
            verify(sprintRepository, never()).save(any());
            verify(ticketHelper, never()).relocateTickets(any(), any(), any(), any(), any(), any());
        }
    }

    /**
     * Create, link and bulk link on the real series service, so each test sees
     * whether a board already in another series is refused, moved or left alone.
     * The sprint "Atasprint 2" of the enabled series "Atasprint" runs on Alpha;
     * Beta and Gamma are boards an admin links to it.
     */
    @Nested
    class BoardInOtherSeries {

        @Mock
        private SprintSeriesMapper sprintSeriesMapper;

        private final Board alpha = com.korur.oba.apps.kazan.sprint.mock_data.SprintRecurrenceMocks.board("Alpha");
        private final Board beta = com.korur.oba.apps.kazan.sprint.mock_data.SprintRecurrenceMocks.board("Beta");
        private final Board gamma = com.korur.oba.apps.kazan.sprint.mock_data.SprintRecurrenceMocks.board("Gamma");
        private final User user = User.builder().build();

        private SprintSeries atasprint;
        private Sprint planned;

        @BeforeEach
        void setUp() {
            SprintLifecycleHelper lifecycleHelper = new SprintLifecycleHelper(
                    sprintRepository, sprintSeriesRepository, boardSprintRepository, ticketRepository,
                    sprintValidator, backlogValidator, ticketHelper, loggingService);
            SprintSeriesServiceImpl seriesService = new SprintSeriesServiceImpl(sprintSeriesRepository,
                    sprintSeriesBoardRepository, sprintRepository, boardAccessService, sprintValidator,
                    lifecycleHelper, new SprintRecurrenceCalculator(), sprintSeriesMapper, sprintMapper,
                    loggingService, entityManager, CLOCK);
            sprintServiceImpl = new SprintServiceImpl(
                    sprintRepository, ticketRepository, boardSprintRepository, boardAccessService,
                    sprintValidator, backlogValidator, ticketValidator, sprintMapper, ticketHelper,
                    lifecycleHelper, seriesService, loggingService, entityManager, CLOCK);

            // The board rule runs for real, so the conflict carries the values the client reads.
            SprintValidator realValidator = new SprintValidator(sprintRepository, loggingService);
            lenient().doAnswer(invocation -> {
                realValidator.ensureBoardNotInOtherEnabledSeries(
                        invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2));
                return null;
            }).when(sprintValidator).ensureBoardNotInOtherEnabledSeries(any(), any(), any());

            user.setId(UUID.randomUUID());
            atasprint = com.korur.oba.apps.kazan.sprint.mock_data.SprintRecurrenceMocks.atEndSeries(alpha);
            planned = linkedTo(inSeries(com.korur.oba.apps.kazan.sprint.mock_data.SprintRecurrenceMocks.sprint(
                    SprintStatus.PLANNED, utc("2026-11-16"), utc("2026-11-23")), atasprint, 2), alpha);

            lenient().when(sprintValidator.ensureSprintExistsInOrganization(organizationId, planned.getId()))
                    .thenReturn(planned);
            lenient().when(sprintSeriesRepository.findByIdForUpdate(atasprint.getId()))
                    .thenReturn(Optional.of(atasprint));
            lenient().when(sprintRepository.saveAndFlush(any(Sprint.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            lenient().when(sprintSeriesRepository.save(any(SprintSeries.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            lenient().when(sprintSeriesRepository.saveAndFlush(any(SprintSeries.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
        }

        private void admin(Board... boards) {
            for (Board board : boards) {
                when(boardAccessService.verifyBoardAccess(organizationId, board.getId(), user.getId(), BoardMemberRole.ADMIN))
                        .thenReturn(board);
            }
        }

        private void sprintAdmin(Board board) {
            when(boardAccessService.verifySprintAccess(
                    organizationId, board.getId(), planned.getId(), user.getId(), BoardMemberRole.ADMIN))
                    .thenReturn(planned);
        }

        private SprintSeries otherSeries(String name, boolean enabled, Board... boards) {
            SprintSeries other = com.korur.oba.apps.kazan.sprint.mock_data.SprintRecurrenceMocks.atEndSeries(boards);
            other.setName(name);
            other.setEnabled(enabled);
            return other;
        }

        private void rowOf(Board board, SprintSeries owner) {
            SprintSeriesBoard row = owner.getBoards().stream()
                    .filter(seriesBoard -> seriesBoard.getBoard().getId().equals(board.getId()))
                    .findFirst()
                    .orElseThrow();
            when(sprintSeriesBoardRepository.findByBoard_Id(board.getId())).thenReturn(Optional.of(row));
        }

        private List<UUID> boardIds(SprintSeries owner) {
            return owner.getBoards().stream().map(seriesBoard -> seriesBoard.getBoard().getId()).toList();
        }

        private SprintCreateRequest repeatingCreate(Boolean enabled, List<UUID> childBoardIds) {
            SprintSeriesRequest recurrence = new SprintSeriesRequest(enabled, 7,
                    SprintRecurrenceTicketAction.MOVE_TO_NEXT_SPRINT, SprintRecurrenceCreationMode.AT_END, null);
            SprintCreateRequest request = new SprintCreateRequest("Gammasprint", null, utc("2026-11-09"),
                    utc("2026-11-16"), recurrence, childBoardIds, null);
            when(sprintMapper.toEntity(request)).thenReturn(Sprint.builder()
                    .name("Gammasprint").startDate(utc("2026-11-09")).endDate(utc("2026-11-16")).build());
            lenient().when(sprintSeriesMapper.toEntity(recurrence)).thenReturn(SprintSeries.builder()
                    .enabled(true).lengthDays(7).creationMode(SprintRecurrenceCreationMode.AT_END).build());
            return request;
        }

        private void assertTakenBy(ConflictException exception, Board board, SprintSeries owner) {
            assertEquals(ExceptionDomainKazanError.STA_BOARD_IN_OTHER_SPRINT_SERIES.getCode(),
                    exception.getErrorCode());
            assertEquals(board.getId(), exception.getErrorValues().get(SprintConstant.FIELD_BOARD_ID));
            assertEquals(board.getName(), exception.getErrorValues().get(SprintConstant.FIELD_BOARD_NAME));
            assertEquals(owner.getId(), exception.getErrorValues().get(SprintRecurrenceConstant.FIELD_SERIES_ID));
            assertEquals(owner.getName(), exception.getErrorValues().get(SprintRecurrenceConstant.FIELD_SERIES_NAME));
        }

        private DataIntegrityViolationException violation(String constraintName) {
            return new DataIntegrityViolationException("could not execute statement",
                    new ConstraintViolationException("duplicate key", new SQLException("duplicate key"),
                            constraintName));
        }

        @Test
        void createWithRepeat_pathBoardInEnabledSeries_throwsKaiSta018AndStartsNoSeries() {
            // Arrange
            SprintCreateRequest request = repeatingCreate(true, null);
            admin(alpha);
            rowOf(alpha, atasprint);

            // Act
            ConflictException exception = assertThrows(ConflictException.class,
                    () -> sprintServiceImpl.createSprint(user, organizationId, alpha.getId(), request));

            // Assert
            assertTakenBy(exception, alpha, atasprint);
            verify(sprintSeriesRepository, never()).save(any());
            verify(sprintSeriesRepository, never()).saveAndFlush(any());
            verify(sprintMapper, never()).toDetailResponse(any());
        }

        @Test
        void createWithRepeat_onlyChildBoardInEnabledSeries_throwsKaiSta018NamingTheChild() {
            // Arrange
            SprintCreateRequest request = repeatingCreate(true, List.of(alpha.getId()));
            admin(beta, alpha);
            when(sprintSeriesBoardRepository.findByBoard_Id(beta.getId())).thenReturn(Optional.empty());
            rowOf(alpha, atasprint);

            // Act
            ConflictException exception = assertThrows(ConflictException.class,
                    () -> sprintServiceImpl.createSprint(user, organizationId, beta.getId(), request));

            // Assert
            assertTakenBy(exception, alpha, atasprint);
            verify(sprintMapper, never()).toDetailResponse(any());
        }

        @Test
        void createWithRepeatOff_createsTheSprintAndReadsNoSeriesBoard() {
            // Arrange
            SprintCreateRequest request = repeatingCreate(false, null);
            admin(alpha);

            // Act
            sprintServiceImpl.createSprint(user, organizationId, alpha.getId(), request);

            // Assert
            verify(sprintRepository).saveAndFlush(any(Sprint.class));
            verify(sprintMapper).toDetailResponse(any(Sprint.class));
            verifyNoInteractions(sprintSeriesBoardRepository, sprintSeriesRepository);
        }

        @Test
        void link_boardInEnabledSeries_writesTheLinkFirstThenThrowsKaiSta018() {
            // Arrange
            SprintSeries betasprint = otherSeries("Betasprint", true, beta);
            admin(beta);
            rowOf(beta, betasprint);

            // Act
            ConflictException exception = assertThrows(ConflictException.class,
                    () -> sprintServiceImpl.linkSprint(user, organizationId, beta.getId(), planned.getId()));

            // Assert
            assertTakenBy(exception, beta, betasprint);
            assertEquals(List.of(alpha.getId()), boardIds(atasprint));
            assertEquals(List.of(beta.getId()), boardIds(betasprint));
            InOrder order = inOrder(sprintRepository, sprintSeriesBoardRepository);
            order.verify(sprintRepository).saveAndFlush(planned);
            order.verify(sprintSeriesBoardRepository).findByBoard_Id(beta.getId());
            verify(sprintMapper, never()).toDetailResponse(any());
        }

        @Test
        void link_boardInDisabledSeries_movesItsRowAndLeavesThatSeriesOffWithItsOtherBoards() {
            // Arrange
            SprintSeries betasprint = otherSeries("Betasprint", false, beta, gamma);
            admin(beta);
            rowOf(beta, betasprint);
            when(sprintSeriesRepository.findByIdForUpdate(betasprint.getId())).thenReturn(Optional.of(betasprint));

            // Act
            sprintServiceImpl.linkSprint(user, organizationId, beta.getId(), planned.getId());

            // Assert
            assertEquals(List.of(alpha.getId(), beta.getId()), boardIds(atasprint));
            assertEquals(List.of(gamma.getId()), boardIds(betasprint));
            assertEquals(false, betasprint.isEnabled());
            // The sprint's new link, the old series row and the new series row are written in that order.
            InOrder order = inOrder(sprintRepository, sprintSeriesRepository);
            order.verify(sprintRepository).saveAndFlush(planned);
            order.verify(sprintSeriesRepository).saveAndFlush(betasprint);
            order.verify(sprintSeriesRepository).saveAndFlush(atasprint);
            verify(sprintMapper).toDetailResponse(planned);
        }

        @Test
        void link_boardAlreadyInTheSameSeries_addsNoRow() {
            // Arrange
            atasprint.getBoards().add(SprintSeriesBoard.builder().series(atasprint).board(beta).position(1).build());
            admin(beta);
            rowOf(beta, atasprint);

            // Act
            sprintServiceImpl.linkSprint(user, organizationId, beta.getId(), planned.getId());

            // Assert
            assertEquals(List.of(alpha.getId(), beta.getId()), boardIds(atasprint));
            verify(sprintSeriesRepository, never()).saveAndFlush(any());
            verify(sprintMapper).toDetailResponse(planned);
        }

        @Test
        void link_completedSeriesSprint_checksNoSeries() {
            // Arrange
            planned.setStatus(SprintStatus.COMPLETED);
            admin(beta);

            // Act
            sprintServiceImpl.linkSprint(user, organizationId, beta.getId(), planned.getId());

            // Assert
            verifyNoInteractions(sprintSeriesBoardRepository);
            assertEquals(List.of(alpha.getId()), boardIds(atasprint));
            verify(sprintMapper).toDetailResponse(planned);
        }

        @Test
        void link_sprintOutsideAnySeries_checksNoSeries() {
            // Arrange
            planned.setSeries(null);
            planned.setRound(null);
            admin(beta);

            // Act
            sprintServiceImpl.linkSprint(user, organizationId, beta.getId(), planned.getId());

            // Assert
            verifyNoInteractions(sprintSeriesBoardRepository, sprintSeriesRepository);
            verify(sprintMapper).toDetailResponse(planned);
        }

        @Test
        void link_slugTakenMeanwhile_throwsKaiSta017AndClaimsNoBoard() {
            // Arrange
            admin(beta);
            when(sprintRepository.saveAndFlush(planned)).thenThrow(violation(SprintConstant.SLUG_UNIQUE_INDEX));

            // Act
            ConflictException exception = assertThrows(ConflictException.class,
                    () -> sprintServiceImpl.linkSprint(user, organizationId, beta.getId(), planned.getId()));

            // Assert
            assertEquals(ExceptionDomainKazanError.STA_SLUG_TAKEN.getCode(), exception.getErrorCode());
            verifyNoInteractions(sprintSeriesBoardRepository);
            assertEquals(List.of(alpha.getId()), boardIds(atasprint));
        }

        @Test
        void bulkLink_secondBoardInEnabledSeries_throwsKaiSta018NamingIt() {
            // Arrange
            SprintSeries betasprint = otherSeries("Betasprint", true, gamma);
            sprintAdmin(alpha);
            admin(beta, gamma);
            when(sprintSeriesBoardRepository.findByBoard_Id(beta.getId())).thenReturn(Optional.empty());
            rowOf(gamma, betasprint);

            // Act
            ConflictException exception = assertThrows(ConflictException.class,
                    () -> sprintServiceImpl.bulkLinkSprint(user, organizationId, alpha.getId(), planned.getId(),
                            new SprintBulkLinkRequest(List.of(beta.getId(), gamma.getId()))));

            // Assert
            assertTakenBy(exception, gamma, betasprint);
            verify(sprintMapper, never()).toDetailResponse(any());
        }

        @Test
        void bulkLink_writesEachLinkBeforeTheSeriesClaimsItsBoard() {
            // Arrange
            sprintAdmin(alpha);
            admin(beta, gamma);
            when(sprintSeriesBoardRepository.findByBoard_Id(any(UUID.class))).thenReturn(Optional.empty());

            // Act
            sprintServiceImpl.bulkLinkSprint(user, organizationId, alpha.getId(), planned.getId(),
                    new SprintBulkLinkRequest(List.of(beta.getId(), gamma.getId())));

            // Assert
            assertEquals(List.of(alpha.getId(), beta.getId(), gamma.getId()), boardIds(atasprint));
            InOrder order = inOrder(sprintRepository, sprintSeriesBoardRepository, sprintSeriesRepository);
            order.verify(sprintRepository).saveAndFlush(planned);
            order.verify(sprintSeriesBoardRepository).findByBoard_Id(beta.getId());
            order.verify(sprintSeriesRepository).saveAndFlush(atasprint);
            order.verify(sprintRepository).saveAndFlush(planned);
            order.verify(sprintSeriesBoardRepository).findByBoard_Id(gamma.getId());
            order.verify(sprintSeriesRepository).saveAndFlush(atasprint);
        }
    }
}
