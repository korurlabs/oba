import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import en from "@/locales/en.json";

// The menu, the shell and the messages are real. The router's path and link are
// fixed values; the header's shared parts and the session sync are stand-ins.
let pathname: string;

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };
const actualSwitcher = { ...(await import("@oba/shared/components/app-header/app-switcher")) };
const actualSelector = {
  ...(await import("@oba/shared/components/app-header/organization-selector")),
};
const actualProfile = { ...(await import("@oba/shared/components/app-header/profile-dropdown")) };
const actualLanguage = { ...(await import("@oba/shared/components/language-switcher")) };
const actualSync = { ...(await import("./account-session-sync")) };

mock.module("@oba/shared/libs/i18n/navigation", () => ({
  ...actualNavigation,
  usePathname: () => pathname,
  Link: ({ href, children, ...rest }: { href: string; children?: ReactNode }) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));
mock.module("@oba/shared/components/app-header/app-switcher", () => ({
  ...actualSwitcher,
  AppSwitcher: () => null,
}));
mock.module("@oba/shared/components/app-header/organization-selector", () => ({
  ...actualSelector,
  OrganizationSelector: () => null,
}));
mock.module("@oba/shared/components/app-header/profile-dropdown", () => ({
  ...actualProfile,
  ProfileDropdown: () => null,
}));
mock.module("@oba/shared/components/language-switcher", () => ({
  ...actualLanguage,
  LanguageSwitcherSubMenu: () => null,
}));
mock.module("./account-session-sync", () => ({ ...actualSync, AccountSessionSync: () => null }));

afterAll(() => {
  mock.module("@oba/shared/libs/i18n/navigation", () => actualNavigation);
  mock.module("@oba/shared/components/app-header/app-switcher", () => actualSwitcher);
  mock.module("@oba/shared/components/app-header/organization-selector", () => actualSelector);
  mock.module("@oba/shared/components/app-header/profile-dropdown", () => actualProfile);
  mock.module("@oba/shared/components/language-switcher", () => actualLanguage);
  mock.module("./account-session-sync", () => actualSync);
});

// The app name comes from the shared messages the real setup merges in.
const MESSAGES = { ...en, apps: { account: { name: "Account" } } };

const { AccountShell } = await import("./account-shell");

function render(): string {
  return renderToStaticMarkup(
    <NextIntlClientProvider locale="en" messages={MESSAGES} timeZone="UTC">
      <AccountShell>
        <p>the page</p>
      </AccountShell>
    </NextIntlClientProvider>
  );
}

/** The hrefs of the menu links the menu marks as the page in view. */
function activeHrefs(html: string): string[] {
  return [...html.matchAll(/<a\b[^>]*>/g)]
    .map((match) => match[0])
    .filter((tag) => tag.includes('data-active="true"'))
    .map((tag) => /href="([^"]+)"/.exec(tag)?.[1] ?? "");
}

beforeEach(() => {
  pathname = "/";
});

describe("AccountShell menu", () => {
  test("lists the profile, organizations, security, notifications and invitations in order", () => {
    const html = render();
    const labels = ["Profile", "Organizations", "Security", "Notifications", "My Invitations"];
    const positions = labels.map((label) => html.indexOf(`<span>${label}</span>`));
    expect(positions.every((position) => position > -1)).toBe(true);
    expect([...positions].sort((a, b) => a - b)).toEqual(positions);
    const hrefs = ["/organizations", "/security", "/notifications", "/invitations"].map((href) =>
      html.indexOf(`href="${href}"`)
    );
    expect(hrefs.every((position) => position > -1)).toBe(true);
    expect([...hrefs].sort((a, b) => a - b)).toEqual(hrefs);
  });

  test("keeps Settings in the footer", () => {
    const html = render();
    const footer = html.slice(html.indexOf('data-sidebar="footer"'));
    expect(footer).toContain("Settings");
    expect(footer).toContain('href="/settings"');
  });

  test("marks only the profile on the root page", () => {
    expect(activeHrefs(render())).toEqual(["/"]);
  });

  test.each(["/organizations", "/security", "/notifications"])(
    "marks %s while it is in view",
    (href) => {
      pathname = href;
      expect(activeHrefs(render())).toEqual([href]);
    }
  );

  test("marks Organizations while the create organization page is in view", () => {
    pathname = "/organizations/new";
    expect(activeHrefs(render())).toEqual(["/organizations"]);
  });

  test("shows the page", () => {
    expect(render()).toContain("the page");
  });
});
