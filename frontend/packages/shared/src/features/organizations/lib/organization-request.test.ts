import { describe, expect, test } from "bun:test";
import { toAppGrantRequest } from "./organization-request";

const ID = "7d1f4a0e-6a3b-4c0e-9a55-2f0c1b8d9e10";

describe("toAppGrantRequest", () => {
  test("sends only userId for a USER target", () => {
    const request = toAppGrantRequest({ app: "KAYA", targetType: "USER", targetId: ID });

    expect(request).toEqual({ app: "KAYA", userId: ID });
    expect("groupId" in request).toBe(false);
  });

  test("sends only groupId for a GROUP target", () => {
    const request = toAppGrantRequest({ app: "KAZAN", targetType: "GROUP", targetId: ID });

    expect(request).toEqual({ app: "KAZAN", groupId: ID });
    expect("userId" in request).toBe(false);
  });
});
