import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { NuqsTestingAdapter } from "nuqs/adapters/testing";
import { renderToStaticMarkup } from "react-dom/server";
import type { Invitation, OrganizationMember } from "@oba/shared/features/organizations/schemas";

// The Humans and Invitations lists read their rows through query hooks. The
// hooks, the columns, the table and the invite dialog are replaced: the table
// prints its rows or the empty state it was given, so the test reads what the
// search, the filters and the role passed down. The translator echoes its key.
let members: OrganizationMember[];
let invitations: Invitation[];

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualIntl = { ...(await import("next-intl")) };
const actualGetMe = { ...(await import("@oba/shared/features/users/hooks/use-get-me")) };
const actualMembers = { ...(await import("../hooks/use-organization-members")) };
const actualInvitations = { ...(await import("../hooks/use-invitations")) };
const actualMemberColumns = { ...(await import("../hooks/use-member-columns")) };
const actualInvitationColumns = { ...(await import("../hooks/use-invitation-columns")) };
const actualTable = { ...(await import("@/components/data-table")) };
const actualDialog = { ...(await import("./create-organization-invitation-dialog")) };

const mutation = { isPending: false, mutate: () => undefined };

mock.module("next-intl", () => ({
  ...actualIntl,
  useTranslations: () => (key: string) => key,
}));
mock.module("@oba/shared/features/users/hooks/use-get-me", () => ({
  ...actualGetMe,
  useGetMe: () => ({ data: { data: { id: "u-1", organizations: [] } } }),
}));
mock.module("../hooks/use-organization-members", () => ({
  ...actualMembers,
  useOrganizationMembers: () => ({ data: { data: members }, isLoading: false }),
  useUpdateMemberRole: () => mutation,
  useUpdateMemberStatus: () => mutation,
  useRemoveMember: () => mutation,
}));
mock.module("../hooks/use-invitations", () => ({
  ...actualInvitations,
  useInvitations: () => ({ data: { data: invitations }, isLoading: false }),
  useRevokeInvitation: () => mutation,
}));
mock.module("../hooks/use-member-columns", () => ({
  ...actualMemberColumns,
  useMemberColumns: () => [],
}));
mock.module("../hooks/use-invitation-columns", () => ({
  ...actualInvitationColumns,
  useInvitationColumns: () => [],
}));
mock.module("@/components/data-table", () => ({
  ...actualTable,
  DataTable: ({
    data,
    emptyMessage,
    getRowId,
  }: {
    data: unknown[];
    emptyMessage: ReactNode;
    getRowId: (row: unknown) => string;
  }) => (data.length === 0 ? <>{emptyMessage}</> : <p>{`rows:${data.map(getRowId).join(",")}`}</p>),
}));
mock.module("./create-organization-invitation-dialog", () => ({
  ...actualDialog,
  CreateInvitationDialog: () => <p>invite-dialog</p>,
}));

afterAll(() => {
  mock.module("next-intl", () => actualIntl);
  mock.module("@oba/shared/features/users/hooks/use-get-me", () => actualGetMe);
  mock.module("../hooks/use-organization-members", () => actualMembers);
  mock.module("../hooks/use-invitations", () => actualInvitations);
  mock.module("../hooks/use-member-columns", () => actualMemberColumns);
  mock.module("../hooks/use-invitation-columns", () => actualInvitationColumns);
  mock.module("@/components/data-table", () => actualTable);
  mock.module("./create-organization-invitation-dialog", () => actualDialog);
});

const { MembersTab } = await import("./organization-members-tab");
const { InvitationsTab } = await import("./organization-invitations-tab");

function wrap(node: ReactNode, searchParams: string): string {
  return renderToStaticMarkup(
    <NuqsTestingAdapter searchParams={searchParams}>{node}</NuqsTestingAdapter>
  );
}

function renderMembers(canEdit: boolean, searchParams = ""): string {
  return wrap(<MembersTab organizationId="org-1" canEdit={canEdit} />, searchParams);
}

function renderInvitations(canEdit: boolean, searchParams = ""): string {
  return wrap(
    <InvitationsTab organizationId="org-1" canEdit={canEdit} currentUserRole="ADMIN" />,
    searchParams
  );
}

const ada = {
  userId: "u-1",
  firstName: "Ada",
  lastName: "Lovelace",
  email: "ada@example.com",
  role: "OWNER",
  status: "ACTIVE",
} as OrganizationMember;
const grace = {
  userId: "u-2",
  firstName: "Grace",
  lastName: "Hopper",
  email: "grace@example.com",
  role: "MEMBER",
  status: "DISABLED",
} as OrganizationMember;

beforeEach(() => {
  members = [ada, grace];
  invitations = [
    { id: "i-1", email: "new@example.com", status: "PENDING" } as Invitation,
    { id: "i-2", email: "old@example.com", status: "EXPIRED" } as Invitation,
  ];
});

describe("MembersTab", () => {
  test("an OWNER or ADMIN gets the search, the filter and the invite button", () => {
    const html = renderMembers(true);

    expect(html).toContain('data-slot="list-toolbar"');
    expect(html).toContain("members.searchLabel");
    expect(html).toContain("trigger");
    expect(html).toContain("invitations.invite");
    expect(html).toContain("invite-dialog");
  });

  test("a MEMBER keeps the search and the filter, without the invite button", () => {
    const html = renderMembers(false);

    expect(html).toContain("members.searchLabel");
    expect(html).not.toContain("invitations.invite");
    expect(html).not.toContain("invite-dialog");
  });

  test("the search, role and status in the URL narrow the rows", () => {
    expect(renderMembers(false, "?search=hopper")).toContain("rows:u-2");
    expect(renderMembers(false, "?role=OWNER")).toContain("rows:u-1");
    expect(renderMembers(false, "?status=DISABLED")).toContain("rows:u-2");
  });

  test("a filter that hides every member offers to clear it, not to invite", () => {
    const html = renderMembers(true, "?role=GUEST");

    expect(html).toContain("members.filteredTitle");
    expect(html).toContain("clearFilters");
    expect(html.match(/invitations\.invite/g)).toHaveLength(1);
  });

  test("an empty list offers the invite action instead of pointing to another page", () => {
    members = [];

    const html = renderMembers(true);

    expect(html).toContain("members.noMembersDescription");
    expect(html.match(/invitations\.invite/g)).toHaveLength(2);
    expect(renderMembers(false)).toContain("members.noMembersReadOnly");
  });
});

describe("InvitationsTab", () => {
  test("a MEMBER keeps the search and the status filter, without the invite button", () => {
    const html = renderInvitations(false);

    expect(html).toContain("invitations.searchLabel");
    expect(html).toContain("trigger");
    expect(html).not.toContain("invitations.invite");
  });

  test("the search on email and the status in the URL narrow the rows", () => {
    expect(renderInvitations(true, "?search=old")).toContain("rows:i-2");
    expect(renderInvitations(true, "?status=PENDING")).toContain("rows:i-1");
  });

  test("an empty list offers the invite button to an OWNER or ADMIN only", () => {
    invitations = [];

    expect(renderInvitations(true).match(/invitations\.invite/g)).toHaveLength(2);
    expect(renderInvitations(false)).toContain("invitations.noInvitationsReadOnly");
  });

  test("a search that hides every invitation offers to clear it", () => {
    const html = renderInvitations(true, "?search=nobody");

    expect(html).toContain("invitations.filteredTitle");
    expect(html).toContain("clearFilters");
  });
});
