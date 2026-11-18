import { describe, expect, it } from "bun:test";

import type { BoardRow } from "@/components/boards/boardModel";
import {
  isBoardPinned,
  pinnedBoardRows,
  readSidebarPreferences,
  toggleBoardPin,
  withSidebarPreferences,
  type SidebarPreferences,
} from "@/components/boards/boardPins";

function row(id: string, parentId: string | null = null): BoardRow {
  return {
    id,
    name: id.toUpperCase(),
    prefix: null,
    slug: id,
    color: null,
    icon: null,
    depth: parentId ? 1 : 0,
    parentId,
    parentName: parentId ? parentId.toUpperCase() : null,
    childCount: 0,
  };
}

const EMPTY: SidebarPreferences = readSidebarPreferences(null);

describe("readSidebarPreferences", () => {
  it("gives empty lists without a saved record", () => {
    expect(EMPTY).toEqual({
      boardOrder: [],
      pinnedBoardIds: [],
      childBoardOrder: {},
      pinnedChildBoardIds: {},
      pinnedDisplayOrder: [],
    });
  });

  it("keeps only string ids and drops fields of another shape", () => {
    const sidebar = readSidebarPreferences({
      sidebar: {
        pinnedBoardIds: ["a", 3, null],
        boardOrder: "b",
        pinnedChildBoardIds: { a: ["c", 4] },
        childBoardOrder: ["x"],
      },
    });
    expect(sidebar.pinnedBoardIds).toEqual(["a"]);
    expect(sidebar.boardOrder).toEqual([]);
    expect(sidebar.pinnedChildBoardIds).toEqual({ a: ["c"] });
    expect(sidebar.childBoardOrder).toEqual({});
  });
});

describe("toggleBoardPin", () => {
  it("pins a top-level board as the web does", () => {
    const before: SidebarPreferences = { ...EMPTY, boardOrder: ["a", "b"] };
    const after = toggleBoardPin(before, row("a"));
    expect(after.pinnedBoardIds).toEqual(["a"]);
    expect(after.boardOrder).toEqual(["b"]);
    expect(after.pinnedDisplayOrder).toEqual(["a"]);
    expect(isBoardPinned(after, row("a"))).toBe(true);
    expect(before.pinnedBoardIds).toEqual([]);
  });

  it("unpins a top-level board back to the end of the board order", () => {
    const pinned: SidebarPreferences = {
      ...EMPTY,
      boardOrder: ["b"],
      pinnedBoardIds: ["a"],
      pinnedDisplayOrder: ["a"],
    };
    const after = toggleBoardPin(pinned, row("a"));
    expect(after.pinnedBoardIds).toEqual([]);
    expect(after.boardOrder).toEqual(["b", "a"]);
    expect(after.pinnedDisplayOrder).toEqual([]);
  });

  it("keeps a child pin under the direct parent", () => {
    const before: SidebarPreferences = { ...EMPTY, childBoardOrder: { p: ["c", "d"] } };
    const pinned = toggleBoardPin(before, row("c", "p"));
    expect(pinned.pinnedChildBoardIds).toEqual({ p: ["c"] });
    expect(pinned.childBoardOrder).toEqual({ p: ["d"] });
    expect(pinned.pinnedDisplayOrder).toEqual(["c"]);
    expect(isBoardPinned(pinned, row("c", "p"))).toBe(true);
    expect(isBoardPinned(pinned, row("c"))).toBe(false);

    const unpinned = toggleBoardPin(pinned, row("c", "p"));
    expect(unpinned.pinnedChildBoardIds).toEqual({});
    expect(unpinned.childBoardOrder).toEqual({ p: ["d", "c"] });
    expect(unpinned.pinnedDisplayOrder).toEqual([]);
  });

  it("does not list a board twice in the display order", () => {
    const stale: SidebarPreferences = { ...EMPTY, pinnedDisplayOrder: ["a"] };
    expect(toggleBoardPin(stale, row("a")).pinnedDisplayOrder).toEqual(["a"]);
  });
});

describe("withSidebarPreferences", () => {
  it("keeps the other preferences and unknown sidebar fields", () => {
    const preferences = { theme: "dark", sidebar: { collapsed: true, pinnedBoardIds: ["x"] } };
    const next = withSidebarPreferences(preferences, toggleBoardPin(EMPTY, row("a")));
    expect(next.theme).toBe("dark");
    expect(next.sidebar).toMatchObject({ collapsed: true, pinnedBoardIds: ["a"] });
  });
});

describe("pinnedBoardRows", () => {
  const rows: BoardRow[] = [row("a"), row("c", "a"), row("b"), row("d", "b")];

  it("follows the display order, then the other pins, top level first", () => {
    const sidebar: SidebarPreferences = {
      ...EMPTY,
      pinnedBoardIds: ["a", "b"],
      pinnedChildBoardIds: { b: ["d"] },
      pinnedDisplayOrder: ["d", "b"],
    };
    expect(pinnedBoardRows(rows, sidebar).map((pinned) => pinned.id)).toEqual(["d", "b", "a"]);
  });

  it("drops a pin whose board moved or is gone", () => {
    const sidebar: SidebarPreferences = {
      ...EMPTY,
      pinnedBoardIds: ["c", "gone"],
      pinnedChildBoardIds: { a: ["d"] },
      pinnedDisplayOrder: ["c", "gone", "d"],
    };
    expect(pinnedBoardRows(rows, sidebar)).toEqual([]);
  });
});
