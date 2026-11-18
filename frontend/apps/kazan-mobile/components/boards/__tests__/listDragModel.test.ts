import { describe, expect, it } from "bun:test";

import {
  edgeScrollOffset,
  groupAt,
  listBottomInset,
  listDropAction,
  sameScope,
  type ListDropGroup,
} from "@/components/boards/listDragModel";
import type { BoardTicket } from "@/lib/schemas/boards";

const BOARD = "board-1";

function ticket(
  id: string,
  status: string,
  displayOrder: number | null,
  boardId: string = BOARD
): BoardTicket {
  return {
    id,
    createdAt: "2026-11-01T09:00:00Z",
    title: `Ticket ${id}`,
    key: `KZN-${id}`,
    displayOrder,
    type: "FEATURE",
    status,
    priority: "MEDIUM",
    missionRank: "M",
    dueDate: null,
    assignees: [],
    board: { id: boardId, name: "Board", prefix: boardId === BOARD ? "KZN" : "MOB" },
  } as BoardTicket;
}

const a = ticket("a", "TODO", 1);
const b = ticket("b", "IN_PROGRESS", 2);
const c = ticket("c", "TODO", 3);
const child = ticket("m", "TODO", 4, "board-2");
const x = ticket("x", "TODO", 10);
const y = ticket("y", "TODO", 11);

const sprintRows: BoardTicket[] = [a, b, c, child];
const sprintGroup: ListDropGroup = {
  key: "sprint",
  place: { scope: { sprintId: "s1" }, rows: sprintRows },
  rows: sprintRows,
  accepts: true,
};
const backlogGroup: ListDropGroup = {
  key: "backlog",
  place: { scope: { backlogId: "b1" }, rows: [x, y] },
  rows: [x, y],
  accepts: true,
};

function statusGroup(status: "TODO" | "IN_PROGRESS", rows: BoardTicket[]): ListDropGroup {
  return {
    key: status,
    place: { scope: { sprintId: "s1" }, rows: sprintRows },
    rows,
    status,
    accepts: true,
  };
}

describe("sameScope", () => {
  it("compares sprints and backlogs by id", () => {
    expect(sameScope({ sprintId: "s1" }, { sprintId: "s1" })).toBe(true);
    expect(sameScope({ sprintId: "s1" }, { sprintId: "s2" })).toBe(false);
    expect(sameScope({ backlogId: "b1" }, { backlogId: "b1" })).toBe(true);
    expect(sameScope({ sprintId: "b1" }, { backlogId: "b1" })).toBe(false);
  });
});

describe("listDropAction", () => {
  it("reorders within a group", () => {
    expect(listDropAction(c, sprintGroup, sprintGroup, "a", BOARD)).toEqual({
      kind: "reorder",
      beforeId: "a",
    });
    expect(listDropAction(a, sprintGroup, sprintGroup, null, BOARD)).toEqual({
      kind: "reorder",
      beforeId: null,
    });
  });

  it("changes nothing when the row lands where it was", () => {
    expect(listDropAction(a, sprintGroup, sprintGroup, "b", BOARD)).toBeNull();
    expect(listDropAction(a, sprintGroup, sprintGroup, "a", BOARD)).toBeNull();
    expect(listDropAction(child, sprintGroup, sprintGroup, null, BOARD)).toBeNull();
  });

  it("plans a ticket into the other scope at the slot", () => {
    expect(listDropAction(b, sprintGroup, backlogGroup, "y", BOARD)).toEqual({
      kind: "place",
      scope: { backlogId: "b1" },
      beforeId: "y",
    });
    expect(listDropAction(x, backlogGroup, sprintGroup, null, BOARD)).toEqual({
      kind: "place",
      scope: { sprintId: "s1" },
      beforeId: null,
    });
  });

  it("keeps a child board's ticket in its own scope", () => {
    expect(listDropAction(child, sprintGroup, backlogGroup, null, BOARD)).toBeNull();
    expect(listDropAction(child, sprintGroup, sprintGroup, "a", BOARD)).toEqual({
      kind: "reorder",
      beforeId: "a",
    });
  });

  it("drops nothing into a group that takes no drops", () => {
    const sorted: ListDropGroup = { ...backlogGroup, accepts: false };
    expect(listDropAction(a, sprintGroup, sorted, null, BOARD)).toBeNull();
    expect(listDropAction(y, sorted, sorted, "x", BOARD)).toBeNull();
  });

  it("moves between the status groups of a sprint list", () => {
    const todo: ListDropGroup = statusGroup("TODO", [a, c, child]);
    const doing: ListDropGroup = statusGroup("IN_PROGRESS", [b]);
    expect(listDropAction(a, todo, doing, "b", BOARD)).toEqual({
      kind: "status",
      target: { status: "IN_PROGRESS", beforeId: "b" },
    });
    expect(listDropAction(c, todo, todo, "a", BOARD)).toEqual({
      kind: "status",
      target: { status: "TODO", beforeId: "a" },
    });
    expect(listDropAction(a, todo, todo, "c", BOARD)).toBeNull();
  });
});

describe("groupAt", () => {
  const tops = [
    { key: "backlog", top: 900 },
    { key: "sprint", top: 120 },
  ];

  it("finds the last group that starts above the finger", () => {
    expect(groupAt(tops, 500)).toBe("sprint");
    expect(groupAt(tops, 900)).toBe("backlog");
    expect(groupAt(tops, 2000)).toBe("backlog");
  });

  it("takes the first group above them all, and none without groups", () => {
    expect(groupAt(tops, 10)).toBe("sprint");
    expect(groupAt([], 10)).toBeNull();
  });
});

describe("edgeScrollOffset", () => {
  const extent = (contentHeight: number, viewportHeight: number, bottomInset: number = 0) => ({
    contentHeight,
    viewportHeight,
    bottomInset,
  });

  it("steps within the content", () => {
    expect(edgeScrollOffset(100, 1, extent(2000, 800), 10)).toBe(110);
    expect(edgeScrollOffset(100, -1, extent(2000, 800), 10)).toBe(90);
  });

  it("stops at the top and at the end", () => {
    expect(edgeScrollOffset(4, -1, extent(2000, 800), 10)).toBe(0);
    expect(edgeScrollOffset(1195, 1, extent(2000, 800), 10)).toBe(1200);
    expect(edgeScrollOffset(0, 1, extent(500, 800), 10)).toBe(0);
  });

  it("scrolls on past the content end by the bottom inset", () => {
    expect(edgeScrollOffset(1195, 1, extent(2000, 800, 83), 10)).toBe(1205);
    expect(edgeScrollOffset(1280, 1, extent(2000, 800, 83), 10)).toBe(1283);
    expect(edgeScrollOffset(0, 1, extent(700, 800, 83), 10)).toBe(0);
  });
});

describe("listBottomInset", () => {
  it("is the part of the list box below the safe area", () => {
    // A full-height list on an 852 pt screen with an 83 pt tab bar area.
    expect(listBottomInset({ y: 100, height: 752 }, 852 - 83)).toBe(83);
  });

  it("is zero for a list that ends above the safe area bottom", () => {
    expect(listBottomInset({ y: 100, height: 500 }, 769)).toBe(0);
  });
});
