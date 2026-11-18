package com.korur.oba.apps.kaizen.ticket.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.board.enums.BoardMemberRole;
import com.korur.oba.apps.kaizen.board.service.BoardAccessService;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.apps.kaizen.ticket.enums.TicketType;
import com.korur.oba.apps.kaizen.ticket.repository.TicketRepository;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class TicketServiceImplUnitSyncTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private BoardAccessService boardAccessService;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private TicketServiceImpl ticketServiceImpl;

    private static User user() {
        User user = User.builder().build();
        user.setId(UUID.randomUUID());
        return user;
    }

    private static Ticket ticket(Board board, int percentage) {
        Ticket ticket = Ticket.builder().title("T").key("B-1").type(TicketType.FEATURE)
                .board(board).percentage(percentage).build();
        ticket.setId(UUID.randomUUID());
        return ticket;
    }

    @Nested
    class SyncTicketPercentage {

        @Test
        void memberOfTheBoard_setsOnlyThePercentageAndSaves() {
            User user = user();
            Board board = Board.builder().id(UUID.randomUUID()).build();
            Ticket ticket = ticket(board, 10);
            ticket.setTitle("Unchanged title");
            when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
            when(boardAccessService.verifyBoardAccess(organizationId, board.getId(), user.getId(), BoardMemberRole.MEMBER))
                    .thenReturn(board);

            ticketServiceImpl.syncTicketPercentage(user, organizationId, ticket.getId(), 75);

            assertThat(ticket.getPercentage()).isEqualTo(75);
            assertThat(ticket.getTitle()).isEqualTo("Unchanged title");
            verify(ticketRepository).save(ticket);
        }

        @Test
        void ticketNotFound_throwsNotFoundWithTicketCode() {
            UUID ticketId = UUID.randomUUID();
            when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ticketServiceImpl.syncTicketPercentage(user(), organizationId, ticketId, 50))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainKaizenError.NOT_TICKET_FOUND.getCode()));

            verify(ticketRepository, never()).save(any());
        }

        @Test
        void viewerOnTheBoard_propagatesForbiddenAndLeavesPercentage() {
            User user = user();
            Board board = Board.builder().id(UUID.randomUUID()).build();
            Ticket ticket = ticket(board, 10);
            when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
            when(boardAccessService.verifyBoardAccess(organizationId, board.getId(), user.getId(), BoardMemberRole.MEMBER))
                    .thenThrow(new ForbiddenException());

            assertThatThrownBy(() -> ticketServiceImpl.syncTicketPercentage(user, organizationId, ticket.getId(), 75))
                    .isInstanceOf(ForbiddenException.class);

            assertThat(ticket.getPercentage()).isEqualTo(10);
            verify(ticketRepository, never()).save(any());
        }

        @Test
        void nonMember_propagatesNotFoundAndLeavesPercentage() {
            User user = user();
            Board board = Board.builder().id(UUID.randomUUID()).build();
            Ticket ticket = ticket(board, 10);
            when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
            when(boardAccessService.verifyBoardAccess(organizationId, board.getId(), user.getId(), BoardMemberRole.MEMBER))
                    .thenThrow(new NotFoundException(ExceptionDomainKaizenError.NOT_BOARD_FOUND));

            assertThatThrownBy(() -> ticketServiceImpl.syncTicketPercentage(user, organizationId, ticket.getId(), 75))
                    .isInstanceOf(NotFoundException.class);

            assertThat(ticket.getPercentage()).isEqualTo(10);
        }
    }
}
