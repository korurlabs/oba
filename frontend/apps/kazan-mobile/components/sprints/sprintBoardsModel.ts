import type { BoardRow } from "@/components/boards/boardModel";
import type { BoardPickItem } from "@/components/boards/boardPickModel";
import type { Sprint } from "@/lib/schemas/boards";

/*
 * The pure half of Edit sprint's Boards tab, as Kazan web's Boards tab and
 * Unlink sprint dialog decide it: which linked boards are listed and in what
 * order, which boards can be linked, the choices of an unlink, how they reset
 * one another, when Unlink may be pressed and the body it sends. No React or
 * native import, so `bun test` covers it.
 */

/** A board that shares the sprint, as a row of the Boards tab. */
export interface LinkedBoardRow {
  board: BoardRow;
  /** True for the board Edit sprint was opened from: it is never unlinked here. */
  current: boolean;
}

/**
 * The linked boards in board-tree order, indented by their depth in the tree.
 * A linked board the tree does not hold (one the reader cannot see) is left
 * out, as the web does.
 */
export function linkedBoardRows(
  rows: readonly BoardRow[],
  linkedIds: readonly string[],
  currentBoardId: string
): LinkedBoardRow[] {
  const linked: ReadonlySet<string> = new Set(linkedIds);
  return rows
    .filter((row) => linked.has(row.id))
    .map((row) => ({ board: row, current: row.id === currentBoardId }));
}

/** A board-tree row as a row of a pick list, disabled with `note` when the note is given. */
function pickItem(row: BoardRow, note: string | null): BoardPickItem {
  return {
    id: row.id,
    name: row.name,
    prefix: row.prefix,
    color: row.color,
    indent: row.depth,
    disabled: note !== null,
    note,
    noteTone: "muted",
  };
}

/**
 * Every board of the tree as a link candidate: the linked ones stay listed
 * but cannot be picked, with `alreadyLinked` as the reason. No other board is
 * disabled; the server refuses a board the user does not administer or that
 * is not related to a linked board.
 */
export function linkCandidates(
  rows: readonly BoardRow[],
  linkedIds: readonly string[],
  alreadyLinked: string
): BoardPickItem[] {
  const linked: ReadonlySet<string> = new Set(linkedIds);
  return rows.map((row) => pickItem(row, linked.has(row.id) ? alreadyLinked : null));
}

/** Ticks or unticks `boardId`, keeping the others in the order they were picked. */
export function togglePickedBoard(picked: readonly string[], boardId: string): string[] {
  return picked.includes(boardId) ? picked.filter((id) => id !== boardId) : [...picked, boardId];
}

/** `SprintBulkLinkRequest`: the picked boards in pick order. */
export interface SprintBulkLinkBody {
  boardIds: string[];
}

/** The bulk-link body, or null when nothing is picked (the web's Link is disabled then). */
export function bulkLinkBody(picked: readonly string[]): SprintBulkLinkBody | null {
  return picked.length > 0 ? { boardIds: [...picked] } : null;
}

// ── Unlink ───────────────────────────────────────────────────────

/** What happens to the unlinked board's tickets of the sprint. */
export type UnlinkTicketAction = "MOVE_TO_BACKLOG" | "MOVE_TO_SPRINT" | "DELETE";

/** The ticket choices in the web's order. */
export const UNLINK_TICKET_ACTIONS: readonly UnlinkTicketAction[] = [
  "MOVE_TO_BACKLOG",
  "MOVE_TO_SPRINT",
  "DELETE",
];

/** Whether the tickets stay on the unlinked board or move to another board. */
export type UnlinkTarget = "current" | "other";

/** The target choices in the web's order. */
export const UNLINK_TARGETS: readonly UnlinkTarget[] = ["current", "other"];

/** The choices of the unlink view. */
export interface UnlinkChoices {
  target: UnlinkTarget;
  /** The other board picked; read only while `target` is `other`. */
  targetBoardId: string | null;
  ticketAction: UnlinkTicketAction;
  /** The sprint the tickets move to; set only for a sprint move. */
  targetSprintId: string | null;
}

/** The web's defaults, applied each time the dialog opens: the backlog of the unlinked board. */
export const DEFAULT_UNLINK_CHOICES: UnlinkChoices = {
  target: "current",
  targetBoardId: null,
  ticketAction: "MOVE_TO_BACKLOG",
  targetSprintId: null,
};

/**
 * The board whose backlog or sprint receives the tickets: the picked other
 * board, else the unlinked board. With "Other board" and nothing picked yet
 * it stays the unlinked board, as on the web, so its sprints are listed.
 */
export function effectiveBoardId(choices: UnlinkChoices, unlinkedBoardId: string): string {
  return choices.target === "other" && choices.targetBoardId
    ? choices.targetBoardId
    : unlinkedBoardId;
}

/** `next`, with the picked sprint cleared when the receiving board changed. */
function keepSprintOfSameBoard(
  previous: UnlinkChoices,
  next: UnlinkChoices,
  unlinkedBoardId: string
): UnlinkChoices {
  return effectiveBoardId(previous, unlinkedBoardId) === effectiveBoardId(next, unlinkedBoardId)
    ? next
    : { ...next, targetSprintId: null };
}

/** A new target: "Current board" drops the other board picked. */
export function chooseUnlinkTarget(
  choices: UnlinkChoices,
  target: UnlinkTarget,
  unlinkedBoardId: string
): UnlinkChoices {
  const next: UnlinkChoices = {
    ...choices,
    target,
    targetBoardId: target === "current" ? null : choices.targetBoardId,
  };
  return keepSprintOfSameBoard(choices, next, unlinkedBoardId);
}

/** A new other board; the sprint picked on the previous board is cleared. */
export function chooseUnlinkBoard(
  choices: UnlinkChoices,
  boardId: string,
  unlinkedBoardId: string
): UnlinkChoices {
  return keepSprintOfSameBoard(choices, { ...choices, targetBoardId: boardId }, unlinkedBoardId);
}

/** A new ticket choice; leaving the sprint move clears the sprint. */
export function chooseUnlinkAction(
  choices: UnlinkChoices,
  ticketAction: UnlinkTicketAction
): UnlinkChoices {
  return {
    ...choices,
    ticketAction,
    targetSprintId: ticketAction === "MOVE_TO_SPRINT" ? choices.targetSprintId : null,
  };
}

/** A sprint for the sprint move. */
export function chooseUnlinkSprint(choices: UnlinkChoices, sprintId: string): UnlinkChoices {
  return { ...choices, targetSprintId: sprintId };
}

/**
 * The boards "Other board" offers: every board of the tree, the unlinked
 * board listed but disabled with `currentBoardHint`. No role hints: the board
 * list carries no role, and the server refuses a board the user does not
 * administer.
 */
export function unlinkTargetItems(
  rows: readonly BoardRow[],
  unlinkedBoardId: string,
  currentBoardHint: string
): BoardPickItem[] {
  return rows.map((row) => pickItem(row, row.id === unlinkedBoardId ? currentBoardHint : null));
}

/** The sprints the tickets can move to: the receiving board's sprints not completed, other than this one. */
export function unlinkSprintCandidates(sprints: readonly Sprint[], sprintId: string): Sprint[] {
  return sprints.filter((sprint) => sprint.status !== "COMPLETED" && sprint.id !== sprintId);
}

/** Unlink may be pressed once a sprint move has a sprint and "Other board" has a board. */
export function isUnlinkValid(choices: UnlinkChoices): boolean {
  if (choices.ticketAction === "MOVE_TO_SPRINT" && !choices.targetSprintId) return false;
  if (choices.target === "other" && !choices.targetBoardId) return false;
  return true;
}

/** `SprintUnlinkRequest`: delete the tickets, or move them to a board's backlog or sprint. */
export type SprintUnlinkBody =
  | { deleteTickets: true }
  | { transfer: { boardId: string; sprintId?: string } };

/** The web's unlink body, or null while the choices are not valid. */
export function unlinkBody(
  choices: UnlinkChoices,
  unlinkedBoardId: string
): SprintUnlinkBody | null {
  if (!isUnlinkValid(choices)) return null;
  if (choices.ticketAction === "DELETE") return { deleteTickets: true };
  const boardId: string = effectiveBoardId(choices, unlinkedBoardId);
  if (choices.ticketAction === "MOVE_TO_SPRINT" && choices.targetSprintId) {
    return { transfer: { boardId, sprintId: choices.targetSprintId } };
  }
  return { transfer: { boardId } };
}

// ── Link refusals ────────────────────────────────────────────────

/** The 409 for a link that would put a board into a second enabled sprint series. */
export const SPRINT_SERIES_BOARD_TAKEN_CODE = "KAZ_STA_018";

/** A refusal worded by the app: the translation key and its values. */
export interface SprintBoardsMessage {
  key: "sprintBoardsInOtherSeries" | "sprintBoardsInOtherSeriesUnnamed";
  values: Record<string, string>;
}

/**
 * The app's wording for a link refused because a board already belongs to
 * another repeating series, with the board and series names the refusal
 * carries; null for every other refusal, which keeps the server's message.
 */
export function linkRefusalMessage(refusal: {
  code?: string;
  errorValues: Record<string, unknown>;
}): SprintBoardsMessage | null {
  if (refusal.code !== SPRINT_SERIES_BOARD_TAKEN_CODE) return null;
  const { boardName, seriesName } = refusal.errorValues;
  if (typeof boardName !== "string") return null;
  if (typeof seriesName === "string") {
    return {
      key: "sprintBoardsInOtherSeries",
      values: { board: boardName, series: seriesName },
    };
  }
  return { key: "sprintBoardsInOtherSeriesUnnamed", values: { board: boardName } };
}
