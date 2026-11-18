import { describe, expect, test } from "bun:test";

import { hasRole, userMeResponseSchema } from "./schemas";

const KORUR = "org-korur";
describe("userMeResponseSchema", () => {
  test("reads the platform user into its name, default organization and memberships", () => {
    const parsed = userMeResponseSchema.parse({
      id: "u1",
      email: "dev@example.com",
      userStatus: "ACTIVE",
      profile: { firstName: "Ada", lastName: "Lovelace" },
      defaultOrganizationId: KORUR,
      organizations: [{ id: KORUR, name: "Korur", role: "OWNER", apps: ["KAZAN", "KAYA"] }],
    });
    expect(parsed).toEqual({
      id: "u1",
      email: "dev@example.com",
      userStatus: "ACTIVE",
      displayName: "Ada Lovelace",
      defaultOrganizationId: KORUR,
      memberships: [{ organizationId: KORUR, name: "Korur", role: "OWNER" }],
      kayaWithheld: false,
      managesOrganizations: false,
    });
  });

  test("a user with no profile, default or organizations reads as empty", () => {
    const parsed = userMeResponseSchema.parse({ id: "u", email: "e", userStatus: "PENDING_ONBOARDING" });
    expect(parsed.displayName).toBeNull();
    expect(parsed.defaultOrganizationId).toBeNull();
    expect(parsed.memberships).toEqual([]);
  });

  test("a guest membership is left out, since Kaya refuses a guest", () => {
    const parsed = userMeResponseSchema.parse({
      id: "u",
      email: "e",
      userStatus: "ACTIVE",
      organizations: [
        { id: "org-guest", name: "Guest", role: "GUEST" },
        { id: KORUR, name: "Korur", role: "MEMBER", apps: ["KAYA"] },
      ],
    });
    expect(parsed.memberships).toEqual([{ organizationId: KORUR, name: "Korur", role: "MEMBER" }]);
  });

  test("refuses an unknown role and a missing user status", () => {
    expect(
      userMeResponseSchema.safeParse({
        id: "u",
        email: "e",
        userStatus: "ACTIVE",
        organizations: [{ id: KORUR, name: "Korur", role: "VIEWER" }],
      }).success
    ).toBe(false);
    expect(userMeResponseSchema.safeParse({ id: "u", email: "e" }).success).toBe(false);
  });
});

describe("userMeResponseSchema managesOrganizations", () => {
  const parseWith = (
    organizations: { id: string; name: string; role: string; organizationStatus?: string }[]
  ) =>
    userMeResponseSchema.parse({ id: "u", email: "e", userStatus: "ACTIVE", organizations })
      .managesOrganizations;

  test("is true for an ACTIVE ADMIN and for an ACTIVE OWNER", () => {
    expect(parseWith([{ id: KORUR, name: "Korur", role: "ADMIN", organizationStatus: "ACTIVE" }])).toBe(true);
    expect(parseWith([{ id: KORUR, name: "Korur", role: "OWNER", organizationStatus: "ACTIVE" }])).toBe(true);
  });

  test("is false for an ACTIVE MEMBER or GUEST", () => {
    expect(parseWith([{ id: KORUR, name: "Korur", role: "MEMBER", organizationStatus: "ACTIVE" }])).toBe(false);
    expect(parseWith([{ id: KORUR, name: "Korur", role: "GUEST", organizationStatus: "ACTIVE" }])).toBe(false);
  });

  test("is false for an OWNER of a DISABLED organization", () => {
    expect(parseWith([{ id: KORUR, name: "Korur", role: "OWNER", organizationStatus: "DISABLED" }])).toBe(false);
  });

  test("is false when the API sends no organization status", () => {
    expect(parseWith([{ id: KORUR, name: "Korur", role: "OWNER" }])).toBe(false);
  });

  test("is true when one of several organizations is managed, and false without organizations", () => {
    expect(
      parseWith([
        { id: "a", name: "A", role: "MEMBER", organizationStatus: "ACTIVE" },
        { id: "b", name: "B", role: "ADMIN", organizationStatus: "ACTIVE" },
      ])
    ).toBe(true);
    expect(parseWith([])).toBe(false);
  });

  test("refuses an unknown organization status", () => {
    expect(
      userMeResponseSchema.safeParse({
        id: "u",
        email: "e",
        userStatus: "ACTIVE",
        organizations: [{ id: KORUR, name: "Korur", role: "OWNER", organizationStatus: "ARCHIVED" }],
      }).success
    ).toBe(false);
  });
});

describe("userMeResponseSchema Kaya access", () => {
  const parse = (organizations?: { id: string; name: string; role: string; organizationStatus?: string; apps?: string[] }[]) =>
    userMeResponseSchema.parse({ id: "u", email: "e", userStatus: "ACTIVE", organizations });

  test("drops a membership whose apps do not list KAYA", () => {
    const parsed = parse([
      { id: "a", name: "A", role: "MEMBER", organizationStatus: "ACTIVE", apps: ["KAZAN"] },
      { id: "b", name: "B", role: "MEMBER", organizationStatus: "ACTIVE", apps: ["KAZAN", "KAYA"] },
    ]);
    expect(parsed.memberships).toEqual([{ organizationId: "b", name: "B", role: "MEMBER" }]);
  });

  test("drops a membership without apps", () => {
    const parsed = parse([{ id: "a", name: "A", role: "OWNER", organizationStatus: "ACTIVE" }]);
    expect(parsed.memberships).toEqual([]);
  });

  test("an API without apps still parses", () => {
    expect(
      userMeResponseSchema.safeParse({
        id: "u",
        email: "e",
        userStatus: "ACTIVE",
        organizations: [{ id: "a", name: "A", role: "MEMBER" }],
      }).success
    ).toBe(true);
  });

  test("keeps an app string that Kaya does not know", () => {
    const parsed = parse([{ id: "a", name: "A", role: "MEMBER", apps: ["KALKAN", "KAYA"] }]);
    expect(parsed.memberships).toHaveLength(1);
  });

  test("kayaWithheld is true for a dropped non-GUEST membership", () => {
    for (const role of ["OWNER", "ADMIN", "MEMBER"]) {
      expect(
        parse([{ id: "a", name: "A", role, organizationStatus: "ACTIVE", apps: ["KAZAN"] }])
          .kayaWithheld
      ).toBe(true);
    }
  });

  test("kayaWithheld is true when apps is missing on an ACTIVE membership", () => {
    expect(parse([{ id: "a", name: "A", role: "MEMBER", organizationStatus: "ACTIVE" }]).kayaWithheld).toBe(true);
  });

  test("kayaWithheld is false for a GUEST, a DISABLED organization and a granted membership", () => {
    expect(parse([{ id: "a", name: "A", role: "GUEST", organizationStatus: "ACTIVE", apps: [] }]).kayaWithheld).toBe(false);
    expect(parse([{ id: "a", name: "A", role: "MEMBER", organizationStatus: "DISABLED", apps: [] }]).kayaWithheld).toBe(false);
    expect(parse([{ id: "a", name: "A", role: "MEMBER", organizationStatus: "ACTIVE", apps: ["KAYA"] }]).kayaWithheld).toBe(false);
  });

  test("kayaWithheld is false without organizations", () => {
    expect(parse().kayaWithheld).toBe(false);
    expect(parse([]).kayaWithheld).toBe(false);
  });

  test("managesOrganizations reads a manager organization that grants no Kaya", () => {
    const parsed = parse([{ id: "a", name: "A", role: "OWNER", organizationStatus: "ACTIVE", apps: ["KAZAN"] }]);
    expect(parsed.memberships).toEqual([]);
    expect(parsed.managesOrganizations).toBe(true);
  });
});

describe("hasRole", () => {
  test("a role includes the rights of the roles below it", () => {
    expect(hasRole("OWNER", "ADMIN")).toBe(true);
    expect(hasRole("ADMIN", "ADMIN")).toBe(true);
    expect(hasRole("ADMIN", "OWNER")).toBe(false);
    expect(hasRole("MEMBER", "ADMIN")).toBe(false);
    expect(hasRole("MEMBER", "MEMBER")).toBe(true);
  });
});
