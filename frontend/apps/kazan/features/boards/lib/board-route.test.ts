import { describe, expect, test } from "bun:test";
import {
  BOARD_DEFAULT_TAB,
  BOARD_FIXED_SEGMENTS,
  BOARD_TAB,
  BOARD_TAB_ORDER,
  CLASSIC_BOARD_TAB_ORDER,
  boardDefaultTab,
  boardPath,
  boardTabOrder,
} from "./board-route";

describe("board tabs", () => {
  test("are listed Sprint, Backlog, Roadmap, All", () => {
    expect(BOARD_TAB_ORDER).toEqual(["sprint", "backlog", "roadmap", "all"]);
  });

  test("list every tab exactly once", () => {
    expect([...BOARD_TAB_ORDER].sort()).toEqual(Object.values(BOARD_TAB).sort());
  });

  test("are fixed segments, so no sprint slug can take their place", () => {
    for (const tab of BOARD_TAB_ORDER) {
      expect(BOARD_FIXED_SEGMENTS.has(tab)).toBe(true);
    }
  });
});

describe("default board tab", () => {
  test("is the Sprint tab, the first one listed", () => {
    expect(BOARD_DEFAULT_TAB).toBe(BOARD_TAB.sprint);
    expect(BOARD_DEFAULT_TAB).toBe(BOARD_TAB_ORDER[0]!);
  });

  test("builds the sprint tab path, and explicit tabs keep their own path", () => {
    expect(boardPath("ops", BOARD_DEFAULT_TAB)).toBe("/board/ops/sprint");
    expect(boardPath("ops", BOARD_TAB.all)).toBe("/board/ops/all");
    expect(boardPath("ops", BOARD_TAB.backlog)).toBe("/board/ops/backlog");
    expect(boardPath("ops", BOARD_TAB.roadmap)).toBe("/board/ops/roadmap");
  });
});

describe("board tabs per layout", () => {
  test("list All first in the classic layout: All, Sprint, Backlog, Roadmap", () => {
    expect(CLASSIC_BOARD_TAB_ORDER).toEqual(["all", "sprint", "backlog", "roadmap"]);
    expect(boardTabOrder("classic")).toEqual(CLASSIC_BOARD_TAB_ORDER);
  });

  test("keep the modern order, with All last", () => {
    expect(boardTabOrder("modern")).toEqual(BOARD_TAB_ORDER);
    expect(boardTabOrder("modern").at(-1)).toBe(BOARD_TAB.all);
  });

  test("list every tab exactly once in the classic order", () => {
    expect([...CLASSIC_BOARD_TAB_ORDER].sort()).toEqual(Object.values(BOARD_TAB).sort());
  });

  test("open a board on All in the classic layout and on Sprint in the modern one", () => {
    expect(boardDefaultTab("classic")).toBe(BOARD_TAB.all);
    expect(boardDefaultTab("modern")).toBe(BOARD_DEFAULT_TAB);
    for (const layout of ["classic", "modern"] as const) {
      expect(boardDefaultTab(layout)).toBe(boardTabOrder(layout)[0]!);
    }
  });
});
