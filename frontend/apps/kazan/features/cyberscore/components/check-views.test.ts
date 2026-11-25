import { describe, expect, test } from "bun:test";
import type { Check } from "../schemas";
import { resolveDropStatus } from "./check-kanban";
import { toCheckTableRows } from "./check-table";

function makeCheck(id: string, status: Check["status"], category: Check["category"]): Check {
  return {
    id,
    name: id,
    description: null,
    expected: "holds",
    weight: 5,
    sortOrder: 0,
    category,
    outcome: "TODO",
    status,
    updatedAt: new Date("2026-11-25T08:30:00Z"),
  } as unknown as Check;
}

describe("resolveDropStatus", () => {
  const checks = [makeCheck("c1", "IN_PROGRESS", "DATA"), makeCheck("c2", "DONE", "DATA")];

  test("a drop on a column takes that column's status", () => {
    expect(resolveDropStatus("NOT_APPLICABLE", undefined, checks)).toBe("NOT_APPLICABLE");
  });

  test("a drop on a card takes its sortable container, then the card's status", () => {
    expect(resolveDropStatus("c2", "NOT_STARTED", checks)).toBe("NOT_STARTED");
    expect(resolveDropStatus("c2", undefined, checks)).toBe("DONE");
  });

  test("a drop on anything else moves nothing", () => {
    expect(resolveDropStatus("elsewhere", "not-a-status", checks)).toBeNull();
  });
});

describe("toCheckTableRows", () => {
  test("makes one heading row per group that holds the group's checks", () => {
    const identity = [makeCheck("c1", "DONE", "IDENTITY"), makeCheck("c2", "DONE", "IDENTITY")];
    const none = [makeCheck("c3", "DONE", null)];
    const rows = toCheckTableRows([
      { category: "IDENTITY", checks: identity },
      { category: null, checks: none },
      { category: "DATA", checks: [] },
    ]);

    expect(rows.map((row) => row.id)).toEqual(["category:IDENTITY", "category:NONE"]);
    expect(rows[0]?.groupChecks).toEqual(identity);
    expect(rows[1]?.groupChecks).toEqual(none);
    expect(rows[0]?.name).toBe("c1");
  });
});
