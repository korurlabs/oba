package com.korur.oba.apps.kaizen.milestone.validator;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.milestone.entity.Milestone;
import com.korur.oba.apps.kaizen.milestone.enums.MilestoneStatus;
import com.korur.oba.apps.kaizen.milestone.repository.MilestoneDependencyRepository;
import com.korur.oba.apps.kaizen.milestone.repository.MilestoneRepository;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;

class MilestoneValidatorUnitTest {

    private final MilestoneRepository repository = mock(MilestoneRepository.class);

    private final MilestoneDependencyRepository dependencyRepository = mock(MilestoneDependencyRepository.class);

    private final MilestoneValidator validator =
            new MilestoneValidator(repository, dependencyRepository, new LoggingService());

    private static Board board(String name, Board parent) {
        Board board = Board.builder().name(name).prefix(name).parentBoard(parent).build();
        board.setId(UUID.randomUUID());
        return board;
    }

    @Nested
    class IsBoardRelated {

        @Test
        void sameBoard_isRelated() {
            Board board = board("A", null);

            assertThat(validator.isBoardRelated(board, board)).isTrue();
        }

        @Test
        void ticketOnChildOfMilestoneBoard_isRelated() {
            Board parent = board("P", null);
            Board child = board("C", parent);

            assertThat(validator.isBoardRelated(parent, child)).isTrue();
        }

        @Test
        void ticketOnParentOfMilestoneBoard_isRelated() {
            Board parent = board("P", null);
            Board child = board("C", parent);

            assertThat(validator.isBoardRelated(child, parent)).isTrue();
        }

        @Test
        void siblings_areRelated() {
            Board parent = board("P", null);

            assertThat(validator.isBoardRelated(board("A", parent), board("B", parent))).isTrue();
        }

        @Test
        void unrelatedTopLevelBoards_areNotRelated() {
            assertThat(validator.isBoardRelated(board("A", null), board("B", null))).isFalse();
        }

        @Test
        void grandchild_isNotRelated() {
            Board root = board("R", null);
            Board child = board("C", root);
            Board grandchild = board("G", child);

            assertThat(validator.isBoardRelated(root, grandchild)).isFalse();
        }
    }

    @Nested
    class EnsureMilestoneLinkableToBoard {

        @Test
        void unrelatedBoard_throwsNotLinkable() {
            Milestone milestone = Milestone.builder().name("M").board(board("A", null))
                    .targetDate(LocalDate.of(2026, 12, 1)).build();
            milestone.setId(UUID.randomUUID());

            assertThatThrownBy(() -> validator.ensureMilestoneLinkableToBoard(milestone, board("B", null)))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class EnsureStartNotAfterTarget {

        @Test
        void startAfterTarget_throws() {
            LocalDate target = LocalDate.of(2026, 12, 1);

            assertThatThrownBy(() -> validator.ensureStartNotAfterTarget(target.plusDays(1), target))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void startEqualToTargetOrMissing_passes() {
            LocalDate target = LocalDate.of(2026, 12, 1);

            validator.ensureStartNotAfterTarget(target, target);
            validator.ensureStartNotAfterTarget(null, target);
        }
    }

    @Nested
    class EnsureMilestoneExistsOnBoards {

        @Test
        void milestoneOnAllowedBoard_returnsMilestone() {
            Board board = board("A", null);
            Milestone milestone = Milestone.builder().name("M").board(board).build();
            UUID id = UUID.randomUUID();
            when(repository.findById(id)).thenReturn(Optional.of(milestone));

            assertThat(validator.ensureMilestoneExistsOnBoards(id, List.of(board.getId()))).isSameAs(milestone);
        }

        @Test
        void milestoneOnBoardOutsideList_throwsNotFound() {
            Milestone milestone = Milestone.builder().name("M").board(board("A", null)).build();
            UUID id = UUID.randomUUID();
            when(repository.findById(id)).thenReturn(Optional.of(milestone));

            assertThatThrownBy(() -> validator.ensureMilestoneExistsOnBoards(id, List.of(UUID.randomUUID())))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void milestoneMissing_throwsNotFound() {
            UUID id = UUID.randomUUID();
            when(repository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> validator.ensureMilestoneExistsOnBoards(id, List.of(UUID.randomUUID())))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class EnsureMilestoneExists {

        @Test
        void notOnBoard_throwsNotFound() {
            UUID boardId = UUID.randomUUID();
            UUID id = UUID.randomUUID();
            when(repository.findByBoard_IdAndId(boardId, id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> validator.ensureMilestoneExists(boardId, id))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class StatusGuards {

        private Milestone withStatus(MilestoneStatus status) {
            Milestone milestone = Milestone.builder().name("M").status(status).build();
            milestone.setId(UUID.randomUUID());
            return milestone;
        }

        @Test
        void closeAlreadyClosed_throwsConflict() {
            assertThatThrownBy(() -> validator.ensureMilestoneIsOpen(withStatus(MilestoneStatus.CLOSED)))
                    .isInstanceOf(ConflictException.class);
            validator.ensureMilestoneIsOpen(withStatus(MilestoneStatus.OPEN));
        }

        @Test
        void reopenAlreadyOpen_throwsConflict() {
            assertThatThrownBy(() -> validator.ensureMilestoneIsClosed(withStatus(MilestoneStatus.OPEN)))
                    .isInstanceOf(ConflictException.class);
            validator.ensureMilestoneIsClosed(withStatus(MilestoneStatus.CLOSED));
        }
    }

    @Nested
    class EnsureDependencyAllowed {

        // Existing edges "key depends on each value", served one level per query like the database.
        private final Map<UUID, List<UUID>> edges = new HashMap<>();

        private Milestone milestone(Board board) {
            Milestone milestone = Milestone.builder().name("M").board(board)
                    .targetDate(LocalDate.of(2026, 12, 1)).build();
            milestone.setId(UUID.randomUUID());
            return milestone;
        }

        private void dependsOn(Milestone dependent, Milestone dependency) {
            edges.computeIfAbsent(dependent.getId(), id -> new java.util.ArrayList<>()).add(dependency.getId());
        }

        private void serveEdges() {
            when(dependencyRepository.findDependsOnIdsByMilestoneIds(anyCollection())).thenAnswer(call -> {
                Collection<UUID> ids = call.getArgument(0);
                return ids.stream()
                        .flatMap(id -> edges.getOrDefault(id, List.of()).stream())
                        .distinct()
                        .toList();
            });
        }

        @Test
        void self_throwsVal033() {
            Milestone a = milestone(board("A", null));

            assertThatThrownBy(() -> validator.ensureDependencyAllowed(a, a))
                    .isInstanceOf(ValidationException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "KAI_VAL_033");
        }

        @Test
        void unrelatedBoards_throwsVal035() {
            Milestone a = milestone(board("A", null));
            Milestone b = milestone(board("B", null));

            assertThatThrownBy(() -> validator.ensureDependencyAllowed(a, b))
                    .isInstanceOf(ValidationException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "KAI_VAL_035");
        }

        @Test
        void siblingBoards_withoutCycle_isAllowed() {
            Board parent = board("P", null);
            Milestone a = milestone(board("A", parent));
            Milestone b = milestone(board("B", parent));
            serveEdges();

            assertThatCode(() -> validator.ensureDependencyAllowed(a, b)).doesNotThrowAnyException();
        }

        @Test
        void directReverseDependency_throwsVal034() {
            Board board = board("A", null);
            Milestone a = milestone(board);
            Milestone b = milestone(board);
            dependsOn(a, b);
            serveEdges();

            // b depends on a would close a -> b -> a.
            assertThatThrownBy(() -> validator.ensureDependencyAllowed(b, a))
                    .isInstanceOf(ValidationException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "KAI_VAL_034");
        }

        @Test
        void threeDeepChain_closingEdge_throwsVal034() {
            Board board = board("A", null);
            Milestone a = milestone(board);
            Milestone b = milestone(board);
            Milestone c = milestone(board);
            dependsOn(a, b);
            dependsOn(b, c);
            serveEdges();

            // c depends on a would close a -> b -> c -> a.
            assertThatThrownBy(() -> validator.ensureDependencyAllowed(c, a))
                    .isInstanceOf(ValidationException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "KAI_VAL_034");
        }

        @Test
        void sameDirectionAsChain_isAllowed() {
            Board board = board("A", null);
            Milestone a = milestone(board);
            Milestone b = milestone(board);
            Milestone c = milestone(board);
            dependsOn(a, b);
            dependsOn(b, c);
            serveEdges();

            // a depends on c is a shortcut, not a cycle.
            assertThatCode(() -> validator.ensureDependencyAllowed(a, c)).doesNotThrowAnyException();
        }

        @Test
        void existingCycleElsewhere_walkTerminates() {
            Board board = board("A", null);
            Milestone a = milestone(board);
            Milestone x = milestone(board);
            Milestone y = milestone(board);
            Milestone target = milestone(board);
            dependsOn(x, y);
            dependsOn(y, x);
            serveEdges();

            // The walk from x loops x -> y -> x and never reaches a; it must stop.
            assertThatCode(() -> validator.ensureDependencyAllowed(a, x)).doesNotThrowAnyException();
            assertThatCode(() -> validator.ensureDependencyAllowed(target, a)).doesNotThrowAnyException();
        }
    }

    @Nested
    class EnsureDependencyExists {

        @Test
        void missingPair_throwsNot010() {
            UUID milestoneId = UUID.randomUUID();
            UUID dependsOnId = UUID.randomUUID();
            when(dependencyRepository.findByMilestoneIdAndDependsOnMilestoneId(milestoneId, dependsOnId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> validator.ensureDependencyExists(milestoneId, dependsOnId))
                    .isInstanceOf(NotFoundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "KAI_NOT_010");
        }
    }
}
