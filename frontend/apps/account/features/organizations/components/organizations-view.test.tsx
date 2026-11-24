import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";

// The organizations card lists the memberships and links to the organization
// creation page. The user
// query is replaced by a value the test sets; the translator echoes its key.
type Organization = { id: string; name: string; role: string };

let organizations: Organization[];

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualIntl = { ...(await import("next-intl")) };
const actualGetMe = { ...(await import("@oba/shared/features/users/hooks/use-get-me")) };
const actualNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };

mock.module("next-intl", () => ({
  ...actualIntl,
  useTranslations: () => (key: string) => key,
}));
mock.module("@oba/shared/features/users/hooks/use-get-me", () => ({
  ...actualGetMe,
  useGetMe: () => ({
    data: {
      data: {
        email: "a@b.c",
        profile: { firstName: "Ada", lastName: "L" },
        organizations,
      },
    },
    isPending: false,
    isFetching: false,
    refetch: async () => undefined,
  }),
}));
mock.module("@oba/shared/libs/i18n/navigation", () => ({
  ...actualNavigation,
  Link: ({ href, children }: { href: string; children: ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

afterAll(() => {
  mock.module("next-intl", () => actualIntl);
  mock.module("@oba/shared/features/users/hooks/use-get-me", () => actualGetMe);
  mock.module("@oba/shared/libs/i18n/navigation", () => actualNavigation);
});

const { OrganizationsView } = await import("./organizations-view");

beforeEach(() => {
  organizations = [];
});

function createLinks(html: string): string[] {
  return [...html.matchAll(/<a href="\/organizations\/new"[^>]*>(.*?)<\/a>/g)].map(
    (match) => match[1] ?? ""
  );
}

describe("OrganizationsView", () => {
  test("links to /organizations/new when the user has no organizations", () => {
    const html = renderToStaticMarkup(<OrganizationsView />);

    expect(createLinks(html)).toHaveLength(1);
    expect(createLinks(html)[0]).toContain("organization.new.title");
    expect(html).toContain("organization.noOrganizations");
  });

  test("links to /organizations/new when the user has organizations", () => {
    organizations = [{ id: "o1", name: "Acme", role: "OWNER" }];

    const html = renderToStaticMarkup(<OrganizationsView />);

    expect(createLinks(html)).toHaveLength(1);
    expect(html).toContain("Acme");
    expect(html).not.toContain("organization.noOrganizations");
  });

  test("shows the page heading and the organizations card", () => {
    const html = renderToStaticMarkup(<OrganizationsView />);

    expect(html).toContain("organization.list.title");
    expect(html).toContain("organization.list.subtitle");
    expect(html).toContain("organization.list.yourOrganizations");
  });
});
