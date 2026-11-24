import { afterAll, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

// The page is not built yet: its heading and one "coming soon" panel. The
// translator echoes its key.

// A copy, not the namespace: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mock back for the files that run later.
const actualServerIntl = { ...(await import("next-intl/server")) };

mock.module("next-intl/server", () => ({
  ...actualServerIntl,
  getTranslations: async () => (key: string) => key,
}));

afterAll(() => {
  mock.module("next-intl/server", () => actualServerIntl);
});

const { default: Page, generateMetadata } = await import("./page");

describe("security page", () => {
  test("shows the page heading and the coming soon panel", async () => {
    const html = renderToStaticMarkup(await Page());

    expect(html).toContain("<h1>title</h1>");
    expect(html).toContain("subtitle");
    expect(html).toContain('id="security-heading"');
    expect(html).toContain("panelDescription");
    expect(html).toContain("comingSoon");
  });

  test("names the tab after the page", async () => {
    expect(await generateMetadata()).toEqual({ title: "title", description: "subtitle" });
  });
});
