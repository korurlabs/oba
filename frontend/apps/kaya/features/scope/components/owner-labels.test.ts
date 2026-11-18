import { describe, expect, test } from "bun:test";

import { ownerLabel } from "./owner-labels";

describe("owner labels", () => {
  test("a pack reads as a pack, whether a root or a child pack", () => {
    expect(ownerLabel({ name: "org" })).toBe("Pack org");
    expect(ownerLabel({ name: "kagi" })).toBe("Pack kagi");
  });
});
