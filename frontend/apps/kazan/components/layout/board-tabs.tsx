"use client";

import { useRef, useState, type ReactNode } from "react";
import { useParams } from "next/navigation";
import { createSerializer } from "nuqs";
import { Link, usePathname } from "@oba/shared/libs/i18n/navigation";
import { cn } from "@oba/ui/lib/utils";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { ChevronRight, Home, Settings, UserPlus } from "lucide-react";
import { BoardSettingsDialog } from "@/features/boards/components/board-settings-dialog";
import { InviteGuestDialog } from "@/features/boards/components/invite-guest-dialog";
import { useCachedBoard } from "@/features/boards/hooks/use-boards";
import { DEFAULT_BOARD_COLOR } from "@/features/boards/lib/board-colors";
import {
  BOARD_FIXED_SEGMENTS,
  BOARD_TAB,
  boardPath,
  boardTabOrder,
  type BoardTab,
} from "@/features/boards/lib/board-route";
import { useBoardHref } from "@/hooks/use-board-preferences";
import {
  ticketFilterParams,
  useTicketFilterParams,
} from "@/features/tickets/hooks/use-ticket-filter-params";
import { useBoardRoleContext } from "@/features/boards/providers/board-role-provider";
import { useKazanAppearance } from "@/features/settings/hooks/use-kazan-appearance";
import { BoardBarSlotTarget } from "./board-bar-slot";
import { ContentSidebarTrigger } from "./classic/content-sidebar-trigger";

interface BoardTabsProps {
  boardId: string;
}

interface Tab {
  id: BoardTab;
  label: string;
  href: string;
}

/**
 * The tab a board path belongs to. A sprint page sits at
 * `/board/<slug>/<sprint-slug>`, so any segment that is not a fixed one is a
 * sprint and belongs to the Sprint tab. The ticket page belongs to no tab.
 */
function activeTabOf(pathname: string, boardSlug: string): BoardTab | null {
  const boardPrefix = `${boardPath(boardSlug)}/`;
  if (!pathname.startsWith(boardPrefix)) return null;
  const segment = pathname.slice(boardPrefix.length).split("/")[0];
  if (!segment) return null;
  const tab = Object.values(BOARD_TAB).find((value) => value === segment);
  if (tab) return tab;
  return BOARD_FIXED_SEGMENTS.has(segment) ? null : BOARD_TAB.sprint;
}

const serializeTicketFilterParams = createSerializer(ticketFilterParams);

/**
 * The ticket filter parameters of the current URL, as a query string ("" when
 * there are none). The Sprint, Backlog and All tabs of a board share one saved
 * filter set, so the parameters that differ from it on one tab mean the same
 * on the others and travel with the tab links. Empty parameters are kept: they
 * clear a saved filter.
 */
function useBoardTabQuery(): string {
  const [filters] = useTicketFilterParams();
  return serializeTicketFilterParams(filters);
}

/** The board as the bar and the settings dialog read it. */
interface BoardBarData {
  id: string;
  slug: string;
  name: string;
  prefix: string;
  description: string;
  color: string;
}

interface BoardBarProps {
  board: BoardBarData;
  isLoading: boolean;
  tabs: Tab[];
  activeTab: BoardTab | null;
  /** The gear menu, shown to a board manager only. */
  actions: ReactNode;
}

/** The tab links share one type: every board tab path. */
type BoardTabHref =
  | `/${string}/board/${string}/all`
  | `/${string}/board/${string}/sprint`
  | `/${string}/board/${string}/backlog`
  | `/${string}/board/${string}/roadmap`;

/**
 * The board bar: the board name, its tabs, the view controls of the page and
 * the board settings. The tab order, the default tab and the look follow the
 * user's layout; the data, the role and the dialogs are the same in both.
 */
export function BoardTabs({ boardId }: BoardTabsProps) {
  const pathname = usePathname();
  // The board layout renders these tabs only for a slug URL; an id URL
  // redirects before the tabs are shown.
  const { boardSlug } = useParams<{ boardSlug: string }>();
  const t = useTranslations();
  const [showInviteDialog, setShowInviteDialog] = useState(false);
  const { data: board, isLoading } = useCachedBoard(boardId);
  const { canManage } = useBoardRoleContext();
  const getBoardHref = useBoardHref();
  const tabQuery = useBoardTabQuery();
  const { layout } = useKazanAppearance();

  const boardData: BoardBarData = {
    id: boardId,
    slug: boardSlug,
    name: board?.name ?? "",
    prefix: board?.prefix ?? "",
    description: board?.description ?? "",
    color: board?.color ?? DEFAULT_BOARD_COLOR,
  };

  const tabLabels: Record<BoardTab, string> = {
    [BOARD_TAB.sprint]: t("sprint.title"),
    [BOARD_TAB.backlog]: t("backlog.title"),
    [BOARD_TAB.roadmap]: t("roadmap.title"),
    [BOARD_TAB.all]: t("all.title"),
  };

  // The roadmap reads no ticket filters, so its link carries none.
  const tabs: Tab[] = boardTabOrder(layout).map((id) => {
    const path = getBoardHref({ id: boardId, slug: boardSlug }, id);
    return {
      id,
      label: tabLabels[id],
      href: id === BOARD_TAB.roadmap ? path : `${path}${tabQuery}`,
    };
  });

  const barProps: BoardBarProps = {
    board: boardData,
    isLoading,
    tabs,
    activeTab: activeTabOf(pathname, boardSlug),
    actions: canManage ? (
      <BoardActions board={boardData} onInvite={() => setShowInviteDialog(true)} />
    ) : null,
  };

  return (
    <>
      {layout === "classic" ? <ClassicBoardBar {...barProps} /> : <ModernBoardBar {...barProps} />}

      {/* Invite Guest Dialog */}
      <InviteGuestDialog
        boardId={boardId}
        open={showInviteDialog}
        onOpenChange={setShowInviteDialog}
      />
    </>
  );
}

/** The gear menu of a board manager: board settings and inviting a guest. */
function BoardActions({ board, onInvite }: { board: BoardBarData; onInvite: () => void }) {
  const t = useTranslations();
  const settingsTriggerRef = useRef<HTMLButtonElement>(null);

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button
            variant="ghost"
            size="icon"
            aria-label={t("common.settings")}
            className="text-muted-foreground hover:text-foreground h-8 w-8"
          >
            <Settings className="h-4 w-4" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="w-48">
          <DropdownMenuItem onClick={() => settingsTriggerRef.current?.click()}>
            <Settings className="h-3.5 w-3.5" />
            {t("board.settings.title")}
          </DropdownMenuItem>
          <DropdownMenuItem onClick={onInvite}>
            <UserPlus className="h-3.5 w-3.5" />
            {t("guest.invite")}
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>

      {/*
        Hidden settings trigger -- BoardSettingsDialog manages its own
        open state. The trigger is programmatic only and carries no
        label, so it is hidden from assistive tech (aria-hidden) rather
        than announced as a bare nameless button.
      */}
      <BoardSettingsDialog boardId={board.id} board={board} canManage>
        <button
          type="button"
          ref={settingsTriggerRef}
          className="sr-only"
          tabIndex={-1}
          aria-hidden="true"
        />
      </BoardSettingsDialog>
    </>
  );
}

/**
 * The modern bar: one row with the board name as a grid heading and its tabs
 * on the left, the view controls of the page and the board settings on the
 * right. When the row is too narrow, the right group wraps under the tabs
 * and the tabs scroll sideways.
 */
function ModernBoardBar({ board, isLoading, tabs, activeTab, actions }: BoardBarProps) {
  return (
    <div className="border-border bg-card border-b">
      <div className="flex flex-wrap items-center gap-x-6 px-4 lg:px-6">
        <div className="flex min-w-0 items-center gap-6">
          <h1 className="kazan-grid-heading max-w-[180px] shrink-0">
            {isLoading ? (
              <Skeleton className="inline-block h-4 w-24" />
            ) : (
              <span className="truncate">{board.name}</span>
            )}
          </h1>

          {/*
            The tab row scrolls when it does not fit, and that clip would cut
            the focus ring, which sits 4px outside a tab: `p-1` gives the ring
            room inside the clip and `-m-1` takes that room back out of the
            layout.
          */}
          <nav
            className="-m-1 flex min-w-0 gap-5 overflow-x-auto p-1"
            aria-label="Board navigation tabs"
          >
            {tabs.map((tab) => {
              const isActive = tab.id === activeTab;

              return (
                <Link
                  key={tab.id}
                  href={tab.href as BoardTabHref}
                  className={cn(
                    "relative shrink-0 py-3 font-mono text-[11px] font-semibold tracking-[0.14em] whitespace-nowrap uppercase transition-colors",
                    "focus-visible:ring-primary focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:outline-none",
                    isActive
                      ? "text-korur-blue-strong"
                      : "text-muted-foreground hover:text-foreground"
                  )}
                  aria-current={isActive ? "page" : undefined}
                >
                  {tab.label}

                  {/* Active indicator */}
                  {isActive && (
                    <span
                      className="bg-primary absolute right-0 bottom-0 left-0 h-0.5"
                      aria-hidden="true"
                    />
                  )}
                </Link>
              );
            })}
          </nav>
        </div>

        {/* Right side: the page's view controls, then the board actions */}
        <div className="ml-auto flex items-center gap-2 py-1.5">
          <BoardBarSlotTarget className="flex flex-wrap items-center justify-end gap-2" />
          {actions}
        </div>
      </div>
    </div>
  );
}

/**
 * The classic bar, as Kazan had it before the grid redesign: the sidebar
 * toggle, a breadcrumb (home, then the board name), a divider and the tabs
 * in sentence case on the left, the board settings on the right. The page's
 * view controls take a row of their own below it, where each page had its
 * toolbar; the row is hidden while a page has no controls.
 */
function ClassicBoardBar({ board, isLoading, tabs, activeTab, actions }: BoardBarProps) {
  return (
    <div className="border-border bg-card border-b" data-board-bar="">
      {/*
        Below `md` the breadcrumb, the actions and the tabs do not fit one
        row: the two wrapper divs become `contents`, so their children are
        items of this row, and the tabs wrap to a row of their own that
        scrolls sideways. From `md` up all of them share one row.
      */}
      <div className="flex items-center justify-between px-6 max-md:flex-wrap max-md:justify-start max-md:gap-x-3 max-md:pt-2">
        <div className="flex items-center gap-3 max-md:contents">
          {/* The sidebar toggle stays reachable while the sidebar is collapsed. */}
          <ContentSidebarTrigger className="-ml-2 flex items-center gap-2" />

          <div className="flex items-center gap-6 max-md:contents">
            <nav
              aria-label="Breadcrumb"
              className="flex items-center gap-1.5 text-sm max-md:min-w-0"
            >
              <Link
                href="/home"
                className="text-muted-foreground hover:text-primary flex items-center gap-1 transition-colors"
              >
                <Home className="h-3.5 w-3.5" />
              </Link>
              <ChevronRight className="h-3.5 w-3.5" />
              <h1 className="text-foreground max-w-[150px] truncate text-sm font-medium">
                {isLoading ? <Skeleton className="inline-block h-4 w-24" /> : board.name}
              </h1>
            </nav>

            <div className="bg-border h-5 w-px max-md:hidden" />

            {/*
              Below `md` this row scrolls, and its clip would cut the focus
              ring, which sits 4px outside a tab: `p-1` gives the ring room
              inside the clip and `-m-1` takes that room back out of the
              layout.
            */}
            <nav
              className="flex gap-6 max-md:order-last max-md:-m-1 max-md:basis-full max-md:overflow-x-auto max-md:p-1"
              aria-label="Board navigation tabs"
            >
              {tabs.map((tab) => {
                const isActive = tab.id === activeTab;

                return (
                  <Link
                    key={tab.id}
                    href={tab.href as BoardTabHref}
                    className={cn(
                      "relative py-3 text-sm font-medium transition-colors max-md:shrink-0 max-md:whitespace-nowrap",
                      "focus-visible:ring-primary focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:outline-none",
                      isActive ? "text-foreground" : "text-muted-foreground hover:text-foreground"
                    )}
                    aria-current={isActive ? "page" : undefined}
                  >
                    {tab.label}
                    {isActive && (
                      <span
                        className="bg-primary absolute right-0 bottom-0 left-0 h-0.5"
                        aria-hidden="true"
                      />
                    )}
                  </Link>
                );
              })}
            </nav>
          </div>
        </div>

        <div className="flex items-center gap-1.5 max-md:ml-auto">{actions}</div>
      </div>

      {/*
        The page's view controls: a toolbar row of their own, right-aligned
        as each page's toolbar had them. The sprint selector and the
        days-left chip that sat on its left now lead the section strip.
      */}
      <BoardBarSlotTarget className="border-border bg-card flex flex-wrap items-center justify-end gap-2 border-t px-4 py-2 empty:hidden lg:px-6" />
    </div>
  );
}
