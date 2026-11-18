package com.korur.oba.apps.kazan.board.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.backlog.validator.BacklogValidator;
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.entity.BoardMember;
import com.korur.oba.apps.kazan.board.enums.BoardMemberRole;
import com.korur.oba.apps.kazan.board.validator.BoardValidator;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.milestone.validator.MilestoneValidator;
import com.korur.oba.apps.kazan.sprint.validator.SprintValidator;
import com.korur.oba.apps.kazan.ticket.validator.TicketValidator;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class BoardAccessServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();
    private final UUID otherOrganizationId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Mock
    private BoardValidator boardValidator;

    @Mock
    private SprintValidator sprintValidator;

    @Mock
    private TicketValidator ticketValidator;

    @Mock
    private BacklogValidator backlogValidator;

    @Mock
    private MilestoneValidator milestoneValidator;

    @Mock
    private LoggingService loggingService;

    private BoardAccessServiceImpl service;
    private Board foreignBoard;
    private Board ownBoard;

    @BeforeEach
    void setUp() {
        service = new BoardAccessServiceImpl(boardValidator, sprintValidator, ticketValidator, backlogValidator,
                milestoneValidator, loggingService);
        foreignBoard = Board.builder().organizationId(otherOrganizationId).build();
        foreignBoard.setId(UUID.randomUUID());
        ownBoard = Board.builder().organizationId(organizationId).build();
        ownBoard.setId(UUID.randomUUID());
    }

    private void givenForeignBoardRefused() {
        when(boardValidator.ensureBoardInOrganization(organizationId, foreignBoard.getId()))
                .thenThrow(new NotFoundException(ExceptionDomainKazanError.NOT_BOARD_FOUND));
    }

    private void assertBoardNotFound(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ExceptionDomainKazanError.NOT_BOARD_FOUND.getCode()));
    }

    @Nested
    class ForeignBoard {

        @Test
        void verifyBoardAccess_boardOfAnotherOrganization_throwsBoardNotFoundBeforeMembershipCheck() {
            givenForeignBoardRefused();

            assertBoardNotFound(() -> service.verifyBoardAccess(
                    organizationId, foreignBoard.getId(), userId, BoardMemberRole.VIEWER));

            verify(boardValidator).ensureBoardInOrganization(organizationId, foreignBoard.getId());
            verify(boardValidator, never()).ensureBoardMemberExists(any(), any());
            verifyNoInteractions(sprintValidator, ticketValidator, backlogValidator, milestoneValidator);
        }

        @Test
        void verifySprintAccess_boardOfAnotherOrganization_throwsBoardNotFoundBeforeSprintLookup() {
            givenForeignBoardRefused();

            assertBoardNotFound(() -> service.verifySprintAccess(
                    organizationId, foreignBoard.getId(), UUID.randomUUID(), userId, BoardMemberRole.VIEWER));

            verifyNoInteractions(sprintValidator);
        }

        @Test
        void verifyTicketAccess_boardOfAnotherOrganization_throwsBoardNotFoundBeforeTicketLookup() {
            givenForeignBoardRefused();

            assertBoardNotFound(() -> service.verifyTicketAccess(
                    organizationId, foreignBoard.getId(), UUID.randomUUID(), userId, BoardMemberRole.VIEWER));

            verifyNoInteractions(ticketValidator);
        }

        @Test
        void verifyBacklogAccess_boardOfAnotherOrganization_throwsBoardNotFoundBeforeBacklogLookup() {
            givenForeignBoardRefused();

            assertBoardNotFound(() -> service.verifyBacklogAccess(
                    organizationId, foreignBoard.getId(), userId, BoardMemberRole.VIEWER));

            verifyNoInteractions(backlogValidator);
        }

        @Test
        void verifyMilestoneAccess_boardOfAnotherOrganization_throwsBoardNotFoundBeforeMilestoneLookup() {
            givenForeignBoardRefused();

            assertBoardNotFound(() -> service.verifyMilestoneAccess(
                    organizationId, foreignBoard.getId(), UUID.randomUUID(), userId, BoardMemberRole.VIEWER));

            verifyNoInteractions(milestoneValidator);
        }
    }

    @Nested
    class OwnBoard {

        @Test
        void verifyBoardAccess_boardOfOrganizationAndMember_returnsBoard() {
            BoardMember member = BoardMember.builder().role(BoardMemberRole.MEMBER).build();
            when(boardValidator.ensureBoardInOrganization(organizationId, ownBoard.getId())).thenReturn(ownBoard);
            when(boardValidator.ensureBoardMemberExists(ownBoard.getId(), userId)).thenReturn(member);

            Board result = service.verifyBoardAccess(
                    organizationId, ownBoard.getId(), userId, BoardMemberRole.MEMBER);

            assertThat(result).isSameAs(ownBoard);
        }

        @Test
        void verifyBoardAccess_boardOfOrganizationAndNotMember_throwsBoardMemberNotFound() {
            when(boardValidator.ensureBoardInOrganization(organizationId, ownBoard.getId())).thenReturn(ownBoard);
            when(boardValidator.ensureBoardMemberExists(ownBoard.getId(), userId))
                    .thenThrow(new NotFoundException(ExceptionDomainKazanError.NOT_BOARD_MEMBER_FOUND));

            assertThatThrownBy(() -> service.verifyBoardAccess(
                    organizationId, ownBoard.getId(), userId, BoardMemberRole.VIEWER))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainKazanError.NOT_BOARD_MEMBER_FOUND.getCode()));
        }
    }
}
