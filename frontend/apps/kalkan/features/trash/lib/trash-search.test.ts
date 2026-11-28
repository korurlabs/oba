import { describe, expect, test } from "bun:test";
import type { PageTrashItem } from "@/features/pages/schemas";
import { trashItemMatchesSearch } from "./trash-search";

const ITEM: PageTrashItem = {
  id: "page-1",
  title: "Old policy",
  path: "policies/old-policy",
  deletedAt: "2026-11-22T09:00:00Z",
  deletedByName: null,
  descendantCount: 0,
};

describe("trashItemMatchesSearch", () => {
  test("matches the title without regard to case", () => {
    expect(trashItemMatchesSearch(ITEM, "OLD", "en")).toBe(true);
  });

  test("matches the path", () => {
    expect(trashItemMatchesSearch(ITEM, "policies/", "en")).toBe(true);
  });

  test("matches a page without a title by its path only", () => {
    expect(trashItemMatchesSearch({ ...ITEM, title: null }, "old", "en")).toBe(true);
    expect(trashItemMatchesSearch({ ...ITEM, title: null }, "risk", "en")).toBe(false);
  });
});
