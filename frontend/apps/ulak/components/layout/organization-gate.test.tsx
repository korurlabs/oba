import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { NextIntlClientProvider } from "next-intl";
import { renderToStaticMarkup } from "react-dom/server";
import en from "@/locales/en.json";

// The gate reads the current organization and the current user; both are fixed
// values. The selector is a stand-in that shows the props the gate gives it.
type Membership = { id: string; name: string; role: string; organizationStatus: string };
type MeState = {
  data?: { data?: { organizations?: Membership[] } };
  isPending: boolean;
  isError: boolean;
};

let organizationId: string | null;
let me: MeState;

const actualId = { ...(await import("@/hooks/use-organization-id")) };
const actualGetMe = { ...(await import("@oba/shared/features/users/hooks/use-get-me")) };
const actualSelector = {
  ...(await import("@oba/shared/components/app-header/organization-selector")),
};

mock.module("@/hooks/use-organization-id", () => ({
  ...actualId,
  useOrganizationId: () => organizationId,
}));
mock.module("@oba/shared/features/users/hooks/use-get-me", () => ({
  ...actualGetMe,
  useGetMe: () => me,
}));
mock.module("@oba/shared/components/app-header/organization-selector", () => ({
  ...actualSelector,
  OrganizationSelector: (props: { managedOnly?: boolean }) => (
    <div data-selector="" data-managed-only={String(props.managedOnly === true)} />
  ),
}));

afterAll(() => {
  mock.module("@/hooks/use-organization-id", () => actualId);
  mock.module("@oba/shared/features/users/hooks/use-get-me", () => actualGetMe);
  mock.module("@oba/shared/components/app-header/organization-selector", () => actualSelector);
});

const { OrganizationGate } = await import("./organization-gate");

function membership(id: string, role: string, organizationStatus = "ACTIVE"): Membership {
  return { id, name: id, role, organizationStatus };
}

function loaded(organizations: Membership[]): MeState {
  return { data: { data: { organizations } }, isPending: false, isError: false };
}

function render(): string {
  return renderToStaticMarkup(
    <NextIntlClientProvider locale="en" messages={en} timeZone="UTC">
      <OrganizationGate>
        <p>the page</p>
      </OrganizationGate>
    </NextIntlClientProvider>
  );
}

beforeEach(() => {
  organizationId = "org-1";
  me = loaded([membership("org-1", "OWNER")]);
});

describe("OrganizationGate", () => {
  test("shows the page for a default organization the user manages", () => {
    const html = render();
    expect(html).toContain("the page");
    expect(html).not.toContain(en.organizationGate.title);
  });

  test("shows the page for an ADMIN of the default organization", () => {
    me = loaded([membership("org-1", "ADMIN")]);
    expect(render()).toContain("the page");
  });

  test("shows the panel and no page for a null default organization", () => {
    organizationId = null;
    me = { isPending: true, isError: false };
    const html = render();
    expect(html).not.toContain("the page");
    expect(html).toContain(en.organizationGate.title);
    expect(html).toContain(en.organizationGate.description);
  });

  test("shows the panel for a default organization the user does not manage", () => {
    me = loaded([membership("org-1", "MEMBER"), membership("org-2", "OWNER")]);
    const html = render();
    expect(html).not.toContain("the page");
    expect(html).toContain(en.organizationGate.title);
  });

  test("shows the panel when the managed organization is not the default one", () => {
    me = loaded([membership("org-2", "OWNER")]);
    expect(render()).toContain(en.organizationGate.title);
  });

  test("shows the panel when the managed default organization is disabled", () => {
    me = loaded([membership("org-1", "OWNER", "DISABLED")]);
    expect(render()).toContain(en.organizationGate.title);
  });

  test("offers only the organizations the user manages", () => {
    me = loaded([membership("org-1", "GUEST")]);
    const html = render();
    expect(html).toContain('data-managed-only="true"');
  });

  test("shows the loading state, not the panel, while the user loads", () => {
    me = { isPending: true, isError: false };
    const html = render();
    expect(html).toContain(en.organization.loadingOrganization);
    expect(html).not.toContain(en.organizationGate.title);
    expect(html).not.toContain("the page");
  });

  test("shows the page after a failed load of the user, so the page shows its own error", () => {
    me = { isPending: false, isError: true };
    expect(render()).toContain("the page");
  });

  test("still shows the panel for no organization after a failed load of the user", () => {
    organizationId = null;
    me = { isPending: false, isError: true };
    const html = render();
    expect(html).not.toContain("the page");
    expect(html).toContain(en.organizationGate.title);
  });
});
