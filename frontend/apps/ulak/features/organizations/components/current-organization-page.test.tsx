import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { NextIntlClientProvider } from "next-intl";
import { renderToStaticMarkup } from "react-dom/server";
import en from "@/locales/en.json";
import type { OrganizationSection } from "./current-organization-page";

// The current organization, the current user, the organization detail and the
// section bodies are fixed values or stand-ins that show which props they got;
// the page's own rules, its heading and the messages are real.
type Role = "OWNER" | "ADMIN" | "MEMBER" | "GUEST";
type MeState = {
  data?: { data?: { organizations?: Array<{ id: string; role: Role }> } };
  isPending: boolean;
  isError: boolean;
};
type OrganizationState = {
  data?: { data?: { name: string | null } };
  isLoading: boolean;
  isError: boolean;
};

let organizationId: string | null;
let me: MeState;
let organization: OrganizationState;

const actualId = { ...(await import("@/hooks/use-organization-id")) };
const actualGetMe = { ...(await import("@oba/shared/features/users/hooks/use-get-me")) };
const actualOrganizations = {
  ...(await import("@/features/organizations/hooks/use-organizations")),
};
const actualMembers = { ...(await import("./organization-members-tab")) };
const actualGroups = { ...(await import("./organization-groups-tab")) };
const actualLegal = { ...(await import("./organization-legal-entities-tab")) };
const actualAccess = { ...(await import("./organization-access-tab")) };
const actualInvitations = { ...(await import("./organization-invitations-tab")) };
const actualDetail = { ...(await import("./organization-detail-form")) };
const actualAgents = {
  ...(await import("@/features/agents/components/organization-agents-tab")),
};
const actualIntegrations = {
  ...(await import("@/features/integrations/components/integrations-tab")),
};
const actualAzure = {
  ...(await import("@/features/integrations/components/azure-connections-tab")),
};

function body(name: string) {
  return function Body(props: { canEdit?: boolean; currentUserRole?: string }) {
    return (
      <div
        data-body={name}
        data-can-edit={String(props.canEdit)}
        data-role={props.currentUserRole}
      />
    );
  };
}

mock.module("@/hooks/use-organization-id", () => ({
  ...actualId,
  useOrganizationId: () => organizationId,
}));
mock.module("@oba/shared/features/users/hooks/use-get-me", () => ({
  ...actualGetMe,
  useGetMe: () => ({ ...me, refetch: async () => undefined }),
}));
mock.module("@/features/organizations/hooks/use-organizations", () => ({
  ...actualOrganizations,
  useGetOrganization: () => organization,
}));
mock.module("./organization-members-tab", () => ({ ...actualMembers, MembersTab: body("humans") }));
mock.module("./organization-groups-tab", () => ({ ...actualGroups, GroupsTab: body("groups") }));
mock.module("./organization-legal-entities-tab", () => ({
  ...actualLegal,
  LegalEntitiesTab: body("legalEntities"),
}));
mock.module("./organization-access-tab", () => ({ ...actualAccess, AccessTab: body("access") }));
mock.module("./organization-invitations-tab", () => ({
  ...actualInvitations,
  InvitationsTab: body("invitations"),
}));
mock.module("./organization-detail-form", () => ({
  ...actualDetail,
  OrganizationDetailForm: body("settings"),
}));
mock.module("@/features/agents/components/organization-agents-tab", () => ({
  ...actualAgents,
  AgentsTab: body("agents"),
}));
mock.module("@/features/integrations/components/integrations-tab", () => ({
  ...actualIntegrations,
  IntegrationsTab: body("integrations"),
}));
mock.module("@/features/integrations/components/azure-connections-tab", () => ({
  ...actualAzure,
  AzureConnectionsTab: body("azureIntegration"),
}));

afterAll(() => {
  mock.module("@/hooks/use-organization-id", () => actualId);
  mock.module("@oba/shared/features/users/hooks/use-get-me", () => actualGetMe);
  mock.module("@/features/organizations/hooks/use-organizations", () => actualOrganizations);
  mock.module("./organization-members-tab", () => actualMembers);
  mock.module("./organization-groups-tab", () => actualGroups);
  mock.module("./organization-legal-entities-tab", () => actualLegal);
  mock.module("./organization-access-tab", () => actualAccess);
  mock.module("./organization-invitations-tab", () => actualInvitations);
  mock.module("./organization-detail-form", () => actualDetail);
  mock.module("@/features/agents/components/organization-agents-tab", () => actualAgents);
  mock.module("@/features/integrations/components/integrations-tab", () => actualIntegrations);
  mock.module("@/features/integrations/components/azure-connections-tab", () => actualAzure);
});

const { CurrentOrganizationPage } = await import("./current-organization-page");

const SECTIONS: Array<{ section: OrganizationSection; title: string }> = [
  { section: "agents", title: en.sidebar.agents },
  { section: "humans", title: en.sidebar.humans },
  { section: "groups", title: en.sidebar.groups },
  { section: "legalEntities", title: en.sidebar.legalEntities },
  { section: "access", title: en.sidebar.access },
  { section: "invitations", title: en.sidebar.invitations },
  { section: "integrations", title: en.sidebar.integrations },
  { section: "azureIntegration", title: en.sidebar.azureIntegration },
  { section: "settings", title: en.sidebar.organizationSettings },
];

function render(section: OrganizationSection): string {
  return renderToStaticMarkup(
    <NextIntlClientProvider locale="en" messages={en} timeZone="UTC">
      <CurrentOrganizationPage section={section} />
    </NextIntlClientProvider>
  );
}

function asRole(role: Role | null): MeState {
  return {
    data: { data: { organizations: role ? [{ id: "org-1", role }] : [] } },
    isPending: false,
    isError: false,
  };
}

const NOT_FOUND = en.errors.notFound.title;

beforeEach(() => {
  organizationId = "org-1";
  me = asRole("OWNER");
  organization = { data: { data: { name: "Korur" } }, isLoading: false, isError: false };
});

describe("CurrentOrganizationPage sections", () => {
  for (const { section, title } of SECTIONS) {
    test(`${section} shows its heading, the organization name and its body to an OWNER`, () => {
      const html = render(section);
      expect(html).toContain(`<h1 class="break-words">${title}</h1>`);
      expect(html).toContain("Korur");
      expect(html).toContain(`data-body="${section}"`);
      expect(html).not.toContain(NOT_FOUND);
    });
  }

  for (const section of [
    "agents",
    "groups",
    "access",
    "integrations",
    "azureIntegration",
  ] as const) {
    test(`${section} answers not found for a MEMBER and a GUEST, without its body`, () => {
      for (const role of ["MEMBER", "GUEST"] as const) {
        me = asRole(role);
        const html = render(section);
        expect(html).toContain(NOT_FOUND);
        expect(html).not.toContain("data-body");
      }
    });

    test(`${section} shows to an ADMIN`, () => {
      me = asRole("ADMIN");
      expect(render(section)).toContain(`data-body="${section}"`);
    });
  }

  test("legal entities show to a MEMBER, read-only, and are not found for a GUEST", () => {
    me = asRole("MEMBER");
    const member = render("legalEntities");
    expect(member).toContain('data-body="legalEntities"');
    expect(member).toContain('data-can-edit="false"');
    me = asRole("GUEST");
    const guest = render("legalEntities");
    expect(guest).toContain(NOT_FOUND);
    expect(guest).not.toContain("data-body");
  });

  for (const section of ["humans", "invitations", "settings"] as const) {
    test(`${section} shows read-only to a MEMBER`, () => {
      me = asRole("MEMBER");
      const html = render(section);
      expect(html).toContain(`data-body="${section}"`);
      expect(html).toContain('data-can-edit="false"');
    });
  }

  test("an OWNER or ADMIN gets edit controls", () => {
    expect(render("humans")).toContain('data-can-edit="true"');
    me = asRole("ADMIN");
    expect(render("humans")).toContain('data-can-edit="true"');
  });

  test("the invitations body learns the user's role", () => {
    me = asRole("ADMIN");
    expect(render("invitations")).toContain('data-role="ADMIN"');
  });

  test("a user without a role in the organization sees hidden sections as not found", () => {
    me = asRole(null);
    expect(render("groups")).toContain(NOT_FOUND);
    expect(render("legalEntities")).toContain(NOT_FOUND);
  });
});

describe("CurrentOrganizationPage states", () => {
  test("shows the loading state while the organization loads", () => {
    organization = { isLoading: true, isError: false };
    const html = render("humans");
    expect(html).toContain(en.organization.loadingOrganization);
    expect(html).not.toContain("data-body");
  });

  test("shows the loading state while the user loads, so a role is never guessed", () => {
    me = { isPending: true, isError: false };
    const html = render("groups");
    expect(html).toContain(en.organization.loadingOrganization);
    expect(html).not.toContain(NOT_FOUND);
  });

  test("answers organization not found without an organization id", () => {
    organizationId = null;
    organization = { isLoading: false, isError: false };
    const html = render("humans");
    expect(html).toContain(en.organization.notFound);
    expect(html).not.toContain("data-body");
  });

  test("answers organization not found when the organization failed to load", () => {
    organization = { isLoading: false, isError: true };
    expect(render("humans")).toContain(en.organization.notFound);
  });

  test("replaces the section with a load error and retry when no user could be loaded", () => {
    me = { isPending: false, isError: true };
    const html = render("groups");
    expect(html).toContain(en.errors.somethingWentWrong);
    expect(html).toContain(en.errors.tryAgain);
    expect(html).not.toContain("data-body");
    expect(html).not.toContain(NOT_FOUND);
  });

  test("keeps the section when a refetch of the cached user failed", () => {
    me = { ...asRole("OWNER"), isError: true };
    expect(render("groups")).toContain('data-body="groups"');
  });
});
