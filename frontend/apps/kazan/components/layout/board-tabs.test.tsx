import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { NextIntlClientProvider } from "next-intl";
import en from "@/locales/en.json";

// The board data, the role, the router and the dialogs are stand-ins; the
// bar, the tab order and the messages are real.
let layout: "modern" | "classic";
let canManage: boolean;

const actualNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };
const actualNextNavigation = { ...(await import("next/navigation")) };
const actualBoards = { ...(await import("@/features/boards/hooks/use-boards")) };
const actualRole = { ...(await import("@/features/boards/providers/board-role-provider")) };
const actualPreferences = { ...(await import("@/hooks/use-board-preferences")) };
const actualFilters = { ...(await import("@/features/tickets/hooks/use-ticket-filter-params")) };
const actualAppearance = {
  ...(await import("@/features/settings/hooks/use-kazan-appearance")),
};
const actualSettings = { ...(await import("@/features/boards/components/board-settings-dialog")) };
const actualInvite = { ...(await import("@/features/boards/components/invite-guest-dialog")) };
const actualTrigger = { ...(await import("./classic/content-sidebar-trigger")) };

mock.module("@oba/shared/libs/i18n/navigation", () => ({
  ...actualNavigation,
  usePathname: () => "/board/ops/sprint",
  Link: ({ href, children, ...rest }: { href: string; children?: ReactNode }) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));
mock.module("next/navigation", () => ({
  ...actualNextNavigation,
  useParams: () => ({ boardSlug: "ops" }),
}));
mock.module("@/features/boards/hooks/use-boards", () => ({
  ...actualBoards,
  useCachedBoard: () => ({ data: { name: "Operations", prefix: "OPS" }, isLoading: false }),
}));
mock.module("@/features/boards/providers/board-role-provider", () => ({
  ...actualRole,
  useBoardRoleContext: () => ({ canManage }),
}));
mock.module("@/hooks/use-board-preferences", () => ({
  ...actualPreferences,
  useBoardHref: () => (board: { slug: string }, tab: string) => `/board/${board.slug}/${tab}`,
}));
mock.module("@/features/tickets/hooks/use-ticket-filter-params", () => ({
  ...actualFilters,
  useTicketFilterParams: () => [{}],
}));
mock.module("@/features/settings/hooks/use-kazan-appearance", () => ({
  ...actualAppearance,
  useKazanAppearance: () => ({ layout, background: "grid" }),
}));
mock.module("@/features/boards/components/board-settings-dialog", () => ({
  ...actualSettings,
  BoardSettingsDialog: () => <div data-settings-dialog="" />,
}));
mock.module("@/features/boards/components/invite-guest-dialog", () => ({
  ...actualInvite,
  InviteGuestDialog: () => null,
}));
mock.module("./classic/content-sidebar-trigger", () => ({
  ...actualTrigger,
  ContentSidebarTrigger: () => <div data-content-trigger="" />,
}));

afterAll(() => {
  mock.module("@oba/shared/libs/i18n/navigation", () => actualNavigation);
  mock.module("next/navigation", () => actualNextNavigation);
  mock.module("@/features/boards/hooks/use-boards", () => actualBoards);
  mock.module("@/features/boards/providers/board-role-provider", () => actualRole);
  mock.module("@/hooks/use-board-preferences", () => actualPreferences);
  mock.module("@/features/tickets/hooks/use-ticket-filter-params", () => actualFilters);
  mock.module("@/features/settings/hooks/use-kazan-appearance", () => actualAppearance);
  mock.module("@/features/boards/components/board-settings-dialog", () => actualSettings);
  mock.module("@/features/boards/components/invite-guest-dialog", () => actualInvite);
  mock.module("./classic/content-sidebar-trigger", () => actualTrigger);
});

const { BoardTabs } = await import("./board-tabs");
const { BoardBarSlotProvider } = await import("./board-bar-slot");

function markup(): string {
  return renderToStaticMarkup(
    <NextIntlClientProvider locale="en" messages={en} timeZone="UTC">
      <BoardBarSlotProvider>
        <BoardTabs boardId="board-1" />
      </BoardBarSlotProvider>
    </NextIntlClientProvider>
  );
}

/** The tab hrefs in the order the bar lists them. */
function tabOrder(html: string): string[] {
  const nav = html.slice(html.indexOf('aria-label="Board navigation tabs"'));
  return [...nav.matchAll(/href="\/board\/ops\/(\w+)/g)].map((match) => match[1] ?? "");
}

beforeEach(() => {
  layout = "modern";
  canManage = false;
});

describe("BoardTabs modern", () => {
  test("lists Sprint first and All last", () => {
    expect(tabOrder(markup())).toEqual(["sprint", "backlog", "roadmap", "all"]);
  });

  test("names the board as a grid heading, with no breadcrumb or sidebar toggle", () => {
    const html = markup();
    expect(html).toContain("kazan-grid-heading");
    expect(html).not.toContain('aria-label="Breadcrumb"');
    expect(html).not.toContain("data-content-trigger");
  });
});

describe("BoardTabs classic", () => {
  beforeEach(() => {
    layout = "classic";
  });

  test("lists All first: All, Sprint, Backlog, Roadmap", () => {
    expect(tabOrder(markup())).toEqual(["all", "sprint", "backlog", "roadmap"]);
  });

  test("leads with the sidebar toggle and a breadcrumb to home and the board", () => {
    const html = markup();
    const trigger = html.indexOf("data-content-trigger");
    const crumb = html.indexOf('aria-label="Breadcrumb"');
    expect(trigger).toBeGreaterThan(-1);
    expect(trigger).toBeLessThan(crumb);
    expect(html.slice(crumb)).toContain('href="/home"');
    expect(html).toContain("Operations");
    expect(html).not.toContain("kazan-grid-heading");
  });

  test("marks itself as the board bar, on the card surface", () => {
    const html = markup();
    const root = html.slice(0, html.indexOf(">"));
    expect(root).toContain('data-board-bar=""');
    expect(root).toContain("bg-card");
  });

  test("marks the tab in view", () => {
    expect(markup()).toMatch(/href="\/board\/ops\/sprint[^"]*"[^>]*aria-current="page"/);
  });

  test("shows the board settings to a manager only", () => {
    expect(markup()).not.toContain(`aria-label="${en.common.settings}"`);
    canManage = true;
    expect(markup()).toContain(`aria-label="${en.common.settings}"`);
  });
});
