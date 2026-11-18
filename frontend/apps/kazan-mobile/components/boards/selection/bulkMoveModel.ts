import type { BoardRow } from "@/components/boards/boardModel";
import type { BoardPickItem } from "@/components/boards/boardPickModel";
import type { Sprint } from "@/lib/schemas/boards";
import type { TicketBulkMoveResult } from "@/lib/schemas/tickets";

/*
 * The pure half of Move selected tickets, with the rules of Kazan web's bulk
 * move dialog: the choices and their defaults, how they reset one another,
 * which actions and sprints are offered, when the sub-ticket choice shows,
 * when Move may be pressed, the request body and the result toast. No React
 * or native import, so `bun test` covers it.
 */

/** The list the selected tickets come from. */
export type BulkMoveSource = "sprint" | "backlog";

/** `TicketRelocationAction` without DELETE, which a bulk move refuses. */
export type BulkMoveAction = "MOVE_TO_BACKLOG" | "MOVE_TO_SPRINT";

/** Whether the tickets stay on the viewed board or move to another one. */
export type BulkMoveTarget = "current" | "other";

/** The target choices in the web's order. */
export const BULK_MOVE_TARGETS: readonly BulkMoveTarget[] = ["current", "other"];

/**
 * Which sub-tickets come along. A bulk move offers no "pick": one checklist
 * cannot cover the sub-tickets of several parents.
 */
export type BulkMoveSubTicketMode = "NONE" | "UNFINISHED";

/** The sub-ticket choices in the web's order. */
export const BULK_MOVE_SUB_TICKET_MODES: readonly BulkMoveSubTicketMode[] = ["NONE", "UNFINISHED"];

/** The choices of the Move sheet. */
export interface BulkMoveChoices {
  target: BulkMoveTarget;
  /** The other board picked; read only while `target` is `other`. */
  targetBoardId: string | null;
  action: BulkMoveAction;
  /** The sprint the tickets move to; set only for a sprint move. */
  targetSprintId: string | null;
  subTicketMode: BulkMoveSubTicketMode;
}

/**
 * The web's defaults, applied each time the sheet opens: this board's
 * backlog, with the unfinished sub-tickets. Move to backlog stays the default
 * even where it is not offered (`moveToBacklogShown`), as on the web.
 */
export const DEFAULT_BULK_MOVE_CHOICES: BulkMoveChoices = {
  target: "current",
  targetBoardId: null,
  action: "MOVE_TO_BACKLOG",
  targetSprintId: null,
  subTicketMode: "UNFINISHED",
};

/** Move to backlog is offered unless the tickets come from a backlog, whatever the target board. */
export function moveToBacklogShown(source: BulkMoveSource): boolean {
  return source !== "backlog";
}

/** The action radios the sheet offers, in the web's order. */
export function bulkMoveActions(source: BulkMoveSource): BulkMoveAction[] {
  return moveToBacklogShown(source) ? ["MOVE_TO_BACKLOG", "MOVE_TO_SPRINT"] : ["MOVE_TO_SPRINT"];
}

/**
 * The board whose backlog or sprint receives the tickets: the picked other
 * board, else the viewed board. With "Other board" and nothing picked yet it
 * stays the viewed board, as on the web, so its sprints are listed.
 */
export function effectiveBoardId(choices: BulkMoveChoices, boardId: string): string {
  return choices.target === "other" && choices.targetBoardId ? choices.targetBoardId : boardId;
}

/** `next`, with the picked sprint cleared when the receiving board changed. */
function keepSprintOfSameBoard(
  previous: BulkMoveChoices,
  next: BulkMoveChoices,
  boardId: string
): BulkMoveChoices {
  return effectiveBoardId(previous, boardId) === effectiveBoardId(next, boardId)
    ? next
    : { ...next, targetSprintId: null };
}

/** A new target: "Current board" drops the other board picked. */
export function chooseBulkMoveTarget(
  choices: BulkMoveChoices,
  target: BulkMoveTarget,
  boardId: string
): BulkMoveChoices {
  const next: BulkMoveChoices = {
    ...choices,
    target,
    targetBoardId: target === "current" ? null : choices.targetBoardId,
  };
  return keepSprintOfSameBoard(choices, next, boardId);
}

/** A new other board; the sprint picked on the previous board is cleared. */
export function chooseBulkMoveBoard(
  choices: BulkMoveChoices,
  targetBoardId: string,
  boardId: string
): BulkMoveChoices {
  return keepSprintOfSameBoard(choices, { ...choices, targetBoardId }, boardId);
}

/** A new action; leaving the sprint move clears the sprint. */
export function chooseBulkMoveAction(
  choices: BulkMoveChoices,
  action: BulkMoveAction
): BulkMoveChoices {
  return {
    ...choices,
    action,
    targetSprintId: action === "MOVE_TO_SPRINT" ? choices.targetSprintId : null,
  };
}

/** A sprint for the sprint move. */
export function chooseBulkMoveSprint(choices: BulkMoveChoices, sprintId: string): BulkMoveChoices {
  return { ...choices, targetSprintId: sprintId };
}

/** A sub-ticket choice. */
export function chooseBulkMoveSubTickets(
  choices: BulkMoveChoices,
  subTicketMode: BulkMoveSubTicketMode
): BulkMoveChoices {
  return { ...choices, subTicketMode };
}

/**
 * The sprints the tickets can move to: the receiving board's sprints that are
 * not completed, without the viewed sprint while the target is this board.
 * On another board a sprint shared with this board stays listed, as on the web.
 */
export function bulkMoveSprintCandidates(
  sprints: readonly Sprint[],
  choices: BulkMoveChoices,
  currentSprintId: string | null
): Sprint[] {
  return sprints.filter(
    (sprint) =>
      sprint.status !== "COMPLETED" && (choices.target === "other" || sprint.id !== currentSprintId)
  );
}

/**
 * The boards "Other board" offers: every board of the tree, the viewed board
 * listed but disabled with `currentBoardHint`. No role hints: the board list
 * carries no role, and the server refuses a board the user cannot add to.
 */
export function bulkMoveTargetItems(
  rows: readonly BoardRow[],
  boardId: string,
  currentBoardHint: string
): BoardPickItem[] {
  return rows.map((row) => {
    const current: boolean = row.id === boardId;
    return {
      id: row.id,
      name: row.name,
      prefix: row.prefix,
      color: row.color,
      indent: row.depth,
      disabled: current,
      note: current ? currentBoardHint : null,
      noteTone: "muted",
    };
  });
}

/** A listed ticket as far as a bulk move reads it. */
interface MovableRow {
  id: string;
  status: string;
  subTickets?: readonly unknown[] | null;
}

/** Whether a selected ticket among the listed rows has sub-tickets. */
export function selectionHasSubTickets(
  rows: readonly MovableRow[],
  ticketIds: readonly string[]
): boolean {
  const selected: Set<string> = new Set(ticketIds);
  return rows.some((row) => selected.has(row.id) && (row.subTickets?.length ?? 0) > 0);
}

/**
 * The sub-ticket choice shows only while the target is this board and a
 * selected ticket has sub-tickets: a ticket that changes board always takes
 * its whole subtree.
 */
export function subTicketChoiceShown(choices: BulkMoveChoices, hasSubTickets: boolean): boolean {
  return choices.target === "current" && hasSubTickets;
}

/**
 * Move may be pressed once there is a ticket, a sprint move has a sprint and
 * "Other board" has a board. From a backlog the hidden Move to backlog stays
 * the default, so Move without a sprint moves the tickets to the backlog, as
 * on the web.
 */
export function isBulkMoveValid(choices: BulkMoveChoices, ticketCount: number): boolean {
  if (ticketCount === 0) return false;
  if (choices.action === "MOVE_TO_SPRINT" && !choices.targetSprintId) return false;
  if (choices.target === "other" && !choices.targetBoardId) return false;
  return true;
}

/**
 * `TicketBulkMoveRequest`. It names no backlog: a board has exactly one, and
 * the server resolves the receiving board's.
 */
export interface TicketBulkMoveBody {
  ticketIds: string[];
  action: BulkMoveAction;
  targetSprintId?: string;
  targetBoardId?: string;
  subTicketMode?: BulkMoveSubTicketMode;
  /**
   * With `targetBoardId`: leave the sub-tickets on the old board without a
   * parent instead of taking them along.
   */
  detachSubTickets?: boolean;
}

/** The web's request body, or null while the choices are not valid. */
export function bulkMoveBody(
  choices: BulkMoveChoices,
  ticketIds: readonly string[],
  hasSubTickets: boolean
): TicketBulkMoveBody | null {
  if (!isBulkMoveValid(choices, ticketIds.length)) return null;
  const body: TicketBulkMoveBody = { ticketIds: [...ticketIds], action: choices.action };
  if (choices.action === "MOVE_TO_SPRINT" && choices.targetSprintId) {
    body.targetSprintId = choices.targetSprintId;
  }
  if (choices.target === "other" && choices.targetBoardId) {
    body.targetBoardId = choices.targetBoardId;
  }
  if (subTicketChoiceShown(choices, hasSubTickets)) body.subTicketMode = choices.subTicketMode;
  return body;
}

/** The tickets "Move unfinished tickets" selects: every listed one that is not Done, in list order. */
export function unfinishedIds(rows: readonly MovableRow[]): string[] {
  return rows.filter((row) => row.status !== "DONE").map((row) => row.id);
}

/** The ids of `ticketIds` in the order of `rows`, the board order the list shows. */
export function inListOrder(rows: readonly MovableRow[], ticketIds: ReadonlySet<string>): string[] {
  return rows.filter((row) => ticketIds.has(row.id)).map((row) => row.id);
}

/** The toast after a bulk move. */
export type BulkMoveToast =
  | { tone: "success" }
  /** Tickets that moved to a board unrelated to their milestone's board lost the milestone. */
  | { tone: "warning"; count: number; keys: string };

/** A warning naming the tickets that lost their milestone, else a plain success, as on the web. */
export function bulkMoveToast(result: TicketBulkMoveResult): BulkMoveToast {
  const keys: string[] = result.milestoneClearedTicketKeys ?? [];
  return keys.length > 0
    ? { tone: "warning", count: keys.length, keys: keys.join(", ") }
    : { tone: "success" };
}
