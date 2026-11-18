import { describe, expect, test } from "bun:test";
import type { UserMe, UserOrganization } from "../../users/schema";
import { canManageOrganizations, managedOrganizations } from "./managed-organizations";

const organization = (
  name: string,
  role: UserOrganization["role"],
  organizationStatus: UserOrganization["organizationStatus"] = "ACTIVE"
): UserOrganization => ({
  id: crypto.randomUUID(),
  name,
  role,
  organizationStatus,
});

const me = (organizations?: UserOrganization[]): UserMe => ({
  id: "user-1",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
  email: "user@example.test",
  userStatus: "ACTIVE",
  roles: ["USER"],
  profile: null,
  defaultOrganizationId: null,
  userPreferences: null,
  organizations,
});

describe("managedOrganizations", () => {
  test("keeps OWNER and ADMIN memberships and drops MEMBER and GUEST", () => {
    const owner = organization("Owned", "OWNER");
    const admin = organization("Administered", "ADMIN");

    const result = managedOrganizations(
      me([owner, organization("Joined", "MEMBER"), admin, organization("Visited", "GUEST")])
    );

    expect(result).toEqual([owner, admin]);
  });

  test("keeps an ACTIVE OWNER and ADMIN and drops a DISABLED organization of any role", () => {
    const owner = organization("Owned", "OWNER");
    const admin = organization("Administered", "ADMIN");

    const result = managedOrganizations(
      me([
        organization("Switched off", "OWNER", "DISABLED"),
        owner,
        organization("Closed", "ADMIN", "DISABLED"),
        admin,
      ])
    );

    expect(result).toEqual([owner, admin]);
  });

  test("is empty without a user or without organizations", () => {
    expect(managedOrganizations(undefined)).toEqual([]);
    expect(managedOrganizations(null)).toEqual([]);
    expect(managedOrganizations(me())).toEqual([]);
  });
});

describe("canManageOrganizations", () => {
  test("is true with one ADMIN membership", () => {
    expect(
      canManageOrganizations(
        me([organization("Joined", "MEMBER"), organization("Administered", "ADMIN")])
      )
    ).toBe(true);
  });

  test("is false when the only OWNER membership is in a DISABLED organization", () => {
    expect(
      canManageOrganizations(me([organization("Switched off", "OWNER", "DISABLED")]))
    ).toBe(false);
  });

  test("is true when a DISABLED OWNER membership sits next to an ACTIVE ADMIN one", () => {
    expect(
      canManageOrganizations(
        me([organization("Switched off", "OWNER", "DISABLED"), organization("Open", "ADMIN")])
      )
    ).toBe(true);
  });

  test("is false for a member-only or guest-only user and without a user", () => {
    expect(
      canManageOrganizations(
        me([organization("Joined", "MEMBER"), organization("Visited", "GUEST")])
      )
    ).toBe(false);
    expect(canManageOrganizations(undefined)).toBe(false);
    expect(canManageOrganizations(null)).toBe(false);
    expect(canManageOrganizations(me())).toBe(false);
  });
});
