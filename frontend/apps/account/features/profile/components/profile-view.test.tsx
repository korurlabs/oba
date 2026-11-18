import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";

// The organizations card links to the organization creation page. The user
// query is replaced by a value the test sets; the translator echoes its key.
type Organization = { id: string; name: string; role: string };

let organizations: Organization[];

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualIntl = { ...(await import("next-intl")) };
const actualGetMe = { ...(await import("@oba/shared/features/users/hooks/use-get-me")) };
const actualNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };
const actualForm = { ...(await import("./profile-form")) };

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
mock.module("./profile-form", () => ({
  ...actualForm,
  ProfileForm: () => <div data-testid="profile-form" />,
}));

afterAll(() => {
  mock.module("next-intl", () => actualIntl);
  mock.module("@oba/shared/features/users/hooks/use-get-me", () => actualGetMe);
  mock.module("@oba/shared/libs/i18n/navigation", () => actualNavigation);
  mock.module("./profile-form", () => actualForm);
});

const { ProfileView } = await import("./profile-view");

beforeEach(() => {
  organizations = [];
});

function createLinks(html: string): string[] {
  return [...html.matchAll(/<a href="\/organizations\/new"[^>]*>(.*?)<\/a>/g)].map(
    (match) => match[1] ?? "",
  );
}

describe("ProfileView create organization link", () => {
  test("links to /organizations/new when the user has no organizations", () => {
    const html = renderToStaticMarkup(<ProfileView />);

    expect(createLinks(html)).toHaveLength(1);
    expect(createLinks(html)[0]).toContain("organization.new.title");
    expect(html).toContain("organization.noOrganizations");
  });

  test("links to /organizations/new when the user has organizations", () => {
    organizations = [{ id: "o1", name: "Acme", role: "OWNER" }];

    const html = renderToStaticMarkup(<ProfileView />);

    expect(createLinks(html)).toHaveLength(1);
    expect(html).toContain("Acme");
    expect(html).not.toContain("organization.noOrganizations");
  });
});
