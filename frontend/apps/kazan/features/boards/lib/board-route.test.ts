import { describe, expect, test } from "bun:test";
import {
  BOARD_DEFAULT_TAB,
  BOARD_FIXED_SEGMENTS,
  BOARD_TAB,
  BOARD_TAB_ORDER,
  boardPath,
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
