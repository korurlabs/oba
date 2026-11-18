import { describe, expect, test } from "bun:test";
import { clientDefaultOrganizationId } from "./session-update";

describe("clientDefaultOrganizationId", () => {
  test("stores null when the update selects the personal workspace", () => {
    expect(clientDefaultOrganizationId({ user: { defaultOrganizationId: null } }, "org-1")).toBeNull();
  });

  test("stores null for an empty organization id", () => {
    expect(clientDefaultOrganizationId({ user: { defaultOrganizationId: "" } }, "org-1")).toBeNull();
  });

  test("stores the organization the update selects", () => {
    expect(clientDefaultOrganizationId({ user: { defaultOrganizationId: "org-2" } }, null)).toBe(
      "org-2",
    );
  });

  test.each([undefined, null, {}, { user: {} }])(
    "keeps the current organization when the update does not name one (%p)",
    (updateData) => {
      expect(clientDefaultOrganizationId(updateData, "org-1")).toBe("org-1");
    },
  );
});
