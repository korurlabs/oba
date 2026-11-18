import { describe, expect, test } from "bun:test";

import { newerRelease, scopeSourceLabel, scopeSourceText, type SourceScope } from "./scope-source";

const PACK: SourceScope = {
  scope: { kind: "PACK", packId: "k-1" },
  release: null,
  newestReleaseNumber: null,
  version: { number: 1, status: "APPROVED", files: [] },
};

const REPOSITORY: SourceScope = {
  scope: { kind: "REPOSITORY", repositoryId: "r-1" },
  release: { number: 3, createdAt: "2026-10-23T09:00:00Z", fileCount: 12 },
  newestReleaseNumber: 4,
  version: null,
};

describe("scopeSourceText", () => {
  test("names a pack's version, its status and its file count", () => {
    expect(scopeSourceText(PACK, "village")).toBe("Pack village: version 1 (approved), 0 files.");
  });

  test("a pack without a version starts its first draft empty", () => {
    expect(scopeSourceText({ ...PACK, version: null }, "village")).toBe(
      "Pack village: no approved version yet. Its first change starts empty."
    );
  });

  test("a repository shows its release, read only", () => {
    const text = scopeSourceText(REPOSITORY, "kagi");
    expect(text).toStartWith("kagi: config release 3 of ");
    expect(text).toEndWith("12 delivered paths. Read only: each file is edited in its pack.");
  });
});

describe("scopeSourceLabel", () => {
  test("puts the word Pack before a pack's name and none before a repository's", () => {
    expect(scopeSourceLabel(PACK, "village")).toBe("Pack village");
    expect(scopeSourceLabel(REPOSITORY, "kagi")).toBe("kagi");
  });
});

describe("newerRelease", () => {
  test("names the newest release only while an older one is shown", () => {
    expect(newerRelease(REPOSITORY)).toBe(4);
    expect(newerRelease({ ...REPOSITORY, newestReleaseNumber: 3 })).toBeNull();
    expect(newerRelease(PACK)).toBeNull();
  });
});
