import { describe, expect, test } from "bun:test";
import { createSerializer } from "nuqs";

import { sessionFilterParams, type SessionFilterParams } from "../hooks/use-session-filter-params";
import { sessionFilterCategories, sessionsFiltered } from "./session-filters";
import { toQuery } from "./session-wall";

const NONE: SessionFilterParams = {
  search: null,
  repositoryId: null,
  userId: null,
  machineId: null,
  model: null,
  accountOrganizationUuid: null,
  ended: null,
  page: 1,
};

describe("the wall's filters", () => {
  test("keep no stage in the URL", () => {
    const serialize = createSerializer(sessionFilterParams);
    expect(serialize({ repositoryId: "r1" })).toBe("?repositoryId=r1");
    expect(Object.keys(sessionFilterParams)).not.toContain("stage");
  });

  test("pass only the set filters to the API", () => {
    expect(toQuery(NONE)).toEqual({ page: 1, amount: 60 });
    expect(toQuery({ ...NONE, repositoryId: "r1", ended: false })).toEqual({
      page: 1,
      amount: 60,
      repositoryId: "r1",
      ended: false,
    });
  });
});

describe("the wall's filter categories", () => {
  const options = {
    users: [{ id: "u1", displayName: "Ada" }],
    machines: [
      { id: "m1", machineName: "ada-mac", userId: "u1" },
      { id: "m2", machineName: "bob-mac", userId: "u2" },
    ],
    models: ["opus"],
    subscriptions: [],
  } as unknown as Parameters<typeof sessionFilterCategories>[2];

  test("count the search and every filter, but not the page", () => {
    expect(sessionsFiltered(NONE)).toBe(false);
    expect(sessionsFiltered({ ...NONE, page: 3 })).toBe(false);
    expect(sessionsFiltered({ ...NONE, search: "fix" })).toBe(true);
    expect(sessionsFiltered({ ...NONE, ended: false })).toBe(true);
  });

  test("a user narrows the machines and choosing one clears the machine", () => {
    const changes: Partial<SessionFilterParams>[] = [];
    const categories = sessionFilterCategories(
      { ...NONE, userId: "u1", machineId: "m9" },
      (change) => changes.push(change),
      options,
      []
    );
    const machine = categories.find((category) => category.key === "machineId");
    // m9 is no longer offered, but stays listed so it can be cleared.
    expect(machine?.options.map((option) => option.value)).toEqual(["m1", "m9"]);
    const user = categories.find((category) => category.key === "userId");
    if (user?.mode !== "single") throw new Error("user is a single select");
    user.onChange(null);
    expect(changes).toEqual([{ userId: null, machineId: null }]);
  });

  test("the session category maps to the ended boolean", () => {
    const changes: Partial<SessionFilterParams>[] = [];
    const ended = sessionFilterCategories(
      { ...NONE, ended: true },
      (c) => changes.push(c),
      options,
      []
    ).find((category) => category.key === "ended");
    if (ended?.mode !== "single") throw new Error("ended is a single select");
    expect(ended.value).toBe("true");
    ended.onChange("false");
    ended.onChange(null);
    expect(changes).toEqual([{ ended: false }, { ended: null }]);
  });
});
