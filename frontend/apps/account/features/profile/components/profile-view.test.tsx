import { afterAll, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

// The profile holds only the user's identity and the edit form: the
// organizations, security and notifications have pages of their own. The user
// query is replaced by a fixed user; the translator echoes its key.

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualIntl = { ...(await import("next-intl")) };
const actualGetMe = { ...(await import("@oba/shared/features/users/hooks/use-get-me")) };
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
        organizations: [{ id: "o1", name: "Acme", role: "OWNER" }],
      },
    },
    isPending: false,
    isFetching: false,
    refetch: async () => undefined,
  }),
}));
mock.module("./profile-form", () => ({
  ...actualForm,
  ProfileForm: () => <div data-testid="profile-form" />,
}));

afterAll(() => {
  mock.module("next-intl", () => actualIntl);
  mock.module("@oba/shared/features/users/hooks/use-get-me", () => actualGetMe);
  mock.module("./profile-form", () => actualForm);
});

const { ProfileView } = await import("./profile-view");

describe("ProfileView", () => {
  test("shows the user's name, email and the edit form", () => {
    const html = renderToStaticMarkup(<ProfileView />);

    expect(html).toContain("profile.yourProfile");
    expect(html).toContain("Ada L");
    expect(html).toContain("a@b.c");
    expect(html).toContain('data-testid="profile-form"');
  });

  test("leaves the organizations, security and notifications to their own pages", () => {
    const html = renderToStaticMarkup(<ProfileView />);

    expect(html).not.toContain("Acme");
    expect(html).not.toContain("/organizations/new");
    expect(html).not.toContain("security");
    expect(html).not.toContain("notifications");
  });
});
