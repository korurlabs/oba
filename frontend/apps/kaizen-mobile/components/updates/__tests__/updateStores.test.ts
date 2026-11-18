import { describe, expect, it } from "bun:test";

import { markNudged, nudgedMembersOf } from "@/components/updates/nudgeStore";
import { setUpdateFilter, updateFilterOf } from "@/components/updates/updateFilterStore";
import { NO_UPDATE_FILTER } from "@/components/updates/updateModel";

describe("update filter store", () => {
  it("narrows to today and nobody until a filter is set, and without an organization", () => {
    expect(updateFilterOf("org-unset")).toEqual(NO_UPDATE_FILTER);
    expect(updateFilterOf(null)).toEqual(NO_UPDATE_FILTER);
  });

  it("keeps each organization's filter apart", () => {
    setUpdateFilter("org-a", { day: "2026-11-06", personId: "user-1" });
    setUpdateFilter("org-b", { day: null, personId: "user-2" });
    expect(updateFilterOf("org-a")).toEqual({ day: "2026-11-06", personId: "user-1" });
    expect(updateFilterOf("org-b")).toEqual({ day: null, personId: "user-2" });
  });
});

describe("nudge store", () => {
  it("remembers nudges per organization and day", () => {
    markNudged("org-a", "2026-11-09", "user-1");
    expect(nudgedMembersOf("org-a", "2026-11-09").has("user-1")).toBe(true);
    expect(nudgedMembersOf("org-a", "2026-11-10").has("user-1")).toBe(false);
    expect(nudgedMembersOf("org-b", "2026-11-09").has("user-1")).toBe(false);
    expect(nudgedMembersOf(null, "2026-11-09").size).toBe(0);
  });

  it("keeps the same set while nothing new is added, so readers do not redraw", () => {
    markNudged("org-c", "2026-11-09", "user-1");
    const before = nudgedMembersOf("org-c", "2026-11-09");
    markNudged("org-c", "2026-11-09", "user-1");
    expect(nudgedMembersOf("org-c", "2026-11-09")).toBe(before);
    markNudged("org-c", "2026-11-09", "user-2");
    expect(nudgedMembersOf("org-c", "2026-11-09")).not.toBe(before);
  });
});
