import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { NextIntlClientProvider } from "next-intl";
import { ACCOUNT_LINKS } from "@oba/shared/constants/apps";
import shared from "@oba/shared/locales/en.json";
import en from "@/locales/en.json";

// The router, the user, the header widgets, the menu, the gate and the
// session side effects are stand-ins that record how the shell wires them.
// The shell, the sky frame (top bar, menu panel, foot), the width provider,
// the resize handle and the messages are real.
type Role = "OWNER" | "MEMBER";

let pathname: string;
let role: Role;
let layout: "modern" | "classic";

const actualMe = { ...(await import("@oba/shared/features/users/hooks/use-get-me")) };
const actualNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };
const actualSelector = {
  ...(await import("@oba/shared/components/app-header/organization-selector")),
};
const actualSwitcher = { ...(await import("@oba/shared/components/app-header/app-switcher")) };
const actualProfile = { ...(await import("@oba/shared/components/app-header/profile-dropdown")) };
const actualLanguage = { ...(await import("@oba/shared/components/language-switcher")) };
const actualMenu = { ...(await import("./kazan-menu")) };
const actualNavItems = { ...(await import("./kazan-nav-items")) };
const actualAppearanceItem = { ...(await import("./appearance-menu-item")) };
const actualAppearance = {
  ...(await import("@/features/settings/hooks/use-kazan-appearance")),
};
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
    profileHref,
    extraMenuContent,
  }: {
    profileHref?: string;
    extraMenuContent?: ReactNode;
  }) => (
    <div data-profile-menu="" data-profile-href={profileHref}>
      {extraMenuContent}
    </div>
  ),
}));
mock.module("@oba/shared/components/language-switcher", () => ({
  ...actualLanguage,
  LanguageSwitcherSubMenu: () => <div data-language="" />,
}));
mock.module("./kazan-menu", () => ({ ...actualMenu, KazanMenu: () => <div data-menu="" /> }));
// The rows render a marker each, so a test sees the order the classic
// sidebar lists them in.
mock.module("./kazan-nav-items", () => ({
  ...actualNavItems,
  useKazanNavGroups: () => ({
    operations: ["home", "incidents", "cyberscore"].map((id) => ({
      id,
      node: <li data-row={id} />,
    })),
    work: ["tickets", "chats", "boards"].map((id) => ({ id, node: <li data-row={id} /> })),
  }),
}));
mock.module("./appearance-menu-item", () => ({
  ...actualAppearanceItem,
  AppearanceMenuItem: () => <div data-appearance-link="" />,
}));
mock.module("@/features/settings/hooks/use-kazan-appearance", () => ({
  ...actualAppearance,
  useKazanAppearance: () => ({ layout, background: layout === "classic" ? "plain" : "grid" }),
}));
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
  mock.module("./kazan-menu", () => actualMenu);
  mock.module("./kazan-nav-items", () => actualNavItems);
  mock.module("./appearance-menu-item", () => actualAppearanceItem);
  mock.module("@/features/settings/hooks/use-kazan-appearance", () => actualAppearance);
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
  layout = "modern";
});

describe("KazanAppShell top bar", () => {
  test("links the wordmark to /home with the app name", () => {
    const header = between(markup(), "<header", "</header>");
    expect(header).toContain('href="/home"');
    expect(header).toContain('aria-label="Kazan"');
  });

  test("shows the menu toggle below 768px, where the menu is a sheet, not at Kazan's 744px md", () => {
    const html = markup();
    const at = html.indexOf('data-sidebar="trigger"');
    expect(at).toBeGreaterThan(-1);
    const toggle = html.slice(html.lastIndexOf("<button", at), html.indexOf(">", at));
    expect(toggle).toContain('data-slot="sidebar-trigger"');
    expect(toggle).toContain("min-[768px]:hidden");
    expect(toggle).not.toMatch(/[" ]md:hidden/);
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

  test("points the profile menu's Profile at the account app, and the menu has no Settings link", () => {
    const menu = markup().match(/<div data-profile-menu=""[^>]*>/)?.[0];
    expect(menu).toContain(`data-profile-href="${ACCOUNT_LINKS.profile}"`);
    expect(menu).not.toContain("settings");
  });

  test("offers the language submenu to everyone", () => {
    expect(markup()).toContain("data-language");
  });

  test("links the appearance settings from the profile menu", () => {
    expect(between(markup(), "data-profile-menu", "</header>")).toContain("data-appearance-link");
  });

  test("has no Organizations row, not even for an owner", () => {
    role = "OWNER";
    const menu = between(markup(), "data-profile-menu", "</header>");
    expect(menu).not.toContain("Organizations");
    expect(menu).not.toContain("ulak");
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
    expect(html).not.toContain("/settings/cli");
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
    expect(edge).toContain('aria-valuenow="256"');
    expect(edge).toContain('tabindex="0"');
    // The rail primitive is a button with the same data attribute; only the handle is left.
    expect(html).not.toContain('<button data-sidebar="rail"');
  });

  test("gives the panel the stored-width variable, 256 by default", () => {
    expect(between(markup(), 'data-slot="sidebar-wrapper"', ">")).toContain(
      "--sidebar-width:256px"
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

describe("KazanAppShell classic layout", () => {
  beforeEach(() => {
    layout = "classic";
  });

  test("has no top bar and draws the inset sidebar beside an inset content panel", () => {
    const html = markup();
    expect(html).not.toContain("<header");
    expect(html).toContain('data-variant="inset"');
    expect(html).toContain('data-collapsible=""');
    expect(html).toContain('data-slot="sidebar-inset"');
    expect(html.indexOf('data-variant="inset"')).toBeLessThan(
      html.indexOf('data-slot="sidebar-inset"')
    );
  });

  test("puts the organization selector and the app switcher in the sidebar header", () => {
    const header = between(markup(), 'data-sidebar="header"', 'data-sidebar="content"');
    expect(header).toContain('data-selector="KAZAN"');
    expect(header).toContain('data-switcher="kazan"');
  });

  test("lists the rows in one group: Home and Tickets first, then operations, then work, then Settings", () => {
    const content = between(markup(), 'data-sidebar="content"', 'data-sidebar="footer"');
    expect(content).toContain(en.sidebar.navigation);
    const order = ["home", "tickets", "incidents", "cyberscore", "chats", "boards"].map((id) =>
      content.indexOf(`data-row="${id}"`)
    );
    expect(order[0]).toBeGreaterThan(-1);
    expect([...order].sort((a, b) => a - b)).toEqual(order);
    const settings = content.indexOf('href="/settings/columns"');
    expect(settings).toBeGreaterThan(order[order.length - 1] ?? 0);
  });

  test("keeps the CLI page and the account menu in the sidebar foot", () => {
    const footer = footerOf(markup());
    expect(footer).toContain('href="/settings/cli"');
    expect(footer).toContain("data-profile-menu");
    expect(footer).toContain("data-appearance-link");
    expect(footer).toContain("data-language");
  });

  test("shows the sidebar toggle bar in the content area outside a board", () => {
    const inset = markup().slice(markup().indexOf('data-slot="sidebar-inset"'));
    expect(inset).toContain(`aria-label="${en.sidebar.toggleSidebar}"`);
    expect(inset.indexOf('data-slot="sidebar-trigger"')).toBeLessThan(inset.indexOf("data-gate"));
  });

  test("hides the toggle bar while the content holds a board bar, on any path", () => {
    // A /board/ path without a board bar (an unknown board, Kazan shut) keeps
    // the bar, so the hiding hangs on the board bar's marker, not the path.
    pathname = "/board/ops/all";
    const inset = markup().slice(markup().indexOf('data-slot="sidebar-inset"'));
    expect(inset.slice(0, inset.indexOf(">"))).toContain("group/classic-content");
    const at = inset.indexOf('data-slot="sidebar-trigger"');
    expect(at).toBeGreaterThan(-1);
    const row = inset.slice(inset.lastIndexOf("<div", at), at);
    expect(row).toContain("group-has-[[data-board-bar]]/classic-content:hidden");
  });

  test("keeps the page body: canvas, gate and the session effects outside it", () => {
    const html = markup();
    expect(html).toContain("kazan-canvas");
    const gate = html.indexOf("data-gate");
    expect(gate).toBeLessThan(html.indexOf("data-page"));
    for (const effect of ["session-sync", "incident-alert", "unread-title"]) {
      const at = html.indexOf(`data-effect="${effect}"`);
      expect(at).toBeGreaterThan(-1);
      expect(at).toBeLessThan(gate);
    }
  });
});
