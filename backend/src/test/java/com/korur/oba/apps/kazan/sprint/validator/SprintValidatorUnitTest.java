package com.korur.oba.apps.kazan.sprint.validator;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.entity.BoardSprint;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.sprint.constant.SprintConstant;
import com.korur.oba.apps.kazan.sprint.constant.SprintRecurrenceConstant;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintCompleteRequest;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintUpdateRequest;
import com.korur.oba.apps.kazan.sprint.entity.Sprint;
import com.korur.oba.apps.kazan.sprint.entity.SprintSeries;
import com.korur.oba.apps.kazan.sprint.entity.SprintSeriesBoard;
import com.korur.oba.apps.kazan.sprint.enums.SprintRecurrenceCreationMode;
import com.korur.oba.apps.kazan.sprint.enums.SprintStatus;
import com.korur.oba.apps.kazan.sprint.repository.SprintRepository;
import com.korur.oba.apps.kazan.ticket.enums.TicketRelocationAction;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class SprintValidatorUnitTest {

    @Mock
    private SprintRepository sprintRepository;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private SprintValidator sprintValidator;

    @Nested
    class EnsureSprintExists {

        @Test
        void sprintFound_returnsSprint() {
            // Arrange
            UUID sprintId = UUID.randomUUID();
            Sprint sprint = Sprint.builder().build();
            sprint.setId(sprintId);
            when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));

            // Act
            Sprint result = sprintValidator.ensureSprintExists(sprintId);

            // Assert
            assertEquals(sprint, result);
        }

        @Test
        void sprintNotFound_throwsNotFoundException() {
            // Arrange
            UUID sprintId = UUID.randomUUID();
            when(sprintRepository.findById(sprintId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(NotFoundException.class, () -> sprintValidator.ensureSprintExists(sprintId));
        }
    }

    @Nested
    class EnsureSprintCanBeLinked {

        @Test
        void boardAlreadyLinked_throwsValidationException() {
            // Arrange
            Board board = Board.builder().build();
            board.setId(UUID.randomUUID());

            Sprint sprint = Sprint.builder().build();
            sprint.setId(UUID.randomUUID());
            sprint.getBoardSprints().add(BoardSprint.builder().board(board).sprint(sprint).build());

            // Act & Assert
            assertThrows(ValidationException.class, () -> sprintValidator.ensureSprintCanBeLinked(sprint, board));
        }

        @Test
        void boardIsChildOfSprintBoard_doesNotThrow() {
            // Arrange
            Board board = Board.builder().build();
            board.setId(UUID.randomUUID());

            Board sprintBoard = Board.builder().build();
            sprintBoard.setId(UUID.randomUUID());
            sprintBoard.getChildBoards().add(board);

            Sprint sprint = Sprint.builder().build();
            sprint.setId(UUID.randomUUID());
            sprint.getBoardSprints().add(BoardSprint.builder().board(sprintBoard).sprint(sprint).build());

            // Act & Assert
            assertDoesNotThrow(() -> sprintValidator.ensureSprintCanBeLinked(sprint, board));
        }

        @Test
        void boardIsParentOfSprintBoard_doesNotThrow() {
            // Arrange
            Board parentBoard = Board.builder().build();
            parentBoard.setId(UUID.randomUUID());

            Board sprintBoard = Board.builder().build();
            sprintBoard.setId(UUID.randomUUID());
            sprintBoard.setParentBoard(parentBoard);

            Sprint sprint = Sprint.builder().build();
            sprint.setId(UUID.randomUUID());
            sprint.getBoardSprints().add(BoardSprint.builder().board(sprintBoard).sprint(sprint).build());

            // Act & Assert
            assertDoesNotThrow(() -> sprintValidator.ensureSprintCanBeLinked(sprint, parentBoard));
        }

        @Test
        void boardIsSiblingOfSprintBoard_doesNotThrow() {
            // Arrange
            Board sharedParent = Board.builder().build();
            sharedParent.setId(UUID.randomUUID());

            Board sprintBoard = Board.builder().build();
            sprintBoard.setId(UUID.randomUUID());
            sprintBoard.setParentBoard(sharedParent);

            Board board = Board.builder().build();
            board.setId(UUID.randomUUID());
            board.setParentBoard(sharedParent);

            Sprint sprint = Sprint.builder().build();
            sprint.setId(UUID.randomUUID());
            sprint.getBoardSprints().add(BoardSprint.builder().board(sprintBoard).sprint(sprint).build());

            // Act & Assert
            assertDoesNotThrow(() -> sprintValidator.ensureSprintCanBeLinked(sprint, board));
        }

        @Test
        void sprintHasNoBoards_doesNotThrow() {
            // Arrange
            Board board = Board.builder().build();
            board.setId(UUID.randomUUID());

            Sprint sprint = Sprint.builder().build();
            sprint.setId(UUID.randomUUID());
            // sprint.getBoards() is empty by default

            // Act & Assert
            assertDoesNotThrow(() -> sprintValidator.ensureSprintCanBeLinked(sprint, board));
        }

        @Test
        void boardIsUnrelated_throwsValidationException() {
            // Arrange
            // sprintBoard is root (no parent) and board is not its child
            Board sprintBoard = Board.builder().build();
            sprintBoard.setId(UUID.randomUUID());
            // parentBoard is null by default — sprintBoard is a root board

            Board board = Board.builder().build();
            board.setId(UUID.randomUUID());
            // board is not in sprintBoard.getChildBoards() and has no parent

            Sprint sprint = Sprint.builder().build();
            sprint.setId(UUID.randomUUID());
            sprint.getBoardSprints().add(BoardSprint.builder().board(sprintBoard).sprint(sprint).build());

            // Act & Assert
            assertThrows(ValidationException.class, () -> sprintValidator.ensureSprintCanBeLinked(sprint, board));
        }
    }

    @Nested
    class EnsureRecurrenceSprintNotCompleted {

        @Test
        void completedSprint_throwsKaiVal042() {
            Sprint sprint = recurrenceSprint(SprintStatus.COMPLETED, "2026-01-01", "2026-01-08");

            ValidationException exception = assertThrows(ValidationException.class,
                    () -> sprintValidator.ensureRecurrenceSprintNotCompleted(sprint));

            assertEquals("KAZ_VAL_042", exception.getErrorCode());
        }

        @Test
        void plannedSprint_doesNotThrow() {
            Sprint sprint = recurrenceSprint(SprintStatus.PLANNED, "2026-01-01", "2026-01-08");

            assertDoesNotThrow(() -> sprintValidator.ensureRecurrenceSprintNotCompleted(sprint));
        }

        @Test
        void activeSprint_doesNotThrow() {
            Sprint sprint = recurrenceSprint(SprintStatus.ACTIVE, "2026-01-01", "2026-01-08");

            assertDoesNotThrow(() -> sprintValidator.ensureRecurrenceSprintNotCompleted(sprint));
        }
    }

    @Nested
    class EnsureRecurrenceDatesValid {

        @Test
        void midnightDatesAndLengthSeven_doesNotThrow() {
            Sprint sprint = recurrenceSprint(SprintStatus.PLANNED, "2026-01-01", "2026-01-08");

            assertDoesNotThrow(() -> sprintValidator.ensureRecurrenceDatesValid(sprint, 7));
        }

        @Test
        void lengthOne_doesNotThrow() {
            Sprint sprint = recurrenceSprint(SprintStatus.PLANNED, "2026-01-01", "2026-01-02");

            assertDoesNotThrow(() -> sprintValidator.ensureRecurrenceDatesValid(sprint, 1));
        }

        @Test
        void length365_doesNotThrow() {
            Sprint sprint = recurrenceSprint(SprintStatus.PLANNED, "2026-01-01", "2027-01-01");

            assertDoesNotThrow(() -> sprintValidator.ensureRecurrenceDatesValid(sprint, 365));
        }

        @Test
        void missingStartDate_throwsKaiVal040() {
            Sprint sprint = recurrenceSprint(SprintStatus.PLANNED, null, "2026-01-08");

            assertKaiVal040(sprint, null);
        }

        @Test
        void missingEndDate_throwsKaiVal040() {
            Sprint sprint = recurrenceSprint(SprintStatus.PLANNED, "2026-01-01", null);

            assertKaiVal040(sprint, null);
        }

        @Test
        void startNotAtMidnightUtc_throwsKaiVal040() {
            Sprint sprint = recurrenceSprint(SprintStatus.PLANNED, "2026-01-01", "2026-01-08");
            sprint.setStartDate(Instant.parse("2026-01-01T09:30:00Z"));

            assertKaiVal040(sprint, 6);
        }

        @Test
        void endNotAtMidnightUtc_throwsKaiVal040() {
            Sprint sprint = recurrenceSprint(SprintStatus.PLANNED, "2026-01-01", "2026-01-08");
            sprint.setEndDate(Instant.parse("2026-01-08T00:00:01Z"));

            assertKaiVal040(sprint, 7);
        }

        @Test
        void lengthZero_throwsKaiVal040() {
            Sprint sprint = recurrenceSprint(SprintStatus.PLANNED, "2026-01-01", "2026-01-01");

            assertKaiVal040(sprint, 0);
        }

        @Test
        void length366_throwsKaiVal040() {
            Sprint sprint = recurrenceSprint(SprintStatus.PLANNED, "2026-01-01", "2027-01-02");

            assertKaiVal040(sprint, 366);
        }

        @Test
        void negativeLength_throwsKaiVal040() {
            Sprint sprint = recurrenceSprint(SprintStatus.PLANNED, "2026-01-08", "2026-01-01");

            assertKaiVal040(sprint, -7);
        }

        private void assertKaiVal040(Sprint sprint, Integer lengthDays) {
            ValidationException exception = assertThrows(ValidationException.class,
                    () -> sprintValidator.ensureRecurrenceDatesValid(sprint, lengthDays));

            assertEquals("KAZ_VAL_040", exception.getErrorCode());
        }
    }

    @Nested
    class EnsureRecurrenceDaysBeforeValid {

        private final Sprint sprint = recurrenceSprint(SprintStatus.PLANNED, "2026-01-01", "2026-01-08");

        @Test
        void atEndMode_ignoresDaysBefore() {
            assertDoesNotThrow(() -> sprintValidator.ensureRecurrenceDaysBeforeValid(
                    sprint, SprintRecurrenceCreationMode.AT_END, null, 7));
            assertDoesNotThrow(() -> sprintValidator.ensureRecurrenceDaysBeforeValid(
                    sprint, SprintRecurrenceCreationMode.AT_END, 0, 7));
        }

        @Test
        void daysBeforeOne_doesNotThrow() {
            assertDoesNotThrow(() -> sprintValidator.ensureRecurrenceDaysBeforeValid(
                    sprint, SprintRecurrenceCreationMode.DAYS_BEFORE, 1, 7));
        }

        @Test
        void daysBeforeLengthMinusOne_doesNotThrow() {
            assertDoesNotThrow(() -> sprintValidator.ensureRecurrenceDaysBeforeValid(
                    sprint, SprintRecurrenceCreationMode.DAYS_BEFORE, 6, 7));
        }

        @Test
        void daysBeforeMissing_throwsKaiVal041() {
            assertKaiVal041(null);
        }

        @Test
        void daysBeforeZero_throwsKaiVal041() {
            assertKaiVal041(0);
        }

        @Test
        void daysBeforeEqualsLength_throwsKaiVal041() {
            assertKaiVal041(7);
        }

        @Test
        void daysBeforeAboveLength_throwsKaiVal041() {
            assertKaiVal041(30);
        }

        @Test
        void lengthMissing_throwsKaiVal041() {
            ValidationException exception = assertThrows(ValidationException.class,
                    () -> sprintValidator.ensureRecurrenceDaysBeforeValid(
                            sprint, SprintRecurrenceCreationMode.DAYS_BEFORE, 2, null));

            assertEquals("KAZ_VAL_041", exception.getErrorCode());
        }

        private void assertKaiVal041(Integer daysBefore) {
            ValidationException exception = assertThrows(ValidationException.class,
                    () -> sprintValidator.ensureRecurrenceDaysBeforeValid(
                            sprint, SprintRecurrenceCreationMode.DAYS_BEFORE, daysBefore, 7));

            assertEquals("KAZ_VAL_041", exception.getErrorCode());
        }
    }

    private static Sprint recurrenceSprint(SprintStatus status, String startDate, String endDate) {
        Sprint sprint = Sprint.builder()
                .status(status)
                .startDate(startDate == null ? null : Instant.parse(startDate + "T00:00:00Z"))
                .endDate(endDate == null ? null : Instant.parse(endDate + "T00:00:00Z"))
                .build();
        sprint.setId(UUID.randomUUID());
        return sprint;
    }

    @Nested
    class EnsureBoardIsDescendant {

        private Board board(Board parent) {
            Board board = Board.builder().name("Board").prefix("B").parentBoard(parent).build();
            board.setId(UUID.randomUUID());
            return board;
        }

        @Test
        void directChild_doesNotThrow() {
            Board root = board(null);
            Board child = board(root);

            assertDoesNotThrow(() -> sprintValidator.ensureBoardIsDescendant(root, child));
        }

        @Test
        void grandchild_doesNotThrow() {
            Board root = board(null);
            Board grandchild = board(board(root));

            assertDoesNotThrow(() -> sprintValidator.ensureBoardIsDescendant(root, grandchild));
        }

        @Test
        void sibling_throwsValidationException() {
            Board parent = board(null);
            Board root = board(parent);
            Board sibling = board(parent);

            ValidationException exception = assertThrows(ValidationException.class,
                    () -> sprintValidator.ensureBoardIsDescendant(root, sibling));

            assertEquals("KAZ_VAL_008", exception.getErrorCode());
            assertEquals(root.getId(), exception.getErrorValues().get("rootBoardId"));
            assertEquals(sibling.getId(), exception.getErrorValues().get("boardId"));
        }

        @Test
        void rootItself_throwsValidationException() {
            Board root = board(board(null));

            assertThrows(ValidationException.class, () -> sprintValidator.ensureBoardIsDescendant(root, root));
        }

        @Test
        void ancestorOfRoot_throwsValidationException() {
            Board ancestor = board(null);
            Board root = board(ancestor);

            assertThrows(ValidationException.class, () -> sprintValidator.ensureBoardIsDescendant(root, ancestor));
        }

        @Test
        void candidateWithoutParent_throwsValidationException() {
            Board root = board(null);
            Board unrelated = board(null);

            assertThrows(ValidationException.class, () -> sprintValidator.ensureBoardIsDescendant(root, unrelated));
        }

        @Test
        void cyclicHierarchyWithoutRoot_throwsValidationExceptionInsteadOfLooping() {
            Board root = board(null);
            Board first = board(null);
            Board second = board(first);
            first.setParentBoard(second);

            assertThrows(ValidationException.class, () -> sprintValidator.ensureBoardIsDescendant(root, first));
        }
    }

    @Nested
    class EnsureNoBlockingBoards {

        private Board board(String name) {
            Board board = Board.builder().name(name).prefix(name.substring(0, 1)).build();
            board.setId(UUID.randomUUID());
            return board;
        }

        @Test
        void noBlockingBoards_doesNotThrow() {
            assertDoesNotThrow(() -> sprintValidator.ensureNoBlockingBoards(UUID.randomUUID(), List.of()));
        }

        @Test
        void blockingBoards_throwsConflictNamingEveryBoard() {
            UUID sprintId = UUID.randomUUID();

            ConflictException exception = assertThrows(ConflictException.class,
                    () -> sprintValidator.ensureNoBlockingBoards(sprintId, List.of(board("Alpha"), board("Beta"))));

            assertEquals("KAZ_STA_004", exception.getErrorCode());
            assertEquals("Alpha, Beta", exception.getErrorValues().get("boardNames"));
            assertEquals(sprintId, exception.getErrorValues().get("sprintId"));
        }

        @Test
        void singleBlockingBoard_namesItWithoutSeparator() {
            ConflictException exception = assertThrows(ConflictException.class,
                    () -> sprintValidator.ensureNoBlockingBoards(UUID.randomUUID(), List.of(board("Alpha"))));

            assertEquals("Alpha", exception.getErrorValues().get("boardNames"));
        }
    }

    @Nested
    class EnsureMoveToNextSprintAllowed {

        private Sprint seriesSprint(boolean enabled) {
            SprintSeries series = SprintSeries.builder().name("Atasprint").nextRound(3).enabled(enabled).lengthDays(14).build();
            series.setId(UUID.randomUUID());
            Sprint sprint = Sprint.builder().name("Atasprint 2").series(series).round(2).status(SprintStatus.ACTIVE).build();
            sprint.setId(UUID.randomUUID());
            return sprint;
        }

        private SprintCompleteRequest request(TicketRelocationAction action, UUID targetSprintId, UUID targetBoardId) {
            return new SprintCompleteRequest(action, targetSprintId, targetBoardId, true);
        }

        private void assertRefused(Sprint sprint, SprintCompleteRequest request, boolean liveBoard) {
            ValidationException exception = assertThrows(ValidationException.class, () ->
                    sprintValidator.ensureMoveToNextSprintAllowed(sprint, request, liveBoard));
            assertEquals("KAZ_VAL_061", exception.getErrorCode());
        }

        @Test
        void enabledSeriesWithLiveBoard_passes() {
            assertDoesNotThrow(() -> sprintValidator.ensureMoveToNextSprintAllowed(seriesSprint(true),
                    request(TicketRelocationAction.MOVE_TO_SPRINT, null, null), true));
        }

        @Test
        void withoutMoveToNextSprint_passesForAnySprint() {
            Sprint loose = Sprint.builder().name("Loose").build();
            assertDoesNotThrow(() -> sprintValidator.ensureMoveToNextSprintAllowed(loose,
                    new SprintCompleteRequest(TicketRelocationAction.MOVE_TO_BACKLOG, null, null, null), false));
            assertDoesNotThrow(() -> sprintValidator.ensureMoveToNextSprintAllowed(loose,
                    new SprintCompleteRequest(TicketRelocationAction.MOVE_TO_BACKLOG, null, null, false), false));
        }

        @Test
        void sprintOutsideSeries_isRefused() {
            Sprint loose = Sprint.builder().name("Loose").build();
            loose.setId(UUID.randomUUID());
            assertRefused(loose, request(TicketRelocationAction.MOVE_TO_SPRINT, null, null), false);
        }

        @Test
        void disabledSeries_isRefused() {
            assertRefused(seriesSprint(false), request(TicketRelocationAction.MOVE_TO_SPRINT, null, null), true);
        }

        @Test
        void otherTicketAction_isRefused() {
            assertRefused(seriesSprint(true), request(TicketRelocationAction.MOVE_TO_BACKLOG, null, null), true);
            assertRefused(seriesSprint(true), request(TicketRelocationAction.DELETE, null, null), true);
        }

        @Test
        void targetSprintOrBoard_isRefused() {
            assertRefused(seriesSprint(true),
                    request(TicketRelocationAction.MOVE_TO_SPRINT, UUID.randomUUID(), null), true);
            assertRefused(seriesSprint(true),
                    request(TicketRelocationAction.MOVE_TO_SPRINT, null, UUID.randomUUID()), true);
        }

        @Test
        void noLiveSeriesBoard_isRefused() {
            assertRefused(seriesSprint(true), request(TicketRelocationAction.MOVE_TO_SPRINT, null, null), false);
        }
    }

    @Nested
    class EnsureSeriesNameValid {

        private void assertKaiVal060(String name) {
            ValidationException exception = assertThrows(ValidationException.class,
                    () -> sprintValidator.ensureSeriesNameValid(name, UUID.randomUUID()));

            assertEquals("KAZ_VAL_060", exception.getErrorCode());
        }

        @Test
        void name240Characters_doesNotThrow() {
            assertDoesNotThrow(() -> sprintValidator.ensureSeriesNameValid("a".repeat(240), UUID.randomUUID()));
        }

        @Test
        void name241Characters_throwsKaiVal060() {
            assertKaiVal060("a".repeat(241));
        }

        @Test
        void longNameWithSurroundingBlanks_isMeasuredTrimmed() {
            assertDoesNotThrow(() -> sprintValidator.ensureSeriesNameValid(" " + "a".repeat(240) + " ", null));
        }

        @Test
        void blankName_throwsKaiVal060() {
            assertKaiVal060("   ");
        }

        @Test
        void nullName_throwsKaiVal060() {
            assertKaiVal060(null);
        }

        @Test
        void unsavedSprint_stillThrowsKaiVal060() {
            ValidationException exception = assertThrows(ValidationException.class,
                    () -> sprintValidator.ensureSeriesNameValid("", null));

            assertEquals("KAZ_VAL_060", exception.getErrorCode());
        }
    }

    @Nested
    class EnsureSeriesNaming {

        private Sprint sprint(boolean inSeries, boolean enabled, SprintStatus status) {
            Sprint sprint = Sprint.builder().name("Atasprint 2").status(status).build();
            sprint.setId(UUID.randomUUID());
            if (inSeries) {
                SprintSeries series = SprintSeries.builder().name("Atasprint").nextRound(3).enabled(enabled)
                        .lengthDays(14).build();
                series.setId(UUID.randomUUID());
                sprint.setSeries(series);
                sprint.setRound(2);
            }
            return sprint;
        }

        private SprintUpdateRequest request(String name, String seriesName) {
            return new SprintUpdateRequest(name, null, null, null, seriesName);
        }

        private void assertRefused(Sprint sprint, SprintUpdateRequest request) {
            ValidationException exception = assertThrows(ValidationException.class,
                    () -> sprintValidator.ensureSeriesNaming(sprint, request));

            assertEquals("KAZ_VAL_060", exception.getErrorCode());
        }

        @Test
        void enabledSeriesWithSeriesName_doesNotThrow() {
            assertDoesNotThrow(() -> sprintValidator.ensureSeriesNaming(
                    sprint(true, true, SprintStatus.PLANNED), request(null, "Korursprint")));
        }

        @Test
        void enabledSeriesWithoutNames_doesNotThrow() {
            assertDoesNotThrow(() -> sprintValidator.ensureSeriesNaming(
                    sprint(true, true, SprintStatus.ACTIVE), request(null, null)));
        }

        @Test
        void enabledSeriesWithName_throwsKaiVal060() {
            assertRefused(sprint(true, true, SprintStatus.ACTIVE), request("Own name", null));
        }

        @Test
        void enabledSeriesCompletedSprintWithSeriesName_throwsKaiVal060() {
            assertRefused(sprint(true, true, SprintStatus.COMPLETED), request(null, "Korursprint"));
        }

        @Test
        void enabledSeriesBlankSeriesName_throwsKaiVal060() {
            assertRefused(sprint(true, true, SprintStatus.PLANNED), request(null, "  "));
        }

        @Test
        void enabledSeriesTooLongSeriesName_throwsKaiVal060() {
            assertRefused(sprint(true, true, SprintStatus.PLANNED), request(null, "a".repeat(241)));
        }

        @Test
        void sprintWithoutSeriesWithSeriesName_throwsKaiVal060() {
            assertRefused(sprint(false, false, SprintStatus.PLANNED), request(null, "Korursprint"));
        }

        @Test
        void sprintWithoutSeriesWithName_doesNotThrow() {
            assertDoesNotThrow(() -> sprintValidator.ensureSeriesNaming(
                    sprint(false, false, SprintStatus.PLANNED), request("Release prep", null)));
        }

        @Test
        void disabledSeriesWithName_doesNotThrow() {
            assertDoesNotThrow(() -> sprintValidator.ensureSeriesNaming(
                    sprint(true, false, SprintStatus.PLANNED), request("Release prep", null)));
        }

        @Test
        void disabledSeriesWithSeriesName_throwsKaiVal060() {
            assertRefused(sprint(true, false, SprintStatus.PLANNED), request(null, "Korursprint"));
        }
    }

    @Nested
    class OrganizationScope {

        private Sprint sprintWithId() {
            Sprint sprint = Sprint.builder().build();
            sprint.setId(UUID.randomUUID());
            return sprint;
        }

        @Test
        void ensureSprintInOrganization_linkedOnlyToTheOrganization_doesNotThrow() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            Sprint sprint = sprintWithId();
            when(sprintRepository.isLinkedOnlyToOrganization(sprint.getId(), organizationId)).thenReturn(true);

            // Act & Assert
            assertDoesNotThrow(() -> sprintValidator.ensureSprintInOrganization(sprint, organizationId));
        }

        @Test
        void ensureSprintInOrganization_linkedToAnotherOrganizationOrNotLinked_throwsNotFound() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            Sprint sprint = sprintWithId();
            when(sprintRepository.isLinkedOnlyToOrganization(sprint.getId(), organizationId)).thenReturn(false);

            // Act & Assert
            NotFoundException exception = assertThrows(NotFoundException.class,
                    () -> sprintValidator.ensureSprintInOrganization(sprint, organizationId));
            assertEquals(ExceptionDomainKazanError.NOT_SPRINT_FOUND.getCode(), exception.getErrorCode());
        }

        @Test
        void ensureSprintExistsInOrganization_sprintOfTheOrganization_returnsSprint() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            Sprint sprint = sprintWithId();
            when(sprintRepository.findById(sprint.getId())).thenReturn(Optional.of(sprint));
            when(sprintRepository.isLinkedOnlyToOrganization(sprint.getId(), organizationId)).thenReturn(true);

            // Act
            Sprint result = sprintValidator.ensureSprintExistsInOrganization(organizationId, sprint.getId());

            // Assert
            assertEquals(sprint, result);
        }

        @Test
        void ensureSprintExistsInOrganization_sprintOfAnotherOrganization_throwsNotFound() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            Sprint sprint = sprintWithId();
            when(sprintRepository.findById(sprint.getId())).thenReturn(Optional.of(sprint));
            when(sprintRepository.isLinkedOnlyToOrganization(sprint.getId(), organizationId)).thenReturn(false);

            // Act & Assert
            assertThrows(NotFoundException.class,
                    () -> sprintValidator.ensureSprintExistsInOrganization(organizationId, sprint.getId()));
        }

        @Test
        void ensureSprintExistsInOrganization_unknownSprint_throwsNotFoundWithoutOrganizationLookup() {
            // Arrange
            UUID sprintId = UUID.randomUUID();
            when(sprintRepository.findById(sprintId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(NotFoundException.class,
                    () -> sprintValidator.ensureSprintExistsInOrganization(UUID.randomUUID(), sprintId));
        }

        @Test
        void ensureSprintWasOnBoard_linkExistsEvenDeleted_doesNotThrow() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            Sprint sprint = sprintWithId();
            when(sprintRepository.existsLinkWithDeleted(sprint.getId(), boardId)).thenReturn(true);

            // Act & Assert
            assertDoesNotThrow(() -> sprintValidator.ensureSprintWasOnBoard(sprint, boardId));
        }

        @Test
        void ensureSprintWasOnBoard_neverLinked_throwsNotFound() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            Sprint sprint = sprintWithId();
            when(sprintRepository.existsLinkWithDeleted(sprint.getId(), boardId)).thenReturn(false);

            // Act & Assert
            NotFoundException exception = assertThrows(NotFoundException.class,
                    () -> sprintValidator.ensureSprintWasOnBoard(sprint, boardId));
            assertEquals(ExceptionDomainKazanError.NOT_SPRINT_FOUND.getCode(), exception.getErrorCode());
        }
    }

    @Nested
    class EnsureBoardNotInOtherEnabledSeries {

        private final Board board = boardNamed("Fabrikam");

        private Board boardNamed(String name) {
            Board created = Board.builder().name(name).build();
            created.setId(UUID.randomUUID());
            return created;
        }

        private SprintSeries seriesNamed(String name, boolean enabled) {
            SprintSeries created = SprintSeries.builder().name(name).enabled(enabled).build();
            created.setId(UUID.randomUUID());
            return created;
        }

        private Optional<SprintSeriesBoard> rowIn(SprintSeries owner) {
            return Optional.of(SprintSeriesBoard.builder().series(owner).board(board).build());
        }

        @Test
        void boardInNoSeries_doesNotThrow() {
            assertDoesNotThrow(() -> sprintValidator.ensureBoardNotInOtherEnabledSeries(
                    board, seriesNamed("Betasprint", true), Optional.empty()));
        }

        @Test
        void boardInTheSameSeries_doesNotThrow() {
            SprintSeries series = seriesNamed("Atasprint", true);

            assertDoesNotThrow(() -> sprintValidator.ensureBoardNotInOtherEnabledSeries(
                    board, series, rowIn(series)));
        }

        @Test
        void boardInAnotherDisabledSeries_doesNotThrow() {
            assertDoesNotThrow(() -> sprintValidator.ensureBoardNotInOtherEnabledSeries(
                    board, seriesNamed("Betasprint", true), rowIn(seriesNamed("Atasprint", false))));
        }

        @Test
        void boardInAnotherEnabledSeries_throwsKaiSta018NamingIt() {
            // Arrange
            SprintSeries owner = seriesNamed("Atasprint", true);
            SprintSeries unsaved = SprintSeries.builder().name("Betasprint").build();

            // Act
            ConflictException exception = assertThrows(ConflictException.class,
                    () -> sprintValidator.ensureBoardNotInOtherEnabledSeries(board, unsaved, rowIn(owner)));

            // Assert
            assertEquals(ExceptionDomainKazanError.STA_BOARD_IN_OTHER_SPRINT_SERIES.getCode(),
                    exception.getErrorCode());
            assertEquals(board.getId(), exception.getErrorValues().get(SprintConstant.FIELD_BOARD_ID));
            assertEquals("Fabrikam", exception.getErrorValues().get(SprintConstant.FIELD_BOARD_NAME));
            assertEquals(owner.getId(), exception.getErrorValues().get(SprintRecurrenceConstant.FIELD_SERIES_ID));
            assertEquals("Atasprint", exception.getErrorValues().get(SprintRecurrenceConstant.FIELD_SERIES_NAME));
        }
    }
}
