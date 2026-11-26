import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";
import { NextIntlClientProvider } from "next-intl";
import en from "@/locales/en.json";

// The access answer and the router are stand-ins, and the organization
// selector a marker. The gate, its two panels and the messages are real.
type Access =
  | { status: "pending" }
  | { status: "open" }
  | { status: "noOrganization" }
  | { status: "closed"; organization: { id: string; name: string } };

let access: Access;
let pathname: string;

const actualAccess = { ...(await import("./use-kazan-access")) };
const actualNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };
const actualSelector = {
  ...(await import("@oba/shared/components/app-header/organization-selector")),
};

mock.module("./use-kazan-access", () => ({ ...actualAccess, useKazanAccess: () => access }));
mock.module("@oba/shared/libs/i18n/navigation", () => ({
  ...actualNavigation,
  usePathname: () => pathname,
  useRouter: () => ({ refresh: () => undefined, push: () => undefined, replace: () => undefined }),
}));
mock.module("@oba/shared/components/app-header/organization-selector", () => ({
  ...actualSelector,
  OrganizationSelector: ({ app }: { app?: string }) => <div data-selector={app} />,
}));

afterAll(() => {
  mock.module("./use-kazan-access", () => actualAccess);
  mock.module("@oba/shared/libs/i18n/navigation", () => actualNavigation);
  mock.module("@oba/shared/components/app-header/organization-selector", () => actualSelector);
});

const { AppAccessGate } = await import("./app-access-gate");

function markup(): string {
  return renderToStaticMarkup(
    <NextIntlClientProvider locale="en" messages={en} timeZone="UTC">
      <AppAccessGate>
        <p data-page="">the page</p>
      </AppAccessGate>
    </NextIntlClientProvider>
  );
}

const CHOOSE = en.noOrganization.title;

beforeEach(() => {
  access = { status: "open" };
  pathname = "/home";
});

describe("AppAccessGate", () => {
  test("renders the page while Kazan is open", () => {
    const html = markup();
    expect(html).toContain('data-page=""');
    expect(html).not.toContain(CHOOSE);
  });

  test("renders the page while the answer is pending", () => {
    access = { status: "pending" };
    expect(markup()).toContain('data-page=""');
  });

  describe("without an organization", () => {
    beforeEach(() => {
      access = { status: "noOrganization" };
    });

    test("shows the choose panel with the selector for Kazan in place of the page", () => {
      const html = markup();
      expect(html).toContain(CHOOSE);
      expect(html).toContain(en.noOrganization.description);
      expect(html).toContain('data-selector="KAZAN"');
      expect(html).not.toContain('data-page=""');
    });

    test("shows it on a board page too", () => {
      pathname = "/board/platform/all";
      expect(markup()).toContain(CHOOSE);
    });

    test("lets /settings/columns render", () => {
      pathname = "/settings/columns";
      const html = markup();
      expect(html).toContain('data-page=""');
      expect(html).not.toContain(CHOOSE);
    });

    test("lets /settings/appearance render", () => {
      pathname = "/settings/appearance";
      const html = markup();
      expect(html).toContain('data-page=""');
      expect(html).not.toContain(CHOOSE);
    });

    test("does not let another settings page through", () => {
      pathname = "/settings/alerts";
      expect(markup()).toContain(CHOOSE);
    });
  });

  describe("in an organization that does not grant Kazan", () => {
    beforeEach(() => {
      access = { status: "closed", organization: { id: "org-2", name: "Acme" } };
    });

    test("shows the no-access panel naming the organization, not the choose panel", () => {
      const html = markup();
      expect(html).toContain("Kazan is not open to you in Acme");
      expect(html).toContain(en.noAppAccess.description);
      expect(html).toContain(en.noAppAccess.openAccount);
      expect(html).not.toContain(CHOOSE);
      expect(html).not.toContain('data-page=""');
    });

    test("covers /settings/columns as well, since the organization is chosen", () => {
      pathname = "/settings/columns";
      const html = markup();
      expect(html).toContain("Kazan is not open to you in Acme");
      expect(html).not.toContain('data-page=""');
    });
  });
});
