import { describe, expect, it } from "bun:test";

import type { BoardRow } from "@/components/boards/boardModel";
import {
  chooseChildBoardAction,
  chooseNewParent,
  chooseTargetBoard,
  chooseTargetSprint,
  chooseTicketAction,
  confirmLines,
  DEFAULT_DELETE_BOARD_CHOICES,
  deleteBoardContext,
  deleteBoardParams,
  deletedBoardIds,
  isChildActionLocked,
  isDeleteBoardValid,
  isDeletedBoardQuery,
  newParentRows,
  targetSprintCandidates,
  ticketTargetRows,
  type DeleteBoardChoices,
  type DeleteBoardContext,
} from "@/components/boards/settings/deleteBoardModel";
import type { Sprint } from "@/lib/schemas/boards";

function row(id: string, depth: number, parentId: string | null): BoardRow {
  return {
    id,
    name: `Board ${id}`,
    prefix: id.toUpperCase(),
    slug: id,
    color: null,
    icon: null,
    depth,
    parentId,
    parentName: parentId ? `Board ${parentId}` : null,
    childCount: 0,
  };
}

/*
 * The flattened tree, depth first:
 *   other
 *   target (deleted) -> child -> grandchild
 *                    -> second
 *   leaf
 */
const ROWS: BoardRow[] = [
  row("other", 0, null),
  row("target", 0, null),
  row("child", 1, "target"),
  row("grandchild", 2, "child"),
  row("second", 1, "target"),
  row("leaf", 0, null),
];

const WITH_CHILDREN: DeleteBoardContext = deleteBoardContext(ROWS, "target");
const NO_CHILDREN: DeleteBoardContext = deleteBoardContext(ROWS, "leaf");

function sprint(id: string, status: string): Sprint {
  return { id, name: `Sprint ${id}`, slug: null, status, startDate: null, endDate: null };
}

describe("deleteBoardContext", () => {
  it("lists every descendant, depth first, with the direct children marked", () => {
    expect(WITH_CHILDREN.descendants.map((entry) => entry.board.id)).toEqual([
      "child",
      "grandchild",
      "second",
    ]);
    expect(WITH_CHILDREN.descendants.map((entry) => entry.direct)).toEqual([true, false, true]);
    expect(WITH_CHILDREN.hasChildren).toBe(true);
    expect(NO_CHILDREN.hasChildren).toBe(false);
  });
});

describe("choices", () => {
  it("defaults to deleting the tickets and the child boards", () => {
    expect(DEFAULT_DELETE_BOARD_CHOICES).toEqual({
      ticketAction: "DELETE",
      targetBoardId: null,
      targetSprintId: null,
      childBoardAction: "DELETE",
      newParentBoardId: null,
    });
  });

  it("clears the target and the sprint when the tickets are deleted again", () => {
    let choices: DeleteBoardChoices = chooseTicketAction(
      DEFAULT_DELETE_BOARD_CHOICES,
      "MOVE_TO_SPRINT"
    );
    choices = chooseTargetBoard(choices, "other", WITH_CHILDREN);
    choices = chooseTargetSprint(choices, "s1");
    expect(chooseTicketAction(choices, "DELETE")).toMatchObject({
      targetBoardId: null,
      targetSprintId: null,
    });
  });

  it("keeps the target but clears the sprint on a switch to the backlog", () => {
    let choices: DeleteBoardChoices = chooseTicketAction(
      DEFAULT_DELETE_BOARD_CHOICES,
      "MOVE_TO_SPRINT"
    );
    choices = chooseTargetBoard(choices, "other", WITH_CHILDREN);
    choices = chooseTargetSprint(choices, "s1");
    expect(chooseTicketAction(choices, "MOVE_TO_BACKLOG")).toMatchObject({
      targetBoardId: "other",
      targetSprintId: null,
    });
  });

  it("clears the sprint when another target board is picked, not when the same is", () => {
    let choices: DeleteBoardChoices = chooseTicketAction(
      DEFAULT_DELETE_BOARD_CHOICES,
      "MOVE_TO_SPRINT"
    );
    choices = chooseTargetSprint(chooseTargetBoard(choices, "other", WITH_CHILDREN), "s1");
    expect(chooseTargetBoard(choices, "other", WITH_CHILDREN)).toBe(choices);
    expect(chooseTargetBoard(choices, "leaf", WITH_CHILDREN).targetSprintId).toBeNull();
  });

  it("makes the child boards standalone when the tickets move to a descendant", () => {
    let choices: DeleteBoardChoices = chooseChildBoardAction(
      chooseTicketAction(DEFAULT_DELETE_BOARD_CHOICES, "MOVE_TO_BACKLOG"),
      "MOVE",
      WITH_CHILDREN
    );
    choices = chooseNewParent(choices, "other");
    choices = chooseTargetBoard(choices, "grandchild", WITH_CHILDREN);
    expect(choices.childBoardAction).toBe("PROMOTE");
    expect(choices.newParentBoardId).toBeNull();
    expect(isChildActionLocked(choices, WITH_CHILDREN)).toBe(true);
    expect(chooseChildBoardAction(choices, "DELETE", WITH_CHILDREN)).toBe(choices);
    expect(chooseChildBoardAction(choices, "MOVE", WITH_CHILDREN)).toBe(choices);
  });

  it("frees the child choice again when the target leaves the descendants", () => {
    let choices: DeleteBoardChoices = chooseTicketAction(
      DEFAULT_DELETE_BOARD_CHOICES,
      "MOVE_TO_BACKLOG"
    );
    choices = chooseTargetBoard(choices, "child", WITH_CHILDREN);
    choices = chooseTargetBoard(choices, "other", WITH_CHILDREN);
    expect(isChildActionLocked(choices, WITH_CHILDREN)).toBe(false);
    expect(chooseChildBoardAction(choices, "DELETE", WITH_CHILDREN).childBoardAction).toBe(
      "DELETE"
    );
  });

  it("clears the new parent when the child boards stop moving", () => {
    let choices: DeleteBoardChoices = chooseChildBoardAction(
      DEFAULT_DELETE_BOARD_CHOICES,
      "MOVE",
      WITH_CHILDREN
    );
    choices = chooseNewParent(choices, "other");
    expect(chooseChildBoardAction(choices, "PROMOTE", WITH_CHILDREN).newParentBoardId).toBeNull();
  });
});

describe("candidates", () => {
  it("offers every board but the deleted one as ticket target, descendants included", () => {
    expect(ticketTargetRows(ROWS, "target").map((entry) => entry.id)).toEqual([
      "other",
      "child",
      "grandchild",
      "second",
      "leaf",
    ]);
  });

  it("offers no descendant as new parent", () => {
    expect(newParentRows(ROWS, "target").map((entry) => entry.id)).toEqual(["other", "leaf"]);
  });

  it("offers every sprint that is not completed", () => {
    const sprints: Sprint[] = [
      sprint("a", "ACTIVE"),
      sprint("b", "COMPLETED"),
      sprint("c", "PLANNED"),
    ];
    expect(targetSprintCandidates(sprints).map((entry) => entry.id)).toEqual(["a", "c"]);
  });
});

describe("isDeleteBoardValid", () => {
  it("accepts the defaults", () => {
    expect(isDeleteBoardValid(DEFAULT_DELETE_BOARD_CHOICES, WITH_CHILDREN)).toBe(true);
  });

  it("needs a target for a move and a sprint for a sprint move", () => {
    const backlog: DeleteBoardChoices = chooseTicketAction(
      DEFAULT_DELETE_BOARD_CHOICES,
      "MOVE_TO_BACKLOG"
    );
    expect(isDeleteBoardValid(backlog, NO_CHILDREN)).toBe(false);
    expect(isDeleteBoardValid(chooseTargetBoard(backlog, "other", NO_CHILDREN), NO_CHILDREN)).toBe(
      true
    );
    const toSprint: DeleteBoardChoices = chooseTargetBoard(
      chooseTicketAction(DEFAULT_DELETE_BOARD_CHOICES, "MOVE_TO_SPRINT"),
      "other",
      NO_CHILDREN
    );
    expect(isDeleteBoardValid(toSprint, NO_CHILDREN)).toBe(false);
    expect(isDeleteBoardValid(chooseTargetSprint(toSprint, "s1"), NO_CHILDREN)).toBe(true);
  });

  it("needs a new parent for a child move", () => {
    const move: DeleteBoardChoices = chooseChildBoardAction(
      DEFAULT_DELETE_BOARD_CHOICES,
      "MOVE",
      WITH_CHILDREN
    );
    expect(isDeleteBoardValid(move, WITH_CHILDREN)).toBe(false);
    expect(isDeleteBoardValid(chooseNewParent(move, "other"), WITH_CHILDREN)).toBe(true);
  });
});

describe("deleteBoardParams", () => {
  it("sends only the ticket choice for a board without children", () => {
    expect(deleteBoardParams(DEFAULT_DELETE_BOARD_CHOICES, NO_CHILDREN)).toEqual({
      ticketAction: "DELETE",
    });
  });

  it("sends the child choice whenever the board has children", () => {
    expect(deleteBoardParams(DEFAULT_DELETE_BOARD_CHOICES, WITH_CHILDREN)).toEqual({
      ticketAction: "DELETE",
      childBoardAction: "DELETE",
    });
  });

  it("sends the target of a backlog move without a sprint", () => {
    const choices: DeleteBoardChoices = chooseTargetBoard(
      chooseTicketAction(DEFAULT_DELETE_BOARD_CHOICES, "MOVE_TO_BACKLOG"),
      "other",
      NO_CHILDREN
    );
    expect(deleteBoardParams(choices, NO_CHILDREN)).toEqual({
      ticketAction: "MOVE_TO_BACKLOG",
      targetBoardId: "other",
    });
  });

  it("sends the target and the sprint of a sprint move, and the new parent of a child move", () => {
    let choices: DeleteBoardChoices = chooseTicketAction(
      DEFAULT_DELETE_BOARD_CHOICES,
      "MOVE_TO_SPRINT"
    );
    choices = chooseTargetSprint(chooseTargetBoard(choices, "leaf", WITH_CHILDREN), "s1");
    choices = chooseNewParent(chooseChildBoardAction(choices, "MOVE", WITH_CHILDREN), "other");
    expect(deleteBoardParams(choices, WITH_CHILDREN)).toEqual({
      ticketAction: "MOVE_TO_SPRINT",
      targetBoardId: "leaf",
      targetSprintId: "s1",
      childBoardAction: "MOVE",
      newParentBoardId: "other",
    });
  });
});

describe("deletedBoardIds and isDeletedBoardQuery", () => {
  it("names the descendants only when the child boards are deleted", () => {
    expect(deletedBoardIds(DEFAULT_DELETE_BOARD_CHOICES, WITH_CHILDREN)).toEqual([
      "target",
      "child",
      "grandchild",
      "second",
    ]);
    const promote: DeleteBoardChoices = chooseChildBoardAction(
      DEFAULT_DELETE_BOARD_CHOICES,
      "PROMOTE",
      WITH_CHILDREN
    );
    expect(deletedBoardIds(promote, WITH_CHILDREN)).toEqual(["target"]);
    expect(deletedBoardIds(DEFAULT_DELETE_BOARD_CHOICES, NO_CHILDREN)).toEqual(["leaf"]);
  });

  it("matches a board-scoped key by its fourth part", () => {
    const deleted: ReadonlySet<string> = new Set(["target"]);
    expect(isDeletedBoardQuery(["boards", "org", "detail", "target"], deleted)).toBe(true);
    expect(isDeletedBoardQuery(["tickets", "org", "board", "target", { page: 0 }], deleted)).toBe(
      true
    );
    expect(isDeletedBoardQuery(["boards", "org", "detail", "other"], deleted)).toBe(false);
    expect(isDeletedBoardQuery(["boards", "org", "tree"], deleted)).toBe(false);
  });
});

describe("confirmLines", () => {
  const sprints: Sprint[] = [sprint("s1", "ACTIVE")];

  it("names the deleted tickets, the open count, the deleted child boards and the warning", () => {
    expect(
      confirmLines(DEFAULT_DELETE_BOARD_CHOICES, WITH_CHILDREN, {
        rows: ROWS,
        sprints: [],
        openCounts: { target: 2, child: 1, grandchild: 4, other: 9 },
      })
    ).toEqual([
      { key: "deleteBoardConfirmTicketsDelete" },
      { key: "deleteBoardConfirmOpenTickets", values: { count: 7 } },
      { key: "deleteBoardConfirmChildrenDelete", values: { count: 3 } },
      { key: "deleteBoardConfirmIrreversible" },
    ]);
  });

  it("leaves the open count out while it is unknown", () => {
    expect(
      confirmLines(DEFAULT_DELETE_BOARD_CHOICES, NO_CHILDREN, {
        rows: ROWS,
        sprints: [],
        openCounts: undefined,
      })
    ).toEqual([
      { key: "deleteBoardConfirmTicketsDelete" },
      { key: "deleteBoardConfirmIrreversible" },
    ]);
  });

  it("names the sprint and board of a sprint move and the new parent of a child move", () => {
    let choices: DeleteBoardChoices = chooseTicketAction(
      DEFAULT_DELETE_BOARD_CHOICES,
      "MOVE_TO_SPRINT"
    );
    choices = chooseTargetSprint(chooseTargetBoard(choices, "leaf", WITH_CHILDREN), "s1");
    choices = chooseNewParent(chooseChildBoardAction(choices, "MOVE", WITH_CHILDREN), "other");
    expect(
      confirmLines(choices, WITH_CHILDREN, {
        rows: ROWS,
        sprints,
        openCounts: { target: 3, child: 5 },
      })
    ).toEqual([
      {
        key: "deleteBoardConfirmTicketsSprint",
        values: { sprint: "Sprint s1", board: "Board leaf" },
      },
      { key: "deleteBoardConfirmOpenTickets", values: { count: 3 } },
      { key: "deleteBoardConfirmChildrenMove", values: { count: 2, board: "Board other" } },
      { key: "deleteBoardConfirmIrreversible" },
    ]);
  });

  it("counts the direct children made standalone after a backlog move", () => {
    const choices: DeleteBoardChoices = chooseTargetBoard(
      chooseTicketAction(DEFAULT_DELETE_BOARD_CHOICES, "MOVE_TO_BACKLOG"),
      "child",
      WITH_CHILDREN
    );
    expect(
      confirmLines(choices, WITH_CHILDREN, { rows: ROWS, sprints, openCounts: undefined })
    ).toEqual([
      { key: "deleteBoardConfirmTicketsBacklog", values: { board: "Board child" } },
      { key: "deleteBoardConfirmChildrenPromote", values: { count: 2 } },
      { key: "deleteBoardConfirmIrreversible" },
    ]);
  });
});
