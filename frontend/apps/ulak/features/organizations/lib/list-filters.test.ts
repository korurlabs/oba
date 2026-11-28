import { describe, expect, test } from "bun:test";
import type {
  AppGrant,
  Invitation,
  MemberAppAccess,
  OrganizationGroup,
  OrganizationMember,
} from "@oba/shared/features/organizations/schemas";
import { filterGrants, filterMemberAccess, hasActiveAccessFilter } from "./access-filters";
import { filterGroups, hasActiveGroupFilter } from "./group-filters";
import { filterInvitations, hasActiveInvitationFilter } from "./invitation-filters";
import { filterMembers, hasActiveMemberFilter } from "./member-filters";

function member(overrides: Partial<OrganizationMember>): OrganizationMember {
  return {
    id: "m-1",
    userId: "u-1",
    firstName: "Ada",
    lastName: "Lovelace",
    email: "ada@example.com",
    role: "MEMBER",
    status: "ACTIVE",
    ...overrides,
  };
}

const ada = member({});
const grace = member({
  id: "m-2",
  userId: "u-2",
  firstName: "Grace",
  lastName: "Hopper",
  email: "grace@navy.example",
  role: "ADMIN",
  status: "DISABLED",
});

describe("filterMembers", () => {
  const none = { search: null, role: null, status: null };

  test("without a search or filter every member shows", () => {
    expect(filterMembers([ada, grace], none)).toEqual([ada, grace]);
    expect(hasActiveMemberFilter(none)).toBe(false);
  });

  test("the search matches the full name and the email", () => {
    expect(filterMembers([ada, grace], { ...none, search: "ada love" })).toEqual([ada]);
    expect(filterMembers([ada, grace], { ...none, search: "navy" })).toEqual([grace]);
  });

  test("role and status narrow together with the search", () => {
    expect(filterMembers([ada, grace], { ...none, role: "ADMIN" })).toEqual([grace]);
    expect(filterMembers([ada, grace], { ...none, status: "ACTIVE" })).toEqual([ada]);
    expect(filterMembers([ada, grace], { search: "ada", role: "ADMIN", status: null })).toEqual([]);
    expect(hasActiveMemberFilter({ ...none, status: "DISABLED" })).toBe(true);
  });
});

describe("filterInvitations", () => {
  const pending = { id: "i-1", email: "new@example.com", status: "PENDING" } as Invitation;
  const expired = { id: "i-2", email: "old@example.com", status: "EXPIRED" } as Invitation;

  test("the search matches the email and the status narrows", () => {
    expect(filterInvitations([pending, expired], { search: "old", status: null })).toEqual([
      expired,
    ]);
    expect(filterInvitations([pending, expired], { search: null, status: "PENDING" })).toEqual([
      pending,
    ]);
    expect(hasActiveInvitationFilter({ search: " ", status: null })).toBe(false);
  });
});

describe("filterGroups", () => {
  const everyone = { id: "g-1", name: "Everyone", description: null } as OrganizationGroup;
  const devs = { id: "g-2", name: "Devs", description: "Backend people" } as OrganizationGroup;

  test("the search matches the name and the description", () => {
    expect(filterGroups([everyone, devs], { search: "every" })).toEqual([everyone]);
    expect(filterGroups([everyone, devs], { search: "backend" })).toEqual([devs]);
    expect(hasActiveGroupFilter({ search: "x" })).toBe(true);
  });
});

describe("access filters", () => {
  const toUser = { id: "a-1", userFullName: "Ada Lovelace", groupName: null } as AppGrant;
  const toGroup = { id: "a-2", userFullName: null, groupName: "Devs" } as AppGrant;

  test("a grant matches on its member or its group", () => {
    expect(filterGrants([toUser, toGroup], "ada")).toEqual([toUser]);
    expect(filterGrants([toUser, toGroup], "devs")).toEqual([toGroup]);
    expect(filterGrants([toUser, toGroup], null)).toEqual([toUser, toGroup]);
  });

  test("a member's effective access matches on the name", () => {
    const access = { userId: "u-1", fullName: "Ada Lovelace" } as MemberAppAccess;
    expect(filterMemberAccess([access], "grace")).toEqual([]);
    expect(hasActiveAccessFilter({ tab: "members", search: "grace" })).toBe(true);
  });
});
