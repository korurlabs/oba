import { parseInstant } from "@oba/shared/lib/format-instant";

import { BACKLOG_PLACEMENT } from "@/components/ticket/newTicketModel";
import { openSprintTargets } from "@/components/boards/ticketMoveModel";
import type { SubTicketMoveMode } from "@/hooks/queries/ticketActions";
import type { TranslationKey } from "@/i18n";
import { dayInZone, daysBetween, parseDateOnly, toDateOnly } from "@/lib/dates";
import type { BoardTicket, Sprint } from "@/lib/schemas/boards";
import {
  SPRINT_GOAL_MAX,
  SPRINT_LENGTH_MAX_DAYS,
  SPRINT_LENGTH_MIN_DAYS,
  SPRINT_MAX_CHILD_BOARDS,
  SPRINT_NAME_MAX,
  SPRINT_SERIES_NAME_MAX,
  type SprintChildBoard,
  type SprintCompleteBody,
  type SprintCreateBody,
  type SprintCreationMode,
  type SprintDetail,
  type SprintSeries,
  type SprintSeriesBody,
  type SprintStartConflict,
  type SprintUnfinishedAction,
  type SprintUpdateBody,
} from "@/lib/schemas/sprints";

/*
 * The rules of the sprint sheets, without React: dates, automation, child
 * boards, takeover, completion and the sub-tickets of a move. They follow the
 * checks the server makes, so a sheet shows a problem under its field before
 * the request is sent.
 */

/** Days between the default start and end of a new sprint. */
export const DEFAULT_SPRINT_LENGTH_DAYS = 14;

// ── Dates ────────────────────────────────────────────────────────

const DAY_MS = 24 * 60 * 60 * 1000;

/**
 * The instant a picked sprint day is sent as. An automated sprint rolls over
 * at 00:00 UTC on its end date and the server refuses any other time, so its
 * days are sent as that day at 00:00 UTC; otherwise "Oct 27" picked in
 * Istanbul would be stored as Oct 26 21:00 UTC and close a day early. A sprint
 * without automation keeps the start of the picked day in the device zone, as
 * Kaizen web sends it.
 */
export function sprintDayInstant(day: string, automated: boolean): string | null {
  if (automated) return parseDateOnly(day) ? `${day}T00:00:00.000Z` : null;
  return parseDateOnly(day)?.toISOString() ?? null;
}

/** True when the instant is exactly 00:00 UTC. */
export function isUtcMidnight(value: string | null): boolean {
  const date: Date | null = parseInstant(value);
  return !!date && date.getTime() % DAY_MS === 0;
}

/** The calendar day of a stored instant in the device zone, `YYYY-MM-DD`. */
export function localDayOf(value: string | null): string | null {
  const date: Date | null = parseInstant(value);
  return date ? toDateOnly(date) : null;
}

/**
 * The day a date field shows for a stored sprint date: the UTC calendar day
 * for an automated sprint, whose days are UTC days, and the device's day
 * otherwise.
 */
export function sprintFormDay(value: string | null, utcDays: boolean): string | null {
  return utcDays ? dayInZone(value, "UTC") : localDayOf(value);
}

/** The sprint's length in calendar days, as the server derives it from the two dates. */
export function sprintLengthDays(startDay: string, endDay: string): number | null {
  return daysBetween(startDay, endDay);
}

/** The rollover of an automated sprint ending on `endDay`: that day at 00:00 UTC. */
export function rolloverInstant(endDay: string): string | null {
  return sprintDayInstant(endDay, true);
}

// ── Series names ─────────────────────────────────────────────────

/** A trailing " <digits>" of a sprint name, as the server splits it into a series name and round. */
const SERIES_NAME_PATTERN = /^(.*\S)\s+(\d{1,6})$/;
const SERIES_MAX_ROUND = 999999;

/**
 * The series name and round the server derives from a sprint name when repeat
 * is switched on for a sprint outside any series: a trailing number from 1 to
 * 999999 is the round ("Sprint 7" gives "Sprint", 7), any other name is the
 * series name with round 1.
 */
export function splitSeriesName(name: string): { seriesName: string; round: number } {
  const trimmed: string = name.trim();
  const match: RegExpExecArray | null = SERIES_NAME_PATTERN.exec(trimmed);
  if (match) {
    const round: number = Number(match[2]);
    if (round >= 1 && round <= SERIES_MAX_ROUND) return { seriesName: match[1] ?? trimmed, round };
  }
  return { seriesName: trimmed, round: 1 };
}

/**
 * The name the next sprint of the series gets: the series' own while it
 * repeats. While repeat is switched on but not saved, the server takes the
 * series name from this sprint's name and counts on from its number; an
 * existing series keeps its counter. Null while nothing repeats.
 */
export function nextSprintName(
  series: SprintSeries | null,
  sprintName: string,
  switchOn: boolean
): string | null {
  if (series?.enabled) return series.nextSprintName;
  if (!switchOn) return null;
  const { seriesName, round } = splitSeriesName(sprintName);
  return `${seriesName} ${series ? series.nextRound : round + 1}`;
}

// ── Automation ───────────────────────────────────────────────────

/** The automation fields as a sheet holds them; the counts are the typed text. */
export interface RecurrenceForm {
  enabled: boolean;
  lengthDays: string;
  unfinishedTicketAction: SprintUnfinishedAction;
  creationMode: SprintCreationMode;
  createDaysBefore: string;
}

/** Automation off, with the defaults Kaizen web offers when it is switched on. */
export function defaultRecurrence(lengthDays: number | null): RecurrenceForm {
  return {
    enabled: false,
    lengthDays: lengthDays === null ? "" : String(clampLength(lengthDays)),
    unfinishedTicketAction: "MOVE_TO_NEXT_SPRINT",
    creationMode: "AT_END",
    createDaysBefore: "",
  };
}

/** The automation of an existing series, or the defaults for a sprint without one. */
export function recurrenceOf(
  series: SprintSeries | null,
  lengthDays: number | null
): RecurrenceForm {
  if (!series) return defaultRecurrence(lengthDays);
  return {
    enabled: series.enabled,
    lengthDays: String(series.lengthDays),
    unfinishedTicketAction:
      series.unfinishedTicketAction === "MOVE_TO_BACKLOG"
        ? "MOVE_TO_BACKLOG"
        : "MOVE_TO_NEXT_SPRINT",
    creationMode: series.creationMode === "DAYS_BEFORE" ? "DAYS_BEFORE" : "AT_END",
    createDaysBefore: series.createDaysBefore === null ? "" : String(series.createDaysBefore),
  };
}

function clampLength(days: number): number {
  return Math.min(Math.max(days, SPRINT_LENGTH_MIN_DAYS), SPRINT_LENGTH_MAX_DAYS);
}

/** A whole number typed in a field, or null for an empty or non-numeric text. */
export function parseCount(text: string): number | null {
  const trimmed: string = text.trim();
  if (!/^\d+$/.test(trimmed)) return null;
  return Number(trimmed);
}

/** What is wrong with the automation fields; each field has at most one problem. */
export interface RecurrenceProblems {
  /** The sprint's own dates do not span 1 to 365 days. */
  dates?: "length-range";
  lengthDays?: "range";
  createDaysBefore?: "required" | "range";
}

/**
 * The server's checks on enabled automation: the sprint's own dates span 1 to
 * 365 days, each new sprint runs 1 to 365 days, and "days before" is 1 to one
 * less than that length. Disabled automation is not checked, as on the server.
 */
export function recurrenceProblems(
  form: RecurrenceForm,
  datesLength: number | null
): RecurrenceProblems {
  if (!form.enabled) return {};
  if (
    datesLength === null ||
    datesLength < SPRINT_LENGTH_MIN_DAYS ||
    datesLength > SPRINT_LENGTH_MAX_DAYS
  ) {
    return { dates: "length-range" };
  }
  const length: number | null = parseCount(form.lengthDays);
  if (length === null || length < SPRINT_LENGTH_MIN_DAYS || length > SPRINT_LENGTH_MAX_DAYS) {
    return { lengthDays: "range" };
  }
  if (form.creationMode !== "DAYS_BEFORE") return {};
  const before: number | null = parseCount(form.createDaysBefore);
  if (before === null) return { createDaysBefore: "required" };
  if (before < 1 || before > length - 1) return { createDaysBefore: "range" };
  return {};
}

/**
 * The series request of the fields. "Days before" is sent only for the mode
 * that uses it. A request that switches automation off still needs a length,
 * which `fallbackLength` gives when the field holds none in range.
 */
export function seriesBody(form: RecurrenceForm, fallbackLength: number): SprintSeriesBody {
  const length: number | null = parseCount(form.lengthDays);
  const inRange: boolean =
    length !== null && length >= SPRINT_LENGTH_MIN_DAYS && length <= SPRINT_LENGTH_MAX_DAYS;
  return {
    enabled: form.enabled,
    lengthDays: inRange && length !== null ? length : clampLength(fallbackLength),
    unfinishedTicketAction: form.unfinishedTicketAction,
    creationMode: form.creationMode,
    createDaysBefore:
      form.creationMode === "DAYS_BEFORE" ? parseCount(form.createDaysBefore) : null,
  };
}

/*
 * The reason a series records when it is switched off arrives as English text
 * from the server. The two reasons that switch a series off without the user
 * doing so are shown in the viewer's language; any other text is shown as it
 * came. The texts match SprintRecurrenceConstant.RUN_MESSAGE_NO_LIVE_SPRINT and
 * the message the one-series-per-board migration wrote; a change there needs
 * the same change here.
 */
const RUN_MESSAGE_NO_LIVE_SPRINT = "no running or planned sprint left";
const RUN_MESSAGE_BOARD_IN_OTHER_SERIES = /^Switched off: board (.+) belongs to series (.+)$/;

/** The series' last run message in the viewer's language, where the app knows it. */
export function seriesRunMessage(
  message: string,
  t: (key: TranslationKey, options?: Record<string, unknown>) => string
): string {
  if (message === RUN_MESSAGE_NO_LIVE_SPRINT) {
    return t("sprintSeriesReasonNoLiveSprint");
  }
  const [, board, series] = RUN_MESSAGE_BOARD_IN_OTHER_SERIES.exec(message) ?? [];
  if (board && series) {
    return t("sprintSeriesReasonBoardInOtherSeries", { board, series });
  }
  return message;
}

// ── Child boards and takeover ────────────────────────────────────

/**
 * One board whose active sprint a new or started sprint would close.
 * `endsSeriesName` names the repeating series that stops with that sprint.
 */
export type TakeoverConflict = Pick<
  SprintStartConflict,
  | "boardId"
  | "boardName"
  | "sprintId"
  | "sprintName"
  | "movesToBacklog"
  | "canManage"
  | "endsSeriesName"
>;

/** A repeating series a takeover stops, with its last running sprint. */
export interface StoppingSeries {
  series: string;
  sprint: string;
}

/**
 * The series a takeover stops, one per series in conflict order: a sprint
 * shared by several boards gives several conflicts, and the first one names
 * the sprint.
 */
export function stoppingSeries(conflicts: readonly TakeoverConflict[]): StoppingSeries[] {
  const bySeries: Map<string, string> = new Map();
  for (const conflict of conflicts) {
    if (conflict.endsSeriesName && !bySeries.has(conflict.endsSeriesName)) {
      bySeries.set(conflict.endsSeriesName, conflict.sprintName);
    }
  }
  return [...bySeries].map(([series, sprint]) => ({ series, sprint }));
}

/**
 * The child boards ticked when Create sprint opens: those the user may link
 * and that run no sprint, so nothing is closed unasked.
 */
export function preselectedChildBoardIds(boards: readonly SprintChildBoard[]): string[] {
  return boards
    .filter((board) => board.canLink && !board.activeSprintId)
    .map((board) => board.id)
    .slice(0, SPRINT_MAX_CHILD_BOARDS);
}

/**
 * The board's own active sprints as takeover rows. Only a board admin opens
 * Create sprint, so the user may manage each of them.
 */
export function boardConflicts(
  boardId: string,
  boardName: string,
  sprints: readonly Sprint[]
): TakeoverConflict[] {
  return sprints
    .filter((sprint) => sprint.status === "ACTIVE")
    .map((sprint) => ({
      boardId,
      boardName,
      sprintId: sprint.id,
      sprintName: sprint.name,
      movesToBacklog: false,
      canManage: true,
      endsSeriesName: sprint.endsSeriesName ?? null,
    }));
}

/**
 * The active sprints a new sprint would take over: the board's own, those of
 * each ticked child board, and, as backlog rows, each unticked child board
 * that shares one of them, whose unfinished tickets go to its own backlog.
 */
export function takeoverConflicts(
  own: readonly TakeoverConflict[],
  childBoards: readonly SprintChildBoard[],
  tickedIds: readonly string[]
): TakeoverConflict[] {
  const ticked: Set<string> = new Set(tickedIds);
  const row = (child: SprintChildBoard, sprintId: string, movesToBacklog: boolean) => ({
    boardId: child.id,
    boardName: child.name,
    sprintId,
    sprintName: child.activeSprintName ?? "",
    movesToBacklog,
    canManage: child.canLink,
    endsSeriesName: child.activeSprintEndsSeriesName ?? null,
  });
  const conflicts: TakeoverConflict[] = [...own];
  for (const child of childBoards) {
    if (child.activeSprintId && ticked.has(child.id)) {
      conflicts.push(row(child, child.activeSprintId, false));
    }
  }
  const blocking: Set<string> = new Set(conflicts.map((conflict) => conflict.sprintId));
  for (const child of childBoards) {
    if (child.activeSprintId && !ticked.has(child.id) && blocking.has(child.activeSprintId)) {
      conflicts.push(row(child, child.activeSprintId, true));
    }
  }
  return conflicts;
}

/**
 * More child boards than one sprint may be used on. Select all and ticking
 * take any number, as on the web; the form refuses the choice on Create.
 */
export function tooManyChildBoards(childBoardIds: readonly string[]): boolean {
  return childBoardIds.length > SPRINT_MAX_CHILD_BOARDS;
}

/** The server refuses a takeover unless the user is admin of every listed board. */
export function canTakeOver(conflicts: readonly TakeoverConflict[]): boolean {
  return conflicts.every((conflict) => conflict.canManage);
}

/**
 * How many of the sprint's own boards hold a blocking sprint. Backlog rows are
 * boards outside the sprint; when only those are listed (the user is not a
 * member of the linked ones), every listed board counts.
 */
export function selectedConflictBoards(conflicts: readonly TakeoverConflict[]): number {
  const linked: Set<string> = new Set(
    conflicts.filter((conflict) => !conflict.movesToBacklog).map((conflict) => conflict.boardId)
  );
  if (linked.size > 0) return linked.size;
  return new Set(conflicts.map((conflict) => conflict.boardId)).size;
}

// ── Create and edit ──────────────────────────────────────────────

export type SprintNameProblem = "required" | "too-long" | "series-too-long";

/** The sprint fields of Create and Edit. Days are `YYYY-MM-DD`. */
export interface SprintFields {
  name: string;
  goal: string;
  startDay: string;
  endDay: string;
}

/** What is wrong with the sprint fields; each field has at most one problem. */
export interface SprintFieldProblems {
  name?: SprintNameProblem;
  goal?: "too-long";
  endDay?: "before-start";
}

/**
 * The server's checks on the sprint fields. `seriesName` is true while the
 * name is a series name, which holds at most 240 characters so the round fits
 * after it.
 */
export function sprintFieldProblems(
  fields: SprintFields,
  seriesName: boolean
): SprintFieldProblems {
  const problems: SprintFieldProblems = {};
  const name: string = fields.name.trim();
  if (!name) problems.name = "required";
  else if (seriesName && name.length > SPRINT_SERIES_NAME_MAX) problems.name = "series-too-long";
  else if (name.length > SPRINT_NAME_MAX) problems.name = "too-long";
  if (fields.goal.trim().length > SPRINT_GOAL_MAX) problems.goal = "too-long";
  const length: number | null = sprintLengthDays(fields.startDay, fields.endDay);
  if (length === null || length <= 0) problems.endDay = "before-start";
  return problems;
}

/** The round the first sprint of a new series gets, shown after the name. */
export const FIRST_ROUND = 1;

/** The name the new sprint gets: with repeat on, the series name and round 1. */
export function createdSprintName(name: string, repeat: boolean): string {
  return repeat ? `${name.trim()} ${FIRST_ROUND}` : name.trim();
}

/**
 * The create request. `takeOver` is sent only while a selected board runs an
 * active sprint, and is true only when the user may take every one of them
 * over and chose to.
 */
export function createSprintBody(input: {
  fields: SprintFields;
  recurrence: RecurrenceForm;
  childBoardIds: readonly string[];
  conflicts: readonly TakeoverConflict[];
  takeOver: boolean;
}): SprintCreateBody | null {
  const { fields, recurrence, childBoardIds, conflicts, takeOver } = input;
  const automated: boolean = recurrence.enabled;
  const startDate: string | null = sprintDayInstant(fields.startDay, automated);
  const endDate: string | null = sprintDayInstant(fields.endDay, automated);
  if (!startDate || !endDate) return null;
  const goal: string = fields.goal.trim();
  const datesLength: number =
    sprintLengthDays(fields.startDay, fields.endDay) ?? DEFAULT_SPRINT_LENGTH_DAYS;
  return {
    name: fields.name.trim(),
    ...(goal ? { goal } : {}),
    startDate,
    endDate,
    ...(automated ? { series: seriesBody(recurrence, datesLength) } : {}),
    ...(childBoardIds.length > 0 ? { childBoardIds: [...childBoardIds] } : {}),
    ...(conflicts.length > 0 ? { takeOver: canTakeOver(conflicts) && takeOver } : {}),
  };
}

/**
 * How Edit treats a sprint's name. A sprint of an enabled series is named
 * "{series name} {round}" and the server refuses a plain `name` for it, so the
 * field edits the series name, with the round as a fixed suffix; a completed
 * sprint of a series keeps its name.
 */
export function editNaming(sprint: SprintDetail): {
  utcDays: boolean;
  namedBySeries: boolean;
  editsSeriesName: boolean;
} {
  const utcDays: boolean = sprint.series?.enabled ?? false;
  const namedBySeries: boolean = utcDays && sprint.round !== null;
  return {
    utcDays,
    namedBySeries,
    editsSeriesName: namedBySeries && sprint.status !== "COMPLETED",
  };
}

/** The fields Edit opens with. */
export function editFieldsOf(sprint: SprintDetail): SprintFields {
  const { utcDays, editsSeriesName } = editNaming(sprint);
  return {
    name: editsSeriesName ? (sprint.series?.name ?? sprint.name) : sprint.name,
    goal: sprint.goal ?? "",
    startDay: sprintFormDay(sprint.startDate, utcDays) ?? "",
    endDay: sprintFormDay(sprint.endDate, utcDays) ?? "",
  };
}

/** What the user changed on Edit's General and Automation tabs. */
export interface EditSprintEdits {
  fields: Partial<SprintFields>;
  recurrence: Partial<RecurrenceForm>;
}

/** Edit's values as the sprint holds them, and as the form shows them. */
export interface EditSprintValues {
  initialFields: SprintFields;
  fields: SprintFields;
  initialRecurrence: RecurrenceForm;
  recurrence: RecurrenceForm;
}

/**
 * Edit's values: the sprint as last read, with the user's edits on top. A
 * value the user has not touched follows every read of the sprint, so a sheet
 * opened on a cached copy shows the sprint as it is once the copy is read
 * again (for one whose automation a new sprint's takeover stopped, the
 * switch turns off), while what was typed stays.
 */
export function editSprintValues(sprint: SprintDetail, edits: EditSprintEdits): EditSprintValues {
  const initialFields: SprintFields = editFieldsOf(sprint);
  const initialRecurrence: RecurrenceForm = recurrenceOf(
    sprint.series,
    sprintLengthDays(initialFields.startDay, initialFields.endDay)
  );
  return {
    initialFields,
    fields: { ...initialFields, ...edits.fields },
    initialRecurrence,
    recurrence: { ...initialRecurrence, ...edits.recurrence },
  };
}

/**
 * The update request of Edit's General tab. `utcDays` overrides how the days
 * are sent, for a save that also switches automation on or off.
 */
export function updateSprintBody(
  sprint: SprintDetail,
  fields: SprintFields,
  utcDaysOverride?: boolean
): SprintUpdateBody | null {
  const { namedBySeries, editsSeriesName } = editNaming(sprint);
  const utcDays: boolean = utcDaysOverride ?? editNaming(sprint).utcDays;
  const startDate: string | null = sprintDayInstant(fields.startDay, utcDays);
  const endDate: string | null = sprintDayInstant(fields.endDay, utcDays);
  if (!startDate || !endDate) return null;
  const name: string = fields.name.trim();
  return {
    ...(namedBySeries ? {} : { name }),
    ...(editsSeriesName && name !== sprint.series?.name ? { seriesName: name } : {}),
    goal: fields.goal.trim(),
    startDate,
    endDate,
  };
}

/**
 * The dates automation runs on. Dates saved before automation was switched on
 * carry the time they were picked at; switching it on moves them to 00:00 UTC
 * of the same calendar day, since the server accepts no other time.
 */
export function automationSchedule(sprint: Pick<SprintDetail, "startDate" | "endDate">): {
  startDay: string | null;
  endDay: string | null;
  needsNormalizing: boolean;
} {
  const needsNormalizing: boolean =
    !isUtcMidnight(sprint.startDate) || !isUtcMidnight(sprint.endDate);
  const dayOf = (value: string | null): string | null =>
    needsNormalizing ? localDayOf(value) : dayInZone(value, "UTC");
  return {
    startDay: dayOf(sprint.startDate),
    endDay: dayOf(sprint.endDate),
    needsNormalizing,
  };
}

/** What one Save of Edit writes: the sprint update first, then the series. */
export interface EditSaveSteps {
  update: SprintUpdateBody | null;
  series: boolean;
}

/**
 * The writes of Edit's Save. The sprint is written before the series: an
 * automated sprint's days must be 00:00 UTC before the server accepts its
 * automation, so switching automation on also moves the days there, and while
 * the series is still enabled a series rename is accepted. A sprint whose days
 * are, or become, automated sends them as UTC days. Null when a day cannot
 * be read.
 */
export function editSaveSteps(input: {
  sprint: SprintDetail;
  fields: SprintFields;
  fieldsChanged: boolean;
  recurrence: RecurrenceForm;
  recurrenceChanged: boolean;
}): EditSaveSteps | null {
  const { sprint, fields, fieldsChanged, recurrence, recurrenceChanged } = input;
  const wasAutomated: boolean = sprint.series?.enabled ?? false;
  const utcDays: boolean = wasAutomated || recurrence.enabled;
  const switchingOn: boolean = recurrence.enabled && !wasAutomated;
  const needsUtcDays: boolean =
    switchingOn && (!isUtcMidnight(sprint.startDate) || !isUtcMidnight(sprint.endDate));
  let update: SprintUpdateBody | null = null;
  if (fieldsChanged || needsUtcDays) {
    update = updateSprintBody(sprint, fields, utcDays);
    if (!update) return null;
  }
  // A sprint outside any series that stays without automation has nothing to write.
  const series: boolean = recurrenceChanged && (recurrence.enabled || sprint.series !== null);
  return { update, series };
}

// ── Complete ─────────────────────────────────────────────────────

/** What Complete does with the unfinished tickets. */
export type CompleteChoice = "next-sprint" | "backlog" | "sprint" | "delete";

/** Done and not done among the sprint's tickets, and the share done in whole percent. */
export function completionCounts(tickets: readonly Pick<BoardTicket, "status">[]): {
  completed: number;
  incomplete: number;
  rate: number;
} {
  const completed: number = tickets.filter((ticket) => ticket.status === "DONE").length;
  const incomplete: number = tickets.length - completed;
  const rate: number = tickets.length > 0 ? Math.round((completed / tickets.length) * 100) : 0;
  return { completed, incomplete, rate };
}

/**
 * The choice Complete opens on: the next sprint when the series is set to
 * carry its unfinished tickets there, else the backlog.
 */
export function defaultCompleteChoice(series: SprintSeries | null): CompleteChoice {
  return series?.enabled && series.unfinishedTicketAction === "MOVE_TO_NEXT_SPRINT"
    ? "next-sprint"
    : "backlog";
}

/** The sprints of the board that can take the unfinished tickets: not completed, not this one. */
export function completeTargets(sprints: readonly Sprint[], sprintId: string): Sprint[] {
  return openSprintTargets(sprints, sprintId);
}

/**
 * The choices Complete offers: the next sprint only for a sprint of an
 * enabled series, which the server requires, and another sprint only while
 * the board has one open.
 */
export function completeChoices(
  series: SprintSeries | null,
  targetCount: number
): CompleteChoice[] {
  return [
    ...(series?.enabled ? (["next-sprint"] as const) : []),
    "backlog",
    ...(targetCount > 0 ? (["sprint"] as const) : []),
    "delete",
  ];
}

/** The complete request of a choice; null while "another sprint" has none picked. */
export function completeBody(
  choice: CompleteChoice,
  targetSprintId: string | null
): SprintCompleteBody | null {
  switch (choice) {
    case "next-sprint":
      return { ticketAction: "MOVE_TO_SPRINT", moveToNextSprint: true };
    case "backlog":
      return { ticketAction: "MOVE_TO_BACKLOG" };
    case "sprint":
      return targetSprintId ? { ticketAction: "MOVE_TO_SPRINT", targetSprintId } : null;
    case "delete":
      return { ticketAction: "DELETE" };
  }
}

// ── Move to sprint ───────────────────────────────────────────────

/** A place a ticket can move to: the backlog or an open sprint. */
export interface SprintMoveTarget {
  /** `BACKLOG_PLACEMENT` or a sprint id. */
  id: string;
  sprint: Sprint | null;
}

/** The backlog, unless the ticket is in it, then the open sprints other than its own. */
export function sprintMoveTargets(
  sprints: readonly Sprint[],
  currentSprintId: string | null
): SprintMoveTarget[] {
  return [
    ...(currentSprintId ? [{ id: BACKLOG_PLACEMENT, sprint: null }] : []),
    ...openSprintTargets(sprints, currentSprintId).map((sprint) => ({ id: sprint.id, sprint })),
  ];
}

/**
 * The place Move to sprint opens on: the one asked for when the ticket can go
 * there, else the first open sprint, else the backlog.
 */
export function defaultMoveTarget(
  targets: readonly SprintMoveTarget[],
  requestedId: string | null
): string | null {
  if (requestedId && targets.some((target) => target.id === requestedId)) return requestedId;
  return (targets.find((target) => target.sprint) ?? targets[0])?.id ?? null;
}

/** The sub-tickets picked when "the sub-tickets I pick" opens: the unfinished ones. */
export function defaultPickedSubTickets(
  subTickets: readonly { id: string; status: string }[]
): string[] {
  return subTickets.filter((sub) => sub.status !== "DONE").map((sub) => sub.id);
}

/**
 * The sub-ticket part of a bulk move. Picking no sub-ticket is the same as
 * "Only this ticket"; ids go along only with `SELECTED`.
 */
export function subTicketMove(
  choice: SubTicketMoveMode,
  pickedIds: readonly string[]
): { subTicketMode: SubTicketMoveMode; subTicketIds?: string[] } {
  if (choice !== "SELECTED") return { subTicketMode: choice };
  if (pickedIds.length === 0) return { subTicketMode: "NONE" };
  return { subTicketMode: "SELECTED", subTicketIds: [...pickedIds] };
}
