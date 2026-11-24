import { describe, expect, test } from "bun:test";

import { isOnSearchPath, matchesSearch } from "./search-match";

type Ticket = { key: string; title: string; subTickets: Ticket[] };

const ticket = (key: string, title: string, subTickets: Ticket[] = []): Ticket => ({
  key,
  title,
  subTickets,
});

describe("matchesSearch", () => {
  test("matches the title or the key, case ignored", () => {
    expect(matchesSearch(ticket("KAGI-3", "A NUL character fails the upload"), "nul char")).toBe(true);
    expect(matchesSearch(ticket("KAGI-3", "Anything"), "kagi-3")).toBe(true);
    expect(matchesSearch(ticket("KAGI-2", "Row skip reasons"), "nul")).toBe(false);
  });
});

describe("isOnSearchPath", () => {
  test("a ticket that matches is on the path", () => {
    expect(isOnSearchPath(ticket("KAGI-3", "NUL character"), "nul")).toBe(true);
  });

  test("a ticket that does not match and has no matching sub-ticket is not", () => {
    const parent = ticket("KAGI-1", "CSV import", [ticket("KAGI-2", "Row skip reasons")]);
    expect(isOnSearchPath(parent, "nul")).toBe(false);
  });

  test("a ticket leading to a matching sub-ticket at any depth is on the path", () => {
    const child = ticket("KAGI-2", "Approval flow", [ticket("KAGI-3", "NUL character")]);
    const parent = ticket("KAGI-1", "CSV import", [child]);

    expect(isOnSearchPath(parent, "nul")).toBe(true);
    expect(isOnSearchPath(child, "nul")).toBe(true);
  });
});
