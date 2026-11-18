package com.korur.oba.apps.kazan.milestone.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.helper.BoardHelper;
import com.korur.oba.apps.kazan.board.service.BoardAccessService;
import com.korur.oba.apps.kazan.board.service.BoardMemberQueryService;
import com.korur.oba.apps.kazan.milestone.dto.response.HomeDeadlineResponse;
import com.korur.oba.apps.kazan.milestone.dto.response.MilestoneListResponse;
import com.korur.oba.apps.kazan.milestone.entity.Milestone;
import com.korur.oba.apps.kazan.milestone.enums.MilestoneHealth;
import com.korur.oba.apps.kazan.milestone.enums.MilestoneStatus;
import com.korur.oba.apps.kazan.milestone.helper.MilestoneHelper;
import com.korur.oba.apps.kazan.milestone.repository.MilestoneRepository;
import com.korur.oba.apps.kazan.milestone.validator.MilestoneValidator;

@ExtendWith(MockitoExtension.class)
class MilestoneQueryServiceImplUnitDeadlinesTest {

    private final UUID organizationId = UUID.randomUUID();

    private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");

    @Mock
    private MilestoneRepository milestoneRepository;

    @Mock
    private MilestoneValidator milestoneValidator;

    @Mock
    private MilestoneHelper milestoneHelper;

    @Mock
    private BoardAccessService boardAccessService;

    @Mock
    private BoardMemberQueryService boardMemberQueryService;

    @Mock
    private BoardHelper boardHelper;

    private final UUID userId = UUID.randomUUID();
    private final List<UUID> memberBoardIds = List.of(UUID.randomUUID());
    private Board board;

    private MilestoneQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        board = Board.builder().name("Kagi").slug("kagi").build();
        board.setId(memberBoardIds.get(0));
        service = newService(NOW);
    }

    private MilestoneQueryServiceImpl newService(Instant now) {
        return new MilestoneQueryServiceImpl(milestoneRepository, milestoneValidator, milestoneHelper,
                boardAccessService, boardMemberQueryService, boardHelper, Clock.fixed(now, ZoneOffset.UTC));
    }

    private Milestone milestone(String name, LocalDate target) {
        Milestone milestone = Milestone.builder().name(name).targetDate(target).board(board)
                .status(MilestoneStatus.OPEN).build();
        milestone.setId(UUID.randomUUID());
        return milestone;
    }

    private void stubHelperToEchoInOrder() {
        when(milestoneHelper.toListResponses(any(), eq(memberBoardIds))).thenAnswer(invocation -> {
            List<Milestone> milestones = invocation.getArgument(0);
            List<MilestoneListResponse> responses = new ArrayList<>();
            for (Milestone m : milestones) {
                responses.add(new MilestoneListResponse(m.getId(), m.getName(), m.getStatus(), m.getStartDate(),
                        m.getTargetDate(), board.getId(), board.getName(), "KAGI", 2L, 5L,
                        MilestoneHealth.ON_TRACK, "#3B82F6", List.of()));
            }
            return responses;
        });
    }

    @Test
    void listHomeDeadlines_userWithoutBoards_returnsEmptyWithoutQuery() {
        // Arrange
        when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(List.of());

        // Act
        List<HomeDeadlineResponse> result = service.listHomeDeadlines(userId, organizationId);

        // Assert
        assertThat(result).isEmpty();
        verifyNoInteractions(milestoneRepository, milestoneHelper);
    }

    @Test
    void listHomeDeadlines_nothingInWindow_returnsEmpty() {
        // Arrange
        when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(memberBoardIds);
        when(milestoneRepository.findByBoard_IdInAndStatus(memberBoardIds, MilestoneStatus.OPEN))
                .thenReturn(List.of(milestone("Far", LocalDate.of(2027, 6, 1))));

        // Act
        List<HomeDeadlineResponse> result = service.listHomeDeadlines(userId, organizationId);

        // Assert
        assertThat(result).isEmpty();
        verifyNoInteractions(milestoneHelper);
    }

    @Test
    void listHomeDeadlines_mixedDates_keepsOverdueAndWindowEdgeOnly() {
        // Arrange: today is 2026-11-04, the window ends 2027-01-03 (60 days)
        when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(memberBoardIds);
        Milestone overdue = milestone("Overdue", LocalDate.of(2026, 10, 1));
        Milestone edge = milestone("Edge", LocalDate.of(2027, 1, 3));
        Milestone beyond = milestone("Beyond", LocalDate.of(2027, 1, 4));
        when(milestoneRepository.findByBoard_IdInAndStatus(memberBoardIds, MilestoneStatus.OPEN))
                .thenReturn(List.of(beyond, edge, overdue));
        stubHelperToEchoInOrder();

        // Act
        List<HomeDeadlineResponse> result = service.listHomeDeadlines(userId, organizationId);

        // Assert
        assertThat(result).extracting(HomeDeadlineResponse::name).containsExactly("Overdue", "Edge");
    }

    @Test
    void listHomeDeadlines_clockLateUtcEvening_windowFollowsUtcDate() {
        // Arrange: 23:30Z is already Nov 5 in Istanbul, but the milestone calendar is UTC
        service = newService(Instant.parse("2026-11-04T23:30:00Z"));
        when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(memberBoardIds);
        Milestone inside = milestone("Inside", LocalDate.of(2027, 1, 3));
        Milestone outside = milestone("Outside", LocalDate.of(2027, 1, 4));
        when(milestoneRepository.findByBoard_IdInAndStatus(memberBoardIds, MilestoneStatus.OPEN))
                .thenReturn(List.of(outside, inside));
        stubHelperToEchoInOrder();

        // Act
        List<HomeDeadlineResponse> result = service.listHomeDeadlines(userId, organizationId);

        // Assert
        assertThat(result).extracting(HomeDeadlineResponse::name).containsExactly("Inside");
    }

    @Test
    void listHomeDeadlines_sameDate_ordersByNameIgnoringCase() {
        // Arrange
        when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(memberBoardIds);
        LocalDate target = LocalDate.of(2026, 11, 20);
        when(milestoneRepository.findByBoard_IdInAndStatus(memberBoardIds, MilestoneStatus.OPEN))
                .thenReturn(List.of(milestone("beta", target), milestone("Alpha", target),
                        milestone("Gamma", LocalDate.of(2026, 11, 10))));
        stubHelperToEchoInOrder();

        // Act
        List<HomeDeadlineResponse> result = service.listHomeDeadlines(userId, organizationId);

        // Assert
        assertThat(result).extracting(HomeDeadlineResponse::name).containsExactly("Gamma", "Alpha", "beta");
    }

    @Test
    void listHomeDeadlines_moreThanEight_loadsProgressForEarliestEightOnly() {
        // Arrange
        when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(memberBoardIds);
        List<Milestone> many = new ArrayList<>();
        for (int day = 12; day >= 1; day--) {
            many.add(milestone("M" + day, LocalDate.of(2026, 11, 4).plusDays(day)));
        }
        when(milestoneRepository.findByBoard_IdInAndStatus(memberBoardIds, MilestoneStatus.OPEN)).thenReturn(many);
        stubHelperToEchoInOrder();

        // Act
        List<HomeDeadlineResponse> result = service.listHomeDeadlines(userId, organizationId);

        // Assert
        assertThat(result).hasSize(8);
        assertThat(result.get(0).name()).isEqualTo("M1");
        assertThat(result.get(7).name()).isEqualTo("M8");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Milestone>> captor = ArgumentCaptor.forClass(List.class);
        verify(milestoneHelper).toListResponses(captor.capture(), eq(memberBoardIds));
        assertThat(captor.getValue()).hasSize(8);
    }

    @Test
    void listHomeDeadlines_dueMilestone_mapsProgressHealthAndBoardSlug() {
        // Arrange
        when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(memberBoardIds);
        Milestone due = milestone("Launch", LocalDate.of(2026, 12, 1));
        when(milestoneRepository.findByBoard_IdInAndStatus(memberBoardIds, MilestoneStatus.OPEN))
                .thenReturn(List.of(due));
        stubHelperToEchoInOrder();

        // Act
        List<HomeDeadlineResponse> result = service.listHomeDeadlines(userId, organizationId);

        // Assert
        assertThat(result).containsExactly(new HomeDeadlineResponse(due.getId(), "Launch",
                LocalDate.of(2026, 12, 1), MilestoneHealth.ON_TRACK, 2L, 5L, "#3B82F6",
                board.getId(), "Kagi", "kagi"));
    }
}
