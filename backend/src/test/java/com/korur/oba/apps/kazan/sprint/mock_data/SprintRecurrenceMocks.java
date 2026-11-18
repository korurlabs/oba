package com.korur.oba.apps.kazan.sprint.mock_data;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.entity.BoardSprint;
import com.korur.oba.apps.kazan.sprint.entity.Sprint;
import com.korur.oba.apps.kazan.sprint.entity.SprintSeries;
import com.korur.oba.apps.kazan.sprint.entity.SprintSeriesBoard;
import com.korur.oba.apps.kazan.sprint.enums.SprintRecurrenceCreationMode;
import com.korur.oba.apps.kazan.sprint.enums.SprintRecurrenceTicketAction;
import com.korur.oba.apps.kazan.sprint.enums.SprintStatus;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.enums.TicketStatus;

/**
 * Builders for the sprint series and recurrence tests. Every date is a UTC midnight, as a
 * repeating sprint requires.
 */
public final class SprintRecurrenceMocks {

    private SprintRecurrenceMocks() {
        // Private constructor to prevent instantiation
    }

    public static Instant utc(String date) {
        return Instant.parse(date + "T00:00:00Z");
    }

    public static Board board(String name) {
        Board board = Board.builder().name(name).prefix(name.substring(0, 1)).build();
        board.setId(UUID.randomUUID());
        return board;
    }

    public static Sprint sprint(SprintStatus status, Instant startDate, Instant endDate) {
        Sprint sprint = Sprint.builder()
                .name("Sprint")
                .status(status)
                .startDate(startDate)
                .endDate(endDate)
                .build();
        sprint.setId(UUID.randomUUID());
        return sprint;
    }

    /**
     * Links the boards to the sprint in the order given.
     */
    public static Sprint linkedTo(Sprint sprint, Board... boards) {
        for (Board board : boards) {
            sprint.getBoardSprints().add(BoardSprint.builder().board(board).sprint(sprint).build());
        }
        return sprint;
    }

    /**
     * An enabled series named "Atasprint" whose next round is 2, with the
     * given settings and boards in position order.
     */
    public static SprintSeries series(
            SprintRecurrenceTicketAction action,
            SprintRecurrenceCreationMode mode,
            Integer createDaysBefore,
            int lengthDays,
            Board... boards) {
        SprintSeries series = SprintSeries.builder()
                .name("Atasprint")
                .nextRound(2)
                .enabled(true)
                .lengthDays(lengthDays)
                .unfinishedTicketAction(action)
                .creationMode(mode)
                .createDaysBefore(createDaysBefore)
                .build();
        series.setId(UUID.randomUUID());
        for (int position = 0; position < boards.length; position++) {
            series.getBoards().add(SprintSeriesBoard.builder()
                    .series(series)
                    .board(boards[position])
                    .position(position)
                    .build());
        }
        return series;
    }

    /**
     * An enabled seven-day series that moves unfinished tickets to the next
     * sprint and creates it at the end date.
     */
    public static SprintSeries atEndSeries(Board... boards) {
        return series(SprintRecurrenceTicketAction.MOVE_TO_NEXT_SPRINT,
                SprintRecurrenceCreationMode.AT_END, null, 7, boards);
    }

    /**
     * Makes the sprint the given round of the series, named after it.
     */
    public static Sprint inSeries(Sprint sprint, SprintSeries series, int round) {
        sprint.setSeries(series);
        sprint.setRound(round);
        sprint.setName(series.getName() + " " + round);
        return sprint;
    }

    public static Ticket ticket(Board board, Sprint sprint, TicketStatus status) {
        Ticket ticket = Ticket.builder().board(board).sprint(sprint).status(status).build();
        ticket.setId(UUID.randomUUID());
        return ticket;
    }
}
