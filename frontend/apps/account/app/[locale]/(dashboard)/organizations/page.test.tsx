import { afterAll, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

// A thin page: the metadata and the organizations view, which the test replaces.
// The translator echoes its key.

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualServerIntl = { ...(await import("next-intl/server")) };
const actualView = { ...(await import("@/features/organizations/components/organizations-view")) };

mock.module("next-intl/server", () => ({
  ...actualServerIntl,
  getTranslations: async () => (key: string) => key,
}));
mock.module("@/features/organizations/components/organizations-view", () => ({
  ...actualView,
  OrganizationsView: () => <p>organizations-view</p>,
}));

afterAll(() => {
  mock.module("next-intl/server", () => actualServerIntl);
  mock.module("@/features/organizations/components/organizations-view", () => actualView);
});

const { default: OrganizationsPage, generateMetadata } = await import("./page");

describe("organizations page", () => {
  test("shows the organizations view", () => {
    expect(renderToStaticMarkup(<OrganizationsPage />)).toContain("organizations-view");
  });

  test("names the tab after the page", async () => {
    expect(await generateMetadata()).toEqual({ title: "title", description: "subtitle" });
  });
});
