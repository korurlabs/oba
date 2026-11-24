import { describe, expect, test } from "bun:test";
import type { OrganizationMember } from "@oba/shared/features/organizations/schemas";
import type { PagePermission } from "./index";
import {
  addableMembers,
  addGrant,
  changeGrantLevel,
  draftsChanged,
  draftsFromPermissions,
  grantableLevels,
  removeGrant,
  toPermissionRequests,
} from "./permission-form";

const ADA = "11111111-1111-4111-8111-111111111111";
const BOB = "22222222-2222-4222-8222-222222222222";
const CEM = "33333333-3333-4333-8333-333333333333";
const GROUP = "44444444-4444-4444-8444-444444444444";

const member = (
  userId: string,
  firstName: string,
  overrides: Partial<OrganizationMember> = {}
): OrganizationMember => ({
  id: userId,
  userId,
  firstName,
  lastName: "Test",
  email: `${firstName.toLowerCase()}@example.com`,
  role: "MEMBER",
  status: "ACTIVE",
  ...overrides,
});

const stored: PagePermission[] = [
  {
    id: "a0000000-0000-4000-8000-000000000001",
    userId: BOB,
    groupId: null,
    displayName: "Bob Test",
    level: "EDIT",
  },
  {
    id: "a0000000-0000-4000-8000-000000000002",
    userId: ADA,
    groupId: null,
    displayName: "Ada Test",
    level: "MANAGE",
  },
  {
    id: "a0000000-0000-4000-8000-000000000003",
    userId: null,
    groupId: GROUP,
    displayName: "Auditors",
    level: "VIEW",
  },
];

describe("permission list", () => {
  test("lists the stored grants by name, groups kept", () => {
    const drafts = draftsFromPermissions(stored);
    expect(drafts.map((draft) => draft.displayName)).toEqual(["Ada Test", "Auditors", "Bob Test"]);
    expect(drafts[1]).toMatchObject({ key: `group:${GROUP}`, userId: null, groupId: GROUP });
  });

  test("adds a member once, in name order", () => {
    const drafts = draftsFromPermissions(stored);
    const added = addGrant(drafts, member(CEM, "Cem"), "EDIT");
    expect(added.map((draft) => draft.displayName)).toEqual([
      "Ada Test",
      "Auditors",
      "Bob Test",
      "Cem Test",
    ]);
    expect(added[3]).toMatchObject({ userId: CEM, groupId: null, level: "EDIT" });
    expect(addGrant(added, member(CEM, "Cem"), "VIEW")).toHaveLength(4);
  });

  test("gives a guest view access at most", () => {
    expect(grantableLevels("GUEST")).toEqual(["VIEW"]);
    expect(grantableLevels("MEMBER")).toEqual(["VIEW", "EDIT", "MANAGE"]);
    const added = addGrant([], member(CEM, "Cem", { role: "GUEST" }), "MANAGE");
    expect(added[0]?.level).toBe("VIEW");
  });

  test("changes and removes a row, and sees the change against the stored list", () => {
    const saved = draftsFromPermissions(stored);
    const changed = changeGrantLevel(saved, `user:${BOB}`, "VIEW");
    expect(changed.find((draft) => draft.userId === BOB)?.level).toBe("VIEW");
    expect(draftsChanged(changed, saved)).toBe(true);
    expect(draftsChanged(changeGrantLevel(changed, `user:${BOB}`, "EDIT"), saved)).toBe(false);
    const removed = removeGrant(saved, `user:${ADA}`);
    expect(removed.map((draft) => draft.userId)).toEqual([null, BOB]);
    expect(draftsChanged(removed, saved)).toBe(true);
  });

  test("saves the full list as the replacement body", () => {
    expect(toPermissionRequests(draftsFromPermissions(stored))).toEqual([
      { userId: ADA, groupId: null, level: "MANAGE" },
      { userId: null, groupId: GROUP, level: "VIEW" },
      { userId: BOB, groupId: null, level: "EDIT" },
    ]);
    expect(toPermissionRequests([])).toEqual([]);
  });

  test("offers only active members not yet on the list", () => {
    const members = [
      member(ADA, "Ada"),
      member(CEM, "Cem"),
      member(BOB, "Bob", { status: "DISABLED" }),
      member("55555555-5555-4555-8555-555555555555", "Dan", { firstName: "", lastName: "" }),
    ];
    const offered = addableMembers(members, draftsFromPermissions(stored));
    expect(offered.map((candidate) => candidate.userId)).toEqual([
      CEM,
      "55555555-5555-4555-8555-555555555555",
    ]);
  });
});
