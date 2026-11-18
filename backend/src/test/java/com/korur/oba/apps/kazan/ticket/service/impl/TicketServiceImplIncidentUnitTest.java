package com.korur.oba.apps.kazan.ticket.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.backlog.entity.Backlog;
import com.korur.oba.apps.kazan.backlog.validator.BacklogValidator;
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.repository.BoardRepository;
import com.korur.oba.apps.kazan.board.service.BoardAccessService;
import com.korur.oba.apps.kazan.board.service.BoardQueryService;
import com.korur.oba.apps.kazan.board.validator.BoardValidator;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.systemactor.entity.SystemActor;
import com.korur.oba.apps.kazan.systemactor.enums.SystemActorType;
import com.korur.oba.apps.kazan.ticket.dto.command.IncidentReport;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.enums.IncidentOutcome;
import com.korur.oba.apps.kazan.ticket.event.IncidentOpenedEvent;
import com.korur.oba.apps.kazan.ticket.enums.MissionRank;
import com.korur.oba.apps.kazan.ticket.enums.TicketPriority;
import com.korur.oba.apps.kazan.ticket.enums.TicketStatus;
import com.korur.oba.apps.kazan.ticket.enums.TicketType;
import com.korur.oba.apps.kazan.ticket.helper.TicketStatusNormalizer;
import com.korur.oba.apps.kazan.ticket.helper.TicketTypeNormalizer;
import com.korur.oba.apps.kazan.ticket.repository.TicketRepository;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class TicketServiceImplIncidentUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private BoardAccessService boardAccessService;

    @Mock
    private BoardQueryService boardQueryService;

    @Mock
    private BoardValidator boardValidator;

    @Mock
    private BacklogValidator backlogValidator;

    @Mock
    private TicketStatusNormalizer ticketStatusNormalizer;

    @Mock
    private TicketTypeNormalizer ticketTypeNormalizer;

    @Mock
    private LoggingService loggingService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private TicketServiceImpl ticketServiceImpl;

    private UUID boardId;
    private Board board;
    private Backlog backlog;
    private SystemActor actor;
    private IncidentReport report;

    @BeforeEach
    void setUp() {
        boardId = UUID.randomUUID();
        board = Board.builder().prefix("OPS").build();
        board.setId(boardId);
        board.setOrganizationId(organizationId);
        lenient().when(boardValidator.ensureBoardInOrganization(organizationId, boardId)).thenReturn(board);
        backlog = Backlog.builder().build();
        actor = SystemActor.builder().name("Alertmanager").type(SystemActorType.ALERTMANAGER).build();
        actor.setId(UUID.randomUUID());
        report = new IncidentReport("fp1", "KeycloakDown: down", "details");
    }

    private void stubBoardAndBacklog() {
        when(backlogValidator.ensureBacklogExistsByBoardId(boardId)).thenReturn(backlog);
        when(boardQueryService.lockForUpdate(boardId)).thenReturn(board);
    }

    private void stubOpenTicketLookup(Optional<Ticket> result) {
        when(ticketRepository.findFirstByBoard_IdAndIncidentKeyAndStatusNotOrderByCreatedAtDesc(
                boardId, "fp1", TicketStatus.DONE)).thenReturn(result);
    }

    @Test
    void reportIncident_openTicketExists_returnsIgnoredAndSavesNothing() {
        stubBoardAndBacklog();
        Ticket open = Ticket.builder().key("OPS-3").type(TicketType.INCIDENT).build();
        stubOpenTicketLookup(Optional.of(open));

        IncidentOutcome outcome = ticketServiceImpl.reportIncident(actor, organizationId, boardId, report, true);

        assertThat(outcome).isEqualTo(IncidentOutcome.IGNORED);
        verify(ticketRepository, never()).save(any());
        verify(boardRepository, never()).incrementAndGetTicketCounter(any());
        verify(boardAccessService, never()).verifyBoardAccess(eq(organizationId), any(), any(), any());
    }

    @Test
    void reportIncident_creationNotAllowed_returnsIgnoredAndSavesNothing() {
        stubBoardAndBacklog();
        stubOpenTicketLookup(Optional.empty());

        IncidentOutcome outcome = ticketServiceImpl.reportIncident(actor, organizationId, boardId, report, false);

        assertThat(outcome).isEqualTo(IncidentOutcome.IGNORED);
        verify(ticketRepository, never()).save(any());
        verify(boardRepository, never()).incrementAndGetTicketCounter(any());
    }

    @Test
    void reportIncident_noOpenTicketAndAllowed_createsP1IncidentInBacklog() {
        stubBoardAndBacklog();
        stubOpenTicketLookup(Optional.empty());
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(boardRepository.incrementAndGetTicketCounter(boardId)).thenReturn(7L);

        IncidentOutcome outcome = ticketServiceImpl.reportIncident(actor, organizationId, boardId, report, true);

        assertThat(outcome).isEqualTo(IncidentOutcome.CREATED);
        ArgumentCaptor<Ticket> saved = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(saved.capture());
        Ticket ticket = saved.getValue();
        assertThat(ticket.getType()).isEqualTo(TicketType.INCIDENT);
        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.P1);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.TODO);
        assertThat(ticket.getKey()).isEqualTo("OPS-7");
        assertThat(ticket.getTitle()).isEqualTo("KeycloakDown: down");
        assertThat(ticket.getDescription()).isEqualTo("details");
        assertThat(ticket.getBoard()).isSameAs(board);
        assertThat(ticket.getBacklog()).isSameAs(backlog);
        assertThat(ticket.getCreatedBySystemActor()).isSameAs(actor);
        assertThat(ticket.getIncidentKey()).isEqualTo("fp1");
        assertThat(ticket.getCreatedBy()).isNull();
    }

    @Test
    void reportIncident_newIncident_storesMissionRankM() {
        stubBoardAndBacklog();
        stubOpenTicketLookup(Optional.empty());
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(boardRepository.incrementAndGetTicketCounter(boardId)).thenReturn(7L);

        ticketServiceImpl.reportIncident(actor, organizationId, boardId, report, true);

        ArgumentCaptor<Ticket> saved = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(saved.capture());
        assertThat(saved.getValue().getMissionRank()).isEqualTo(MissionRank.M);
    }

    @Test
    void reportIncident_newIncident_publishesTheP1IncidentWithoutAnActor() {
        stubBoardAndBacklog();
        stubOpenTicketLookup(Optional.empty());
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(boardRepository.incrementAndGetTicketCounter(boardId)).thenReturn(7L);
        board.setName("Operations");

        ticketServiceImpl.reportIncident(actor, organizationId, boardId, report, true);

        ArgumentCaptor<IncidentOpenedEvent> published = ArgumentCaptor.forClass(IncidentOpenedEvent.class);
        verify(applicationEventPublisher).publishEvent(published.capture());
        IncidentOpenedEvent event = published.getValue();
        assertThat(event.organizationId()).isEqualTo(organizationId);
        assertThat(event.boardId()).isEqualTo(boardId);
        assertThat(event.boardName()).isEqualTo("Operations");
        assertThat(event.ticketKey()).isEqualTo("OPS-7");
        assertThat(event.title()).isEqualTo("KeycloakDown: down");
        assertThat(event.priority()).isEqualTo(TicketPriority.P1);
        assertThat(event.actorId()).isNull();
    }

    @Test
    void reportIncident_openTicketExists_publishesNothing() {
        stubBoardAndBacklog();
        stubOpenTicketLookup(Optional.of(Ticket.builder().key("OPS-3").type(TicketType.INCIDENT).build()));

        ticketServiceImpl.reportIncident(actor, organizationId, boardId, report, true);

        verify(applicationEventPublisher, never()).publishEvent(any(IncidentOpenedEvent.class));
    }

    @Test
    void reportIncident_validatesThenLocksThenLooksUp() {
        stubBoardAndBacklog();
        stubOpenTicketLookup(Optional.empty());
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(boardRepository.incrementAndGetTicketCounter(boardId)).thenReturn(1L);

        ticketServiceImpl.reportIncident(actor, organizationId, boardId, report, true);

        InOrder order = inOrder(boardValidator, backlogValidator, boardQueryService, ticketRepository);
        order.verify(boardValidator).ensureBoardInOrganization(organizationId, boardId);
        order.verify(backlogValidator).ensureBacklogExistsByBoardId(boardId);
        order.verify(boardQueryService).lockForUpdate(boardId);
        order.verify(ticketRepository).findFirstByBoard_IdAndIncidentKeyAndStatusNotOrderByCreatedAtDesc(
                boardId, "fp1", TicketStatus.DONE);
    }

    @Test
    void reportIncident_boardMissingOrOfAnotherOrganization_throwsNotFoundAndSavesNothing() {
        // The board check takes the reporter's organization, and refuses a missing board and a
        // board of another organization alike.
        when(boardValidator.ensureBoardInOrganization(organizationId, boardId))
                .thenThrow(new NotFoundException(ExceptionDomainKazanError.NOT_BOARD_FOUND));

        assertThatThrownBy(() -> ticketServiceImpl.reportIncident(actor, organizationId, boardId, report, true))
                .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ExceptionDomainKazanError.NOT_BOARD_FOUND.getCode()));

        verifyNoInteractions(boardQueryService, ticketRepository, backlogValidator);
    }

    @Test
    void reportIncident_backlogNotFound_throwsAndSavesNothing() {
        when(backlogValidator.ensureBacklogExistsByBoardId(boardId))
                .thenThrow(new NotFoundException(ExceptionDomainKazanError.NOT_BACKLOG_FOUND));

        assertThatThrownBy(() -> ticketServiceImpl.reportIncident(actor, organizationId, boardId, report, true))
                .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ExceptionDomainKazanError.NOT_BACKLOG_FOUND.getCode()));

        verifyNoInteractions(boardQueryService, ticketRepository);
    }

    @Test
    void reportIncident_lockFindsNoBoard_propagatesAndSavesNothing() {
        when(backlogValidator.ensureBacklogExistsByBoardId(boardId)).thenReturn(backlog);
        when(boardQueryService.lockForUpdate(boardId))
                .thenThrow(new NotFoundException(ExceptionDomainKazanError.NOT_BOARD_FOUND));

        assertThatThrownBy(() -> ticketServiceImpl.reportIncident(actor, organizationId, boardId, report, true))
                .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(ticketRepository);
    }

    @Test
    void reportIncident_always_neverChecksBoardAccess() {
        stubBoardAndBacklog();
        stubOpenTicketLookup(Optional.empty());
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(boardRepository.incrementAndGetTicketCounter(boardId)).thenReturn(2L);

        ticketServiceImpl.reportIncident(actor, organizationId, boardId, report, true);

        verifyNoInteractions(boardAccessService);
    }
}
