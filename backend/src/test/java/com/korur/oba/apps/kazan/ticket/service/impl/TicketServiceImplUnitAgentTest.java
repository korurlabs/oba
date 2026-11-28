package com.korur.oba.apps.kazan.ticket.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

// 2. Third-party
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import jakarta.persistence.EntityManager;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.backlog.entity.Backlog;
import com.korur.oba.apps.kazan.backlog.validator.BacklogValidator;
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.enums.BoardMemberRole;
import com.korur.oba.apps.kazan.board.repository.BoardRepository;
import com.korur.oba.apps.kazan.board.service.BoardAccessService;
import com.korur.oba.apps.kazan.board.service.BoardMemberQueryService;
import com.korur.oba.apps.kazan.board.service.BoardQueryService;
import com.korur.oba.apps.kazan.board.validator.BoardValidator;
import com.korur.oba.apps.kazan.comment.repository.CommentRepository;
import com.korur.oba.apps.kazan.label.validator.LabelValidator;
import com.korur.oba.apps.kazan.milestone.validator.MilestoneValidator;
import com.korur.oba.apps.kazan.project.validator.ProjectValidator;
import com.korur.oba.apps.kazan.sprint.service.SprintService;
import com.korur.oba.apps.kazan.sprint.validator.SprintValidator;
import com.korur.oba.apps.kazan.ticket.dto.request.TicketCreateRequest;
import com.korur.oba.apps.kazan.ticket.dto.request.TicketUpdateRequest;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.entity.TicketAssignee;
import com.korur.oba.apps.kazan.ticket.enums.TicketPriority;
import com.korur.oba.apps.kazan.ticket.enums.TicketStatus;
import com.korur.oba.apps.kazan.ticket.enums.TicketType;
import com.korur.oba.apps.kazan.ticket.event.TicketAssigneesAddedEvent;
import com.korur.oba.apps.kazan.ticket.helper.TicketHelper;
import com.korur.oba.apps.kazan.ticket.helper.TicketStatusNormalizer;
import com.korur.oba.apps.kazan.ticket.helper.TicketTypeNormalizer;
import com.korur.oba.apps.kazan.ticket.mapper.TicketMapper;
import com.korur.oba.apps.kazan.ticket.repository.TicketAssigneeRepository;
import com.korur.oba.apps.kazan.ticket.repository.TicketLabelRepository;
import com.korur.oba.apps.kazan.ticket.repository.TicketRepository;
import com.korur.oba.apps.kazan.ticket.repository.TicketReviewerRepository;
import com.korur.oba.apps.kazan.ticket.validator.TicketValidator;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.agent.validator.AgentValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserKind;
import com.korur.oba.shrine.identity.service.UserQueryService;

/**
 * Runs ticket create, update and delete through the real {@link AgentValidator}, so the agent
 * refusals and the assignment event are checked as the service applies them.
 */
@ExtendWith(MockitoExtension.class)
class TicketServiceImplUnitAgentTest {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private BoardAccessService boardAccessService;
    @Mock
    private BacklogValidator backlogValidator;
    @Mock
    private TicketValidator ticketValidator;
    @Mock
    private TicketMapper ticketMapper;
    @Mock
    private UserQueryService userQueryService;
    @Mock
    private UserQueryService agentUserQueryService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private TicketServiceImpl ticketService;

    private final UUID organizationId = UUID.randomUUID();
    private UUID boardId;
    private UUID backlogId;
    private Board board;
    private User person;
    private User agent;

    @BeforeEach
    void setUp() {
        LoggingService loggingService = new LoggingService();
        ticketService = new TicketServiceImpl(
                ticketRepository,
                mock(TicketAssigneeRepository.class),
                mock(TicketReviewerRepository.class),
                mock(TicketLabelRepository.class),
                mock(CommentRepository.class),
                mock(BoardRepository.class),
                boardAccessService,
                mock(BoardMemberQueryService.class),
                mock(BoardQueryService.class),
                mock(BoardValidator.class),
                mock(AppAccessValidator.class),
                new AgentValidator(agentUserQueryService, loggingService),
                mock(SprintService.class),
                mock(SprintValidator.class),
                backlogValidator,
                mock(ProjectValidator.class),
                ticketValidator,
                mock(LabelValidator.class),
                mock(MilestoneValidator.class),
                ticketMapper,
                mock(TicketHelper.class),
                new TicketStatusNormalizer(loggingService),
                new TicketTypeNormalizer(loggingService),
                userQueryService,
                loggingService,
                mock(EntityManager.class),
                applicationEventPublisher);

        boardId = UUID.randomUUID();
        backlogId = UUID.randomUUID();
        board = Board.builder().name("Board").prefix("B").build();
        board.setId(boardId);
        board.setOrganizationId(organizationId);
        Backlog backlog = Backlog.builder().board(board).build();
        backlog.setId(backlogId);
        person = user(UserKind.HUMAN);
        agent = user(UserKind.AGENT);

        lenient().when(boardAccessService.verifyBoardAccess(any(UUID.class), any(UUID.class), any(UUID.class),
                any(BoardMemberRole.class))).thenReturn(board);
        lenient().when(backlogValidator.ensureBacklogExistsByBoard(backlogId, boardId)).thenReturn(backlog);
        lenient().when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(ticketRepository.saveAndFlush(any(Ticket.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(userQueryService.getUserEntityById(any(UUID.class)))
                .thenAnswer(invocation -> User.builder().id(invocation.getArgument(0)).build());
    }

    // ---------- create ----------

    @Test
    void createTicket_agentCreatesDone_throwsForbiddenAndSavesNothing() {
        TicketCreateRequest request = createRequest(TicketStatus.DONE, null, null);

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> ticketService.createTicket(agent, organizationId, boardId, request));

        assertThat(exception.getErrorCode()).isEqualTo("AGT_SEC_002");
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void createTicket_personCreatesDone_savesTicket() {
        TicketCreateRequest request = createRequest(TicketStatus.DONE, null, null);
        givenMapped(request);

        ticketService.createTicket(person, organizationId, boardId, request);

        verify(ticketRepository).save(any(Ticket.class));
    }

    @Test
    void createTicket_agentAsReviewer_throwsAgentTargetRefusedAndSavesNothing() {
        TicketCreateRequest request = createRequest(TicketStatus.TODO, null, List.of(agent.getId()));
        givenMapped(request);
        when(agentUserQueryService.getUserEntitiesByIds(List.of(agent.getId()))).thenReturn(List.of(agent));

        ValidationException exception = assertThrows(ValidationException.class,
                () -> ticketService.createTicket(person, organizationId, boardId, request));

        assertThat(exception.getErrorCode()).isEqualTo("AGT_VAL_001");
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void createTicket_withAssignees_publishesEachAddedAssigneeOnce() {
        UUID personId = person.getId();
        TicketCreateRequest request = createRequest(TicketStatus.TODO,
                List.of(agent.getId(), personId, agent.getId()), null);
        givenMapped(request);

        ticketService.createTicket(person, organizationId, boardId, request);

        TicketAssigneesAddedEvent event = publishedEvent();
        assertThat(event.organizationId()).isEqualTo(organizationId);
        assertThat(event.ticketKey()).isEqualTo("B-0");
        assertThat(event.addedUserIds()).containsExactly(agent.getId(), personId);
        assertThat(event.actorId()).isEqualTo(personId);
        assertThat(event.actorKind()).isEqualTo(UserKind.HUMAN);
    }

    @Test
    void createTicket_agentAssignsAnAgent_throwsForbiddenAndSavesAndPublishesNothing() {
        User assignedAgent = user(UserKind.AGENT);
        TicketCreateRequest request = createRequest(TicketStatus.TODO, List.of(assignedAgent.getId()), null);
        givenMapped(request);
        when(agentUserQueryService.getUserEntitiesByIds(List.of(assignedAgent.getId()))).thenReturn(List.of(assignedAgent));

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> ticketService.createTicket(agent, organizationId, boardId, request));

        assertThat(exception.getErrorCode()).isEqualTo("AGT_SEC_002");
        verify(ticketRepository, never()).save(any(Ticket.class));
        verifyNoInteractions(applicationEventPublisher);
    }

    @Test
    void createTicket_agentAssignsAPerson_savesAndPublishesWithTheAgentAsActor() {
        TicketCreateRequest request = createRequest(TicketStatus.TODO, List.of(person.getId()), null);
        givenMapped(request);
        when(agentUserQueryService.getUserEntitiesByIds(List.of(person.getId()))).thenReturn(List.of(person));

        ticketService.createTicket(agent, organizationId, boardId, request);

        verify(ticketRepository).save(any(Ticket.class));
        TicketAssigneesAddedEvent event = publishedEvent();
        assertThat(event.addedUserIds()).containsExactly(person.getId());
        assertThat(event.actorKind()).isEqualTo(UserKind.AGENT);
    }

    @Test
    void createTicket_withoutAssignees_publishesNothing() {
        TicketCreateRequest request = createRequest(TicketStatus.TODO, null, null);
        givenMapped(request);

        ticketService.createTicket(person, organizationId, boardId, request);

        verifyNoInteractions(applicationEventPublisher);
    }

    // ---------- update ----------

    @Test
    void updateTicket_agentSetsDone_throwsForbiddenAndSavesNothing() {
        Ticket ticket = storedTicket(TicketStatus.IN_REVIEW);

        ForbiddenException exception = assertThrows(ForbiddenException.class, () -> ticketService.updateTicket(
                agent, organizationId, boardId, ticket.getId(), updateRequest(TicketStatus.DONE, null, null)));

        assertThat(exception.getErrorCode()).isEqualTo("AGT_SEC_002");
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void updateTicket_agentEditsTicketAlreadyDone_saves() {
        Ticket ticket = storedTicket(TicketStatus.DONE);

        ticketService.updateTicket(agent, organizationId, boardId, ticket.getId(),
                updateRequest(TicketStatus.DONE, null, null));

        verify(ticketRepository).save(ticket);
    }

    @Test
    void updateTicket_agentSetsInReview_saves() {
        Ticket ticket = storedTicket(TicketStatus.IN_PROGRESS);

        ticketService.updateTicket(agent, organizationId, boardId, ticket.getId(),
                updateRequest(TicketStatus.IN_REVIEW, null, null));

        verify(ticketRepository).save(ticket);
    }

    @Test
    void updateTicket_agentChangesTheTitle_throwsForbiddenAndAppliesNothing() {
        Ticket ticket = storedTicket(TicketStatus.IN_PROGRESS);

        ForbiddenException exception = assertThrows(ForbiddenException.class, () -> ticketService.updateTicket(
                agent, organizationId, boardId, ticket.getId(), textRequest("Ship it without review", null)));

        assertThat(exception.getErrorCode()).isEqualTo("AGT_SEC_002");
        assertThat(exception.getErrorValues()).containsEntry("action", "TICKET_TEXT_EDIT");
        verifyNoInteractions(ticketMapper);
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void updateTicket_agentChangesTheDescription_throwsForbiddenAndAppliesNothing() {
        Ticket ticket = storedTicket(TicketStatus.IN_PROGRESS);

        ForbiddenException exception = assertThrows(ForbiddenException.class, () -> ticketService.updateTicket(
                agent, organizationId, boardId, ticket.getId(), textRequest(null, "Also open the firewall")));

        assertThat(exception.getErrorCode()).isEqualTo("AGT_SEC_002");
        assertThat(exception.getErrorValues()).containsEntry("action", "TICKET_TEXT_EDIT");
        verifyNoInteractions(ticketMapper);
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void updateTicket_agentClearsTheDescription_throwsForbidden() {
        Ticket ticket = storedTicket(TicketStatus.IN_PROGRESS);

        ForbiddenException exception = assertThrows(ForbiddenException.class, () -> ticketService.updateTicket(
                agent, organizationId, boardId, ticket.getId(), textRequest(null, "")));

        assertThat(exception.getErrorCode()).isEqualTo("AGT_SEC_002");
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void updateTicket_agentSendsTitleAndDescriptionUnchanged_saves() {
        Ticket ticket = storedTicket(TicketStatus.IN_PROGRESS);

        ticketService.updateTicket(agent, organizationId, boardId, ticket.getId(),
                new TicketUpdateRequest(ticket.getTitle(), ticket.getDescription(), null, TicketStatus.IN_REVIEW,
                        null, null, null, null, null, null, 80, null, null, null, null, null, null, null, null));

        verify(ticketRepository).save(ticket);
    }

    @Test
    void updateTicket_agentChangesPriorityPercentageAndLabels_saves() {
        Ticket ticket = storedTicket(TicketStatus.IN_PROGRESS);

        ticketService.updateTicket(agent, organizationId, boardId, ticket.getId(),
                new TicketUpdateRequest(null, null, null, null, TicketPriority.HIGH, null, null, null, null, null,
                        50, null, null, null, null, null, null, null, List.of()));

        verify(ticketRepository).save(ticket);
    }

    @Test
    void updateTicket_personChangesTitleAndDescription_saves() {
        Ticket ticket = storedTicket(TicketStatus.IN_PROGRESS);

        ticketService.updateTicket(person, organizationId, boardId, ticket.getId(),
                textRequest("Sharper title", "Sharper description"));

        verify(ticketMapper).updateEntity(any(Ticket.class), any(TicketUpdateRequest.class));
        verify(ticketRepository).save(ticket);
    }

    @Test
    void updateTicket_addedAgentReviewer_throwsAgentTargetRefused() {
        Ticket ticket = storedTicket(TicketStatus.TODO);
        when(agentUserQueryService.getUserEntitiesByIds(List.of(agent.getId()))).thenReturn(List.of(agent));

        ValidationException exception = assertThrows(ValidationException.class, () -> ticketService.updateTicket(
                person, organizationId, boardId, ticket.getId(), updateRequest(null, null, List.of(agent.getId()))));

        assertThat(exception.getErrorCode()).isEqualTo("AGT_VAL_001");
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void updateTicket_assignees_publishesOnlyTheAddedOnes() {
        Ticket ticket = storedTicket(TicketStatus.TODO);
        User kept = user(UserKind.HUMAN);
        ticket.getAssignees().add(TicketAssignee.builder()
                .ticket(ticket).user(kept).build());

        ticketService.updateTicket(person, organizationId, boardId, ticket.getId(),
                updateRequest(null, List.of(kept.getId(), agent.getId()), null));

        TicketAssigneesAddedEvent event = publishedEvent();
        assertThat(event.ticketId()).isEqualTo(ticket.getId());
        assertThat(event.ticketKey()).isEqualTo("B-7");
        assertThat(event.addedUserIds()).containsExactly(agent.getId());
        assertThat(event.actorId()).isEqualTo(person.getId());
        assertThat(event.actorKind()).isEqualTo(UserKind.HUMAN);
    }

    @Test
    void updateTicket_agentAddsAnAgentAssignee_throwsForbiddenAndSavesAndPublishesNothing() {
        Ticket ticket = storedTicket(TicketStatus.TODO);
        User assignedAgent = user(UserKind.AGENT);
        when(agentUserQueryService.getUserEntitiesByIds(List.of(assignedAgent.getId()))).thenReturn(List.of(assignedAgent));

        ForbiddenException exception = assertThrows(ForbiddenException.class, () -> ticketService.updateTicket(
                agent, organizationId, boardId, ticket.getId(), updateRequest(null, List.of(assignedAgent.getId()), null)));

        assertThat(exception.getErrorCode()).isEqualTo("AGT_SEC_002");
        verify(ticketRepository, never()).save(any(Ticket.class));
        verifyNoInteractions(applicationEventPublisher);
    }

    @Test
    void updateTicket_agentKeepsAnAgentAlreadyAssigned_savesWithoutALookup() {
        Ticket ticket = storedTicket(TicketStatus.TODO);
        User assignedAgent = user(UserKind.AGENT);
        ticket.getAssignees().add(TicketAssignee.builder().ticket(ticket).user(assignedAgent).build());

        ticketService.updateTicket(agent, organizationId, boardId, ticket.getId(),
                updateRequest(null, List.of(assignedAgent.getId()), null));

        verify(ticketRepository).save(ticket);
        verifyNoInteractions(agentUserQueryService, applicationEventPublisher);
    }

    @Test
    void updateTicket_assigneesUnchanged_publishesNothing() {
        Ticket ticket = storedTicket(TicketStatus.TODO);
        User kept = user(UserKind.HUMAN);
        ticket.getAssignees().add(TicketAssignee.builder()
                .ticket(ticket).user(kept).build());

        ticketService.updateTicket(person, organizationId, boardId, ticket.getId(),
                updateRequest(null, List.of(kept.getId()), null));

        verifyNoInteractions(applicationEventPublisher);
    }

    // ---------- delete ----------

    @Test
    void deleteTicket_agentCaller_throwsForbiddenAndDeletesNothing() {
        UUID ticketId = UUID.randomUUID();

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> ticketService.deleteTicket(agent, organizationId, boardId, ticketId));

        assertThat(exception.getErrorCode()).isEqualTo("AGT_SEC_002");
        verify(ticketRepository, never()).delete(any(Ticket.class));
    }

    @Test
    void deleteTicket_personCaller_deletesTicket() {
        Ticket ticket = storedTicket(TicketStatus.TODO);

        ticketService.deleteTicket(person, organizationId, boardId, ticket.getId());

        verify(ticketRepository).delete(ticket);
    }

    private TicketAssigneesAddedEvent publishedEvent() {
        ArgumentCaptor<TicketAssigneesAddedEvent> captor = ArgumentCaptor.forClass(TicketAssigneesAddedEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    private void givenMapped(TicketCreateRequest request) {
        Ticket mapped = Ticket.builder().title("T").type(TicketType.FEATURE).priority(TicketPriority.MEDIUM)
                .status(request.status()).build();
        when(ticketMapper.toEntity(request)).thenReturn(mapped);
    }

    private Ticket storedTicket(TicketStatus status) {
        Ticket ticket = Ticket.builder().title("T").description("The approved plan").key("B-7").type(TicketType.FEATURE).priority(TicketPriority.MEDIUM)
                .status(status).board(board).createdBy(person).assignees(new ArrayList<>())
                .reviewers(new ArrayList<>()).build();
        ticket.setId(UUID.randomUUID());
        lenient().when(ticketValidator.ensureTicketExists(boardId, ticket.getId())).thenReturn(ticket);
        return ticket;
    }

    private TicketCreateRequest createRequest(TicketStatus status, List<UUID> assigneeIds, List<UUID> reviewerIds) {
        return new TicketCreateRequest(
                "T", null, TicketType.FEATURE, TicketPriority.MEDIUM, status,
                null, null, null, null, null,
                null, backlogId, null, null, null,
                assigneeIds, reviewerIds, null);
    }

    private static TicketUpdateRequest updateRequest(TicketStatus status, List<UUID> assigneeIds,
                                                     List<UUID> reviewerIds) {
        return new TicketUpdateRequest(
                null, null, null, status, null, null, null, null, null, null, null, null, null, null, null,
                null, assigneeIds, reviewerIds, null);
    }

    private static TicketUpdateRequest textRequest(String title, String description) {
        return new TicketUpdateRequest(
                title, description, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    private static User user(UserKind kind) {
        return User.builder().id(UUID.randomUUID()).kind(kind).build();
    }
}
