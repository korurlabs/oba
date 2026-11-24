import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { QueryClient } from "@tanstack/react-query";
import type { OrganizationSection } from "@/features/organizations/components/current-organization-page";
import {
  APP_ACCESS_KEYS,
  GROUP_KEYS,
  INVITATION_KEYS,
  LEGAL_ENTITY_KEYS,
  MEMBER_KEYS,
  ORGANIZATION_KEYS,
} from "@/features/organizations/hooks/query-keys";

// The session and the API calls are stand-ins: each call records that it ran and
// answers with a marker. The query keys are the real ones the client hooks use,
// so a key that is filled here is a key the view finds filled on first paint.
let defaultOrganizationId: string | null | undefined;
let calls: string[];
let failing: Set<string>;

const actualAuth = { ...(await import("@oba/shared/libs/auth")) };
const actualApi = { ...(await import("@/features/organizations/api")) };

function answer(name: string) {
  return async (organizationId: string) => {
    calls.push(`${name}:${organizationId}`);
    if (failing.has(name)) throw new Error(`${name} was refused`);
    return { data: `${name} of ${organizationId}` };
  };
}

mock.module("@oba/shared/libs/auth", () => ({
  ...actualAuth,
  auth: async () => ({ user: { id: "u1", defaultOrganizationId } }),
}));
mock.module("@/features/organizations/api", () => ({
  ...actualApi,
  getOrganization: answer("organization"),
  listOrganizationMembers: answer("members"),
  listOrganizationGroups: answer("groups"),
  listLegalEntities: answer("legalEntities"),
  listAppGrants: answer("grants"),
  listMemberAppAccess: answer("memberAccess"),
  listInvitations: answer("invitations"),
}));

afterAll(() => {
  mock.module("@oba/shared/libs/auth", () => actualAuth);
  mock.module("@/features/organizations/api", () => actualApi);
});

const { prefetchCurrentOrganization } = await import("./prefetch-current-organization");

/** The keys each section's view reads on mount, besides the organization. */
const SECTION_KEYS: Record<OrganizationSection, readonly (readonly unknown[])[]> = {
  humans: [MEMBER_KEYS.list("org-1")],
  groups: [GROUP_KEYS.list("org-1")],
  legalEntities: [LEGAL_ENTITY_KEYS.list("org-1")],
  access: [APP_ACCESS_KEYS.grants("org-1"), APP_ACCESS_KEYS.members("org-1")],
  invitations: [INVITATION_KEYS.list("org-1")],
  settings: [],
};

/** Runs `action` with `console.error` silenced, for a path that logs a failure on purpose. */
async function withoutConsoleError(action: () => Promise<void>): Promise<string[]> {
  const original = console.error;
  const logged: string[] = [];
  console.error = (message: string) => {
    logged.push(String(message));
  };
  try {
    await action();
  } finally {
    console.error = original;
  }
  return logged;
}

beforeEach(() => {
  defaultOrganizationId = "org-1";
  calls = [];
  failing = new Set();
});

describe("prefetchCurrentOrganization", () => {
  for (const section of Object.keys(SECTION_KEYS) as OrganizationSection[]) {
    test(`${section} fills the organization and the keys its view reads`, async () => {
      const queryClient = new QueryClient();
      await prefetchCurrentOrganization(queryClient, section);

      expect(queryClient.getQueryData(ORGANIZATION_KEYS.detail("org-1"))).toEqual({
        data: "organization of org-1",
      });
      for (const key of SECTION_KEYS[section]) {
        expect(queryClient.getQueryData(key)).toBeDefined();
      }
      // Nothing else is cached: one entry for the organization plus the section's own.
      expect(queryClient.getQueryCache().getAll()).toHaveLength(1 + SECTION_KEYS[section].length);
    });
  }

  test("asks only for the organization of the session's default", async () => {
    await prefetchCurrentOrganization(new QueryClient(), "humans");
    expect(calls.sort()).toEqual(["members:org-1", "organization:org-1"]);
  });

  test("the access section asks for the grants and the members' access", async () => {
    await prefetchCurrentOrganization(new QueryClient(), "access");
    expect(calls.sort()).toEqual(["grants:org-1", "memberAccess:org-1", "organization:org-1"]);
  });

  test("fetches nothing without a default organization", async () => {
    for (const value of [null, undefined]) {
      defaultOrganizationId = value;
      const queryClient = new QueryClient();
      await prefetchCurrentOrganization(queryClient, "humans");
      expect(queryClient.getQueryCache().getAll()).toHaveLength(0);
    }
    expect(calls).toEqual([]);
  });

  test("leaves a refused list uncached and logs it, keeping the organization", async () => {
    failing.add("groups");
    const queryClient = new QueryClient();
    const logged = await withoutConsoleError(() =>
      prefetchCurrentOrganization(queryClient, "groups")
    );

    expect(queryClient.getQueryData(GROUP_KEYS.list("org-1"))).toBeUndefined();
    expect(queryClient.getQueryData(ORGANIZATION_KEYS.detail("org-1"))).toBeDefined();
    expect(logged).toHaveLength(1);
    expect(logged[0]).toContain("[groups-page]");
  });

  test("a refused organization does not drop the section's list", async () => {
    failing.add("organization");
    const queryClient = new QueryClient();
    await withoutConsoleError(() => prefetchCurrentOrganization(queryClient, "humans"));

    expect(queryClient.getQueryData(ORGANIZATION_KEYS.detail("org-1"))).toBeUndefined();
    expect(queryClient.getQueryData(MEMBER_KEYS.list("org-1"))).toBeDefined();
  });
});
