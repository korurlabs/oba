import { describe, expect, it } from "bun:test";

import {
  clearBulkMove,
  getBulkMove,
  showBulkMove,
  type BulkMoveSelection,
} from "@/components/boards/selection/bulkMoveStore";
import {
  allPicked,
  pruneToListed,
  selectableIn,
  selectAllInGroup,
  selectionGroupOf,
  startSelection,
  toggleInSelection,
  type SelectionState,
} from "@/components/boards/selection/useTicketSelection";

const ids = (state: SelectionState): string[] => [...state.ids].sort();

describe("selection groups", () => {
  it("maps a sprint scope and a backlog scope to their group", () => {
    expect(selectionGroupOf({ sprintId: "s1" })).toBe("sprint");
    expect(selectionGroupOf({ backlogId: "b1" })).toBe("backlog");
  });

  it("starts with the given tickets picked", () => {
    const state: SelectionState = startSelection("sprint", ["a", "b"]);
    expect(state.group).toBe("sprint");
    expect(ids(state)).toEqual(["a", "b"]);
  });

  it("lets the first pick decide the group and refuses the other group after it", () => {
    const empty: SelectionState = startSelection(null, []);
    expect(selectableIn(empty, "backlog")).toBe(true);
    const picked: SelectionState = toggleInSelection(empty, "backlog", "x");
    expect(picked.group).toBe("backlog");
    expect(selectableIn(picked, "sprint")).toBe(false);
    expect(toggleInSelection(picked, "sprint", "y")).toBe(picked);
  });

  it("frees the other group once the last ticket is unpicked", () => {
    const none: SelectionState = toggleInSelection(
      startSelection("backlog", ["x"]),
      "backlog",
      "x"
    );
    expect(none.ids.size).toBe(0);
    const sprint: SelectionState = toggleInSelection(none, "sprint", "y");
    expect(sprint.group).toBe("sprint");
    expect(ids(sprint)).toEqual(["y"]);
  });
});

describe("select all", () => {
  it("picks every row, then none once all are picked", () => {
    const all: SelectionState = selectAllInGroup(startSelection(null, []), "sprint", ["a", "b"]);
    expect(ids(all)).toEqual(["a", "b"]);
    expect(allPicked(all, "sprint", ["a", "b"])).toBe(true);
    expect(selectAllInGroup(all, "sprint", ["a", "b"]).ids.size).toBe(0);
  });

  it("does nothing on the group that does not hold the selection", () => {
    const sprint: SelectionState = startSelection("sprint", ["a"]);
    expect(selectAllInGroup(sprint, "backlog", ["x"])).toBe(sprint);
    expect(allPicked(sprint, "backlog", ["x"])).toBe(false);
    expect(allPicked(sprint, "sprint", [])).toBe(false);
  });
});

describe("pruneToListed", () => {
  it("drops the tickets that left the list and keeps the state otherwise", () => {
    const state: SelectionState = startSelection("sprint", ["a", "b"]);
    expect(ids(pruneToListed(state, ["a", "c"]))).toEqual(["a"]);
    expect(pruneToListed(state, ["a", "b", "c"])).toBe(state);
  });
});

describe("bulkMoveStore", () => {
  const selection = (boardId: string): BulkMoveSelection => ({
    boardId,
    ticketIds: ["a"],
    source: "sprint",
    currentSprintId: "s1",
    hasSubTickets: false,
    onMoved: () => undefined,
  });

  it("keeps a newer sheet's selection when an older sheet clears its own", () => {
    const first: BulkMoveSelection = selection("b1");
    const second: BulkMoveSelection = selection("b2");
    showBulkMove(first);
    showBulkMove(second);
    clearBulkMove(first);
    expect(getBulkMove()).toBe(second);
    clearBulkMove(second);
    expect(getBulkMove()).toBeNull();
  });
});
