package com.korur.oba.apps.kaizen.milestone.helper;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.milestone.dto.projection.MilestoneDependencyProjection;
import com.korur.oba.apps.kaizen.milestone.dto.response.MilestoneDependencyResponse;
import com.korur.oba.apps.kaizen.milestone.dto.response.MilestoneDetailResponse;
import com.korur.oba.apps.kaizen.milestone.dto.response.MilestoneListResponse;
import com.korur.oba.apps.kaizen.milestone.entity.Milestone;
import com.korur.oba.apps.kaizen.milestone.enums.MilestoneHealth;
import com.korur.oba.apps.kaizen.milestone.enums.MilestoneStatus;
import com.korur.oba.apps.kaizen.milestone.mapper.MilestoneMapperImpl;
import com.korur.oba.apps.kaizen.milestone.repository.MilestoneDependencyRepository;
import com.korur.oba.apps.kaizen.milestone.repository.MilestoneRepository;
import com.korur.oba.apps.kaizen.ticket.repository.TicketRepository;

class MilestoneHelperUnitTest {

    private final TicketRepository ticketRepository = mock(TicketRepository.class);
    private final MilestoneRepository milestoneRepository = mock(MilestoneRepository.class);
    private final MilestoneDependencyRepository dependencyRepository = mock(MilestoneDependencyRepository.class);

    private final MilestoneHelper helper = new MilestoneHelper(ticketRepository, milestoneRepository,
            dependencyRepository, new MilestoneMapperImpl(), new MilestoneHealthResolver(),
            Clock.fixed(Instant.parse("2026-11-04T10:00:00Z"), ZoneOffset.UTC));

    private static Board board() {
        Board board = Board.builder().name("B").prefix("B").build();
        board.setId(UUID.randomUUID());
        return board;
    }

    private static Milestone milestone(Board board, LocalDate startDate, LocalDate targetDate) {
        Milestone milestone = Milestone.builder().name("M").board(board).status(MilestoneStatus.OPEN)
                .startDate(startDate).targetDate(targetDate).build();
        milestone.setId(UUID.randomUUID());
        return milestone;
    }

    private static MilestoneDependencyProjection edge(Milestone dependent, Milestone dependsOn) {
        return new MilestoneDependencyProjection() {
            @Override
            public UUID getMilestoneId() {
                return dependent.getId();
            }

            @Override
            public UUID getDependsOnMilestoneId() {
                return dependsOn.getId();
            }
        };
    }

    @Nested
    class IsConflict {

        private final Board board = board();
        private final Milestone dependency = milestone(board, null, LocalDate.of(2026, 11, 15));

        @Test
        void startBeforeDependencyTarget_isConflict() {
            Milestone dependent = milestone(board, LocalDate.of(2026, 11, 14), LocalDate.of(2026, 12, 31));

            assertThat(helper.isConflict(dependent, dependency)).isTrue();
        }

        @Test
        void startOnDependencyTarget_isNoConflict() {
            Milestone dependent = milestone(board, LocalDate.of(2026, 11, 15), LocalDate.of(2026, 12, 31));

            assertThat(helper.isConflict(dependent, dependency)).isFalse();
        }

        @Test
        void noStart_targetBeforeDependencyTarget_isConflict() {
            Milestone dependent = milestone(board, null, LocalDate.of(2026, 11, 1));

            assertThat(helper.isConflict(dependent, dependency)).isTrue();
        }

        @Test
        void noStart_targetAfterDependencyTarget_isNoConflict() {
            Milestone dependent = milestone(board, null, LocalDate.of(2026, 12, 1));

            assertThat(helper.isConflict(dependent, dependency)).isFalse();
        }
    }

    @Nested
    class DependsOn {

        @Test
        void toListResponses_dependencyOnInvisibleBoard_isLeftOut() {
            Board visibleBoard = board();
            Board hiddenBoard = board();
            Milestone dependent = milestone(visibleBoard, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1));
            Milestone visible = milestone(visibleBoard, null, LocalDate.of(2026, 11, 20));
            Milestone hidden = milestone(hiddenBoard, null, LocalDate.of(2026, 10, 1));
            when(dependencyRepository.findLiveDependencies(anyCollection()))
                    .thenReturn(List.of(edge(dependent, visible), edge(dependent, hidden)));
            when(milestoneRepository.findAllById(anyCollection())).thenReturn(List.of(visible, hidden));

            List<MilestoneListResponse> result = helper.toListResponses(List.of(dependent), List.of(visibleBoard.getId()));

            assertThat(result).hasSize(1);
            List<MilestoneDependencyResponse> dependsOn = result.get(0).dependsOn();
            assertThat(dependsOn).hasSize(1);
            assertThat(dependsOn.get(0).milestoneId()).isEqualTo(visible.getId());
            assertThat(dependsOn.get(0).conflict()).isTrue();
        }

        @Test
        void toDetailResponse_noDependencies_returnsEmptyListWithoutMilestoneLookup() {
            Board board = board();
            Milestone dependent = milestone(board, null, LocalDate.of(2026, 12, 1));
            when(dependencyRepository.findLiveDependencies(anyCollection())).thenReturn(List.of());

            MilestoneDetailResponse result = helper.toDetailResponse(dependent, List.of(board.getId()));

            assertThat(result.dependsOn()).isEmpty();
            verify(milestoneRepository, never()).findAllById(anyCollection());
        }
    }

    @Nested
    class HealthOnTheUtcDay {

        private MilestoneHelper helperAt(String instant) {
            return new MilestoneHelper(ticketRepository, milestoneRepository, dependencyRepository,
                    new MilestoneMapperImpl(), new MilestoneHealthResolver(),
                    Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
        }

        private MilestoneHealth healthAt(String instant) {
            Board board = board();
            Milestone milestone = milestone(board, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 3));
            when(dependencyRepository.findLiveDependencies(anyCollection())).thenReturn(List.of());

            return helperAt(instant).toDetailResponse(milestone, List.of(board.getId())).health();
        }

        @Test
        void targetDayStillRunningInUtc_isNotOverdueAt2130Utc() {
            assertThat(healthAt("2026-11-03T21:30:00Z")).isNotEqualTo(MilestoneHealth.OVERDUE);
        }

        @Test
        void dayAfterTarget_isOverdueAt0030Utc() {
            assertThat(healthAt("2026-11-04T00:30:00Z")).isEqualTo(MilestoneHealth.OVERDUE);
        }

        @Test
        void toListResponses_followsTheSameUtcDay() {
            Board board = board();
            Milestone milestone = milestone(board, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 3));
            when(dependencyRepository.findLiveDependencies(anyCollection())).thenReturn(List.of());

            List<MilestoneListResponse> before = helperAt("2026-11-03T21:30:00Z")
                    .toListResponses(List.of(milestone), List.of(board.getId()));
            List<MilestoneListResponse> after = helperAt("2026-11-04T00:30:00Z")
                    .toListResponses(List.of(milestone), List.of(board.getId()));

            assertThat(before.get(0).health()).isNotEqualTo(MilestoneHealth.OVERDUE);
            assertThat(after.get(0).health()).isEqualTo(MilestoneHealth.OVERDUE);
        }
    }
}
