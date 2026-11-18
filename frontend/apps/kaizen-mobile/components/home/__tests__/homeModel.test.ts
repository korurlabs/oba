import { describe, expect, it } from "bun:test";

import {
  burndownGeometry,
  canWriteTickets,
  dueState,
  daysUntilDeadline,
  displayName,
  formatPoints,
  groupTicketsByStatus,
  hslWithAlpha,
  leaderboardRows,
  pruneSelection,
  sprintProgress,
  toggleSelection,
} from "@/components/home/homeModel";
import type { HomeLeaderboardEntry } from "@/lib/schemas/home";
import type { TicketListItem } from "@/lib/schemas/tickets";

// A zone west of UTC, so a day read in the wrong zone shows up as a shift.
process.env.TZ = "America/New_York";

describe("sprintProgress", () => {
  // Sprint from 27 Oct to 10 Nov on the Istanbul calendar (midnight UTC+3).
  const start = "2026-10-26T21:00:00Z";
  const end = "2026-11-09T21:00:00Z";

  it("counts days left and days passed on the Istanbul calendar", () => {
    // 22:30 UTC on 5 Nov is already 6 Nov in Istanbul.
    const progress = sprintProgress(start, end, new Date("2026-11-05T22:30:00Z"));
    expect(progress).toEqual({
      kind: "running",
      startDay: "2026-10-27",
      endDay: "2026-11-10",
      daysLeft: 4,
      percent: 73,
    });
  });

  it("reports an ended sprint with negative days left and a full bar", () => {
    const progress = sprintProgress(start, end, new Date("2026-11-12T09:00:00Z"));
    expect(progress.kind).toBe("ended");
    if (progress.kind !== "no-end") {
      expect(progress.daysLeft).toBe(-2);
      expect(progress.percent).toBe(100);
    }
  });

  it("reports a sprint without an end date", () => {
    expect(sprintProgress(start, null)).toEqual({ kind: "no-end", startDay: "2026-10-27" });
  });
});

describe("burndownGeometry", () => {
  const points = [
    { date: "2026-11-01", remaining: 10, ideal: 10 },
    { date: "2026-11-02", remaining: 6, ideal: 5 },
    { date: "2026-11-03", remaining: null, ideal: 0 },
  ];

  it("stops the actual line at today and spans the ideal line", () => {
    const geometry = burndownGeometry(points, 220, 120, 10);
    expect(geometry.todayIndex).toBe(1);
    expect(geometry.actualPath).toBe("M10.0,10.0 L110.0,50.0");
    expect(geometry.idealPath).toBe("M10.0,10.0 L110.0,60.0 L210.0,110.0");
  });

  it("finds the nearest day for a scrub position, clamped to the chart", () => {
    const geometry = burndownGeometry(points, 220, 120, 10);
    expect(geometry.indexAt(-40)).toBe(0);
    expect(geometry.indexAt(70)).toBe(1);
    expect(geometry.indexAt(500)).toBe(2);
  });

  it("returns empty paths without days", () => {
    const geometry = burndownGeometry([], 220, 120, 10);
    expect(geometry.actualPath).toBe("");
    expect(geometry.todayIndex).toBe(-1);
  });
});

describe("leaderboard", () => {
  const entry = (
    userId: string,
    points: number,
    firstName: string | null = userId
  ): HomeLeaderboardEntry => ({
    userId,
    firstName,
    lastName: null,
    email: `${userId}@example.com`,
    points,
    tickets: 1,
  });

  it("names a user by name, or by email without one", () => {
    expect(displayName({ firstName: "Deniz", lastName: "Arslan", email: "d@k.com" })).toBe(
      "Deniz Arslan"
    );
    expect(displayName({ firstName: " ", lastName: null, email: "d@k.com" })).toBe("d@k.com");
  });

  it("shares a rank on equal points and appends the current user below the top rows", () => {
    const entries = [
      entry("a", 9),
      entry("b", 9),
      entry("c", 5),
      entry("d", 3),
      entry("e", 2),
      entry("f", 1),
    ];
    const rows = leaderboardRows(entries, "f", 3);
    expect(rows.map((row) => [row.entry.userId, row.rank, row.isMe, row.detached])).toEqual([
      ["a", 1, false, false],
      ["b", 1, false, false],
      ["c", 3, false, false],
      ["f", 6, true, true],
    ]);
  });

  it("keeps the current user in place when they are in the top rows", () => {
    const entries = [entry("a", 9), entry("b", 5), entry("c", 3), entry("d", 1)];
    const rows = leaderboardRows(entries, "b", 3);
    expect(rows.map((row) => [row.entry.userId, row.isMe, row.detached])).toEqual([
      ["a", false, false],
      ["b", true, false],
      ["c", false, false],
    ]);
  });

  it("shows five rows by default", () => {
    const entries = ["a", "b", "c", "d", "e", "f", "g"].map((id, index) => entry(id, 10 - index));
    expect(leaderboardRows(entries, "g").map((row) => row.entry.userId)).toEqual([
      "a",
      "b",
      "c",
      "d",
      "e",
      "g",
    ]);
  });

  it("formats half points in the app language", () => {
    expect(formatPoints(24.5, "en")).toBe("24.5");
    expect(formatPoints(24.5, "nl")).toBe("24,5");
    expect(formatPoints(31, "tr")).toBe("31");
  });
});

describe("hslWithAlpha", () => {
  it("adds the alpha in the form React Native reads", () => {
    expect(hslWithAlpha("hsl(20.5 90.2% 48.2%)", 0.15)).toBe("hsla(20.5 90.2% 48.2% / 0.15)");
  });

  it("leaves a colour in another form unchanged", () => {
    expect(hslWithAlpha("#ea580c", 0.15)).toBe("#ea580c");
  });
});

describe("daysUntilDeadline", () => {
  it("counts on the UTC calendar", () => {
    // 21:00 in New York on 5 Nov is already 6 Nov in UTC.
    const now = new Date("2026-11-06T02:00:00Z");
    expect(daysUntilDeadline("2026-11-06", now)).toBe(0);
    expect(daysUntilDeadline("2026-11-04", now)).toBe(-2);
    expect(daysUntilDeadline("2026-11-18", now)).toBe(12);
  });
});

describe("stale selection", () => {
  it("toggles and prunes ids", () => {
    const selected = toggleSelection(toggleSelection(new Set(), "a"), "b");
    expect([...toggleSelection(selected, "a")]).toEqual(["b"]);
    expect([...pruneSelection(selected, ["b", "c"])]).toEqual(["b"]);
  });
});

describe("groupTicketsByStatus", () => {
  const ticket = (id: string, status: string): TicketListItem => ({
    id,
    createdAt: "2026-11-01T10:00:00Z",
    title: id,
    key: id,
    type: "FEATURE",
    status,
    priority: null,
    missionRank: null,
    dueDate: null,
    assignees: null,
    board: null,
  });

  it("orders groups in progress first and keeps unknown statuses last", () => {
    const groups = groupTicketsByStatus([
      ticket("1", "TODO"),
      ticket("2", "IN_PROGRESS"),
      ticket("3", "BLOCKED"),
      ticket("4", "TODO"),
    ]);
    expect(groups.map((group) => [group.status, group.tickets.map((t) => t.id)])).toEqual([
      ["IN_PROGRESS", ["2"]],
      ["TODO", ["1", "4"]],
      ["BLOCKED", ["3"]],
    ]);
  });
});

describe("canWriteTickets", () => {
  it("lets members and above write, never a guest or an unknown role", () => {
    expect(canWriteTickets("OWNER")).toBe(true);
    expect(canWriteTickets("ADMIN")).toBe(true);
    expect(canWriteTickets("MEMBER")).toBe(true);
    expect(canWriteTickets("GUEST")).toBe(false);
    expect(canWriteTickets(undefined)).toBe(false);
    expect(canWriteTickets(null)).toBe(false);
  });
});

describe("dueState", () => {
  // The suite runs in America/New_York (UTC-5 in November).
  const now = new Date("2026-11-06T15:00:00Z");

  it("reads the due day in the viewer's zone", () => {
    // 02:00 UTC on 7 Nov is still 6 Nov in New York.
    expect(dueState("2026-11-07T02:00:00Z", now)).toEqual({
      day: "2026-11-06",
      today: true,
      overdue: false,
    });
  });

  it("marks a past day overdue and a later day neither", () => {
    expect(dueState("2026-11-04T12:00:00Z", now)?.overdue).toBe(true);
    expect(dueState("2026-11-10T12:00:00Z", now)).toEqual({
      day: "2026-11-10",
      today: false,
      overdue: false,
    });
  });

  it("returns null without a due date", () => {
    expect(dueState(null, now)).toBeNull();
  });
});
