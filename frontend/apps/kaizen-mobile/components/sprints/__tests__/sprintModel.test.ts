import { describe, expect, it } from "bun:test";

import {
  automationSchedule,
  boardConflicts,
  canTakeOver,
  completeBody,
  completeChoices,
  completeTargets,
  completionCounts,
  createSprintBody,
  createdSprintName,
  defaultCompleteChoice,
  defaultPickedSubTickets,
  defaultRecurrence,
  editFieldsOf,
  editSaveSteps,
  editSprintValues,
  isUtcMidnight,
  nextSprintName,
  parseCount,
  preselectedChildBoardIds,
  recurrenceOf,
  recurrenceProblems,
  selectedConflictBoards,
  seriesBody,
  seriesRunMessage,
  splitSeriesName,
  sprintDayInstant,
  tooManyChildBoards,
  sprintFieldProblems,
  stoppingSeries,
  defaultMoveTarget,
  sprintMoveTargets,
  subTicketMove,
  takeoverConflicts,
  updateSprintBody,
  type RecurrenceForm,
  type TakeoverConflict,
} from "@/components/sprints/sprintModel";
import { BACKLOG_PLACEMENT } from "@/components/ticket/newTicketModel";
import type { TranslationKey } from "@/i18n";
import { parseDateOnly } from "@/lib/dates";
import type { Sprint } from "@/lib/schemas/boards";
import type { SprintChildBoard, SprintDetail, SprintSeries } from "@/lib/schemas/sprints";

function series(patch: Partial<SprintSeries> = {}): SprintSeries {
  return {
    id: "series-1",
    name: "Sprint",
    nextRound: 8,
    nextSprintName: "Sprint 8",
    enabled: true,
    lengthDays: 14,
    unfinishedTicketAction: "MOVE_TO_NEXT_SPRINT",
    creationMode: "AT_END",
    createDaysBefore: null,
    nextRolloverAt: "2026-11-20T00:00:00Z",
    nextCreationAt: null,
    lastRunAt: null,
    lastRunStatus: null,
    lastRunMessage: null,
    ...patch,
  };
}

function detail(patch: Partial<SprintDetail> = {}): SprintDetail {
  return {
    id: "sprint-7",
    name: "Sprint 7",
    goal: "Ship",
    startDate: "2026-11-06T00:00:00Z",
    endDate: "2026-11-20T00:00:00Z",
    status: "ACTIVE",
    round: 7,
    series: series(),
    ...patch,
  };
}

function sprint(id: string, status: string, startDate: string | null = null): Sprint {
  return { id, name: id, status, startDate, endDate: null };
}

function child(patch: Partial<SprintChildBoard> & { id: string }): SprintChildBoard {
  return {
    name: patch.id,
    prefix: null,
    parentBoardId: "board",
    depth: 1,
    canLink: true,
    activeSprintId: null,
    activeSprintName: null,
    ...patch,
  };
}

const recurrence = (patch: Partial<RecurrenceForm> = {}): RecurrenceForm => ({
  ...defaultRecurrence(14),
  enabled: true,
  ...patch,
});

describe("sprintDayInstant", () => {
  it("sends an automated day as 00:00 UTC of that calendar day", () => {
    expect(sprintDayInstant("2026-11-20", true)).toBe("2026-11-20T00:00:00.000Z");
  });

  it("sends any other day as the start of that day in the device zone", () => {
    expect(sprintDayInstant("2026-11-20", false)).toBe(
      (parseDateOnly("2026-11-20") as Date).toISOString()
    );
  });

  it("refuses a value that is not a day", () => {
    expect(sprintDayInstant("20-11-2026", true)).toBeNull();
    expect(sprintDayInstant("", false)).toBeNull();
  });
});

describe("isUtcMidnight", () => {
  it("is true only at exactly 00:00 UTC", () => {
    expect(isUtcMidnight("2026-11-20T00:00:00Z")).toBe(true);
    expect(isUtcMidnight("2026-11-19T21:00:00Z")).toBe(false);
    expect(isUtcMidnight(null)).toBe(false);
  });
});

describe("splitSeriesName", () => {
  it("splits a trailing round number", () => {
    expect(splitSeriesName("Sprint 7")).toEqual({ seriesName: "Sprint", round: 7 });
    expect(splitSeriesName("  Team A  12 ")).toEqual({ seriesName: "Team A", round: 12 });
  });

  it("keeps a name without a round in range as round 1", () => {
    expect(splitSeriesName("Sprint")).toEqual({ seriesName: "Sprint", round: 1 });
    expect(splitSeriesName("Sprint 0")).toEqual({ seriesName: "Sprint 0", round: 1 });
    expect(splitSeriesName("Sprint 1234567")).toEqual({ seriesName: "Sprint 1234567", round: 1 });
  });
});

describe("nextSprintName", () => {
  it("uses the series' own name while it repeats", () => {
    expect(nextSprintName(series(), "Sprint 7", true)).toBe("Sprint 8");
  });

  it("previews the name a new series would give", () => {
    expect(nextSprintName(null, "Sprint 7", true)).toBe("Sprint 8");
    expect(nextSprintName(series({ enabled: false, nextRound: 3 }), "Sprint 9", true)).toBe(
      "Sprint 3"
    );
  });

  it("is null while nothing repeats", () => {
    expect(nextSprintName(null, "Sprint 7", false)).toBeNull();
  });
});

describe("recurrenceProblems", () => {
  it("checks nothing while automation is off", () => {
    expect(recurrenceProblems({ ...recurrence(), enabled: false }, null)).toEqual({});
  });

  it("needs the sprint's own dates to span 1 to 365 days", () => {
    expect(recurrenceProblems(recurrence(), 0)).toEqual({ dates: "length-range" });
    expect(recurrenceProblems(recurrence(), 366)).toEqual({ dates: "length-range" });
    expect(recurrenceProblems(recurrence(), 14)).toEqual({});
  });

  it("needs a series length of 1 to 365 days", () => {
    expect(recurrenceProblems(recurrence({ lengthDays: "" }), 14)).toEqual({ lengthDays: "range" });
    expect(recurrenceProblems(recurrence({ lengthDays: "400" }), 14)).toEqual({
      lengthDays: "range",
    });
  });

  it("needs days before from 1 to one less than the length", () => {
    const daysBefore = (value: string) =>
      recurrence({ creationMode: "DAYS_BEFORE", createDaysBefore: value, lengthDays: "14" });
    expect(recurrenceProblems(daysBefore(""), 14)).toEqual({ createDaysBefore: "required" });
    expect(recurrenceProblems(daysBefore("0"), 14)).toEqual({ createDaysBefore: "range" });
    expect(recurrenceProblems(daysBefore("14"), 14)).toEqual({ createDaysBefore: "range" });
    expect(recurrenceProblems(daysBefore("13"), 14)).toEqual({});
  });
});

describe("seriesBody", () => {
  it("sends days before only for its mode", () => {
    expect(seriesBody(recurrence({ createDaysBefore: "3" }), 14).createDaysBefore).toBeNull();
    expect(
      seriesBody(recurrence({ creationMode: "DAYS_BEFORE", createDaysBefore: "3" }), 14)
        .createDaysBefore
    ).toBe(3);
  });

  it("falls back to the dates' length when the field holds none in range", () => {
    expect(seriesBody(recurrence({ lengthDays: "" }), 10).lengthDays).toBe(10);
    expect(seriesBody(recurrence({ lengthDays: "21" }), 10).lengthDays).toBe(21);
  });
});

describe("recurrenceOf", () => {
  it("reads an existing series", () => {
    expect(
      recurrenceOf(
        series({
          creationMode: "DAYS_BEFORE",
          createDaysBefore: 2,
          unfinishedTicketAction: "MOVE_TO_BACKLOG",
        }),
        7
      )
    ).toEqual({
      enabled: true,
      lengthDays: "14",
      unfinishedTicketAction: "MOVE_TO_BACKLOG",
      creationMode: "DAYS_BEFORE",
      createDaysBefore: "2",
    });
  });

  it("starts a new series with the sprint's own length", () => {
    expect(recurrenceOf(null, 10).lengthDays).toBe("10");
    expect(recurrenceOf(null, 500).lengthDays).toBe("365");
  });
});

describe("parseCount", () => {
  it("reads only whole numbers", () => {
    expect(parseCount(" 12 ")).toBe(12);
    expect(parseCount("1.5")).toBeNull();
    expect(parseCount("")).toBeNull();
  });
});

describe("child boards and takeover", () => {
  const own: TakeoverConflict[] = boardConflicts("board", "Web", [
    sprint("s-active", "ACTIVE"),
    sprint("s-planned", "PLANNED"),
  ]);

  it("lists the board's own active sprints", () => {
    expect(own).toEqual([
      {
        boardId: "board",
        boardName: "Web",
        sprintId: "s-active",
        sprintName: "s-active",
        movesToBacklog: false,
        canManage: true,
        endsSeriesName: null,
      },
    ]);
  });

  it("carries the series that stops from the board's own sprints and the child boards", () => {
    const ownSeries: TakeoverConflict[] = boardConflicts("board", "Web", [
      { ...sprint("s-active", "ACTIVE"), endsSeriesName: "Web series" },
    ]);
    const conflicts: TakeoverConflict[] = takeoverConflicts(
      ownSeries,
      [
        child({
          id: "ticked",
          activeSprintId: "s-child",
          activeSprintName: "Child 3",
          activeSprintEndsSeriesName: "Child series",
        }),
      ],
      ["ticked"]
    );
    expect(conflicts.map((row) => row.endsSeriesName)).toEqual(["Web series", "Child series"]);
  });

  it("preselects linkable boards without an active sprint", () => {
    expect(
      preselectedChildBoardIds([
        child({ id: "a" }),
        child({ id: "b", canLink: false }),
        child({ id: "c", activeSprintId: "x" }),
      ])
    ).toEqual(["a"]);
  });

  it("refuses more than 50 child boards, the web's limit, and takes 50", () => {
    const ids = (count: number): string[] => Array.from({ length: count }, (_, i) => `b${i}`);
    expect(tooManyChildBoards(ids(50))).toBe(false);
    expect(tooManyChildBoards(ids(51))).toBe(true);
    expect(tooManyChildBoards([])).toBe(false);
  });

  it("adds ticked children's sprints and unticked children that share a blocking sprint", () => {
    const children: SprintChildBoard[] = [
      child({ id: "ticked", activeSprintId: "s-child", activeSprintName: "Child 3" }),
      child({
        id: "sharing",
        activeSprintId: "s-active",
        activeSprintName: "s-active",
        canLink: false,
      }),
      child({ id: "other", activeSprintId: "s-elsewhere" }),
    ];
    const conflicts: TakeoverConflict[] = takeoverConflicts(own, children, ["ticked"]);
    expect(conflicts.map((row) => [row.boardId, row.sprintId, row.movesToBacklog])).toEqual([
      ["board", "s-active", false],
      ["ticked", "s-child", false],
      ["sharing", "s-active", true],
    ]);
    expect(canTakeOver(conflicts)).toBe(false);
    expect(selectedConflictBoards(conflicts)).toBe(2);
  });

  it("counts every listed board when only backlog rows are listed", () => {
    expect(
      selectedConflictBoards([
        {
          boardId: "a",
          boardName: "A",
          sprintId: "s",
          sprintName: "S",
          movesToBacklog: true,
          canManage: true,
        },
      ])
    ).toBe(1);
  });
});

describe("stoppingSeries", () => {
  const conflict = (
    boardId: string,
    sprintName: string,
    endsSeriesName: string | null
  ): TakeoverConflict => ({
    boardId,
    boardName: boardId,
    sprintId: sprintName,
    sprintName,
    movesToBacklog: false,
    canManage: true,
    endsSeriesName,
  });

  it("names each series once, with the sprint of its first conflict, in conflict order", () => {
    expect(
      stoppingSeries([
        conflict("a", "Web 4", "Web"),
        conflict("b", "Web 4", "Web"),
        conflict("c", "Ops 2", null),
        conflict("d", "Mobile 9", "Mobile"),
      ])
    ).toEqual([
      { series: "Web", sprint: "Web 4" },
      { series: "Mobile", sprint: "Mobile 9" },
    ]);
  });

  it("is empty when no conflict ends a series", () => {
    expect(stoppingSeries([conflict("a", "Web 4", null)])).toEqual([]);
    expect(stoppingSeries([])).toEqual([]);
  });
});

describe("seriesRunMessage", () => {
  const t = (key: TranslationKey, options?: Record<string, unknown>): string =>
    options ? `${key} ${JSON.stringify(options)}` : key;

  it("translates the reason a takeover leaves", () => {
    expect(seriesRunMessage("no running or planned sprint left", t)).toBe(
      "sprintSeriesReasonNoLiveSprint"
    );
  });

  it("translates the one-series-per-board reason with its board and series", () => {
    expect(seriesRunMessage("Switched off: board Web App belongs to series Web 2026", t)).toBe(
      'sprintSeriesReasonBoardInOtherSeries {"board":"Web App","series":"Web 2026"}'
    );
  });

  it("shows any other message as sent", () => {
    expect(seriesRunMessage("Rollover failed: board archived", t)).toBe(
      "Rollover failed: board archived"
    );
    expect(seriesRunMessage("No running or planned sprint left", t)).toBe(
      "No running or planned sprint left"
    );
  });
});

describe("sprintFieldProblems", () => {
  const fields = { name: "Sprint 1", goal: "", startDay: "2026-11-09", endDay: "2026-11-23" };

  it("accepts valid fields", () => {
    expect(sprintFieldProblems(fields, false)).toEqual({});
  });

  it("requires a name and limits its length", () => {
    expect(sprintFieldProblems({ ...fields, name: "  " }, false).name).toBe("required");
    expect(sprintFieldProblems({ ...fields, name: "a".repeat(256) }, false).name).toBe("too-long");
    expect(sprintFieldProblems({ ...fields, name: "a".repeat(241) }, true).name).toBe(
      "series-too-long"
    );
  });

  it("needs the end after the start and a goal within 1000 characters", () => {
    expect(sprintFieldProblems({ ...fields, endDay: "2026-11-09" }, false).endDay).toBe(
      "before-start"
    );
    expect(sprintFieldProblems({ ...fields, goal: "g".repeat(1001) }, false).goal).toBe("too-long");
  });
});

describe("createSprintBody", () => {
  const fields = { name: " Sprint ", goal: "", startDay: "2026-11-09", endDay: "2026-11-23" };

  it("sends a plain sprint without series, children or takeover", () => {
    expect(
      createSprintBody({
        fields,
        recurrence: defaultRecurrence(14),
        childBoardIds: [],
        conflicts: [],
        takeOver: true,
      })
    ).toEqual({
      name: "Sprint",
      startDate: (parseDateOnly("2026-11-09") as Date).toISOString(),
      endDate: (parseDateOnly("2026-11-23") as Date).toISOString(),
    });
  });

  it("sends automated dates at 00:00 UTC with the series and the takeover choice", () => {
    const conflicts: TakeoverConflict[] = boardConflicts("board", "Web", [sprint("s", "ACTIVE")]);
    expect(
      createSprintBody({
        fields,
        recurrence: recurrence({ lengthDays: "14" }),
        childBoardIds: ["c"],
        conflicts,
        takeOver: true,
      })
    ).toEqual({
      name: "Sprint",
      startDate: "2026-11-09T00:00:00.000Z",
      endDate: "2026-11-23T00:00:00.000Z",
      series: {
        enabled: true,
        lengthDays: 14,
        unfinishedTicketAction: "MOVE_TO_NEXT_SPRINT",
        creationMode: "AT_END",
        createDaysBefore: null,
      },
      childBoardIds: ["c"],
      takeOver: true,
    });
  });

  it("names the first sprint of a series round 1", () => {
    expect(createdSprintName(" Sprint ", true)).toBe("Sprint 1");
    expect(createdSprintName(" Sprint ", false)).toBe("Sprint");
  });
});

describe("edit", () => {
  it("edits the series name of a sprint in an enabled series, with UTC days", () => {
    const sprint7: SprintDetail = detail();
    expect(editFieldsOf(sprint7)).toEqual({
      name: "Sprint",
      goal: "Ship",
      startDay: "2026-11-06",
      endDay: "2026-11-20",
    });
    expect(
      updateSprintBody(sprint7, {
        name: "Team",
        goal: "Ship",
        startDay: "2026-11-06",
        endDay: "2026-11-21",
      })
    ).toEqual({
      seriesName: "Team",
      goal: "Ship",
      startDate: "2026-11-06T00:00:00.000Z",
      endDate: "2026-11-21T00:00:00.000Z",
    });
  });

  it("sends no series name when it is unchanged", () => {
    expect(updateSprintBody(detail(), editFieldsOf(detail()))).not.toHaveProperty("seriesName");
  });

  it("renames a sprint outside a series", () => {
    const plain: SprintDetail = detail({ series: null, round: null });
    const body = updateSprintBody(plain, { ...editFieldsOf(plain), name: "Hotfix" });
    expect(body?.name).toBe("Hotfix");
    expect(body).not.toHaveProperty("seriesName");
  });

  it("moves dates that are not at 00:00 UTC to the same day at 00:00 UTC when automation turns on", () => {
    expect(automationSchedule(detail()).needsNormalizing).toBe(false);
    const picked = automationSchedule({
      startDate: (parseDateOnly("2026-11-06") as Date).toISOString(),
      endDate: "2026-11-20T10:30:00Z",
    });
    expect(picked.needsNormalizing).toBe(true);
    expect(picked.startDay).toBe("2026-11-06");
  });
});

describe("complete", () => {
  it("counts done and not done tickets", () => {
    expect(
      completionCounts([{ status: "DONE" }, { status: "TODO" }, { status: "IN_REVIEW" }])
    ).toEqual({ completed: 1, incomplete: 2, rate: 33 });
    expect(completionCounts([])).toEqual({ completed: 0, incomplete: 0, rate: 0 });
  });

  it("opens on the next sprint only when the series carries tickets there", () => {
    expect(defaultCompleteChoice(series())).toBe("next-sprint");
    expect(defaultCompleteChoice(series({ unfinishedTicketAction: "MOVE_TO_BACKLOG" }))).toBe(
      "backlog"
    );
    expect(defaultCompleteChoice(series({ enabled: false }))).toBe("backlog");
    expect(defaultCompleteChoice(null)).toBe("backlog");
  });

  it("offers the board's other open sprints", () => {
    expect(
      completeTargets(
        [sprint("now", "ACTIVE"), sprint("next", "PLANNED"), sprint("old", "COMPLETED")],
        "now"
      ).map((target) => target.id)
    ).toEqual(["next"]);
  });

  it("builds the request of each choice", () => {
    expect(completeBody("next-sprint", null)).toEqual({
      ticketAction: "MOVE_TO_SPRINT",
      moveToNextSprint: true,
    });
    expect(completeBody("backlog", null)).toEqual({ ticketAction: "MOVE_TO_BACKLOG" });
    expect(completeBody("sprint", null)).toBeNull();
    expect(completeBody("sprint", "next")).toEqual({
      ticketAction: "MOVE_TO_SPRINT",
      targetSprintId: "next",
    });
    expect(completeBody("delete", null)).toEqual({ ticketAction: "DELETE" });
  });
});

describe("move to sprint", () => {
  const sprints: Sprint[] = [
    sprint("planned", "PLANNED", "2026-12-01T00:00:00Z"),
    sprint("active", "ACTIVE"),
    sprint("done", "COMPLETED"),
  ];

  it("offers the backlog and the other open sprints, active first", () => {
    expect(sprintMoveTargets(sprints, "active").map((target) => target.id)).toEqual([
      BACKLOG_PLACEMENT,
      "planned",
    ]);
    expect(sprintMoveTargets(sprints, null).map((target) => target.id)).toEqual([
      "active",
      "planned",
    ]);
  });

  it("opens on the requested place, else the first open sprint, else the backlog", () => {
    const fromSprint = sprintMoveTargets(sprints, "active");
    expect(defaultMoveTarget(fromSprint, BACKLOG_PLACEMENT)).toBe(BACKLOG_PLACEMENT);
    expect(defaultMoveTarget(fromSprint, null)).toBe("planned");
    expect(defaultMoveTarget(fromSprint, "done")).toBe("planned");
    expect(defaultMoveTarget(sprintMoveTargets([sprints[1] as Sprint], "active"), null)).toBe(
      BACKLOG_PLACEMENT
    );
    expect(defaultMoveTarget([], null)).toBeNull();
  });

  it("picks the unfinished sub-tickets first", () => {
    expect(
      defaultPickedSubTickets([
        { id: "a", status: "DONE" },
        { id: "b", status: "TODO" },
      ])
    ).toEqual(["b"]);
  });

  it("sends picked ids only with SELECTED, and no pick as only this ticket", () => {
    expect(subTicketMove("UNFINISHED", ["a"])).toEqual({ subTicketMode: "UNFINISHED" });
    expect(subTicketMove("SELECTED", ["a"])).toEqual({
      subTicketMode: "SELECTED",
      subTicketIds: ["a"],
    });
    expect(subTicketMove("SELECTED", [])).toEqual({ subTicketMode: "NONE" });
  });
});

describe("editSprintValues", () => {
  it("opens with the sprint's values and nothing changed", () => {
    const values = editSprintValues(detail(), { fields: {}, recurrence: {} });
    expect(values.fields).toEqual(values.initialFields);
    expect(values.recurrence).toEqual(values.initialRecurrence);
    expect(values.recurrence.enabled).toBe(true);
  });

  it("follows a newer read of the sprint where nothing was typed, and keeps what was", () => {
    const cached: SprintDetail = detail();
    // A new sprint's takeover stopped the series after this sprint was cached.
    const read: SprintDetail = detail({ series: series({ enabled: false }) });
    const edits = { fields: { goal: "Ship it" }, recurrence: {} };
    expect(editSprintValues(cached, edits).recurrence.enabled).toBe(true);
    const values = editSprintValues(read, edits);
    expect(values.recurrence.enabled).toBe(false);
    expect(values.initialRecurrence.enabled).toBe(false);
    expect(values.fields.goal).toBe("Ship it");
    expect(values.fields.name).toBe("Sprint 7");
  });

  it("keeps a typed automation value over a newer read", () => {
    const read: SprintDetail = detail({ series: series({ enabled: false }) });
    const values = editSprintValues(read, { fields: {}, recurrence: { enabled: true } });
    expect(values.recurrence.enabled).toBe(true);
    expect(values.initialRecurrence.enabled).toBe(false);
  });
});

describe("editSaveSteps", () => {
  const plain = (): SprintDetail =>
    detail({
      name: "Hotfix",
      round: null,
      series: null,
      startDate: "2026-11-05T21:00:00Z",
      endDate: "2026-11-19T21:00:00Z",
    });

  it("writes nothing for a sprint that stays without automation and is unchanged", () => {
    const sprint: SprintDetail = plain();
    expect(
      editSaveSteps({
        sprint,
        fields: editFieldsOf(sprint),
        fieldsChanged: false,
        recurrence: defaultRecurrence(14),
        recurrenceChanged: true,
      })
    ).toEqual({ update: null, series: false });
  });

  it("moves the days to 00:00 UTC before switching automation on", () => {
    const sprint: SprintDetail = plain();
    const fields = { ...editFieldsOf(sprint), startDay: "2026-11-06", endDay: "2026-11-20" };
    const steps = editSaveSteps({
      sprint,
      fields,
      fieldsChanged: false,
      recurrence: recurrence(),
      recurrenceChanged: true,
    });
    expect(steps?.series).toBe(true);
    expect(steps?.update?.startDate).toBe("2026-11-06T00:00:00.000Z");
    expect(steps?.update?.endDate).toBe("2026-11-20T00:00:00.000Z");
    expect(steps?.update?.name).toBe("Hotfix");
  });

  it("writes only the series of an automated sprint whose fields are unchanged", () => {
    const sprint: SprintDetail = detail();
    expect(
      editSaveSteps({
        sprint,
        fields: editFieldsOf(sprint),
        fieldsChanged: false,
        recurrence: recurrence({ lengthDays: "7" }),
        recurrenceChanged: true,
      })
    ).toEqual({ update: null, series: true });
  });

  it("keeps UTC days on a save that switches automation off", () => {
    const sprint: SprintDetail = detail();
    const steps = editSaveSteps({
      sprint,
      fields: { ...editFieldsOf(sprint), goal: "Ship it" },
      fieldsChanged: true,
      recurrence: recurrence({ enabled: false }),
      recurrenceChanged: true,
    });
    expect(steps?.series).toBe(true);
    expect(steps?.update?.startDate).toBe("2026-11-06T00:00:00.000Z");
    expect(steps?.update?.goal).toBe("Ship it");
  });
});

describe("completeChoices", () => {
  it("offers the next sprint only for an enabled series", () => {
    expect(completeChoices(series(), 1)).toEqual(["next-sprint", "backlog", "sprint", "delete"]);
    expect(completeChoices(series({ enabled: false }), 1)).toEqual(["backlog", "sprint", "delete"]);
  });

  it("offers another sprint only while the board has one open", () => {
    expect(completeChoices(null, 0)).toEqual(["backlog", "delete"]);
  });
});
