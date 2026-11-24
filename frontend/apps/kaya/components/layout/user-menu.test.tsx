import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import type { UserMeResponse } from "@/lib/identity/schemas";
import { ACCOUNT_LINKS, ULAK_LINKS } from "@oba/shared/constants/apps";

// The menu reads the user from the identity context. The dropdown mounts its
// content only while open, so it is replaced by a passthrough and the test sees
// the items the menu builds.
let me: UserMeResponse;

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualIdentity = { ...(await import("@/lib/identity/identity-context")) };
const actualDropdown = { ...(await import("@oba/ui/sky/dropdown-menu")) };
const actualLogoutClient = { ...(await import("@/lib/auth/logout-client")) };

mock.module("@/lib/identity/identity-context", () => ({
  ...actualIdentity,
  useIdentity: () => ({ me }),
}));
mock.module("@oba/ui/sky/dropdown-menu", () => ({
  ...actualDropdown,
  DropdownMenu: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  DropdownMenuTrigger: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  DropdownMenuContent: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  DropdownMenuLabel: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  DropdownMenuSeparator: () => <hr />,
  DropdownMenuItem: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));
mock.module("@/lib/auth/logout-client", () => ({
  ...actualLogoutClient,
  submitLogout: () => undefined,
}));

afterAll(() => {
  mock.module("@/lib/identity/identity-context", () => actualIdentity);
  mock.module("@oba/ui/sky/dropdown-menu", () => actualDropdown);
  mock.module("@/lib/auth/logout-client", () => actualLogoutClient);
});

const { UserMenu } = await import("./user-menu");

function meWith(managesOrganizations: boolean): UserMeResponse {
  return {
    id: "u1",
    email: "dev@example.com",
    userStatus: "ACTIVE",
    displayName: "Ada Lovelace",
    defaultOrganizationId: "org-1",
    memberships: [{ organizationId: "org-1", name: "Korur", role: "MEMBER" }],
    kayaWithheld: false,
    managesOrganizations,
  };
}

beforeEach(() => {
  me = meWith(false);
});

describe("UserMenu organization link", () => {
  test("links the organization settings of Ulak for a user who manages an organization", () => {
    me = meWith(true);

    const html = renderToStaticMarkup(<UserMenu />);

    expect(html).toContain(`href="${ULAK_LINKS.organizations}"`);
    expect(html).toContain("Organization");
  });

  test("hides the organization link from a user who manages none", () => {
    const html = renderToStaticMarkup(<UserMenu />);

    expect(html).not.toContain(ULAK_LINKS.organizations);
    expect(html).not.toContain(">Organization<");
  });

  test("keeps the account link and sign-out for every user", () => {
    const html = renderToStaticMarkup(<UserMenu />);

    expect(html).toContain(`href="${ACCOUNT_LINKS.profile}"`);
    expect(html).toContain("Sign out");
  });
});
