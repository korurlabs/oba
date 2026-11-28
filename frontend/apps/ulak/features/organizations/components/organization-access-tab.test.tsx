import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { NuqsTestingAdapter } from "nuqs/adapters/testing";
import { renderToStaticMarkup } from "react-dom/server";
import type { AppGrant, MemberAppAccess } from "@oba/shared/features/organizations/schemas";

// The tab reads grants and effective access through query hooks. The hooks,
// the table, the grant dialog and the confirm dialog are replaced; the confirm
// button's handler is recorded, so the test can run a revoke.
type GrantsResult = {
  data?: { data: AppGrant[] };
  isLoading: boolean;
  isError: boolean;
  isFetching: boolean;
  refetch: () => void;
};

let grants: GrantsResult;
let members: MemberAppAccess[];
const revoked: string[] = [];
const confirmed: (() => void)[] = [];

const actualIntl = { ...(await import("next-intl")) };
const actualAccess = { ...(await import("../hooks/use-app-access")) };
const actualColumns = { ...(await import("../hooks/use-app-access-columns")) };
const actualTable = { ...(await import("@/components/data-table")) };
const actualDialog = { ...(await import("@oba/ui/sky/dialog")) };
const actualButton = { ...(await import("@oba/ui/sky/button")) };
const actualGrantDialog = { ...(await import("./grant-app-dialog")) };
const actualLoadError = { ...(await import("./organization-load-error")) };

mock.module("next-intl", () => ({
  ...actualIntl,
  useTranslations: () => (key: string, values?: Record<string, string>) =>
    values ? `${key}:${JSON.stringify(values)}` : key,
}));
mock.module("../hooks/use-app-access", () => ({
  ...actualAccess,
  useAppGrants: () => grants,
  useMemberAppAccess: () => ({ data: { data: members }, isLoading: false, isError: false }),
  useRevokeAppGrant: () => ({ isPending: false, mutate: (id: string) => void revoked.push(id) }),
}));
mock.module("../hooks/use-app-access-columns", () => ({
  ...actualColumns,
  useAppAccessColumns: () => [],
}));
mock.module("@/components/data-table", () => ({
  ...actualTable,
  DataTable: ({ data, emptyMessage }: { data: MemberAppAccess[]; emptyMessage: ReactNode }) =>
    data.length === 0 ? (
      <>{emptyMessage}</>
    ) : (
      <p>{`effective-table:${data.map((member) => member.fullName).join(",")}`}</p>
    ),
}));
mock.module("@oba/ui/sky/dialog", () => ({
  ...actualDialog,
  Dialog: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DialogTrigger: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DialogContent: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DialogHeader: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DialogFooter: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DialogTitle: ({ children }: { children: ReactNode }) => <h2>{children}</h2>,
  DialogDescription: ({ children }: { children: ReactNode }) => <p>{children}</p>,
  DialogClose: ({ children }: { children: ReactNode }) => <>{children}</>,
}));
// The destructive button is the confirm of the revoke dialog.
mock.module("@oba/ui/sky/button", () => ({
  ...actualButton,
  Button: ({
    children,
    onClick,
    variant,
  }: {
    children: ReactNode;
    onClick?: () => void;
    variant?: string;
  }) => {
    if (variant !== "destructive") return <span>{children}</span>;
    if (onClick) confirmed.push(onClick);
    return <span>{`confirm:${children as string}`}</span>;
  },
}));
mock.module("./grant-app-dialog", () => ({
  ...actualGrantDialog,
  GrantAppDialog: () => <p>grant-dialog</p>,
}));
mock.module("./organization-load-error", () => ({
  ...actualLoadError,
  OrganizationLoadError: () => <p>load-error</p>,
}));

afterAll(() => {
  mock.module("next-intl", () => actualIntl);
  mock.module("../hooks/use-app-access", () => actualAccess);
  mock.module("../hooks/use-app-access-columns", () => actualColumns);
  mock.module("@/components/data-table", () => actualTable);
  mock.module("@oba/ui/sky/dialog", () => actualDialog);
  mock.module("@oba/ui/sky/button", () => actualButton);
  mock.module("./grant-app-dialog", () => actualGrantDialog);
  mock.module("./organization-load-error", () => actualLoadError);
});

const { AccessTab } = await import("./organization-access-tab");

function grant(overrides: Partial<AppGrant>): AppGrant {
  return {
    id: "grant-1",
    app: "KAZAN",
    targetType: "USER",
    userId: "u-1",
    userFullName: "Ada Lovelace",
    groupId: null,
    groupName: null,
    groupType: null,
    ...overrides,
  } as AppGrant;
}

function loaded(list: AppGrant[]): GrantsResult {
  return {
    data: { data: list },
    isLoading: false,
    isError: false,
    isFetching: false,
    refetch: () => undefined,
  };
}

function render(searchParams = ""): string {
  return renderToStaticMarkup(
    <NuqsTestingAdapter searchParams={searchParams}>
      <AccessTab organizationId="org-1" />
    </NuqsTestingAdapter>
  );
}

beforeEach(() => {
  grants = loaded([]);
  members = [];
  revoked.length = 0;
  confirmed.length = 0;
});

describe("AccessTab grants", () => {
  test("shows a card for each grantable app, with the empty text when it has no grants", () => {
    const html = render();

    expect(html).toContain("kazan.name");
    expect(html).toContain("kaya.name");
    expect(html).toContain("kalkan.name");
    expect(html).toContain("otak.name");
    expect(html.match(/access\.noGrants/g)).toHaveLength(4);
  });

  test("lists a user grant and a group grant under their app with the target type badge", () => {
    grants = loaded([
      grant({}),
      grant({
        id: "grant-2",
        app: "KAYA",
        targetType: "GROUP",
        userId: null,
        userFullName: null,
        groupId: "g-1",
        groupName: "Developers",
        groupType: "CUSTOM",
      } as Partial<AppGrant>),
    ]);

    const html = render();

    expect(html).toContain("Ada Lovelace");
    expect(html).toContain("Developers");
    expect(html).toContain("access.targetUser");
    expect(html).toContain("access.targetGroup");
    // Only the Kalkan and Otak cards stay empty.
    expect(html.match(/access\.noGrants/g)).toHaveLength(2);
  });

  test("the Everyone group is bold, a custom group is not", () => {
    grants = loaded([
      grant({
        id: "g-a",
        targetType: "GROUP",
        groupName: "Everyone",
        groupType: "EVERYONE",
      } as Partial<AppGrant>),
      grant({
        id: "g-b",
        targetType: "GROUP",
        groupName: "Developers",
        groupType: "CUSTOM",
      } as Partial<AppGrant>),
    ]);

    const html = render();

    expect(html).toMatch(/font-bold[^>]*>Everyone</);
    expect(html).not.toMatch(/font-bold[^>]*>Developers</);
  });

  test("an unknown app value gets its own card under the raw value, and its grant can be revoked", () => {
    grants = loaded([grant({ id: "grant-x", app: "LATER_APP" } as Partial<AppGrant>)]);

    const html = render();

    expect(html).toContain("LATER_APP");
    expect(html).toContain("Ada Lovelace");
    expect(html).toContain("access.revoke");

    confirmed.at(-1)!();
    expect(revoked).toEqual(["grant-x"]);
  });

  test("the revoke confirmation names the app and the target", () => {
    grants = loaded([grant({})]);

    const html = render();

    expect(html).toContain("access.revokeConfirmDescription");
    expect(html).toContain("kazan.name");
    expect(html).toContain("Ada Lovelace");
  });

  test("shows a skeleton while the grants load", () => {
    grants = { isLoading: true, isError: false, isFetching: true, refetch: () => undefined };

    expect(render()).toContain("animate-pulse");
  });

  test("shows the load error with retry when the grants failed", () => {
    grants = { isLoading: false, isError: true, isFetching: false, refetch: () => undefined };

    const html = render();

    expect(html).toContain("load-error");
    expect(html).not.toContain("effective-table");
  });

  test("a search keeps only the apps with a grant to a matching member or group", () => {
    grants = loaded([
      grant({}),
      grant({
        id: "grant-2",
        app: "KAYA",
        targetType: "GROUP",
        userId: null,
        userFullName: null,
        groupId: "g-1",
        groupName: "Developers",
        groupType: "CUSTOM",
      } as Partial<AppGrant>),
    ]);

    const html = render("?search=develop");

    expect(html).toContain("Developers");
    expect(html).toContain("kaya.name");
    expect(html).not.toContain("Ada Lovelace");
    expect(html).not.toContain("kazan.name");
    expect(html).not.toContain("access.noGrants");
  });

  test("a search that matches no grant offers to clear it", () => {
    grants = loaded([grant({})]);

    const html = render("?search=nobody");

    expect(html).toContain("access.grantsFilteredTitle");
    expect(html).toContain("clearFilters");
  });
});

describe("AccessTab toolbar and tabs", () => {
  test("one toolbar holds the search, both tabs and the grant button", () => {
    const html = render();

    expect(html).toContain('data-slot="list-toolbar"');
    expect(html).toContain("access.searchGrantsLabel");
    expect(html).toContain("access.tabs.grants");
    expect(html).toContain("access.tabs.members");
    expect(html).toContain("access.grantAccess");
  });

  test("the effective access tab lists the members instead of the grant cards", () => {
    members = [
      { userId: "u-1", fullName: "Ada Lovelace", role: "MEMBER", apps: ["KAZAN"] },
      { userId: "u-2", fullName: "Grace Hopper", role: "ADMIN", apps: [] },
    ];

    const html = render("?tab=members");

    expect(html).toContain("effective-table:Ada Lovelace,Grace Hopper");
    expect(html).toContain("access.searchMembersLabel");
    expect(html).not.toContain("kazan.name");
  });

  test("the effective access search narrows the members by name", () => {
    members = [
      { userId: "u-1", fullName: "Ada Lovelace", role: "MEMBER", apps: [] },
      { userId: "u-2", fullName: "Grace Hopper", role: "ADMIN", apps: [] },
    ];

    expect(render("?tab=members&search=grace")).toContain("effective-table:Grace Hopper");
    expect(render("?tab=members&search=nobody")).toContain("access.membersFilteredTitle");
  });
});
