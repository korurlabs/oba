import { describe, expect, it } from "bun:test";
import type { UserMe, UserOrganization } from "@oba/shared/features/users/schema";

import {
  grantsKaizen,
  hasKaizenAccess,
  kaizenOrganizationIds,
  MEMBER_WITHOUT_KAIZEN_ACCESS_CODE,
} from "@/lib/kaizenAccess";

function organization(overrides: Partial<UserOrganization> = {}): UserOrganization {
  return {
    id: "org-a",
    name: "Acme",
    role: "MEMBER",
    organizationStatus: "ACTIVE",
    apps: ["KAIZEN"],
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

describe("grantsKaizen", () => {
  it("is true for an active organization whose apps hold Kaizen", () => {
    expect(grantsKaizen(organization())).toBe(true);
    expect(grantsKaizen(organization({ apps: ["KAYA", "KAIZEN", "SOMETHING_NEW"] }))).toBe(true);
  });

  it("is false without an apps list, as an API without app access sends it", () => {
    expect(grantsKaizen(organization({ apps: undefined }))).toBe(false);
  });

  it("is false when the apps list holds only other or unknown apps", () => {
    expect(grantsKaizen(organization({ apps: [] }))).toBe(false);
    expect(grantsKaizen(organization({ apps: ["KAYA", "OTAK", "SOMETHING_NEW"] }))).toBe(false);
    expect(grantsKaizen(organization({ apps: ["kaizen"] }))).toBe(false);
  });

  it("is false in a disabled organization, also when it lists Kaizen", () => {
    expect(grantsKaizen(organization({ organizationStatus: "DISABLED" }))).toBe(false);
  });
});

describe("kaizenOrganizationIds", () => {
  it("keeps the organizations that grant Kaizen, in the order of the response", () => {
    const organizations: UserOrganization[] = [
      organization({ id: "org-b" }),
      organization({ id: "org-closed", apps: ["KAYA"] }),
      organization({ id: "org-disabled", organizationStatus: "DISABLED" }),
      organization({ id: "org-old-api", apps: undefined }),
      organization({ id: "org-a" }),
    ];
    expect(kaizenOrganizationIds(me(organizations))).toEqual(["org-b", "org-a"]);
  });

  it("is empty when the user has no organizations", () => {
    expect(kaizenOrganizationIds(me(null))).toEqual([]);
    expect(kaizenOrganizationIds(me([]))).toEqual([]);
  });
});

describe("hasKaizenAccess", () => {
  it("is true only when the member list marks the member with Kaizen", () => {
    expect(hasKaizenAccess({ kaizenAccess: true })).toBe(true);
    expect(hasKaizenAccess({ kaizenAccess: false })).toBe(false);
    expect(hasKaizenAccess({})).toBe(false);
  });
});

describe("MEMBER_WITHOUT_KAIZEN_ACCESS_CODE", () => {
  it("is the backend code of an attach refused for a member without Kaizen", () => {
    expect(MEMBER_WITHOUT_KAIZEN_ACCESS_CODE).toBe("APP_VAL_002");
  });
});
