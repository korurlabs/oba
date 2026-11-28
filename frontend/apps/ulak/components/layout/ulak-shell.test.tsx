import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import en from "@/locales/en.json";

// The menu, the shell and the messages are real. The router's path and link are
// fixed values; the header's shared parts, the gates and the session sync are
// stand-ins, and the organization gate shows which organization the shell
// handed to the pages below it.
let pathname: string;

const actualNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };
const actualNext = { ...(await import("next/navigation")) };
const actualId = { ...(await import("@/hooks/use-organization-id")) };
const actualAuth = { ...(await import("next-auth/react")) };
const actualSwitcher = { ...(await import("@oba/shared/components/app-header/app-switcher")) };
const actualSelector = {
  ...(await import("@oba/shared/components/app-header/organization-selector")),
};
const actualProfile = { ...(await import("@oba/shared/components/app-header/profile-dropdown")) };
const actualLanguage = { ...(await import("@oba/shared/components/language-switcher")) };
const actualSync = { ...(await import("./account-session-sync")) };
const actualAccess = { ...(await import("./ulak-access-gate")) };
const actualGate = { ...(await import("./organization-gate")) };

mock.module("@oba/shared/libs/i18n/navigation", () => ({
  ...actualNavigation,
  usePathname: () => pathname,
  Link: ({ href, children, ...rest }: { href: string; children?: ReactNode }) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));
mock.module("next/navigation", () => ({ ...actualNext, usePathname: () => pathname }));
mock.module("@oba/shared/components/app-header/app-switcher", () => ({
  ...actualSwitcher,
  AppSwitcher: () => null,
}));
mock.module("@oba/shared/components/app-header/organization-selector", () => ({
  ...actualSelector,
  OrganizationSelector: (props: { managedOnly?: boolean }) => (
    <div data-selector="" data-managed-only={String(props.managedOnly === true)} />
  ),
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
mock.module("./ulak-access-gate", () => ({ ...actualAccess, UlakAccessGate: () => null }));
mock.module("./organization-gate", () => ({
  ...actualGate,
  OrganizationGate: ({ children }: { children?: ReactNode }) => {
    const organizationId = actualId.useOrganizationId();
    return <section data-gate={String(organizationId)}>{children}</section>;
  },
}));
// The first render, before the browser has read the session: the hook answers
// with the organization the server rendered with.
mock.module("next-auth/react", () => ({
  ...actualAuth,
  useSession: () => ({ data: null, status: "loading" }),
}));

afterAll(() => {
  mock.module("@oba/shared/libs/i18n/navigation", () => actualNavigation);
  mock.module("next/navigation", () => actualNext);
  mock.module("@oba/shared/components/app-header/app-switcher", () => actualSwitcher);
  mock.module("@oba/shared/components/app-header/organization-selector", () => actualSelector);
  mock.module("@oba/shared/components/app-header/profile-dropdown", () => actualProfile);
  mock.module("@oba/shared/components/language-switcher", () => actualLanguage);
  mock.module("./account-session-sync", () => actualSync);
  mock.module("./ulak-access-gate", () => actualAccess);
  mock.module("./organization-gate", () => actualGate);
  mock.module("next-auth/react", () => actualAuth);
});

// The app name comes from the shared messages the real setup merges in.
const MESSAGES = { ...en, apps: { ulak: { name: "Ulak" } } };

const { UlakShell } = await import("./ulak-shell");

function render(organizationId: string | null = "org-1"): string {
  return renderToStaticMarkup(
    <NextIntlClientProvider locale="en" messages={MESSAGES} timeZone="UTC">
      <UlakShell organizationId={organizationId}>
        <p>the page</p>
      </UlakShell>
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
  pathname = "/humans";
});

describe("UlakShell menu", () => {
  test("lists Agents first, then the other pages of the organization in order", () => {
    const html = render();
    const labels = ["Agents", "Humans", "Groups", "Legal entities", "Access", "Invitations"];
    const positions = labels.map((label) => html.indexOf(`<span>${label}</span>`));
    expect(positions.every((position) => position > -1)).toBe(true);
    expect([...positions].sort((a, b) => a - b)).toEqual(positions);
    const hrefs = [
      "/agents",
      "/humans",
      "/groups",
      "/legal-entities",
      "/access",
      "/invitations",
    ].map((href) => html.indexOf(`href="${href}"`));
    expect([...hrefs].sort((a, b) => a - b)).toEqual(hrefs);
    expect(hrefs[0]).toBeGreaterThan(-1);
  });

  test("puts Organization settings in the footer and no organizations list in the menu", () => {
    const html = render();
    const footer = html.slice(html.indexOf('data-sidebar="footer"'));
    expect(footer).toContain("Organization settings");
    expect(footer).toContain('href="/settings"');
    expect(html).not.toContain('href="/organizations"');
  });

  test("puts Integrations in the footer directly above Organization settings", () => {
    const html = render();
    const footer = html.slice(html.indexOf('data-sidebar="footer"'));
    const integrations = footer.indexOf('href="/integrations"');
    const settings = footer.indexOf('href="/settings"');
    expect(integrations).toBeGreaterThan(-1);
    expect(footer).toContain("<span>Integrations</span>");
    expect(integrations).toBeLessThan(settings);
    // No other menu link sits between the two.
    expect(footer.slice(integrations, settings).match(/<a\b/g)).toHaveLength(1);
  });

  test("marks Integrations on its page and on every page below it", () => {
    for (const path of ["/integrations", "/integrations/azure"]) {
      pathname = path;
      expect(activeHrefs(render())).toEqual(["/integrations"]);
    }
  });

  test("marks only the page in view", () => {
    pathname = "/groups";
    const html = render();
    expect(activeHrefs(html)).toEqual(["/groups"]);
  });

  test("marks Agents while it is in view", () => {
    pathname = "/agents";
    expect(activeHrefs(render())).toEqual(["/agents"]);
  });

  test("marks a page below a menu page as that menu page", () => {
    pathname = "/humans/anything";
    expect(activeHrefs(render())).toEqual(["/humans"]);
  });

  test("marks Organization settings in the footer when it is in view", () => {
    pathname = "/settings";
    const html = render();
    expect(activeHrefs(html)).toEqual(["/settings"]);
    expect(html.indexOf('href="/settings"')).toBeGreaterThan(html.indexOf('data-sidebar="footer"'));
  });

  test("offers the organizations the user manages in the top bar", () => {
    const html = render();
    expect(html).toContain('data-managed-only="true"');
  });
});

describe("UlakShell organization", () => {
  test("hands its organizationId to the pages below it", () => {
    expect(render("org-7")).toContain('data-gate="org-7"');
  });

  test("hands null down when the session has no default organization", () => {
    expect(render(null)).toContain('data-gate="null"');
  });

  test("shows the page inside the gate", () => {
    const html = render();
    expect(html).toContain("<section");
    expect(html).toContain("the page");
  });
});
