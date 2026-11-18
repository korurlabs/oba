import { describe, expect, it } from "bun:test";

import {
  boardPath,
  hasJoinableSprint,
  manageableBoardIds,
  parentSprintOptions,
  partialJoinDescription,
  repeatSummary,
  sprintBoardRows,
  type ParentSprintOption,
} from "@/components/sprints/joinParentSprintModel";
import type { BoardMember, BoardTreeItem, Sprint } from "@/lib/schemas/boards";
import type { SprintSeries } from "@/lib/schemas/sprints";

function board(id: string, childBoards: BoardTreeItem[] = []): BoardTreeItem {
  return { id, name: `Board ${id}`, slug: id, color: `#${id}`, childBoards };
}

function sprint(id: string, status: string): Sprint {
  return { id, name: `Sprint ${id}`, status, startDate: null, endDate: null };
}

function member(userId: string, role: string): BoardMember {
  return { id: `${userId}-${role}`, userId, role };
}

/** a > b > c > d, and a > e; f is another root. */
const D: BoardTreeItem = board("d");
const C: BoardTreeItem = board("c", [D]);
const B: BoardTreeItem = board("b", [C]);
const E: BoardTreeItem = board("e");
const A: BoardTreeItem = board("a", [B, E]);
const F: BoardTreeItem = board("f");
const TREE: BoardTreeItem[] = [A, F];

const ids = (boards: readonly BoardTreeItem[]): string[] => boards.map((item) => item.id);

describe("boardPath", () => {
  it("runs from the root down to the board, both included", () => {
    expect(ids(boardPath(TREE, "d"))).toEqual(["a", "b", "c", "d"]);
  });

  it("is the board alone for a root", () => {
    expect(ids(boardPath(TREE, "f"))).toEqual(["f"]);
  });

  it("is empty for a board the tree does not hold", () => {
    expect(boardPath(TREE, "x")).toEqual([]);
  });
});

describe("manageableBoardIds", () => {
  const members: Record<string, BoardMember[]> = {
    a: [member("me", "OWNER")],
    b: [member("me", "ADMIN")],
    c: [member("me", "MEMBER"), member("other", "ADMIN")],
  };

  it("holds the boards where the user is OWNER or ADMIN", () => {
    expect([...manageableBoardIds(["a", "b", "c", "d"], members, "me")]).toEqual(["a", "b"]);
  });

  it("is empty while the user is not known", () => {
    expect(manageableBoardIds(["a", "b"], members, null).size).toBe(0);
  });
});

describe("parentSprintOptions", () => {
  const path: BoardTreeItem[] = [A, B, C, D];
  const all: ReadonlySet<string> = new Set(["a", "b", "c", "d"]);

  it("lists the ancestors nearest first with their distance", () => {
    const options: ParentSprintOption[] = parentSprintOptions(path, {}, all);
    expect(options.map((option) => [option.board.id, option.distance])).toEqual([
      ["c", 1],
      ["b", 2],
      ["a", 3],
    ]);
  });

  it("takes the ACTIVE sprint of each ancestor, none when its sprints are not read", () => {
    const options: ParentSprintOption[] = parentSprintOptions(
      path,
      { b: [sprint("s0", "COMPLETED"), sprint("s1", "ACTIVE")], c: [sprint("s2", "PLANNED")] },
      all
    );
    expect(options.map((option) => option.activeSprint?.id ?? null)).toEqual([null, "s1", null]);
  });

  it("links a grandparent's sprint through the board in between, top-down", () => {
    const options: ParentSprintOption[] = parentSprintOptions(
      path,
      { b: [sprint("s1", "ACTIVE")] },
      all
    );
    const fromB: ParentSprintOption | undefined = options.find((option) => option.board.id === "b");
    expect(ids(fromB?.linkPath ?? [])).toEqual(["c", "d"]);
  });

  it("skips the boards below that already run the sprint", () => {
    const options: ParentSprintOption[] = parentSprintOptions(
      path,
      { a: [sprint("s1", "ACTIVE")], b: [sprint("s1", "ACTIVE")], c: [sprint("s3", "ACTIVE")] },
      all
    );
    const fromA: ParentSprintOption | undefined = options.find((option) => option.board.id === "a");
    expect(ids(fromA?.linkPath ?? [])).toEqual(["c", "d"]);
  });

  it("names the first board of the link path the user lacks the role on", () => {
    const options: ParentSprintOption[] = parentSprintOptions(
      path,
      { a: [sprint("s1", "ACTIVE")] },
      new Set(["a", "d"])
    );
    const fromA: ParentSprintOption | undefined = options.find((option) => option.board.id === "a");
    expect(fromA?.boardWithoutRole?.id).toBe("b");
  });

  it("needs no role on the ancestor that runs the sprint", () => {
    const options: ParentSprintOption[] = parentSprintOptions(
      path,
      { c: [sprint("s2", "ACTIVE")] },
      new Set(["d"])
    );
    expect(options[0]?.boardWithoutRole).toBeNull();
  });

  it("is empty for a root board", () => {
    expect(parentSprintOptions([F], {}, all)).toEqual([]);
  });
});

describe("hasJoinableSprint", () => {
  const path: BoardTreeItem[] = [A, B, C, D];

  it("is true when an ancestor runs a sprint the user may link down", () => {
    const options: ParentSprintOption[] = parentSprintOptions(
      path,
      { b: [sprint("s1", "ACTIVE")] },
      new Set(["c", "d"])
    );
    expect(hasJoinableSprint(options)).toBe(true);
  });

  it("is false when no ancestor runs a sprint", () => {
    expect(hasJoinableSprint(parentSprintOptions(path, {}, new Set(["b", "c", "d"])))).toBe(false);
  });

  it("is false when the user lacks the role on a board of every link path", () => {
    const options: ParentSprintOption[] = parentSprintOptions(
      path,
      { b: [sprint("s1", "ACTIVE")] },
      new Set(["d"])
    );
    expect(hasJoinableSprint(options)).toBe(false);
  });
});

describe("sprintBoardRows", () => {
  it("lists the sprint and joining boards in tree order, marking the joining ones", () => {
    const rows = sprintBoardRows(
      TREE,
      [
        { id: "e", name: "Board e" },
        { id: "a", name: "Board a" },
      ],
      new Set(["b"])
    );
    expect(rows.map((row) => [row.id, row.depth, row.joins])).toEqual([
      ["a", 0, false],
      ["b", 1, true],
      ["e", 1, false],
    ]);
  });

  it("moves a board up to the depth of its nearest shown ancestor", () => {
    const rows = sprintBoardRows(TREE, [{ id: "a", name: "Board a" }], new Set(["c", "d"]));
    expect(rows.map((row) => [row.id, row.depth])).toEqual([
      ["a", 0],
      ["c", 1],
      ["d", 2],
    ]);
  });

  it("keeps the tree's colour and lists a sprint board missing from the tree last, with its own colour", () => {
    const rows = sprintBoardRows(
      TREE,
      [
        { id: "x", name: "Hidden", color: "#123456" },
        { id: "f", name: "Board f" },
        { id: "y", name: "No colour", color: null },
      ],
      new Set()
    );
    expect(rows).toEqual([
      { id: "f", name: "Board f", color: "#f", depth: 0, joins: false },
      { id: "x", name: "Hidden", color: "#123456", depth: 0, joins: false },
      { id: "y", name: "No colour", color: null, depth: 0, joins: false },
    ]);
  });
});

describe("repeatSummary", () => {
  function series(overrides: Partial<SprintSeries>): SprintSeries {
    return {
      id: "series",
      name: "Platform",
      nextRound: 2,
      nextSprintName: null,
      enabled: true,
      lengthDays: 14,
      unfinishedTicketAction: "MOVE_TO_NEXT_SPRINT",
      creationMode: "AUTOMATIC",
      createDaysBefore: null,
      nextRolloverAt: null,
      nextCreationAt: null,
      lastRunAt: null,
      lastRunStatus: null,
      lastRunMessage: null,
      ...overrides,
    };
  }

  it("says the board joins only this sprint without a series", () => {
    expect(repeatSummary(null, "Mobile")).toEqual({
      value: { key: "sprintJoinParentRepeatNone", values: {} },
      effect: { key: "sprintJoinParentOnce", values: { board: "Mobile" } },
      offReason: null,
    });
  });

  it("counts whole weeks, else days", () => {
    expect(repeatSummary(series({ lengthDays: 14 }), "Mobile").value).toEqual({
      key: "sprintJoinParentRepeatEveryWeeks",
      values: { count: 2 },
    });
    expect(repeatSummary(series({ lengthDays: 10 }), "Mobile").value).toEqual({
      key: "sprintJoinParentRepeatEveryDays",
      values: { count: 10 },
    });
    expect(repeatSummary(series({}), "Mobile").effect).toEqual({
      key: "sprintJoinParentRepeats",
      values: { board: "Mobile" },
    });
  });

  it("names a switched-off series and the reason of its failed run", () => {
    const summary = repeatSummary(
      series({ enabled: false, lastRunStatus: "FAILED", lastRunMessage: "no sprint" }),
      "Mobile"
    );
    expect(summary).toEqual({
      value: { key: "sprintJoinParentRepeatOffShort", values: { series: "Platform" } },
      effect: { key: "sprintJoinParentRepeatOff", values: { board: "Mobile" } },
      offReason: "no sprint",
    });
  });

  it("gives no reason for a series switched off by hand", () => {
    const summary = repeatSummary(
      series({ enabled: false, lastRunStatus: "SUCCESS", lastRunMessage: "ok" }),
      "Mobile"
    );
    expect(summary.offReason).toBeNull();
  });
});

describe("partialJoinDescription", () => {
  it("names the boards linked before the refusal", () => {
    expect(partialJoinDescription(["Board b", "Board c"])).toEqual({
      key: "sprintJoinPartiallyLinked",
      values: { boards: "Board b, Board c" },
    });
  });

  it("is null when no board was linked", () => {
    expect(partialJoinDescription([])).toBeNull();
  });
});
