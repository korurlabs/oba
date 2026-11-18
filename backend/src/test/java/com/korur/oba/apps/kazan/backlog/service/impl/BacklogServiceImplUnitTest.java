package com.korur.oba.apps.kazan.backlog.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.backlog.entity.Backlog;
import com.korur.oba.apps.kazan.backlog.repository.BacklogRepository;
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.enums.BoardMemberRole;
import com.korur.oba.apps.kazan.board.repository.BoardRepository;
import com.korur.oba.apps.kazan.board.service.BoardAccessService;
import com.korur.oba.apps.kazan.comment.repository.CommentRepository;
import com.korur.oba.apps.kazan.milestone.repository.MilestoneDependencyRepository;
import com.korur.oba.apps.kazan.milestone.repository.MilestoneRepository;
import com.korur.oba.apps.kazan.milestone.validator.MilestoneValidator;
import com.korur.oba.apps.kazan.sprint.entity.Sprint;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.enums.TicketStatus;
import com.korur.oba.apps.kazan.ticket.enums.TicketType;
import com.korur.oba.apps.kazan.ticket.helper.TicketHelper;
import com.korur.oba.apps.kazan.ticket.helper.TicketStatusNormalizer;
import com.korur.oba.apps.kazan.ticket.repository.TicketAssigneeRepository;
import com.korur.oba.apps.kazan.ticket.repository.TicketLabelRepository;
import com.korur.oba.apps.kazan.ticket.repository.TicketRepository;
import com.korur.oba.apps.kazan.ticket.repository.TicketReviewerRepository;
import com.korur.oba.apps.kazan.ticket.validator.TicketValidator;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class BacklogServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private BacklogRepository backlogRepository;

    @Mock
    private BoardAccessService boardAccessService;

    @Mock
    private TicketValidator ticketValidator;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private LoggingService loggingService;

    private BacklogServiceImpl backlogService;

    @BeforeEach
    void setUp() {
        // Real helper: the hierarchy rule of a move (carry sub-tickets, unparent a
        // sub-ticket moved apart, reset a backlog ticket to TODO) is behaviour under test.
        TicketHelper ticketHelper = new TicketHelper(
                ticketRepository,
                mock(BoardRepository.class),
                mock(CommentRepository.class),
                mock(TicketAssigneeRepository.class),
                mock(TicketReviewerRepository.class),
                mock(TicketLabelRepository.class),
                new TicketStatusNormalizer(new LoggingService()),
                new MilestoneValidator(mock(MilestoneRepository.class), mock(MilestoneDependencyRepository.class),
                        new LoggingService()),
                loggingService);
        backlogService = new BacklogServiceImpl(
                backlogRepository, boardAccessService, ticketValidator, ticketHelper, loggingService);
    }

    @Nested
    class AddTicketToBacklog {

        private Board board;
        private User user;
        private Backlog backlog;
        private Sprint sprint;
        private Ticket parent;
        private Ticket subTicket;

        @BeforeEach
        void setUpHierarchy() {
            // A board with a backlog and a sprint, and a top-level parent in the sprint with
            // one sub-ticket, both IN_PROGRESS.
            board = Board.builder().name("Board").prefix("B").build();
            board.setId(UUID.randomUUID());
            user = User.builder().build();
            user.setId(UUID.randomUUID());
            backlog = Backlog.builder().board(board).build();
            backlog.setId(UUID.randomUUID());
            sprint = Sprint.builder().name("Sprint").build();
            sprint.setId(UUID.randomUUID());
            parent = ticket("B-1", null);
            parent.setSprint(sprint);
            subTicket = ticket("B-2", parent);
            subTicket.setSprint(sprint);

            when(boardAccessService.verifyBacklogAccess(organizationId, board.getId(), user.getId(), BoardMemberRole.MEMBER))
                    .thenReturn(backlog);
        }

        @Test
        void loneSubTicket_keepsParentAndStatus() {
            // Arrange
            when(ticketValidator.ensureTicketExists(board.getId(), subTicket.getId())).thenReturn(subTicket);

            // Act
            backlogService.addTicketToBacklog(user, organizationId, board.getId(), subTicket.getId());

            // Assert
            assertThat(subTicket.getParentTicket()).isEqualTo(parent);
            assertThat(subTicket.getBacklog()).isEqualTo(backlog);
            assertThat(subTicket.getSprint()).isNull();
            // Sub-tickets are exempt from the backlog status rule.
            assertThat(subTicket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
            assertThat(parent.getSprint()).isEqualTo(sprint);
            assertThat(parent.getBacklog()).isNull();
            assertThat(parent.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
            verify(ticketRepository).saveAll(List.of(subTicket));
        }

        @Test
        void parentTicket_takesSubTicketsAlong() {
            // Arrange
            when(ticketValidator.ensureTicketExists(board.getId(), parent.getId())).thenReturn(parent);
            when(ticketRepository.findByParentTicket_IdIn(anyList())).thenReturn(List.of(subTicket), List.of());

            // Act
            backlogService.addTicketToBacklog(user, organizationId, board.getId(), parent.getId());

            // Assert
            assertThat(parent.getBacklog()).isEqualTo(backlog);
            assertThat(parent.getSprint()).isNull();
            assertThat(parent.getStatus()).isEqualTo(TicketStatus.TODO);
            assertThat(subTicket.getParentTicket()).isEqualTo(parent);
            assertThat(subTicket.getBacklog()).isEqualTo(backlog);
            assertThat(subTicket.getSprint()).isNull();
            // Sub-tickets are exempt from the backlog status rule.
            assertThat(subTicket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);

            ArgumentCaptor<List<Ticket>> savedCaptor = ArgumentCaptor.captor();
            verify(ticketRepository).saveAll(savedCaptor.capture());
            assertThat(savedCaptor.getValue()).containsExactly(parent, subTicket);
        }

        @Test
        void parentTicket_leavesDoneSubTicketsAndSubTicketsPlacedElsewhere() {
            // Arrange: add-ticket moves with UNFINISHED, so only the unfinished sub-ticket placed
            // with the parent comes along.
            Ticket doneSubTicket = ticket("B-3", parent);
            doneSubTicket.setStatus(TicketStatus.DONE);
            doneSubTicket.setSprint(sprint);
            Sprint laterSprint = Sprint.builder().name("Later").build();
            laterSprint.setId(UUID.randomUUID());
            Ticket subTicketElsewhere = ticket("B-4", parent);
            subTicketElsewhere.setSprint(laterSprint);
            when(ticketValidator.ensureTicketExists(board.getId(), parent.getId())).thenReturn(parent);
            when(ticketRepository.findByParentTicket_IdIn(anyList()))
                    .thenReturn(List.of(subTicket, doneSubTicket, subTicketElsewhere), List.of());

            // Act
            backlogService.addTicketToBacklog(user, organizationId, board.getId(), parent.getId());

            // Assert
            assertThat(subTicket.getBacklog()).isEqualTo(backlog);
            assertThat(doneSubTicket.getSprint()).isEqualTo(sprint);
            assertThat(doneSubTicket.getBacklog()).isNull();
            assertThat(subTicketElsewhere.getSprint()).isEqualTo(laterSprint);
            assertThat(List.of(subTicket, doneSubTicket, subTicketElsewhere))
                    .allMatch((Ticket ticket) -> ticket.getParentTicket() == parent);
            verify(ticketRepository).saveAll(List.of(parent, subTicket));
        }

        @Test
        void subTicketInParentBacklog_keepsParent() {
            // Arrange
            parent.setSprint(null);
            parent.setBacklog(backlog);
            subTicket.setSprint(null);
            subTicket.setBacklog(backlog);
            when(ticketValidator.ensureTicketExists(board.getId(), subTicket.getId())).thenReturn(subTicket);

            // Act
            backlogService.addTicketToBacklog(user, organizationId, board.getId(), subTicket.getId());

            // Assert
            assertThat(subTicket.getParentTicket()).isEqualTo(parent);
            assertThat(subTicket.getBacklog()).isEqualTo(backlog);
            assertThat(subTicket.getSprint()).isNull();
            assertThat(subTicket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
            verify(ticketRepository).saveAll(List.of(subTicket));
        }

        private Ticket ticket(String key, Ticket parentTicket) {
            Ticket ticket = Ticket.builder().title(key).key(key).type(TicketType.FEATURE)
                    .status(TicketStatus.IN_PROGRESS).board(board).parentTicket(parentTicket)
                    .createdBy(user).build();
            ticket.setId(UUID.randomUUID());
            return ticket;
        }
    }
}
