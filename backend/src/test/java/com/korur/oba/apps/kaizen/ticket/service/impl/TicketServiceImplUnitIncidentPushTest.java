package com.korur.oba.apps.kaizen.ticket.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 2. Third-party
import jakarta.persistence.EntityManager;

// 3. Java SDK
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

// 4. Internal
import com.korur.oba.apps.kaizen.backlog.entity.Backlog;
import com.korur.oba.apps.kaizen.backlog.validator.BacklogValidator;
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.board.entity.BoardSprint;
import com.korur.oba.apps.kaizen.board.enums.BoardMemberRole;
import com.korur.oba.apps.kaizen.board.repository.BoardRepository;
import com.korur.oba.apps.kaizen.board.service.BoardAccessService;
import com.korur.oba.apps.kaizen.board.service.BoardMemberQueryService;
import com.korur.oba.apps.kaizen.comment.repository.CommentRepository;
import com.korur.oba.apps.kaizen.board.validator.BoardValidator;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.apps.kaizen.label.entity.Label;
import com.korur.oba.apps.kaizen.label.validator.LabelValidator;
import com.korur.oba.apps.kaizen.milestone.entity.Milestone;
import com.korur.oba.apps.kaizen.milestone.validator.MilestoneValidator;
import com.korur.oba.apps.kaizen.project.entity.Project;
import com.korur.oba.apps.kaizen.project.validator.ProjectValidator;
import com.korur.oba.apps.kaizen.sprint.entity.Sprint;
import com.korur.oba.apps.kaizen.sprint.service.SprintService;
import com.korur.oba.apps.kaizen.sprint.validator.SprintValidator;
import com.korur.oba.apps.kaizen.ticket.dto.request.TicketBulkMoveRequest;
import com.korur.oba.apps.kaizen.ticket.dto.request.TicketCreateRequest;
import com.korur.oba.apps.kaizen.ticket.dto.request.TicketMoveRequest;
import com.korur.oba.apps.kaizen.ticket.dto.request.TicketOrderUpdateRequest;
import com.korur.oba.apps.kaizen.ticket.dto.request.TicketUpdateRequest;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.apps.kaizen.ticket.event.IncidentOpenedEvent;
import com.korur.oba.apps.kaizen.ticket.entity.TicketAssignee;
import com.korur.oba.apps.kaizen.ticket.entity.TicketReviewer;
import com.korur.oba.apps.kaizen.ticket.enums.MissionRank;
import com.korur.oba.apps.kaizen.ticket.enums.TicketPriority;
import com.korur.oba.apps.kaizen.ticket.enums.TicketRelocationAction;
import com.korur.oba.apps.kaizen.ticket.enums.TicketStatus;
import com.korur.oba.apps.kaizen.ticket.enums.TicketType;
import com.korur.oba.apps.kaizen.ticket.helper.TicketHelper;
import com.korur.oba.apps.kaizen.ticket.helper.TicketStatusNormalizer;
import com.korur.oba.apps.kaizen.ticket.helper.TicketTypeNormalizer;
import com.korur.oba.apps.kaizen.ticket.mapper.TicketMapper;
import com.korur.oba.apps.kaizen.ticket.repository.TicketAssigneeRepository;
import com.korur.oba.apps.kaizen.ticket.repository.TicketLabelRepository;
import com.korur.oba.apps.kaizen.ticket.repository.TicketRepository;
import com.korur.oba.apps.kaizen.ticket.repository.TicketReviewerRepository;
import com.korur.oba.apps.kaizen.ticket.validator.TicketValidator;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.service.UserQueryService;

@ExtendWith(MockitoExtension.class)
class TicketServiceImplUnitIncidentPushTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketAssigneeRepository ticketAssigneeRepository;

    @Mock
    private TicketReviewerRepository ticketReviewerRepository;

    @Mock
    private TicketLabelRepository ticketLabelRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private BoardAccessService boardAccessService;

    @Mock
    private SprintValidator sprintValidator;

    @Mock
    private BacklogValidator backlogValidator;

    @Mock
    private ProjectValidator projectValidator;

    @Mock
    private TicketValidator ticketValidator;

    @Mock
    private LabelValidator labelValidator;
    @Mock
    private BoardValidator boardValidator;

    @Mock
    private TicketMapper ticketMapper;

    @Mock
    private SprintService sprintService;

    @Mock
    private TicketHelper ticketHelper;

    @Mock
    private MilestoneValidator milestoneValidator;

    @Mock
    private BoardMemberQueryService boardMemberQueryService;

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private LoggingService loggingService;

    @Mock
    private EntityManager entityManager;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    // Real instance: the backlog status rule is behaviour under test, not a collaborator to stub.
    @Spy
    private TicketStatusNormalizer ticketStatusNormalizer = new TicketStatusNormalizer(new LoggingService());

    @Spy
    private TicketTypeNormalizer ticketTypeNormalizer = new TicketTypeNormalizer(new LoggingService());

    @InjectMocks
    private TicketServiceImpl ticketServiceImpl;

    private UUID boardId;
    private Board board;
    private User user;

    @BeforeEach
    void setUp() {
        boardId = UUID.randomUUID();
        user = User.builder().build();
        user.setId(UUID.randomUUID());
        board = Board.builder().name("Kaizen Platform").prefix("KZN").build();
        board.setId(boardId);
        lenient().when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(),
                BoardMemberRole.MEMBER)).thenReturn(board);
        lenient().when(boardRepository.incrementAndGetTicketCounter(boardId)).thenReturn(311L);
        lenient().when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private IncidentOpenedEvent publishedIncident() {
        ArgumentCaptor<IncidentOpenedEvent> captor = ArgumentCaptor.forClass(IncidentOpenedEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    private void createWith(TicketType type, TicketPriority priority) {
        UUID backlogId = UUID.randomUUID();
        Backlog backlog = Backlog.builder().board(board).build();
        backlog.setId(backlogId);
        Ticket mapped = Ticket.builder().title("Payment webhooks return 502").key("KZN-311").type(type)
                .priority(priority).status(TicketStatus.TODO).build();
        TicketCreateRequest request = new TicketCreateRequest(
                "Payment webhooks return 502", null, type, priority, TicketStatus.TODO,
                null, null, null, null, null,
                null, backlogId, null, null, null,
                null, null, null);
        when(ticketMapper.toEntity(request)).thenReturn(mapped);
        when(backlogValidator.ensureBacklogExistsByBoard(backlogId, boardId)).thenReturn(backlog);
        ticketServiceImpl.createTicket(user, organizationId, boardId, request);
    }

    /**
     * Updates a stored ticket whose mapper step sets the given type, priority and status.
     */
    private void updateFrom(TicketType type, TicketPriority priority, TicketStatus status,
                            TicketPriority newPriority, TicketStatus newStatus) {
        UUID ticketId = UUID.randomUUID();
        Backlog backlog = Backlog.builder().board(board).build();
        backlog.setId(UUID.randomUUID());
        Ticket ticket = Ticket.builder().title("Payment webhooks return 502").key("KZN-311").type(type)
                .priority(priority).status(status).board(board).backlog(backlog).createdBy(user).build();
        ticket.setId(ticketId);
        TicketUpdateRequest request = new TicketUpdateRequest(
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
        when(ticketValidator.ensureTicketExists(boardId, ticketId)).thenReturn(ticket);
        doAnswer(invocation -> {
            Ticket target = invocation.getArgument(0);
            target.setPriority(newPriority);
            target.setStatus(newStatus);
            return null;
        }).when(ticketMapper).updateEntity(any(Ticket.class), any(TicketUpdateRequest.class));
        ticketServiceImpl.updateTicket(user, organizationId, boardId, ticketId, request);
    }

    @Nested
    class CreateTicket {

        @Test
        void incidentP2_publishesTheIncidentWithTheCreatorAsActor() {
            // Act
            createWith(TicketType.INCIDENT, TicketPriority.P2);

            // Assert
            IncidentOpenedEvent event = publishedIncident();
            assertThat(event.organizationId()).isEqualTo(organizationId);
            assertThat(event.boardId()).isEqualTo(boardId);
            assertThat(event.boardName()).isEqualTo("Kaizen Platform");
            assertThat(event.ticketKey()).isEqualTo("KZN-311");
            assertThat(event.title()).isEqualTo("Payment webhooks return 502");
            assertThat(event.priority()).isEqualTo(TicketPriority.P2);
            assertThat(event.actorId()).isEqualTo(user.getId());
        }

        @Test
        void incidentP3_publishesNothing() {
            // Act
            createWith(TicketType.INCIDENT, TicketPriority.P3);

            // Assert
            verify(applicationEventPublisher, never()).publishEvent(any(IncidentOpenedEvent.class));
        }

        @Test
        void urgentTicketThatIsNoIncident_publishesNothing() {
            // Act
            createWith(TicketType.BUG, TicketPriority.P1);

            // Assert
            verify(applicationEventPublisher, never()).publishEvent(any(IncidentOpenedEvent.class));
        }
    }

    @Nested
    class UpdateTicket {

        @Test
        void incidentRaisedFromP3ToP1_publishesTheIncident() {
            // Act
            updateFrom(TicketType.INCIDENT, TicketPriority.P3, TicketStatus.TODO, TicketPriority.P1,
                    TicketStatus.TODO);

            // Assert
            IncidentOpenedEvent event = publishedIncident();
            assertThat(event.priority()).isEqualTo(TicketPriority.P1);
            assertThat(event.actorId()).isEqualTo(user.getId());
        }

        @Test
        void incidentRaisedFromP2ToP1_publishesAgain() {
            // Act
            updateFrom(TicketType.INCIDENT, TicketPriority.P2, TicketStatus.TODO, TicketPriority.P1,
                    TicketStatus.TODO);

            // Assert
            assertThat(publishedIncident().priority()).isEqualTo(TicketPriority.P1);
        }

        @Test
        void incidentEditedWithoutChangingItsUrgency_publishesNothing() {
            // Act
            updateFrom(TicketType.INCIDENT, TicketPriority.P1, TicketStatus.TODO, TicketPriority.P1,
                    TicketStatus.IN_PROGRESS);

            // Assert
            verify(applicationEventPublisher, never()).publishEvent(any(IncidentOpenedEvent.class));
        }

        @Test
        void incidentLoweredFromP1ToP2_publishesNothing() {
            // Act
            updateFrom(TicketType.INCIDENT, TicketPriority.P1, TicketStatus.TODO, TicketPriority.P2,
                    TicketStatus.TODO);

            // Assert
            verify(applicationEventPublisher, never()).publishEvent(any(IncidentOpenedEvent.class));
        }

        @Test
        void doneIncidentReopenedAtP1_publishesTheIncident() {
            // Act
            updateFrom(TicketType.INCIDENT, TicketPriority.P1, TicketStatus.DONE, TicketPriority.P1,
                    TicketStatus.TODO);

            // Assert
            assertThat(publishedIncident().ticketKey()).isEqualTo("KZN-311");
        }

        @Test
        void incidentRaisedToP3_publishesNothing() {
            // Act
            updateFrom(TicketType.INCIDENT, TicketPriority.MEDIUM, TicketStatus.TODO, TicketPriority.P3,
                    TicketStatus.TODO);

            // Assert
            verify(applicationEventPublisher, never()).publishEvent(any(IncidentOpenedEvent.class));
        }

        @Test
        void bugRaisedToP1_publishesNothing() {
            // Act
            updateFrom(TicketType.BUG, TicketPriority.P3, TicketStatus.TODO, TicketPriority.P1, TicketStatus.TODO);

            // Assert
            verify(applicationEventPublisher, never()).publishEvent(any(IncidentOpenedEvent.class));
        }
    }
}
