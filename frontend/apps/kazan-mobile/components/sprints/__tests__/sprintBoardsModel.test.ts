import { describe, expect, it } from "bun:test";

import type { BoardRow } from "@/components/boards/boardModel";
import {
  bulkLinkBody,
  chooseUnlinkAction,
  chooseUnlinkBoard,
  chooseUnlinkSprint,
  chooseUnlinkTarget,
  DEFAULT_UNLINK_CHOICES,
  effectiveBoardId,
  isUnlinkValid,
  linkCandidates,
  linkedBoardRows,
  linkRefusalMessage,
  togglePickedBoard,
  unlinkBody,
  unlinkSprintCandidates,
  unlinkTargetItems,
  type UnlinkChoices,
} from "@/components/sprints/sprintBoardsModel";
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

/** The flattened tree: a > (b > c), d. */
const ROWS: BoardRow[] = [row("a", 0, null), row("b", 1, "a"), row("c", 2, "b"), row("d", 0, null)];

function sprint(id: string, status: string): Sprint {
  return { id, name: `Sprint ${id}`, status, startDate: null, endDate: null };
}

const UNLINKED = "b";

describe("linkedBoardRows", () => {
  it("lists the linked boards in tree order, whatever order the sprint names them in", () => {
    const rows = linkedBoardRows(ROWS, ["d", "c", "a"], "a");
    expect(rows.map((item) => item.board.id)).toEqual(["a", "c", "d"]);
    expect(rows.map((item) => item.board.depth)).toEqual([0, 2, 0]);
  });

  it("leaves out a linked board the tree does not hold", () => {
    const rows = linkedBoardRows(ROWS, ["a", "hidden"], "a");
    expect(rows.map((item) => item.board.id)).toEqual(["a"]);
  });

  it("marks only the board Edit sprint was opened from as current", () => {
    const rows = linkedBoardRows(ROWS, ["a", "b"], "b");
    expect(rows.map((item) => item.current)).toEqual([false, true]);
  });

  it("is empty when no linked board is in the tree", () => {
    expect(linkedBoardRows(ROWS, [], "a")).toEqual([]);
  });
});

describe("linkCandidates", () => {
  it("lists every board and disables only the linked ones, with the reason", () => {
    const items = linkCandidates(ROWS, ["a", "c"], "Already linked");
    expect(items.map((item) => item.id)).toEqual(["a", "b", "c", "d"]);
    expect(items.map((item) => item.disabled)).toEqual([true, false, true, false]);
    expect(items.map((item) => item.note)).toEqual([
      "Already linked",
      null,
      "Already linked",
      null,
    ]);
    expect(items.map((item) => item.indent)).toEqual([0, 1, 2, 0]);
  });
});

describe("togglePickedBoard and bulkLinkBody", () => {
  it("keeps the boards in pick order", () => {
    let picked: string[] = [];
    picked = togglePickedBoard(picked, "d");
    picked = togglePickedBoard(picked, "b");
    picked = togglePickedBoard(picked, "c");
    expect(bulkLinkBody(picked)).toEqual({ boardIds: ["d", "b", "c"] });
  });

  it("unticks a picked board and keeps the rest in order", () => {
    expect(togglePickedBoard(["d", "b", "c"], "b")).toEqual(["d", "c"]);
  });

  it("sends nothing with no board picked", () => {
    expect(bulkLinkBody([])).toBeNull();
  });
});

describe("unlink defaults and validity", () => {
  it("defaults to the backlog of the unlinked board", () => {
    expect(DEFAULT_UNLINK_CHOICES).toEqual({
      target: "current",
      targetBoardId: null,
      ticketAction: "MOVE_TO_BACKLOG",
      targetSprintId: null,
    });
    expect(effectiveBoardId(DEFAULT_UNLINK_CHOICES, UNLINKED)).toBe(UNLINKED);
    expect(isUnlinkValid(DEFAULT_UNLINK_CHOICES)).toBe(true);
  });

  it("refuses a sprint move without a sprint", () => {
    const choices = chooseUnlinkAction(DEFAULT_UNLINK_CHOICES, "MOVE_TO_SPRINT");
    expect(isUnlinkValid(choices)).toBe(false);
    expect(isUnlinkValid(chooseUnlinkSprint(choices, "s1"))).toBe(true);
  });

  it("refuses Other board without a board picked, for every action", () => {
    const other = chooseUnlinkTarget(DEFAULT_UNLINK_CHOICES, "other", UNLINKED);
    expect(isUnlinkValid(other)).toBe(false);
    expect(isUnlinkValid(chooseUnlinkAction(other, "DELETE"))).toBe(false);
    expect(unlinkBody(other, UNLINKED)).toBeNull();
    expect(isUnlinkValid(chooseUnlinkBoard(other, "d", UNLINKED))).toBe(true);
  });
});

describe("unlink resets", () => {
  it("clears the sprint when the action leaves the sprint move", () => {
    const withSprint = chooseUnlinkSprint(
      chooseUnlinkAction(DEFAULT_UNLINK_CHOICES, "MOVE_TO_SPRINT"),
      "s1"
    );
    expect(chooseUnlinkAction(withSprint, "MOVE_TO_BACKLOG").targetSprintId).toBeNull();
  });

  it("clears the sprint when another board receives the tickets", () => {
    const other: UnlinkChoices = chooseUnlinkBoard(
      chooseUnlinkTarget(DEFAULT_UNLINK_CHOICES, "other", UNLINKED),
      "d",
      UNLINKED
    );
    const withSprint = chooseUnlinkSprint(chooseUnlinkAction(other, "MOVE_TO_SPRINT"), "s1");
    expect(chooseUnlinkBoard(withSprint, "a", UNLINKED).targetSprintId).toBeNull();
    expect(chooseUnlinkBoard(withSprint, "d", UNLINKED).targetSprintId).toBe("s1");
  });

  it("drops the other board and its sprint when going back to Current board", () => {
    const other = chooseUnlinkBoard(
      chooseUnlinkTarget(DEFAULT_UNLINK_CHOICES, "other", UNLINKED),
      "d",
      UNLINKED
    );
    const withSprint = chooseUnlinkSprint(chooseUnlinkAction(other, "MOVE_TO_SPRINT"), "s1");
    const current = chooseUnlinkTarget(withSprint, "current", UNLINKED);
    expect(current.targetBoardId).toBeNull();
    expect(current.targetSprintId).toBeNull();
  });

  it("keeps the sprint when Other board is chosen before a board is picked, as on the web", () => {
    const withSprint = chooseUnlinkSprint(
      chooseUnlinkAction(DEFAULT_UNLINK_CHOICES, "MOVE_TO_SPRINT"),
      "s1"
    );
    const other = chooseUnlinkTarget(withSprint, "other", UNLINKED);
    expect(effectiveBoardId(other, UNLINKED)).toBe(UNLINKED);
    expect(other.targetSprintId).toBe("s1");
  });
});

describe("unlinkTargetItems and unlinkSprintCandidates", () => {
  it("lists every board, the unlinked one disabled with the hint", () => {
    const items = unlinkTargetItems(ROWS, UNLINKED, "Current board");
    expect(items.map((item) => item.id)).toEqual(["a", "b", "c", "d"]);
    expect(items.filter((item) => item.disabled).map((item) => item.id)).toEqual([UNLINKED]);
    expect(items.find((item) => item.id === UNLINKED)?.note).toBe("Current board");
  });

  it("offers sprints that are not completed and not the unlinked sprint", () => {
    const sprints: Sprint[] = [
      sprint("this", "ACTIVE"),
      sprint("planned", "PLANNED"),
      sprint("active", "ACTIVE"),
      sprint("done", "COMPLETED"),
    ];
    expect(unlinkSprintCandidates(sprints, "this").map((item) => item.id)).toEqual([
      "planned",
      "active",
    ]);
  });
});

describe("unlinkBody", () => {
  const other = chooseUnlinkBoard(
    chooseUnlinkTarget(DEFAULT_UNLINK_CHOICES, "other", UNLINKED),
    "d",
    UNLINKED
  );

  it("moves to the unlinked board's backlog", () => {
    expect(unlinkBody(DEFAULT_UNLINK_CHOICES, UNLINKED)).toEqual({
      transfer: { boardId: UNLINKED },
    });
  });

  it("moves to a sprint of the unlinked board", () => {
    const choices = chooseUnlinkSprint(
      chooseUnlinkAction(DEFAULT_UNLINK_CHOICES, "MOVE_TO_SPRINT"),
      "s1"
    );
    expect(unlinkBody(choices, UNLINKED)).toEqual({
      transfer: { boardId: UNLINKED, sprintId: "s1" },
    });
  });

  it("moves to the other board's backlog", () => {
    expect(unlinkBody(other, UNLINKED)).toEqual({ transfer: { boardId: "d" } });
  });

  it("moves to a sprint of the other board", () => {
    const choices = chooseUnlinkSprint(chooseUnlinkAction(other, "MOVE_TO_SPRINT"), "s2");
    expect(unlinkBody(choices, UNLINKED)).toEqual({ transfer: { boardId: "d", sprintId: "s2" } });
  });

  it("deletes the tickets, whatever the target", () => {
    expect(unlinkBody(chooseUnlinkAction(DEFAULT_UNLINK_CHOICES, "DELETE"), UNLINKED)).toEqual({
      deleteTickets: true,
    });
    expect(unlinkBody(chooseUnlinkAction(other, "DELETE"), UNLINKED)).toEqual({
      deleteTickets: true,
    });
  });
});

describe("linkRefusalMessage", () => {
  it("names the board and the series of a board in another series", () => {
    expect(
      linkRefusalMessage({
        code: "KAZ_STA_018",
        errorValues: { boardName: "Ops", seriesName: "Weekly" },
      })
    ).toEqual({ key: "sprintBoardsInOtherSeries", values: { board: "Ops", series: "Weekly" } });
  });

  it("names only the board when the series name is missing", () => {
    expect(linkRefusalMessage({ code: "KAZ_STA_018", errorValues: { boardName: "Ops" } })).toEqual({
      key: "sprintBoardsInOtherSeriesUnnamed",
      values: { board: "Ops" },
    });
  });

  it("keeps the server's message for any other refusal", () => {
    expect(linkRefusalMessage({ code: "KAZ_STA_018", errorValues: {} })).toBeNull();
    expect(linkRefusalMessage({ code: "KAZ_VAL_001", errorValues: {} })).toBeNull();
  });
});
