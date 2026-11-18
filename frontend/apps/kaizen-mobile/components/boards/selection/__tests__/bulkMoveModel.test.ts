import { describe, expect, it } from "bun:test";

import type { BoardRow } from "@/components/boards/boardModel";
import {
  bulkMoveActions,
  bulkMoveBody,
  bulkMoveSprintCandidates,
  bulkMoveTargetItems,
  bulkMoveToast,
  chooseBulkMoveAction,
  chooseBulkMoveBoard,
  chooseBulkMoveSprint,
  chooseBulkMoveSubTickets,
  chooseBulkMoveTarget,
  DEFAULT_BULK_MOVE_CHOICES,
  effectiveBoardId,
  inListOrder,
  isBulkMoveValid,
  moveToBacklogShown,
  selectionHasSubTickets,
  subTicketChoiceShown,
  unfinishedIds,
  type BulkMoveChoices,
} from "@/components/boards/selection/bulkMoveModel";
import type { Sprint } from "@/lib/schemas/boards";
import { ticketBulkMoveResultSchema } from "@/lib/schemas/tickets";

const BOARD = "board-a";

function sprint(id: string, status: string): Sprint {
  return { id, name: `Sprint ${id}`, status, startDate: null, endDate: null };
}

function boardRow(id: string, depth = 0): BoardRow {
  return {
    id,
    name: `Board ${id}`,
    prefix: id.toUpperCase(),
    slug: id,
    color: null,
    icon: null,
    depth,
    parentId: null,
    parentName: null,
    childCount: 0,
  };
}

const sprintMove = (choices: BulkMoveChoices = DEFAULT_BULK_MOVE_CHOICES): BulkMoveChoices =>
  chooseBulkMoveAction(choices, "MOVE_TO_SPRINT");

describe("bulk move actions", () => {
  it("hides Move to backlog for every backlog source, on any target", () => {
    expect(moveToBacklogShown("backlog")).toBe(false);
    expect(bulkMoveActions("backlog")).toEqual(["MOVE_TO_SPRINT"]);
    expect(bulkMoveActions("sprint")).toEqual(["MOVE_TO_BACKLOG", "MOVE_TO_SPRINT"]);
  });

  it("keeps Move to backlog the default, so a backlog move without a sprint goes to the backlog", () => {
    expect(DEFAULT_BULK_MOVE_CHOICES.action).toBe("MOVE_TO_BACKLOG");
    expect(isBulkMoveValid(DEFAULT_BULK_MOVE_CHOICES, 2)).toBe(true);
    expect(bulkMoveBody(DEFAULT_BULK_MOVE_CHOICES, ["t1", "t2"], false)).toEqual({
      ticketIds: ["t1", "t2"],
      action: "MOVE_TO_BACKLOG",
    });
  });

  it("clears the sprint when the action leaves the sprint move", () => {
    const picked: BulkMoveChoices = chooseBulkMoveSprint(sprintMove(), "s1");
    expect(chooseBulkMoveAction(picked, "MOVE_TO_BACKLOG").targetSprintId).toBeNull();
  });
});

describe("bulk move target board", () => {
  it("keeps Confirm off on Other board until a board is picked", () => {
    const other: BulkMoveChoices = chooseBulkMoveTarget(DEFAULT_BULK_MOVE_CHOICES, "other", BOARD);
    expect(isBulkMoveValid(other, 1)).toBe(false);
    expect(bulkMoveBody(other, ["t1"], false)).toBeNull();
    expect(isBulkMoveValid(chooseBulkMoveBoard(other, "board-b", BOARD), 1)).toBe(true);
  });

  it("reads the viewed board's sprints until another board is picked", () => {
    const other: BulkMoveChoices = chooseBulkMoveTarget(DEFAULT_BULK_MOVE_CHOICES, "other", BOARD);
    expect(effectiveBoardId(other, BOARD)).toBe(BOARD);
    expect(effectiveBoardId(chooseBulkMoveBoard(other, "board-b", BOARD), BOARD)).toBe("board-b");
  });

  it("clears the sprint when the receiving board changes, and keeps it otherwise", () => {
    const onA: BulkMoveChoices = chooseBulkMoveSprint(sprintMove(), "s1");
    const otherUnpicked: BulkMoveChoices = chooseBulkMoveTarget(onA, "other", BOARD);
    expect(otherUnpicked.targetSprintId).toBe("s1");
    const onB: BulkMoveChoices = chooseBulkMoveBoard(otherUnpicked, "board-b", BOARD);
    expect(onB.targetSprintId).toBeNull();
    const backToCurrent: BulkMoveChoices = chooseBulkMoveTarget(
      chooseBulkMoveSprint(onB, "sb"),
      "current",
      BOARD
    );
    expect(backToCurrent).toMatchObject({ targetBoardId: null, targetSprintId: null });
  });

  it("lists every board with the viewed one disabled under the hint", () => {
    const items = bulkMoveTargetItems([boardRow(BOARD), boardRow("board-b", 1)], BOARD, "Current");
    expect(items.map((item) => [item.id, item.disabled, item.note, item.indent])).toEqual([
      [BOARD, true, "Current", 0],
      ["board-b", false, null, 1],
    ]);
  });
});

describe("bulk move sprints", () => {
  const sprints: Sprint[] = [
    sprint("s1", "ACTIVE"),
    sprint("s2", "PLANNED"),
    sprint("s3", "COMPLETED"),
  ];

  it("offers this board's open sprints without the viewed one", () => {
    const ids: string[] = bulkMoveSprintCandidates(sprints, sprintMove(), "s1").map((s) => s.id);
    expect(ids).toEqual(["s2"]);
  });

  it("keeps the viewed sprint on another board, where a shared sprint stays listed", () => {
    const other: BulkMoveChoices = chooseBulkMoveBoard(
      chooseBulkMoveTarget(sprintMove(), "other", BOARD),
      "board-b",
      BOARD
    );
    expect(bulkMoveSprintCandidates(sprints, other, "s1").map((s) => s.id)).toEqual(["s1", "s2"]);
  });

  it("needs a sprint for a sprint move", () => {
    expect(isBulkMoveValid(sprintMove(), 1)).toBe(false);
    expect(isBulkMoveValid(chooseBulkMoveSprint(sprintMove(), "s2"), 1)).toBe(true);
    expect(isBulkMoveValid(DEFAULT_BULK_MOVE_CHOICES, 0)).toBe(false);
  });
});

describe("bulk move sub-tickets", () => {
  const rows = [
    { id: "p", status: "TODO", subTickets: [{}] },
    { id: "q", status: "DONE", subTickets: [] },
    { id: "r", status: "IN_PROGRESS", subTickets: null },
  ];

  it("reads only the selected tickets", () => {
    expect(selectionHasSubTickets(rows, ["p", "q"])).toBe(true);
    expect(selectionHasSubTickets(rows, ["q", "r"])).toBe(false);
  });

  it("shows the choice on this board only", () => {
    const other: BulkMoveChoices = chooseBulkMoveTarget(DEFAULT_BULK_MOVE_CHOICES, "other", BOARD);
    expect(subTicketChoiceShown(DEFAULT_BULK_MOVE_CHOICES, true)).toBe(true);
    expect(subTicketChoiceShown(DEFAULT_BULK_MOVE_CHOICES, false)).toBe(false);
    expect(subTicketChoiceShown(other, true)).toBe(false);
  });

  it("sends the sub-ticket mode only while the choice shows", () => {
    const none: BulkMoveChoices = chooseBulkMoveSubTickets(DEFAULT_BULK_MOVE_CHOICES, "NONE");
    expect(bulkMoveBody(none, ["p"], true)?.subTicketMode).toBe("NONE");
    expect(bulkMoveBody(none, ["p"], false)).not.toHaveProperty("subTicketMode");
  });
});

describe("bulk move body", () => {
  it("names the sprint and the other board, and never a backlog", () => {
    const choices: BulkMoveChoices = chooseBulkMoveSprint(
      chooseBulkMoveBoard(chooseBulkMoveTarget(sprintMove(), "other", BOARD), "board-b", BOARD),
      "sb"
    );
    const body = bulkMoveBody(choices, ["t1"], true);
    expect(body).toEqual({
      ticketIds: ["t1"],
      action: "MOVE_TO_SPRINT",
      targetSprintId: "sb",
      targetBoardId: "board-b",
    });
    expect(JSON.stringify(body)).not.toContain("backlog");
  });
});

describe("selection helpers", () => {
  const rows = [
    { id: "a", status: "TODO" },
    { id: "b", status: "DONE" },
    { id: "c", status: "IN_REVIEW" },
  ];

  it("picks every listed ticket that is not Done, in list order", () => {
    expect(unfinishedIds(rows)).toEqual(["a", "c"]);
  });

  it("orders picked ids as the list does", () => {
    expect(inListOrder(rows, new Set(["c", "a"]))).toEqual(["a", "c"]);
  });
});

describe("bulk move result", () => {
  // The data of a real `POST .../tickets/bulk-move` that moved a milestone's
  // ticket to an unrelated board.
  const MILESTONE_CLEARED = { movedCount: 2, milestoneClearedTicketKeys: ["MEIB-2"] };
  const PLAIN = { movedCount: 2, milestoneClearedTicketKeys: [] };

  it("parses the server's result", () => {
    expect(ticketBulkMoveResultSchema.parse(MILESTONE_CLEARED)).toEqual(MILESTONE_CLEARED);
    expect(ticketBulkMoveResultSchema.parse({ movedCount: 1 })).toEqual({ movedCount: 1 });
  });

  it("warns with the keys of the tickets that lost their milestone", () => {
    expect(bulkMoveToast(ticketBulkMoveResultSchema.parse(MILESTONE_CLEARED))).toEqual({
      tone: "warning",
      count: 1,
      keys: "MEIB-2",
    });
    expect(bulkMoveToast(ticketBulkMoveResultSchema.parse(PLAIN))).toEqual({ tone: "success" });
    expect(bulkMoveToast({ movedCount: 1, milestoneClearedTicketKeys: null })).toEqual({
      tone: "success",
    });
  });
});
