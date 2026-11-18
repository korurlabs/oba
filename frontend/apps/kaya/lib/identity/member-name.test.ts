import { describe, expect, test } from "bun:test";

import { memberName } from "./member-name";

describe("memberName", () => {
  test("a user who left is a former member", () => {
    const members = new Map([["u1", "Ada"]]);
    expect(memberName("u1", members)).toBe("Ada");
    expect(memberName("u9", members)).toBe("A former member");
    expect(memberName(null, members)).toBe("Unknown");
  });
});
