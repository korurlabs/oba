import { describe, expect, it } from "bun:test";
import type { UserMe, UserOrganization } from "@oba/shared/features/users/schema";

import {
  grantsKazan,
  hasKazanAccess,
  kazanOrganizationIds,
  MEMBER_WITHOUT_KAZAN_ACCESS_CODE,
} from "@/lib/kazanAccess";

function organization(overrides: Partial<UserOrganization> = {}): UserOrganization {
  return {
    id: "org-a",
    name: "Acme",
    role: "MEMBER",
    organizationStatus: "ACTIVE",
    apps: ["KAZAN"],
    ...overrides,
  };
}

function me(organizations: UserOrganization[] | null): UserMe {
  return {
    id: "user-1",
    createdAt: "2026-11-01T09:00:00Z",
    updatedAt: "2026-11-01T09:00:00Z",
    email: "ada@example.com",
    userStatus: "ACTIVE",
    profile: null,
    defaultOrganizationId: null,
    userPreferences: null,
    organizations,
    pendingInvitations: null,
  };
}

describe("grantsKazan", () => {
  it("is true for an active organization whose apps hold Kazan", () => {
    expect(grantsKazan(organization())).toBe(true);
    expect(grantsKazan(organization({ apps: ["KAYA", "KAZAN", "SOMETHING_NEW"] }))).toBe(true);
  });

  it("is false without an apps list, as an API without app access sends it", () => {
    expect(grantsKazan(organization({ apps: undefined }))).toBe(false);
  });

  it("is false when the apps list holds only other or unknown apps", () => {
    expect(grantsKazan(organization({ apps: [] }))).toBe(false);
    expect(grantsKazan(organization({ apps: ["KAYA", "OTAK", "SOMETHING_NEW"] }))).toBe(false);
    expect(grantsKazan(organization({ apps: ["kazan"] }))).toBe(false);
  });

  it("is false in a disabled organization, also when it lists Kazan", () => {
    expect(grantsKazan(organization({ organizationStatus: "DISABLED" }))).toBe(false);
  });
});

describe("kazanOrganizationIds", () => {
  it("keeps the organizations that grant Kazan, in the order of the response", () => {
    const organizations: UserOrganization[] = [
      organization({ id: "org-b" }),
      organization({ id: "org-closed", apps: ["KAYA"] }),
      organization({ id: "org-disabled", organizationStatus: "DISABLED" }),
      organization({ id: "org-old-api", apps: undefined }),
      organization({ id: "org-a" }),
    ];
    expect(kazanOrganizationIds(me(organizations))).toEqual(["org-b", "org-a"]);
  });

  it("is empty when the user has no organizations", () => {
    expect(kazanOrganizationIds(me(null))).toEqual([]);
    expect(kazanOrganizationIds(me([]))).toEqual([]);
  });
});

describe("hasKazanAccess", () => {
  it("is true only when the member list marks the member with Kazan", () => {
    expect(hasKazanAccess({ kazanAccess: true })).toBe(true);
    expect(hasKazanAccess({ kazanAccess: false })).toBe(false);
    expect(hasKazanAccess({})).toBe(false);
  });
});

describe("MEMBER_WITHOUT_KAZAN_ACCESS_CODE", () => {
  it("is the backend code of an attach refused for a member without Kazan", () => {
    expect(MEMBER_WITHOUT_KAZAN_ACCESS_CODE).toBe("APP_VAL_002");
  });
});
