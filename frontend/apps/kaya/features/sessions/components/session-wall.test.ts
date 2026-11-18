import { describe, expect, test } from "bun:test";
import { createSerializer } from "nuqs";

import { sessionFilterParams, type SessionFilterParams } from "../hooks/use-session-filter-params";
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
