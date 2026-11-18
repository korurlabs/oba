import { activeSprint as runningSprint, canEditBoard } from "@/components/boards/boardModel";
import type { BoardMember, BoardTreeItem, Sprint } from "@/lib/schemas/boards";
import type { SprintSeries } from "@/lib/schemas/sprints";

/*
 * The pure half of "Join a parent board's sprint", as Kazan web decides it:
 * the boards above a board, the active sprint of each, the boards a join
 * links the sprint to, whether the user may link it to all of them, the
 * boards of the sprint as a tree, and what the sprint's series means for the
 * joining board. No React or native import, so `bun test` covers it.
 */

/** A board above the current one, with the sprint it is running now. */
export interface ParentSprintOption {
  board: BoardTreeItem;
  /** 1 for the parent, 2 for the grandparent, and so on. */
  distance: number;
  /** The board's ACTIVE sprint; a board runs at most one at a time. */
  activeSprint: Sprint | null;
  /**
   * The boards the sprint is linked to on join, top-down, ending with the
   * current board. A sprint can only be linked to a parent, child or sibling
   * of a board that already has it, so a grandparent's sprint reaches the
   * current board through each board in between that does not have it yet.
   */
  linkPath: BoardTreeItem[];
  /** The first board of `linkPath` the user may not link a sprint to (ADMIN or OWNER needed). */
  boardWithoutRole: BoardTreeItem | null;
}

/** The boards from a root down to `boardId`, both included; empty when the tree does not hold it. */
export function boardPath(tree: readonly BoardTreeItem[], boardId: string): BoardTreeItem[] {
  for (const board of tree) {
    if (board.id === boardId) return [board];
    const below: BoardTreeItem[] = boardPath(board.childBoards ?? [], boardId);
    if (below.length > 0) return [board, ...below];
  }
  return [];
}

/**
 * The ids of the given boards on which the user is OWNER or ADMIN, read from
 * each board's members, because the board list carries no role. A board whose
 * members are not loaded yet, or a user not known yet, counts as no role.
 */
export function manageableBoardIds(
  boardIds: readonly string[],
  membersByBoard: Readonly<Record<string, readonly BoardMember[]>>,
  userId: string | null
): Set<string> {
  const ids: Set<string> = new Set();
  if (!userId) return ids;
  for (const boardId of boardIds) {
    const role: string | null =
      membersByBoard[boardId]?.find((member) => member.userId === userId)?.role ?? null;
    if (canEditBoard(role)) ids.add(boardId);
  }
  return ids;
}

/**
 * The ancestors of the last board of `path`, nearest first, each with its
 * active sprint, the boards a join links it to and the first of those the
 * user lacks the role on. `path` runs from a root down to the current board;
 * `sprintsByBoard` holds the sprints of the ancestors read so far, and an
 * ancestor whose sprints are not read yet has no active sprint.
 */
export function parentSprintOptions(
  path: readonly BoardTreeItem[],
  sprintsByBoard: Readonly<Record<string, readonly Sprint[]>>,
  manageableIds: ReadonlySet<string>
): ParentSprintOption[] {
  const ancestors: readonly BoardTreeItem[] = path.slice(0, -1);
  const activeOf = (board: BoardTreeItem): Sprint | null =>
    runningSprint(sprintsByBoard[board.id] ?? []);
  const options: ParentSprintOption[] = ancestors.map((ancestor, index) => {
    const sprint: Sprint | null = activeOf(ancestor);
    // The boards below the ancestor, down to the current board, that do not run this sprint yet.
    const linkPath: BoardTreeItem[] = path
      .slice(index + 1)
      .filter((board) => sprint === null || activeOf(board)?.id !== sprint.id);
    return {
      board: ancestor,
      distance: ancestors.length - index,
      activeSprint: sprint,
      linkPath,
      boardWithoutRole: linkPath.find((board) => !manageableIds.has(board.id)) ?? null,
    };
  });
  return options.reverse();
}

/** True when some ancestor runs a sprint the user may link down to the current board. */
export function hasJoinableSprint(options: readonly ParentSprintOption[]): boolean {
  return options.some((option) => option.activeSprint !== null && option.boardWithoutRole === null);
}

/** A board of the sprint, or one that joins it, as a row of the sprint's board tree. */
export interface SprintBoardRow {
  id: string;
  name: string;
  color: string | null;
  depth: number;
  /** True for a board the join links the sprint to. */
  joins: boolean;
}

/**
 * The boards of the sprint plus the boards that join it, in the order and
 * depth of the board tree. A board whose parent is in neither set moves up to
 * the depth of its nearest shown ancestor, so the tree stays connected. A
 * sprint board the tree does not hold is still listed, at the end and at the
 * top level, with the colour the sprint gives it. The tree holds only the
 * boards the user is a member of, so this is mostly a board of the sprint the
 * user is not a member of, more rarely one beyond the pages read.
 */
export function sprintBoardRows(
  tree: readonly BoardTreeItem[],
  sprintBoards: readonly { id: string; name: string; color?: string | null }[],
  joiningIds: ReadonlySet<string>
): SprintBoardRow[] {
  const sprintBoardIds: ReadonlySet<string> = new Set(sprintBoards.map((board) => board.id));
  const rows: SprintBoardRow[] = [];
  const walk = (boards: readonly BoardTreeItem[], depth: number): void => {
    for (const board of boards) {
      const shown: boolean = sprintBoardIds.has(board.id) || joiningIds.has(board.id);
      if (shown) {
        rows.push({
          id: board.id,
          name: board.name,
          color: board.color ?? null,
          depth,
          joins: joiningIds.has(board.id),
        });
      }
      walk(board.childBoards ?? [], shown ? depth + 1 : depth);
    }
  };
  walk(tree, 0);
  const inTree: ReadonlySet<string> = new Set(rows.map((row) => row.id));
  const rest: SprintBoardRow[] = sprintBoards
    .filter((board) => !inTree.has(board.id))
    .map((board) => ({
      id: board.id,
      name: board.name,
      color: board.color ?? null,
      depth: 0,
      joins: false,
    }));
  return [...rows, ...rest];
}

/** The translation keys of the Repeat row of the join details. */
export type RepeatTextKey =
  | "sprintJoinParentRepeatNone"
  | "sprintJoinParentRepeatOffShort"
  | "sprintJoinParentRepeatEveryWeeks"
  | "sprintJoinParentRepeatEveryDays"
  | "sprintJoinParentOnce"
  | "sprintJoinParentRepeatOff"
  | "sprintJoinParentRepeats";

/** A text of the join details: its translation key and values. */
export interface RepeatText {
  key: RepeatTextKey;
  values: Record<string, string | number>;
}

/** What the sprint's series means for the joining board, as the Repeat row shows it. */
export interface RepeatSummary {
  /** Whether and how often the sprint repeats. */
  value: RepeatText;
  /** What joining means for the board: this sprint only, or the next ones too. */
  effect: RepeatText;
  /**
   * The server's run message of a series switched off by a failed run, to be
   * worded by `seriesRunMessage`; null otherwise.
   */
  offReason: string | null;
}

/**
 * The Repeat row for a sprint with `series` that `boardName` joins: no series
 * means this sprint only; a switched-off series is joined and followed once
 * it runs again; a running one repeats every whole number of weeks, or else
 * every number of days.
 */
export function repeatSummary(series: SprintSeries | null, boardName: string): RepeatSummary {
  const board: Record<string, string> = { board: boardName };
  if (!series) {
    return {
      value: { key: "sprintJoinParentRepeatNone", values: {} },
      effect: { key: "sprintJoinParentOnce", values: board },
      offReason: null,
    };
  }
  if (!series.enabled) {
    return {
      value: { key: "sprintJoinParentRepeatOffShort", values: { series: series.name } },
      effect: { key: "sprintJoinParentRepeatOff", values: board },
      offReason:
        series.lastRunStatus === "FAILED" && series.lastRunMessage ? series.lastRunMessage : null,
    };
  }
  const value: RepeatText =
    series.lengthDays % 7 === 0
      ? { key: "sprintJoinParentRepeatEveryWeeks", values: { count: series.lengthDays / 7 } }
      : { key: "sprintJoinParentRepeatEveryDays", values: { count: series.lengthDays } };
  return {
    value,
    effect: { key: "sprintJoinParentRepeats", values: board },
    offReason: null,
  };
}

/** The translation keys of the join toasts. */
export type JoinToastKey = "sprintJoinedParentSprint" | "sprintJoinPartiallyLinked";

/** A failed join: the refusal, with the names of the boards linked before it. */
export type JoinParentSprintError = Error & { linkedBoardNames?: string[] };

/** The second line of a failed join's toast: its translation key and the boards it names. */
export interface PartialJoinDescription {
  key: JoinToastKey;
  values: { boards: string };
}

/**
 * The second line of a failed join's toast: the boards linked before the
 * refusal keep the sprint, so they are named; null when none was linked.
 */
export function partialJoinDescription(
  linkedBoardNames: readonly string[]
): PartialJoinDescription | null {
  return linkedBoardNames.length > 0
    ? { key: "sprintJoinPartiallyLinked", values: { boards: linkedBoardNames.join(", ") } }
    : null;
}
