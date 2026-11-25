import { describe, expect, test } from "bun:test";
import type { UserMe } from "@oba/shared/features/users/schema";
import { isCyberscoreClosed } from "./cyberscore-access";

function meWithRole(role: string): UserMe {
  return {
    defaultOrganizationId: "org-1",
    organizations: [{ id: "org-1", name: "Org", role }],
  } as unknown as UserMe;
}

describe("isCyberscoreClosed", () => {
  test.each(["OWNER", "ADMIN", "MEMBER"])("is false for a %s", (role) => {
    expect(isCyberscoreClosed(meWithRole(role), "org-1")).toBe(false);
  });

  test("is true for a GUEST", () => {
    expect(isCyberscoreClosed(meWithRole("GUEST"), "org-1")).toBe(true);
  });

  test("leaves the decision to the API when the user, the organization or the membership is unknown", () => {
    expect(isCyberscoreClosed(undefined, "org-1")).toBe(false);
    expect(isCyberscoreClosed(meWithRole("GUEST"), null)).toBe(false);
    expect(isCyberscoreClosed(meWithRole("GUEST"), "org-2")).toBe(false);
  });
});
