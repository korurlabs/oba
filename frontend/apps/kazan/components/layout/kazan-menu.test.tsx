import { afterAll, afterEach, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { NextIntlClientProvider } from "next-intl";
import { cleanup, renderLive } from "@oba/shared/testing/live-render";
import { SidebarProvider, useSidebar } from "@oba/ui/sky/sidebar";
import en from "@/locales/en.json";

// The user, the chat channels, the router, the access answer and the two
// heavy lists (board tree, channel list) are stand-ins. The menu, its row
// components, the sky sidebar primitives and the messages are real. The two
// lists render a marker, so a test sees whether the menu mounts them.
type Access =
  | { status: "pending" }
  | { status: "open" }
  | { status: "noOrganization" }
  | { status: "closed"; organization: { id: string; name: string } };

interface Channel {
  id: string;
  slug: string;
  purpose: string;
}

let pathname: string;
let access: Access;
let channels: Channel[] | undefined;
let meId: string | undefined;

const actualMe = { ...(await import("@oba/shared/features/users/hooks/use-get-me")) };
const actualNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };
const actualAccess = { ...(await import("./use-kazan-access")) };
const actualChannels = { ...(await import("@/features/chat/hooks/use-chat-channels")) };
const actualOrder = { ...(await import("@/features/chat/hooks/use-chat-channel-order")) };
const actualRealtime = { ...(await import("@/features/chat/hooks/use-chat-realtime")) };
const actualRead = { ...(await import("@/features/chat/hooks/use-mark-read")) };
const actualCollapsible = { ...(await import("@oba/ui/components/collapsible")) };
const actualChannelList = { ...(await import("@/features/chat/components/chat-channel-list")) };
const actualTree = { ...(await import("./sidebar-board-tree")) };
const actualDialog = { ...(await import("@/features/boards/components/create-board-dialog")) };

mock.module("@oba/shared/features/users/hooks/use-get-me", () => ({
  ...actualMe,
  useGetMe: () => ({ data: { data: { id: meId, organizations: [] } } }),
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
// The collapsible animates through frames and computed styles the stand-in
// DOM lacks, so it passes its children through, always open.
mock.module("@oba/ui/components/collapsible", () => ({
  ...actualCollapsible,
  Collapsible: ({ children }: { children?: ReactNode }) => children,
  CollapsibleTrigger: ({ children }: { children?: ReactNode }) => children,
  CollapsibleContent: ({ children }: { children?: ReactNode }) => <div>{children}</div>,
}));
mock.module("./use-kazan-access", () => ({ ...actualAccess, useKazanAccess: () => access }));
mock.module("@/features/chat/hooks/use-chat-channels", () => ({
  ...actualChannels,
  useChatChannels: () => ({ data: channels }),
}));
mock.module("@/features/chat/hooks/use-chat-channel-order", () => ({
  ...actualOrder,
  useChatChannelOrder: () => ({ listedChannels: [] }),
}));
mock.module("@/features/chat/hooks/use-chat-realtime", () => ({
  ...actualRealtime,
  useChatChannelListRealtime: () => undefined,
}));
mock.module("@/features/chat/hooks/use-mark-read", () => ({
  ...actualRead,
  useChatUnread: () => ({ data: undefined }),
}));
mock.module("@/features/chat/components/chat-channel-list", () => ({
  ...actualChannelList,
  ChatChannelList: () => <div data-marker="channel-list" />,
}));
mock.module("./sidebar-board-tree", () => ({
  ...actualTree,
  SidebarBoardTree: () => <div data-marker="board-tree" />,
}));
mock.module("@/features/boards/components/create-board-dialog", () => ({
  ...actualDialog,
  CreateBoardDialog: () => null,
}));

afterAll(() => {
  mock.module("@oba/shared/features/users/hooks/use-get-me", () => actualMe);
  mock.module("@oba/shared/libs/i18n/navigation", () => actualNavigation);
  mock.module("@oba/ui/components/collapsible", () => actualCollapsible);
  mock.module("./use-kazan-access", () => actualAccess);
  mock.module("@/features/chat/hooks/use-chat-channels", () => actualChannels);
  mock.module("@/features/chat/hooks/use-chat-channel-order", () => actualOrder);
  mock.module("@/features/chat/hooks/use-chat-realtime", () => actualRealtime);
  mock.module("@/features/chat/hooks/use-mark-read", () => actualRead);
  mock.module("@/features/chat/components/chat-channel-list", () => actualChannelList);
  mock.module("./sidebar-board-tree", () => actualTree);
  mock.module("@/features/boards/components/create-board-dialog", () => actualDialog);
});

const { KazanMenu } = await import("./kazan-menu");

function wrap(children: ReactNode) {
  return (
    <NextIntlClientProvider locale="en" messages={en} timeZone="UTC">
      <SidebarProvider>{children}</SidebarProvider>
    </NextIntlClientProvider>
  );
}

function markup(): string {
  return renderToStaticMarkup(wrap(<KazanMenu />));
}

/** Shows whether the mobile sheet is open, so a test can read what a click did. */
function SheetProbe() {
  const { openMobile, setOpenMobile } = useSidebar();
  return (
    <button data-probe="sheet" data-open={String(openMobile)} onClick={() => setOpenMobile(true)} />
  );
}

const sidebar = en.sidebar;
const channel = (purpose: string, id = purpose): Channel => ({ id, slug: id, purpose });

beforeEach(() => {
  pathname = "/home";
  access = { status: "open" };
  channels = [];
  meId = "me-1";
});

afterEach(cleanup);

describe("KazanMenu", () => {
  test("lists the rows under one Navigation label in order", () => {
    channels = [channel("DAILY_UPDATES"), channel("PULL_REQUESTS")];
    const html = markup();
    expect(html).toContain(`>${sidebar.navigation}<`);
    const labels = [
      sidebar.home,
      sidebar.tickets,
      sidebar.incidents,
      sidebar.dailyUpdates,
      sidebar.pullRequests,
      sidebar.chats,
      sidebar.boards,
    ];
    const positions = labels.map((label) => html.indexOf(`<span>${label}</span>`));
    expect(positions.every((position) => position > -1)).toBe(true);
    expect([...positions].sort((a, b) => a - b)).toEqual(positions);
  });

  test("links Home, Incidents and Chats to their pages", () => {
    const html = markup();
    expect(html).toContain('href="/home"');
    expect(html).toContain('href="/incidents"');
    expect(html).toContain('href="/chats"');
  });

  test("links Tickets to the tickets assigned to the signed-in user", () => {
    meId = "user-42";
    expect(markup()).toContain('href="/tickets?assigneeIds=user-42"');
  });

  test("has no Download CLI row and no Settings row", () => {
    const html = markup();
    expect(html).not.toContain(sidebar.downloads);
    expect(html).not.toContain("/downloads");
    expect(html).not.toContain(`<span>${sidebar.settings}</span>`);
  });

  test("marks the row of the page in view and no other", () => {
    pathname = "/incidents";
    const html = markup();
    expect(html.match(/data-active="true"/g)).toHaveLength(1);
    const active = html.indexOf('data-active="true"');
    expect(html.slice(active, html.indexOf("</a>", active))).toContain(sidebar.incidents);
  });

  test("marks Tickets on its own page although its link carries a query", () => {
    pathname = "/tickets";
    const html = markup();
    const active = html.indexOf('data-active="true"');
    expect(html.slice(active, html.indexOf("</a>", active))).toContain(sidebar.tickets);
  });

  describe("the Updates and PR rows", () => {
    test("show only once their channel exists", () => {
      channels = [];
      let html = markup();
      expect(html).not.toContain(`<span>${sidebar.dailyUpdates}</span>`);
      expect(html).not.toContain(`<span>${sidebar.pullRequests}</span>`);

      channels = [channel("DAILY_UPDATES")];
      html = markup();
      expect(html).toContain(`<span>${sidebar.dailyUpdates}</span>`);
      expect(html).toContain('href="/daily-updates"');
      expect(html).not.toContain(`<span>${sidebar.pullRequests}</span>`);

      channels = [channel("PULL_REQUESTS")];
      html = markup();
      expect(html).toContain(`<span>${sidebar.pullRequests}</span>`);
      expect(html).toContain('href="/pull-requests"');
      expect(html).not.toContain(`<span>${sidebar.dailyUpdates}</span>`);
    });

    test("stay out while the channel list loads", () => {
      channels = undefined;
      const html = markup();
      expect(html).not.toContain(`<span>${sidebar.dailyUpdates}</span>`);
      expect(html).not.toContain(`<span>${sidebar.pullRequests}</span>`);
    });
  });

  describe("the board tree and the channel list", () => {
    test("show with their plus while Kazan is open", () => {
      const html = markup();
      expect(html).toContain('data-marker="board-tree"');
      expect(html).toContain('data-marker="channel-list"');
      expect(html).toContain(`title="${sidebar.newBoard}"`);
    });

    test("show while the access answer is pending", () => {
      access = { status: "pending" };
      const html = markup();
      expect(html).toContain('data-marker="board-tree"');
      expect(html).toContain('data-marker="channel-list"');
    });

    for (const shut of [
      { status: "noOrganization" } as const,
      { status: "closed", organization: { id: "org-2", name: "Kalkan only" } } as const,
    ]) {
      test(`are left out, with the plus, when access is ${shut.status}`, () => {
        access = shut;
        const html = markup();
        expect(html).not.toContain('data-marker="board-tree"');
        expect(html).not.toContain('data-marker="channel-list"');
        expect(html).not.toContain(`title="${sidebar.newBoard}"`);
        // The rows themselves stay, so the menu keeps its shape.
        expect(html).toContain(`<span>${sidebar.boards}</span>`);
        expect(html).toContain(`<span>${sidebar.chats}</span>`);
      });
    }
  });

  describe("a click on a row", () => {
    async function clickRow(label: string, href: string): Promise<string | null> {
      const screen = await renderLive(
        wrap(
          <>
            <SheetProbe />
            <KazanMenu />
          </>
        )
      );
      await screen.byAttribute("data-probe", "sheet")[0]?.click();
      expect(screen.byAttribute("data-probe", "sheet")[0]?.attribute("data-open")).toBe("true");
      const link = screen.byAttribute("href", href)[0];
      expect(link?.text).toContain(label);
      await link?.click();
      return screen.byAttribute("data-probe", "sheet")[0]?.attribute("data-open") ?? null;
    }

    test("on Home closes the mobile sheet", async () => {
      expect(await clickRow(sidebar.home, "/home")).toBe("false");
    });

    test("on Tickets closes the mobile sheet", async () => {
      expect(await clickRow(sidebar.tickets, "/tickets?assigneeIds=me-1")).toBe("false");
    });

    test("on Chats closes the mobile sheet", async () => {
      expect(await clickRow(sidebar.chats, "/chats")).toBe("false");
    });

    test("on a channel row closes the mobile sheet", async () => {
      channels = [channel("DAILY_UPDATES")];
      expect(await clickRow(sidebar.dailyUpdates, "/daily-updates")).toBe("false");
    });
  });
});
