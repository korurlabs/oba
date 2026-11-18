import { describe, expect, test } from "bun:test";

import { nextFileHistoryPage } from "./use-changes";

describe("nextFileHistoryPage", () => {
  const page = (currentPage: number, last: boolean, entries: number) => ({
    success: true,
    data: [],
    pagination: {
      totalElements: 45,
      totalPages: 3,
      currentPage,
      pageSize: 20,
      first: currentPage === 1,
      last,
      empty: entries === 0,
    },
  });

  test("asks for the next page of owner versions until the last one", () => {
    expect(nextFileHistoryPage(page(1, false, 2))).toBe(2);
    expect(nextFileHistoryPage(page(3, true, 1))).toBeUndefined();
  });

  test("a page without a version of the file still leads to the older pages", () => {
    expect(nextFileHistoryPage(page(2, false, 0))).toBe(3);
  });

  test("an answer without paging is the whole history", () => {
    expect(nextFileHistoryPage({ success: true, data: [] })).toBeUndefined();
  });
});
