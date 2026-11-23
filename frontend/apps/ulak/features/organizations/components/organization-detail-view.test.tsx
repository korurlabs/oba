import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";
import { ApiError } from "@oba/shared/api/axios";

// The view derives the caller's role from the current-user query and hands
// `canEdit` and the role to its tabs. The query hooks, the router and the tab
// bodies are replaced; each tab body prints the props it received, so the test
// reads what the role gating passed down.
type MeResult = {
  data?: { success: boolean; data?: { organizations: { id: string; role: string }[] } };
  isPending: boolean;
  isError: boolean;
  isFetching: boolean;
};
type OrgResult = {
  data?: { data?: { name: string } };
  isLoading: boolean;
  isError: boolean;
  error?: Error;
};

const ORG_ID = "org-1";

let me: MeResult;
let org: OrgResult;

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualIntl = { ...(await import("next-intl")) };
const actualNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };
const actualUseGetMe = { ...(await import("@oba/shared/features/users/hooks/use-get-me")) };
const actualOrganizations = {
  ...(await import("@/features/organizations/hooks/use-organizations")),
};
const actualTab = { ...(await import("@/features/organizations/hooks/use-org-detail-tab")) };
const actualTabs = { ...(await import("@oba/ui/sky/tabs")) };
const actualForm = {
  ...(await import("@/features/organizations/components/organization-detail-form")),
};
const actualMembers = {
  ...(await import("@/features/organizations/components/organization-members-tab")),
};
const actualInvitations = {
  ...(await import("@/features/organizations/components/organization-invitations-tab")),
};
const actualLoadError = {
  ...(await import("@/features/organizations/components/organization-load-error")),
};
const actualGroups = {
  ...(await import("@/features/organizations/components/organization-groups-tab")),
};
const actualAccess = {
  ...(await import("@/features/organizations/components/organization-access-tab")),
};
const actualLegalEntities = {
  ...(await import("@/features/organizations/components/organization-legal-entities-tab")),
};

mock.module("next-intl", () => ({
  ...actualIntl,
  useTranslations: () => (key: string) => key,
}));
mock.module("@oba/shared/libs/i18n/navigation", () => ({
  ...actualNavigation,
  Link: ({ children }: { children: React.ReactNode }) => <a>{children}</a>,
  useRouter: () => ({ push: () => undefined }),
}));
mock.module("@oba/shared/features/users/hooks/use-get-me", () => ({
  ...actualUseGetMe,
  useGetMe: () => ({ ...me, refetch: async () => undefined }),
}));
mock.module("@/features/organizations/hooks/use-organizations", () => ({
  ...actualOrganizations,
  useGetOrganization: () => org,
}));
mock.module("@/features/organizations/hooks/use-org-detail-tab", () => ({
  ...actualTab,
  useOrgDetailTab: () => ({ activeTab: "detail", setTab: () => undefined }),
}));
mock.module("@oba/ui/sky/tabs", () => ({
  ...actualTabs,
  Tabs: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  TabsList: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  TabsTrigger: ({ children }: { children: React.ReactNode }) => <span>{children}</span>,
  TabsContent: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));
mock.module("@/features/organizations/components/organization-detail-form", () => ({
  ...actualForm,
  OrganizationDetailForm: ({ canEdit }: { canEdit: boolean }) => <p>{`form canEdit=${canEdit}`}</p>,
}));
mock.module("@/features/organizations/components/organization-members-tab", () => ({
  ...actualMembers,
  MembersTab: ({ canEdit }: { canEdit: boolean }) => <p>{`members canEdit=${canEdit}`}</p>,
}));
mock.module("@/features/organizations/components/organization-invitations-tab", () => ({
  ...actualInvitations,
  InvitationsTab: ({
    canEdit,
    currentUserRole,
  }: {
    canEdit: boolean;
    currentUserRole?: string;
  }) => <p>{`invitations canEdit=${canEdit} role=${currentUserRole}`}</p>,
}));
mock.module("@/features/organizations/components/organization-load-error", () => ({
  ...actualLoadError,
  OrganizationLoadError: () => <p>load-error</p>,
}));
mock.module("@/features/organizations/components/organization-groups-tab", () => ({
  ...actualGroups,
  GroupsTab: () => <p>groups-tab</p>,
}));
mock.module("@/features/organizations/components/organization-access-tab", () => ({
  ...actualAccess,
  AccessTab: () => <p>access-tab</p>,
}));
mock.module("@/features/organizations/components/organization-legal-entities-tab", () => ({
  ...actualLegalEntities,
  LegalEntitiesTab: ({ canEdit }: { canEdit: boolean }) => (
    <p>{`legal-entities canEdit=${canEdit}`}</p>
  ),
}));

afterAll(() => {
  mock.module("next-intl", () => actualIntl);
  mock.module("@oba/shared/libs/i18n/navigation", () => actualNavigation);
  mock.module("@oba/shared/features/users/hooks/use-get-me", () => actualUseGetMe);
  mock.module("@/features/organizations/hooks/use-organizations", () => actualOrganizations);
  mock.module("@/features/organizations/hooks/use-org-detail-tab", () => actualTab);
  mock.module("@oba/ui/sky/tabs", () => actualTabs);
  mock.module("@/features/organizations/components/organization-detail-form", () => actualForm);
  mock.module("@/features/organizations/components/organization-members-tab", () => actualMembers);
  mock.module(
    "@/features/organizations/components/organization-invitations-tab",
    () => actualInvitations
  );
  mock.module("@/features/organizations/components/organization-load-error", () => actualLoadError);
  mock.module("@/features/organizations/components/organization-groups-tab", () => actualGroups);
  mock.module("@/features/organizations/components/organization-access-tab", () => actualAccess);
  mock.module(
    "@/features/organizations/components/organization-legal-entities-tab",
    () => actualLegalEntities
  );
});

const { OrganizationDetailView } = await import("./organization-detail-view");

function meWithRole(role: string | null): MeResult {
  return {
    data: {
      success: true,
      data: {
        organizations: role
          ? [
              { id: ORG_ID, role },
              { id: "other", role: "OWNER" },
            ]
          : [{ id: "other", role: "OWNER" }],
      },
    },
    isPending: false,
    isError: false,
    isFetching: false,
  };
}

function render(): string {
  return renderToStaticMarkup(<OrganizationDetailView id={ORG_ID} />);
}

beforeEach(() => {
  me = meWithRole("MEMBER");
  org = { data: { data: { name: "Korur" } }, isLoading: false, isError: false };
});

describe("OrganizationDetailView role gating", () => {
  test.each(["OWNER", "ADMIN"])("%s may edit the details, members and invitations", (role) => {
    me = meWithRole(role);

    const html = render();

    expect(html).toContain("form canEdit=true");
    expect(html).toContain("members canEdit=true");
    expect(html).toContain(`invitations canEdit=true role=${role}`);
    expect(html).toContain("tabs.legalEntities");
    expect(html).toContain("legal-entities canEdit=true");
    expect(html).toContain("tabs.groups");
    expect(html).toContain("groups-tab");
    expect(html).toContain("tabs.access");
    expect(html).toContain("access-tab");
  });

  test.each(["MEMBER", "GUEST"])("%s gets read-only views", (role) => {
    me = meWithRole(role);

    const html = render();

    expect(html).toContain("form canEdit=false");
    expect(html).toContain("members canEdit=false");
    expect(html).toContain(`invitations canEdit=false role=${role}`);
    expect(html).not.toContain("tabs.groups");
    expect(html).not.toContain("groups-tab");
    expect(html).not.toContain("tabs.access");
    expect(html).not.toContain("access-tab");
  });

  test("MEMBER reads the legal entities without write controls", () => {
    me = meWithRole("MEMBER");

    const html = render();

    expect(html).toContain("tabs.legalEntities");
    expect(html).toContain("legal-entities canEdit=false");
  });

  test("GUEST gets no legal entities tab, which the API refuses below MEMBER", () => {
    me = meWithRole("GUEST");

    const html = render();

    expect(html).not.toContain("tabs.legalEntities");
    expect(html).not.toContain("legal-entities");
  });

  test("a role of another organization does not grant editing here", () => {
    me = meWithRole(null);

    const html = render();

    expect(html).toContain("form canEdit=false");
    expect(html).toContain("invitations canEdit=false role=undefined");
  });

  test("shows the organization name once loaded", () => {
    expect(render()).toContain("Korur");
  });
});

describe("OrganizationDetailView states", () => {
  test("shows a skeleton and no tabs while the current user loads", () => {
    me = { isPending: true, isError: false, isFetching: true };

    const html = render();

    expect(html).toContain("animate-pulse");
    expect(html).not.toContain("canEdit");
  });

  test("shows a skeleton while the organization loads", () => {
    org = { isLoading: true, isError: false };

    const html = render();

    expect(html).toContain("animate-pulse");
    expect(html).not.toContain("canEdit");
  });

  test("shows the not-found state when the organization failed to load", () => {
    org = { isLoading: false, isError: true };

    const html = render();

    expect(html).toContain("notFound");
    expect(html).not.toContain("canEdit");
  });

  test("shows the not-found state when the API refuses the role with ORG_SEC_003", () => {
    org = {
      isLoading: false,
      isError: true,
      error: new ApiError("Forbidden", { status: 403, code: "ORG_SEC_003" }),
    };

    const html = render();

    expect(html).toContain("notFound");
    expect(html).not.toContain("canEdit");
  });

  test("shows the not-found state when the organization has no data", () => {
    org = { data: {}, isLoading: false, isError: false };

    expect(render()).toContain("notFound");
  });

  test("shows the retry error instead of read-only tabs when the current user query failed", () => {
    me = { isPending: false, isError: true, isFetching: false };

    const html = render();

    expect(html).toContain("load-error");
    expect(html).not.toContain("canEdit");
  });

  test("keeps the tabs of the cached user when a refetch failed", () => {
    me = {
      data: { success: true, data: { organizations: [{ id: ORG_ID, role: "OWNER" }] } },
      isPending: false,
      isError: true,
      isFetching: false,
    };

    const html = render();

    expect(html).not.toContain("load-error");
    expect(html).toContain("form canEdit=true");
    expect(html).toContain("invitations canEdit=true role=OWNER");
  });
});
