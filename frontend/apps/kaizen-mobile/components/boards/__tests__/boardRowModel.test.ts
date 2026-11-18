import { describe, expect, it } from "bun:test";

import {
  boardIconKey,
  boardSubtitleParts,
  type BoardSubtitleFacts,
} from "@/components/boards/boardRowModel";

const ROOT: BoardSubtitleFacts = {
  prefix: "KZN",
  parentName: null,
  childCount: 0,
  sprint: { name: "Sprint 14", daysLeft: "6 days left" },
  openCount: 23,
};
const CHILD: BoardSubtitleFacts = { ...ROOT, prefix: "MOB", parentName: "Kaizen Platform" };

describe("boardSubtitleParts on iOS", () => {
  it("Recent: sprint and days left, without the key", () => {
    expect(boardSubtitleParts("ios", "recent", ROOT)).toEqual([
      { kind: "sprint", name: "Sprint 14" },
      { kind: "daysLeft", text: "6 days left" },
    ]);
  });

  it("Recent: a child board names its parent and sprint", () => {
    expect(boardSubtitleParts("ios", "recent", CHILD)).toEqual([
      { kind: "parent", name: "Kaizen Platform" },
      { kind: "sprint", name: "Sprint 14" },
    ]);
  });

  it("All boards: key and child count, parent alone, or key and sprint", () => {
    expect(boardSubtitleParts("ios", "all", { ...ROOT, childCount: 3 })).toEqual([
      { kind: "key", prefix: "KZN" },
      { kind: "childCount", count: 3 },
    ]);
    expect(boardSubtitleParts("ios", "all", CHILD)).toEqual([
      { kind: "parent", name: "Kaizen Platform" },
    ]);
    expect(boardSubtitleParts("ios", "all", ROOT)).toEqual([
      { kind: "key", prefix: "KZN" },
      { kind: "sprint", name: "Sprint 14" },
    ]);
    expect(boardSubtitleParts("ios", "all", { ...ROOT, sprint: null })).toEqual([
      { kind: "key", prefix: "KZN" },
      { kind: "noSprint" },
    ]);
  });

  it("leaves out a sprint that is still loading", () => {
    expect(boardSubtitleParts("ios", "all", { ...ROOT, sprint: undefined })).toEqual([
      { kind: "key", prefix: "KZN" },
    ]);
  });
});

describe("boardSubtitleParts on Android", () => {
  it("Recent: key, sprint and days left; a child board names its parent", () => {
    expect(boardSubtitleParts("android", "recent", ROOT)).toEqual([
      { kind: "key", prefix: "KZN" },
      { kind: "sprint", name: "Sprint 14" },
      { kind: "daysLeft", text: "6 days left" },
    ]);
    expect(boardSubtitleParts("android", "recent", CHILD)).toEqual([
      { kind: "parent", name: "Kaizen Platform" },
    ]);
  });

  it("All boards: key and open tickets", () => {
    expect(boardSubtitleParts("android", "all", ROOT)).toEqual([
      { kind: "key", prefix: "KZN" },
      { kind: "open", count: 23 },
    ]);
    expect(boardSubtitleParts("android", "all", { ...ROOT, openCount: undefined })).toEqual([
      { kind: "key", prefix: "KZN" },
    ]);
  });
});

describe("boardIconKey", () => {
  it("turns every spelling into a kebab-case lucide name", () => {
    expect(boardIconKey("lock")).toBe("lock");
    expect(boardIconKey("Building2")).toBe("building-2");
    expect(boardIconKey("building_2")).toBe("building-2");
    expect(boardIconKey("GitBranch")).toBe("git-branch");
    expect(boardIconKey(" lucide:globe ")).toBe("globe");
  });

  it("gives null without a name", () => {
    expect(boardIconKey(null)).toBeNull();
    expect(boardIconKey("  ")).toBeNull();
  });
});
