import { describe, expect, test } from "bun:test";

import type { MachineUsageResponse } from "../schemas";
import {
  countdownText,
  NO_ACCOUNT_TEXT,
  NO_SNAPSHOT_TEXT,
  resetClockText,
  usageView,
  windowView,
} from "./usage-data";

const NOW = Date.parse("2026-10-23T12:25:00Z");
const UTC = "UTC";

function clock(iso: string): string {
  return new Intl.DateTimeFormat(undefined, {
    timeZone: UTC,
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(iso));
}

function known(overrides: Partial<MachineUsageResponse> = {}): MachineUsageResponse {
  return {
    state: "KNOWN",
    capturedAt: "2026-10-23T12:21:00Z",
    reportedByMachineId: "a1",
    reportedByMachineName: "ada-mini",
    sharedWith: [],
    fiveHour: { usedPercentage: 23.5, resetsAt: "2026-10-23T14:30:00Z", reset: false },
    sevenDay: { usedPercentage: "41.00", resetsAt: "2026-10-26T09:00:00Z", reset: false },
    ...overrides,
  };
}

describe("countdownText", () => {
  test("counts minutes, hours and days", () => {
    expect(countdownText(NOW + 30_000, NOW)).toBe("in under a minute");
    expect(countdownText(NOW + 12 * 60_000, NOW)).toBe("in 12 min");
    expect(countdownText(NOW + (2 * 60 + 5) * 60_000, NOW)).toBe("in 2 h 05 min");
    expect(countdownText(NOW + (3 * 24 + 4) * 3_600_000, NOW)).toBe("in 3 d 4 h");
  });
});

describe("resetClockText", () => {
  test("shows only the time for a reset today", () => {
    expect(resetClockText(Date.parse("2026-10-23T14:30:00Z"), NOW, UTC)).toBe(
      clock("2026-10-23T14:30:00Z")
    );
  });

  test("adds the weekday for a reset later this week", () => {
    const text = resetClockText(Date.parse("2026-10-26T09:00:00Z"), NOW, UTC);
    expect(text).toContain(clock("2026-10-26T09:00:00Z"));
    expect(text).not.toBe(clock("2026-10-26T09:00:00Z"));
  });
});

describe("usageView", () => {
  test("shows percentage, local reset time, countdown and age", () => {
    const view = usageView(known(), NOW, UTC);
    expect(view.kind).toBe("known");
    if (view.kind !== "known") return;
    const [fiveHour, weekly] = view.windows;
    expect(fiveHour).toEqual({
      kind: "used",
      label: "5-hour",
      percentage: 23.5,
      percentageText: "24%",
      resetText: `resets ${clock("2026-10-23T14:30:00Z")}, in 2 h 05 min`,
    });
    expect(weekly?.kind).toBe("used");
    if (weekly?.kind === "used") expect(weekly.percentageText).toBe("41%");
    expect(view.ageText).toBe("updated 4 min ago");
    expect(view.sharedText).toBeNull();
  });

  test("turns a window into Reset at once the clock passes resetsAt", () => {
    const later = Date.parse("2026-10-23T14:30:00Z");
    const view = usageView(known(), later, UTC);
    if (view.kind !== "known") throw new Error("expected a known view");
    expect(view.windows[0]).toEqual({
      kind: "reset",
      label: "5-hour",
      resetText: `Reset at ${clock("2026-10-23T14:30:00Z")}`,
    });
  });

  test("trusts the server's reset flag with a null percentage", () => {
    const view = windowView(
      "5-hour",
      { usedPercentage: null, resetsAt: "2026-10-23T12:00:00Z", reset: true },
      NOW,
      UTC
    );
    expect(view.kind).toBe("reset");
  });

  test("renders both unknown texts", () => {
    expect(usageView(known({ state: "NO_ACCOUNT" }), NOW)).toEqual({
      kind: "unknown",
      text: NO_ACCOUNT_TEXT,
    });
    expect(usageView(known({ state: "NO_SNAPSHOT" }), NOW)).toEqual({
      kind: "unknown",
      text: NO_SNAPSHOT_TEXT,
    });
    expect(usageView(null, NOW)).toEqual({ kind: "unknown", text: NO_ACCOUNT_TEXT });
  });

  test("names the machines sharing the account and the reporter", () => {
    const view = usageView(
      known({
        sharedWith: [
          { machineId: "a2", machineName: "ada-studio" },
          { machineId: "a3", machineName: "ada-laptop" },
        ],
      }),
      NOW,
      UTC
    );
    if (view.kind !== "known") throw new Error("expected a known view");
    expect(view.sharedText).toBe("Shared with ada-studio, ada-laptop · reported by ada-mini");
  });

  test("marks a window the machine did not report", () => {
    const view = usageView(known({ sevenDay: null }), NOW, UTC);
    if (view.kind !== "known") throw new Error("expected a known view");
    expect(view.windows[1]).toEqual({ kind: "missing", label: "Weekly" });
  });
});
