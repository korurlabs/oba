import { describe, expect, test } from "bun:test";
import { isSearching, matchesSearch } from "./list-search";

describe("matchesSearch", () => {
  test("an empty or missing search matches every row", () => {
    expect(matchesSearch(null, ["Ada"])).toBe(true);
    expect(matchesSearch(undefined, [])).toBe(true);
    expect(matchesSearch("   ", ["Ada"])).toBe(true);
  });

  test("matches a part of any field, ignoring case and outer spaces", () => {
    expect(matchesSearch(" love", ["Ada Lovelace", "ada@example.com"])).toBe(true);
    expect(matchesSearch("EXAMPLE", ["Ada Lovelace", "ada@example.com"])).toBe(true);
  });

  test("a search found in no field does not match, and null fields are skipped", () => {
    expect(matchesSearch("grace", ["Ada Lovelace", null, undefined])).toBe(false);
  });
});

describe("isSearching", () => {
  test("only a search with text narrows the list", () => {
    expect(isSearching("ada")).toBe(true);
    expect(isSearching(" ")).toBe(false);
    expect(isSearching(null)).toBe(false);
  });
});
