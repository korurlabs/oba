import { describe, expect, test } from "bun:test";

import { organizationIdFromPath, resolveOrganizationHeader } from "./organization-header";

const PATH_ORG = "11111111-1111-1111-1111-111111111111";
const DEFAULT_ORG = "22222222-2222-2222-2222-222222222222";
const EXPLICIT_ORG = "33333333-3333-3333-3333-333333333333";

describe("organizationIdFromPath", () => {
  test.each([
    [`/organizations/${PATH_ORG}`, PATH_ORG],
    [`/organizations/${PATH_ORG}/members/u-1/role`, PATH_ORG],
    [`/kaizen/organizations/${PATH_ORG}/security-board`, PATH_ORG],
    [`/organizations/${PATH_ORG}?tab=members`, PATH_ORG],
    [`https://api.example.test/organizations/${PATH_ORG}/members`, PATH_ORG],
  ])("reads the organization of %s", (url, expected) => {
    expect(organizationIdFromPath(url)).toBe(expected);
  });

  test.each(["/organizations", "/organizations/", "/users/me", "/kaizen/boards/b-1", undefined])(
    "finds none in %s",
    (url) => {
      expect(organizationIdFromPath(url)).toBeUndefined();
    },
  );
});

describe("resolveOrganizationHeader", () => {
  test("keeps a header the caller set itself", () => {
    expect(resolveOrganizationHeader(`/organizations/${PATH_ORG}`, EXPLICIT_ORG, DEFAULT_ORG)).toBe(
      EXPLICIT_ORG,
    );
  });

  test("sends the path organization instead of the default one", () => {
    expect(
      resolveOrganizationHeader(`/organizations/${PATH_ORG}/members`, undefined, DEFAULT_ORG),
    ).toBe(PATH_ORG);
    expect(resolveOrganizationHeader(`/organizations/${PATH_ORG}`, "", DEFAULT_ORG)).toBe(PATH_ORG);
  });

  test("sends the path organization when the session has no default", () => {
    expect(resolveOrganizationHeader(`/organizations/${PATH_ORG}`, undefined, null)).toBe(PATH_ORG);
  });

  test("sends the default organization on a path without one", () => {
    expect(resolveOrganizationHeader("/kaizen/chat/channels", undefined, DEFAULT_ORG)).toBe(
      DEFAULT_ORG,
    );
  });

  test("sends none without a path organization or a default", () => {
    expect(resolveOrganizationHeader("/users/me", undefined, null)).toBeUndefined();
    expect(resolveOrganizationHeader("/users/me", null, "")).toBeUndefined();
    expect(resolveOrganizationHeader("/users/me", undefined, undefined)).toBeUndefined();
  });
});
