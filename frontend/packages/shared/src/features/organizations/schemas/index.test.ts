import { describe, expect, test } from "bun:test";
import { organizationGroupMemberSchema } from "./index";

describe("organizationGroupMemberSchema", () => {
  test("parses a seeded user id that has no RFC 9562 version", () => {
    const parsed = organizationGroupMemberSchema.safeParse({
      userId: "b0000000-0000-0000-0000-000000000001",
      fullName: "Seed User",
      email: "seed@example.com",
      role: "MEMBER",
    });

    expect(parsed.success).toBe(true);
    expect(parsed.data?.userId).toBe("b0000000-0000-0000-0000-000000000001");
  });
});
