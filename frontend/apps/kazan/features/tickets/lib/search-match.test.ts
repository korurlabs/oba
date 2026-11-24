import { describe, expect, test } from "bun:test";

import { isOnSearchPath } from "./search-match";

describe("isOnSearchPath", () => {
  test("a ticket that matches is on the path", () => {
    expect(isOnSearchPath({ searchMatch: true })).toBe(true);
  });

  test("a ticket that does not match and has no matching sub-ticket is not", () => {
    expect(isOnSearchPath({ searchMatch: false, subTickets: [{ searchMatch: false }] })).toBe(false);
  });

  test("a ticket leading to a matching sub-ticket at any depth is on the path", () => {
    const grandchild = { searchMatch: true };
    const child = { searchMatch: false, subTickets: [grandchild] };

    expect(isOnSearchPath({ searchMatch: false, subTickets: [child] })).toBe(true);
  });

  test("a ticket of a list the backend did not search is always shown", () => {
    expect(isOnSearchPath({ searchMatch: null, subTickets: null })).toBe(true);
    expect(isOnSearchPath({})).toBe(true);
  });
});
