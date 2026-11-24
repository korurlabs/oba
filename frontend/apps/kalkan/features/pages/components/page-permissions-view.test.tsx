import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { NextIntlClientProvider } from "next-intl";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { OrganizationMember } from "@oba/shared/features/organizations/schemas";
import { ApiError } from "@oba/shared/api/axios";
import en from "@/locales/en.json";
import type { PageDetail, PagePermission } from "../schemas";

// The page, the grants and the members are fixed values; the view, its gates,
// its states and the messages are real. The pickers are a stand-in: the shared
// select reads messages the app merges in at runtime. The page renders on the
// server only, so the list is checked as it first renders; the list edits
// themselves are covered by the permission-form tests.
type QueryState<T> = { data?: T; error?: unknown };

let pageState: QueryState<PageDetail>;
let permissionsState: QueryState<PagePermission[]>;
let membersState: QueryState<OrganizationMember[]>;
let permissionReads: string[];

const actualPages = { ...(await import("../hooks/use-pages")) };
const actualPermissions = { ...(await import("../hooks/use-page-permissions")) };
const actualMembers = { ...(await import("../hooks/use-organization-members")) };
const actualNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };
const actualFormFields = { ...(await import("@oba/shared/features/form")) };

function query<T>(state: QueryState<T>) {
  return {
    data: state.data !== undefined ? { data: state.data } : undefined,
    isPending: state.data === undefined && state.error === undefined,
    isError: state.error !== undefined,
    error: state.error ?? null,
    fetchStatus: "idle",
    refetch: async () => undefined,
  };
}

mock.module("../hooks/use-pages", () => ({
  ...actualPages,
  usePage: () => query(pageState),
}));
mock.module("../hooks/use-page-permissions", () => ({
  ...actualPermissions,
  usePagePermissions: (rootPageId: string) => {
    permissionReads.push(rootPageId);
    return query(permissionsState);
  },
  useReplacePagePermissions: () => ({ mutate: () => undefined, isPending: false }),
}));
mock.module("../hooks/use-organization-members", () => ({
  ...actualMembers,
  useOrganizationMembers: () => query(membersState),
}));
mock.module("@oba/shared/libs/i18n/navigation", () => ({
  ...actualNavigation,
  Link: ({ href, children }: { href: string; children: ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

mock.module("@oba/shared/features/form", () => ({
  ...actualFormFields,
  SelectFieldStandalone: ({ label, value }: { label?: string; value: string }) => (
    <div data-select={label ?? ""}>{value}</div>
  ),
}));

afterAll(() => {
  mock.module("../hooks/use-pages", () => actualPages);
  mock.module("../hooks/use-page-permissions", () => actualPermissions);
  mock.module("../hooks/use-organization-members", () => actualMembers);
  mock.module("@oba/shared/libs/i18n/navigation", () => actualNavigation);
  mock.module("@oba/shared/features/form", () => actualFormFields);
});

const { PagePermissionsView } = await import("./page-permissions-view");

const ROOT_ID = "a0000000-0000-4000-8000-000000000001";
const BOB = "22222222-2222-4222-8222-222222222222";
const GUEST = "33333333-3333-4333-8333-333333333333";
const GROUP = "44444444-4444-4444-8444-444444444444";

function root(overrides: Partial<PageDetail> = {}): PageDetail {
  return {
    id: ROOT_ID,
    parentId: null,
    rootId: null,
    slug: "policies",
    path: "policies",
    compliance: false,
    version: 2,
    level: "MANAGE",
    updatedAt: "2026-11-20T10:00:00Z",
    translations: [
      {
        locale: "EN",
        title: "Policies",
        body: "",
        updatedAt: "2026-11-20T10:00:00Z",
        updatedByName: null,
      },
    ],
    ...overrides,
  };
}

const member = (userId: string, role: OrganizationMember["role"]): OrganizationMember => ({
  id: userId,
  userId,
  firstName: role === "GUEST" ? "Gul" : "Bob",
  lastName: "Test",
  email: `${userId}@example.com`,
  role,
  status: "ACTIVE",
});

function render(): string {
  return renderToStaticMarkup(
    <QueryClientProvider client={new QueryClient()}>
      <NextIntlClientProvider locale="en" messages={en} timeZone="UTC">
        <PagePermissionsView pageId={ROOT_ID} />
      </NextIntlClientProvider>
    </QueryClientProvider>
  );
}

beforeEach(() => {
  pageState = { data: root() };
  permissionsState = {
    data: [
      { id: "p1", userId: BOB, groupId: null, displayName: "Bob Test", level: "EDIT" },
      { id: "p2", userId: GUEST, groupId: null, displayName: "Gul Test", level: "VIEW" },
      { id: "p3", userId: null, groupId: GROUP, displayName: "Auditors", level: "VIEW" },
    ],
  };
  membersState = { data: [member(BOB, "MEMBER"), member(GUEST, "GUEST")] };
  permissionReads = [];
});

describe("page permissions view gates", () => {
  test("shows the outline while the page loads", () => {
    pageState = {};
    expect(render()).toContain(`aria-label="${en.pages.view.loading}"`);
  });

  test("shows a failed page load with its text and a retry", () => {
    pageState = { error: new ApiError("Not found", { status: 404, code: "KLK_NOT_001" }) };
    const html = render();
    expect(html).toContain(en.pages.errors.notFound);
    expect(html).toContain(en.permissions.retry);
  });

  test("sends a child page to the access of its section, without reading grants", () => {
    pageState = { data: root({ id: "child", parentId: ROOT_ID, rootId: ROOT_ID }) };
    const html = render();
    expect(html).toContain(en.permissions.onChild.title);
    expect(html).toContain(`href="/pages/${ROOT_ID}/permissions"`);
    expect(permissionReads).toEqual([]);
  });

  test("tells a root's editor without MANAGE that access is not theirs to change", () => {
    pageState = { data: root({ level: "EDIT" }) };
    const html = render();
    expect(html).toContain(en.permissions.noAccess.title);
    expect(html).not.toContain(en.permissions.list.title);
    expect(permissionReads).toEqual([]);
  });
});

describe("page permissions view list", () => {
  test("lists every grant of a MANAGE holder, marking groups and guests, with nothing to save", () => {
    const html = render();
    expect(html).toContain("Access to Policies");
    expect(permissionReads).toEqual([ROOT_ID]);
    for (const name of ["Bob Test", "Gul Test", "Auditors"]) {
      expect(html).toContain(name);
    }
    expect(html).toContain(en.permissions.list.group);
    expect(html).toContain(en.permissions.list.guest);
    expect(html).not.toContain(en.permissions.unsavedChanges);
    expect(html).toMatch(/<button[^>]*disabled=""[^>]*>Save access<\/button>/);
  });

  test("says so when nobody has a grant", () => {
    permissionsState = { data: [] };
    expect(render()).toContain(en.permissions.list.empty);
  });

  test("shows the outline while the grants load", () => {
    permissionsState = {};
    expect(render()).toContain(`aria-label="${en.permissions.loading}"`);
  });

  test("shows failed grants with a retry", () => {
    permissionsState = { error: new ApiError("Boom", { status: 500 }) };
    const html = render();
    expect(html).toContain(en.permissions.loadError);
    expect(html).toContain(en.permissions.retry);
  });

  test("keeps the list when the members fail, and says the picker cannot load", () => {
    membersState = { error: new ApiError("Boom", { status: 500 }) };
    const html = render();
    expect(html).toContain("Bob Test");
    expect(html).toContain(en.permissions.add.membersLoadError);
  });
});
