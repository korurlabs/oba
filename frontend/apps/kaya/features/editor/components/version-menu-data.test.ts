import { describe, expect, test } from "bun:test";

import type { FileHistoryResponse } from "@/features/changes/schemas";

import {
  decisionText,
  historyEmptyText,
  historyEntriesOf,
  historyEntryTitle,
  historyTarget,
} from "./version-menu-data";

function entry(values: Partial<FileHistoryResponse>): FileHistoryResponse {
  return {
    versionId: "v-7",
    number: 7,
    status: "APPROVED",
    source: "MANUAL",
    change: "MODIFIED",
    sha256: "s",
    current: false,
    proposedBy: "user-1",
    reviews: [],
    ...values,
  };
}

describe("version menu", () => {
  test("names each entry by number, state and change", () => {
    expect(historyEntryTitle(entry({}))).toBe("Version 7, approved, changed");
    expect(historyEntryTitle(entry({ status: "IN_REVIEW", change: "ADDED" }))).toBe(
      "Version 7, in review, added"
    );
  });

  test("says when there is nothing to choose", () => {
    expect(historyEmptyText([], true)).toBe("No version of this file is known yet.");
    expect(historyEmptyText([entry({})], true)).toBe("Only version 7 holds this file.");
    expect(historyEmptyText([entry({}), entry({ number: 6 })], true)).toBeNull();
  });

  test("says nothing about an empty or single history while older pages are left", () => {
    expect(historyEmptyText([], false)).toBeNull();
    expect(historyEmptyText([entry({})], false)).toBeNull();
  });

  test("lists the entries of every loaded page, newest first", () => {
    const pages = [
      { success: true, data: [entry({ versionId: "v-30", number: 30 })] },
      { success: true, data: [] },
      { success: true, data: [entry({ versionId: "v-3", number: 3 })] },
    ];
    expect(historyEntriesOf(pages).map((each: FileHistoryResponse) => each.number)).toEqual([
      30, 3,
    ]);
  });

  test("opens the current version and a draft live, any other read-only", () => {
    expect(historyTarget(entry({ current: true }))).toBe("current");
    expect(historyTarget(entry({ status: "DRAFT" }))).toBe("draft");
    expect(historyTarget(entry({ status: "REJECTED" }))).toBe("older");
  });
});

describe("decisionText", () => {
  test("an approved or superseded version was approved; a draft was sent back", () => {
    expect(decisionText(entry({ decidedAt: "t" }))).toBe("Approved");
    expect(decisionText(entry({ status: "SUPERSEDED", decidedAt: "t" }))).toBe("Approved");
    expect(decisionText(entry({ status: "DRAFT", decidedAt: "t" }))).toBe("Sent back");
  });

  test("no decision time, no decision", () => {
    expect(decisionText(entry({ decidedAt: null }))).toBeNull();
    expect(decisionText(entry({ status: "IN_REVIEW" }))).toBeNull();
  });
});
