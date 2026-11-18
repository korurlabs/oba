import { parentCandidates, type BoardRow } from "@/components/boards/boardModel";
import {
  childBoardRows,
  type ChildBoardRow,
} from "@/components/boards/settings/boardSettingsModel";
import type { TranslationKey } from "@/i18n";
import type { Sprint } from "@/lib/schemas/boards";

/*
 * The pure half of Delete board, as Kazan web's delete dialog decides it:
 * what happens to the board's tickets and child boards, how the choices reset
 * one another, when Delete may be pressed, the query of the one `DELETE`
 * request, which boards it deletes, and the lines of the final confirmation.
 * No React or native import, so `bun test` covers it.
 */

/** What happens to every ticket of the deleted board (`TicketRelocationAction`). */
export type DeleteTicketAction = "DELETE" | "MOVE_TO_BACKLOG" | "MOVE_TO_SPRINT";

/** What happens to the child boards of the deleted board (`BoardChildBoardAction`). */
export type DeleteChildBoardAction = "DELETE" | "PROMOTE" | "MOVE";

/** The ticket choices in the web's order. */
export const DELETE_TICKET_ACTIONS: readonly DeleteTicketAction[] = [
  "DELETE",
  "MOVE_TO_BACKLOG",
  "MOVE_TO_SPRINT",
];

/** The child board choices in the web's order. */
export const DELETE_CHILD_BOARD_ACTIONS: readonly DeleteChildBoardAction[] = [
  "DELETE",
  "PROMOTE",
  "MOVE",
];

/** The choices of the sheet. */
export interface DeleteBoardChoices {
  ticketAction: DeleteTicketAction;
  /** The board the tickets move to; set only for a move. */
  targetBoardId: string | null;
  /** The sprint the tickets move to; set only for a sprint move. */
  targetSprintId: string | null;
  /** Read only when the board has child boards. */
  childBoardAction: DeleteChildBoardAction;
  /** The new parent of the child boards; set only for a child move. */
  newParentBoardId: string | null;
}

/** The web's defaults, applied each time the dialog opens: tickets and child boards are deleted. */
export const DEFAULT_DELETE_BOARD_CHOICES: DeleteBoardChoices = {
  ticketAction: "DELETE",
  targetBoardId: null,
  targetSprintId: null,
  childBoardAction: "DELETE",
  newParentBoardId: null,
};

/** The deleted board and its place in the board tree. */
export interface DeleteBoardContext {
  boardId: string;
  /** Every descendant, depth first, indented per level below the board. */
  descendants: ChildBoardRow[];
  descendantIds: ReadonlySet<string>;
  /** True when the board has child boards; only then is a child choice asked and sent. */
  hasChildren: boolean;
}

/** The context of deleting `boardId`, from the flattened board tree. */
export function deleteBoardContext(rows: readonly BoardRow[], boardId: string): DeleteBoardContext {
  const descendants: ChildBoardRow[] = childBoardRows(rows, boardId);
  return {
    boardId,
    descendants,
    descendantIds: new Set(descendants.map((row) => row.board.id)),
    hasChildren: descendants.length > 0,
  };
}

/** True for the two choices that move the tickets to another board. */
export function isTicketMove(action: DeleteTicketAction): boolean {
  return action === "MOVE_TO_BACKLOG" || action === "MOVE_TO_SPRINT";
}

/**
 * True when the tickets move to a descendant of the deleted board. Deleting
 * the child boards, or moving them away, would then take the target with
 * them, so only "standalone" stays open.
 */
export function isChildActionLocked(choices: DeleteBoardChoices, ctx: DeleteBoardContext): boolean {
  return (
    ctx.hasChildren &&
    choices.targetBoardId !== null &&
    ctx.descendantIds.has(choices.targetBoardId)
  );
}

/** A new ticket choice: leaving the moves clears the target, leaving the sprint move the sprint. */
export function chooseTicketAction(
  choices: DeleteBoardChoices,
  action: DeleteTicketAction
): DeleteBoardChoices {
  return {
    ...choices,
    ticketAction: action,
    targetBoardId: isTicketMove(action) ? choices.targetBoardId : null,
    targetSprintId: action === "MOVE_TO_SPRINT" ? choices.targetSprintId : null,
  };
}

/**
 * A new target board clears the picked sprint, which belongs to the old one.
 * A descendant as target makes the child boards standalone.
 */
export function chooseTargetBoard(
  choices: DeleteBoardChoices,
  targetBoardId: string,
  ctx: DeleteBoardContext
): DeleteBoardChoices {
  if (targetBoardId === choices.targetBoardId) return choices;
  const next: DeleteBoardChoices = { ...choices, targetBoardId, targetSprintId: null };
  return isChildActionLocked(next, ctx)
    ? { ...next, childBoardAction: "PROMOTE", newParentBoardId: null }
    : next;
}

/** The sprint the tickets move to. */
export function chooseTargetSprint(
  choices: DeleteBoardChoices,
  targetSprintId: string
): DeleteBoardChoices {
  return { ...choices, targetSprintId };
}

/** A new child choice; leaving the move clears the new parent. Refused while only standalone is open. */
export function chooseChildBoardAction(
  choices: DeleteBoardChoices,
  action: DeleteChildBoardAction,
  ctx: DeleteBoardContext
): DeleteBoardChoices {
  if (action !== "PROMOTE" && isChildActionLocked(choices, ctx)) return choices;
  return {
    ...choices,
    childBoardAction: action,
    newParentBoardId: action === "MOVE" ? choices.newParentBoardId : null,
  };
}

/** The new parent of the child boards. */
export function chooseNewParent(
  choices: DeleteBoardChoices,
  newParentBoardId: string
): DeleteBoardChoices {
  return { ...choices, newParentBoardId };
}

/**
 * The boards the tickets may move to: every board of the tree but the deleted
 * one, its descendants included. None is disabled: the web's role hints read a
 * role the board list never carries, so they never show there either.
 */
export function ticketTargetRows(rows: readonly BoardRow[], boardId: string): BoardRow[] {
  return rows.filter((row) => row.id !== boardId);
}

/** The boards the child boards may move under: every board but the deleted one and its descendants. */
export function newParentRows(rows: readonly BoardRow[], boardId: string): BoardRow[] {
  return parentCandidates(rows, boardId);
}

/** The target board's sprints the tickets may move to: every one not completed, in the server's order. */
export function targetSprintCandidates(sprints: readonly Sprint[]): Sprint[] {
  return sprints.filter((sprint) => sprint.status !== "COMPLETED");
}

/** The web form's rule: a move needs a target, a sprint move a sprint, a child move a parent. */
export function isDeleteBoardValid(choices: DeleteBoardChoices, ctx: DeleteBoardContext): boolean {
  if (isTicketMove(choices.ticketAction) && choices.targetBoardId === null) return false;
  if (choices.ticketAction === "MOVE_TO_SPRINT" && choices.targetSprintId === null) return false;
  if (ctx.hasChildren && choices.childBoardAction === "MOVE" && choices.newParentBoardId === null) {
    return false;
  }
  return true;
}

/** The query of `DELETE /kazan/boards/{boardId}`; a value that does not apply is left out. */
export interface DeleteBoardParams {
  ticketAction: DeleteTicketAction;
  targetBoardId?: string;
  targetSprintId?: string;
  childBoardAction?: DeleteChildBoardAction;
  newParentBoardId?: string;
}

/**
 * The query the web sends: the ticket choice always; the target only for a
 * move and the sprint only for a sprint move; the child choice only when the
 * board has child boards, and the new parent only for a child move. No
 * backlog is named: the server takes the target board's only backlog.
 */
export function deleteBoardParams(
  choices: DeleteBoardChoices,
  ctx: DeleteBoardContext
): DeleteBoardParams {
  const params: DeleteBoardParams = { ticketAction: choices.ticketAction };
  if (isTicketMove(choices.ticketAction) && choices.targetBoardId !== null) {
    params.targetBoardId = choices.targetBoardId;
  }
  if (choices.ticketAction === "MOVE_TO_SPRINT" && choices.targetSprintId !== null) {
    params.targetSprintId = choices.targetSprintId;
  }
  if (ctx.hasChildren) {
    params.childBoardAction = choices.childBoardAction;
    if (choices.childBoardAction === "MOVE" && choices.newParentBoardId !== null) {
      params.newParentBoardId = choices.newParentBoardId;
    }
  }
  return params;
}

/** The boards the request deletes: the board, and every descendant when the child boards are deleted. */
export function deletedBoardIds(choices: DeleteBoardChoices, ctx: DeleteBoardContext): string[] {
  return ctx.hasChildren && choices.childBoardAction === "DELETE"
    ? [ctx.boardId, ...ctx.descendantIds]
    : [ctx.boardId];
}

/**
 * True for a query of one of `deletedIds`. Board-scoped keys are laid out as
 * `[domain, orgId, kind, boardId, ...]`, under the board keys and under the
 * ticket keys alike, so the fourth part names the board.
 */
export function isDeletedBoardQuery(
  queryKey: readonly unknown[],
  deletedIds: ReadonlySet<string>
): boolean {
  const boardId: unknown = queryKey[3];
  return typeof boardId === "string" && deletedIds.has(boardId);
}

/** One line of the final confirmation, as a translation key and its values. */
export interface DeleteConfirmLine {
  key: TranslationKey;
  values?: Record<string, string | number>;
}

/** What the final confirmation names besides the choices. */
export interface DeleteConfirmInputs {
  /** The flattened board tree, for the names of the target and the new parent. */
  rows: readonly BoardRow[];
  /** The target board's sprints, for the name of the target sprint. */
  sprints: readonly Sprint[];
  /** Open root tickets per board; undefined while they load or after a failed read. */
  openCounts: Readonly<Record<string, number>> | undefined;
}

/**
 * The body of the final confirmation, one line per choice: where the tickets
 * go, how many open tickets the deleted boards hold (left out while unknown),
 * what happens to the child boards, and that it cannot be undone. The child
 * count is every descendant when they are deleted, and the direct children
 * otherwise, since only those change parent.
 */
export function confirmLines(
  choices: DeleteBoardChoices,
  ctx: DeleteBoardContext,
  inputs: DeleteConfirmInputs
): DeleteConfirmLine[] {
  const nameOf = (boardId: string | null): string =>
    inputs.rows.find((row) => row.id === boardId)?.name ?? "";
  const lines: DeleteConfirmLine[] = [];

  switch (choices.ticketAction) {
    case "DELETE":
      lines.push({ key: "deleteBoardConfirmTicketsDelete" });
      break;
    case "MOVE_TO_BACKLOG":
      lines.push({
        key: "deleteBoardConfirmTicketsBacklog",
        values: { board: nameOf(choices.targetBoardId) },
      });
      break;
    case "MOVE_TO_SPRINT":
      lines.push({
        key: "deleteBoardConfirmTicketsSprint",
        values: {
          sprint: inputs.sprints.find((sprint) => sprint.id === choices.targetSprintId)?.name ?? "",
          board: nameOf(choices.targetBoardId),
        },
      });
      break;
  }

  if (inputs.openCounts) {
    const counts: Readonly<Record<string, number>> = inputs.openCounts;
    const count: number = deletedBoardIds(choices, ctx).reduce(
      (sum, boardId) => sum + (counts[boardId] ?? 0),
      0
    );
    lines.push({ key: "deleteBoardConfirmOpenTickets", values: { count } });
  }

  if (ctx.hasChildren) {
    const direct: number = ctx.descendants.filter((row) => row.direct).length;
    switch (choices.childBoardAction) {
      case "DELETE":
        lines.push({
          key: "deleteBoardConfirmChildrenDelete",
          values: { count: ctx.descendants.length },
        });
        break;
      case "PROMOTE":
        lines.push({ key: "deleteBoardConfirmChildrenPromote", values: { count: direct } });
        break;
      case "MOVE":
        lines.push({
          key: "deleteBoardConfirmChildrenMove",
          values: { count: direct, board: nameOf(choices.newParentBoardId) },
        });
        break;
    }
  }

  lines.push({ key: "deleteBoardConfirmIrreversible" });
  return lines;
}
