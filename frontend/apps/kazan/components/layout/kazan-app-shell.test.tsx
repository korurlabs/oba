import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { NextIntlClientProvider } from "next-intl";
import shared from "@oba/shared/locales/en.json";
import en from "@/locales/en.json";

// The router, the user, the header widgets, the menu, the gate and the
// session side effects are stand-ins that record how the shell wires them.
// The shell, the sky frame (top bar, menu panel, foot), the width provider,
// the resize handle and the messages are real.
type Role = "OWNER" | "MEMBER";

let pathname: string;
let role: Role;

const actualMe = { ...(await import("@oba/shared/features/users/hooks/use-get-me")) };
const actualNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };
const actualSelector = {
  ...(await import("@oba/shared/components/app-header/organization-selector")),
};
const actualSwitcher = { ...(await import("@oba/shared/components/app-header/app-switcher")) };
const actualProfile = { ...(await import("@oba/shared/components/app-header/profile-dropdown")) };
const actualLanguage = { ...(await import("@oba/shared/components/language-switcher")) };
const actualDropdown = { ...(await import("@oba/ui/components/dropdown-menu")) };
const actualMenu = { ...(await import("./kazan-menu")) };
const actualGate = { ...(await import("./app-access-gate")) };
const actualSync = { ...(await import("./account-session-sync")) };
const actualTitle = { ...(await import("./chat-unread-title")) };
const actualWatcher = {
  ...(await import("@/features/incidents/components/incident-alert-watcher")),
};

mock.module("@oba/shared/features/users/hooks/use-get-me", () => ({
  ...actualMe,
  useGetMe: () => ({
    data: { data: { organizations: [{ id: "org-1", role, organizationStatus: "ACTIVE" }] } },
  }),
}));
mock.module("@oba/shared/libs/i18n/navigation", () => ({
  ...actualNavigation,
  usePathname: () => pathname,
  Link: ({ href, children, ...rest }: { href: string; children?: ReactNode }) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));
mock.module("@oba/shared/components/app-header/organization-selector", () => ({
  ...actualSelector,
  OrganizationSelector: ({ app }: { app?: string }) => <div data-selector={app} />,
}));
mock.module("@oba/shared/components/app-header/app-switcher", () => ({
  ...actualSwitcher,
  AppSwitcher: ({ currentAppId }: { currentAppId?: string }) => (
    <div data-switcher={currentAppId} />
  ),
}));
mock.module("@oba/shared/components/app-header/profile-dropdown", () => ({
  ...actualProfile,
  ProfileDropdown: ({
    hrefs,
    extraMenuContent,
  }: {
    hrefs: { profile: string; settings: string };
    extraMenuContent?: ReactNode;
  }) => (
    <div data-profile-menu="" data-settings-href={hrefs.settings}>
      {extraMenuContent}
    </div>
  ),
}));
mock.module("@oba/shared/components/language-switcher", () => ({
  ...actualLanguage,
  LanguageSwitcherSubMenu: () => <div data-language="" />,
}));
mock.module("@oba/ui/components/dropdown-menu", () => ({
  ...actualDropdown,
  DropdownMenuItem: ({ children }: { children?: ReactNode }) => children,
}));
mock.module("./kazan-menu", () => ({ ...actualMenu, KazanMenu: () => <div data-menu="" /> }));
mock.module("./app-access-gate", () => ({
  ...actualGate,
  AppAccessGate: ({ children }: { children?: ReactNode }) => <div data-gate="">{children}</div>,
}));
mock.module("./account-session-sync", () => ({
  ...actualSync,
  AccountSessionSync: () => <i data-effect="session-sync" />,
}));
mock.module("./chat-unread-title", () => ({
  ...actualTitle,
  ChatUnreadTitle: () => <i data-effect="unread-title" />,
}));
mock.module("@/features/incidents/components/incident-alert-watcher", () => ({
  ...actualWatcher,
  IncidentAlertWatcher: () => <i data-effect="incident-alert" />,
}));

afterAll(() => {
  mock.module("@oba/shared/features/users/hooks/use-get-me", () => actualMe);
  mock.module("@oba/shared/libs/i18n/navigation", () => actualNavigation);
  mock.module("@oba/shared/components/app-header/organization-selector", () => actualSelector);
  mock.module("@oba/shared/components/app-header/app-switcher", () => actualSwitcher);
  mock.module("@oba/shared/components/app-header/profile-dropdown", () => actualProfile);
  mock.module("@oba/shared/components/language-switcher", () => actualLanguage);
  mock.module("@oba/ui/components/dropdown-menu", () => actualDropdown);
  mock.module("./kazan-menu", () => actualMenu);
  mock.module("./app-access-gate", () => actualGate);
  mock.module("./account-session-sync", () => actualSync);
  mock.module("./chat-unread-title", () => actualTitle);
  mock.module("@/features/incidents/components/incident-alert-watcher", () => actualWatcher);
});

const { KazanAppShell } = await import("./kazan-app-shell");

const messages = { ...shared, ...en };

function markup(): string {
  return renderToStaticMarkup(
    <NextIntlClientProvider locale="en" messages={messages} timeZone="UTC">
      <KazanAppShell>
        <p data-page="">the page</p>
      </KazanAppShell>
    </NextIntlClientProvider>
  );
}

/** The part of the markup from the first occurrence of `from` to the first `to` after it. */
function between(html: string, from: string, to: string): string {
  const start = html.indexOf(from);
  expect(start).toBeGreaterThan(-1);
  return html.slice(start, html.indexOf(to, start));
}

const footerOf = (html: string): string => html.slice(html.indexOf('data-sidebar="footer"'));

beforeEach(() => {
  pathname = "/home";
  role = "MEMBER";
});

describe("KazanAppShell top bar", () => {
  test("links the wordmark to /home with the app name", () => {
    const header = between(markup(), "<header", "</header>");
    expect(header).toContain('href="/home"');
    expect(header).toContain('aria-label="Kazan"');
  });

  test("shows the menu toggle at every width", () => {
    const html = markup();
    const at = html.indexOf('data-sidebar="trigger"');
    expect(at).toBeGreaterThan(-1);
    const toggle = html.slice(html.lastIndexOf("<button", at), html.indexOf(">", at));
    expect(toggle).toContain('data-slot="sidebar-trigger"');
    expect(toggle).not.toContain("md:hidden");
  });

  test("holds the selector for Kazan, the switcher marking Kazan and the profile menu, in order", () => {
    const header = between(markup(), "<header", "</header>");
    const selector = header.indexOf('data-selector="KAZAN"');
    const switcher = header.indexOf('data-switcher="kazan"');
    const profile = header.indexOf("data-profile-menu");
    expect(selector).toBeGreaterThan(-1);
    expect(switcher).toBeGreaterThan(selector);
    expect(profile).toBeGreaterThan(switcher);
  });

  test("points the profile menu's Settings at the Kazan settings", () => {
    expect(markup()).toContain('data-settings-href="/settings/columns"');
  });

  test("offers the language submenu to everyone", () => {
    expect(markup()).toContain("data-language");
  });

  test("shows the Organizations row only to an owner or admin", () => {
    const member = between(markup(), "data-profile-menu", "</header>");
    expect(member).not.toContain(en.sidebar.organizations);

    role = "OWNER";
    const owner = between(markup(), "data-profile-menu", "</header>");
    expect(owner).toContain(`<span>${en.sidebar.organizations}</span>`);
  });
});

describe("KazanAppShell menu panel", () => {
  test("shows the Kazan menu and only Settings in the foot", () => {
    const html = markup();
    expect(html).toContain("data-menu");
    const footer = footerOf(html);
    expect(footer).toContain(`<span>${en.sidebar.settings}</span>`);
    expect(footer).toContain('href="/settings/columns"');
    expect(footer).not.toContain(en.sidebar.downloads);
    expect(html).not.toContain("/downloads");
    expect(footer.match(/data-sidebar="menu-item"/g)).toHaveLength(1);
  });

  test("marks Settings on /settings/alerts, whose path is not its href", () => {
    pathname = "/settings/alerts";
    const footer = footerOf(markup());
    expect(footer).toContain('data-active="true"');
  });

  test("leaves Settings unmarked on a page outside the settings", () => {
    pathname = "/home";
    const footer = footerOf(markup());
    expect(footer).not.toContain('data-active="true"');
  });

  test("does not mark Settings on a path that only starts with the same letters", () => {
    pathname = "/settingsfoo";
    const footer = footerOf(markup());
    expect(footer).not.toContain('data-active="true"');
  });

  test("draws the resize edge in place of the rail", () => {
    const html = markup();
    const edge = between(html, 'role="separator"', ">");
    expect(edge).toContain(`aria-label="${en.sidebar.resizeSidebar}"`);
    expect(edge).toContain('aria-orientation="vertical"');
    expect(edge).toContain('aria-valuenow="320"');
    expect(edge).toContain('tabindex="0"');
    // The rail primitive is a button with the same data attribute; only the handle is left.
    expect(html).not.toContain('<button data-sidebar="rail"');
  });

  test("gives the panel the stored-width variable, 320 by default", () => {
    expect(between(markup(), 'data-slot="sidebar-wrapper"', ">")).toContain(
      "--sidebar-width:320px"
    );
  });
});

describe("KazanAppShell page area", () => {
  test("fills the space beside the menu, with no centred column", () => {
    const html = markup();
    expect(html).not.toContain("max-w-7xl");
    expect(html).toContain("min-h-0 flex-1 overflow-auto");
  });

  test("puts the page inside the access gate", () => {
    const html = markup();
    expect(html.indexOf("data-gate")).toBeLessThan(html.indexOf("data-page"));
  });

  test("mounts the session effects outside the gate, so every page runs them", () => {
    const html = markup();
    const gate = html.indexOf("data-gate");
    for (const effect of ["session-sync", "incident-alert", "unread-title"]) {
      const at = html.indexOf(`data-effect="${effect}"`);
      expect(at).toBeGreaterThan(-1);
      expect(at).toBeLessThan(gate);
    }
  });
});
