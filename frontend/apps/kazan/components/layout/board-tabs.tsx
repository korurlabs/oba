"use client";

import { useRef, useState } from "react";
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
import { Settings, UserPlus, ChevronRight, Home } from "lucide-react";
import { BoardSettingsDialog } from "@/features/boards/components/board-settings-dialog";
import { InviteGuestDialog } from "@/features/boards/components/invite-guest-dialog";
import { useCachedBoard } from "@/features/boards/hooks/use-boards";
import { DEFAULT_BOARD_COLOR } from "@/features/boards/lib/board-colors";
import {
  BOARD_FIXED_SEGMENTS,
  BOARD_TAB,
  boardPath,
  type BoardTab,
} from "@/features/boards/lib/board-route";
import { useBoardHref } from "@/hooks/use-board-preferences";
import {
  ticketFilterParams,
  useTicketFilterParams,
} from "@/features/tickets/hooks/use-ticket-filter-params";
import { useBoardRoleContext } from "@/features/boards/providers/board-role-provider";

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

export function BoardTabs({ boardId }: BoardTabsProps) {
  const pathname = usePathname();
  // The board layout renders these tabs only for a slug URL; an id URL
  // redirects before the tabs are shown.
  const { boardSlug } = useParams<{ boardSlug: string }>();
  const t = useTranslations();
  const [showInviteDialog, setShowInviteDialog] = useState(false);
  const settingsTriggerRef = useRef<HTMLButtonElement>(null);
  const { data: board, isLoading } = useCachedBoard(boardId);
  const { canManage } = useBoardRoleContext();
  const getBoardHref = useBoardHref();
  const tabQuery = useBoardTabQuery();

  const boardData = {
    id: boardId,
    name: board?.name ?? "",
    prefix: board?.prefix ?? "",
    description: board?.description ?? "",
    color: board?.color ?? DEFAULT_BOARD_COLOR,
  };

  const tabs: Tab[] = [
    {
      id: BOARD_TAB.all,
      label: t("all.title"),
      href: `${getBoardHref({ id: boardId, slug: boardSlug }, BOARD_TAB.all)}${tabQuery}`,
    },
    {
      id: BOARD_TAB.sprint,
      label: t("sprint.title"),
      href: `${getBoardHref({ id: boardId, slug: boardSlug }, BOARD_TAB.sprint)}${tabQuery}`,
    },
    {
      id: BOARD_TAB.backlog,
      label: t("backlog.title"),
      href: `${getBoardHref({ id: boardId, slug: boardSlug }, BOARD_TAB.backlog)}${tabQuery}`,
    },
    {
      id: BOARD_TAB.roadmap,
      label: t("roadmap.title"),
      href: getBoardHref({ id: boardId, slug: boardSlug }, BOARD_TAB.roadmap),
    },
  ];

  const activeTab = activeTabOf(pathname, boardSlug);

  return (
    <div className="border-border bg-card border-b">
      {/*
        Below `md` the breadcrumb, the actions and the tabs do not fit one
        row: the two wrapper divs become `contents`, so their children are
        items of this row, and the tabs wrap to a row of their own that
        scrolls sideways. From `md` up all of them share one row.
      */}
      <div className="flex items-center justify-between px-6 max-md:flex-wrap max-md:justify-start max-md:gap-x-3 max-md:pt-2">
        {/* Left side: Breadcrumb + Tabs */}
        <div className="flex items-center gap-3 max-md:contents">
          <div className="flex items-center gap-6 max-md:contents">
            {/* Breadcrumb */}
            <nav aria-label="Breadcrumb" className="flex items-center gap-1.5 text-sm max-md:min-w-0">
              <Link
                href="/home"
                className="text-muted-foreground hover:text-primary flex items-center gap-1 transition-colors"
              >
                <Home className="h-3.5 w-3.5" />
              </Link>
              <ChevronRight className="h-3.5 w-3.5" />
              <span className="text-foreground max-w-[150px] truncate font-medium">
                {isLoading ? <Skeleton className="inline-block h-4 w-24" /> : boardData.name}
              </span>
            </nav>

            {/* Divider */}
            <div className="bg-border h-5 w-px max-md:hidden" />

            {/*
              Tabs. Below `md` this row scrolls, and its clip would cut the
              focus ring, which sits 4px outside a tab: `p-1` gives the ring
              room inside the clip and `-m-1` takes that room back out of the
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
                    href={
                      tab.href as
                        | `/${string}/board/${string}/all`
                        | `/${string}/board/${string}/sprint`
                        | `/${string}/board/${string}/backlog`
                        | `/${string}/board/${string}/roadmap`
                    }
                    className={cn(
                      "relative py-3 text-sm font-medium transition-colors max-md:shrink-0 max-md:whitespace-nowrap",
                      "focus-visible:ring-primary focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:outline-none",
                      isActive ? "text-foreground" : "text-muted-foreground hover:text-foreground"
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
        </div>

        {/* Right side: Actions */}
        <div className="flex items-center gap-1.5 max-md:ml-auto">
          {/* Board actions menu -- gear opens settings + invite guest */}
          {canManage && (
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
                  <DropdownMenuItem onClick={() => setShowInviteDialog(true)}>
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
              <BoardSettingsDialog
                boardId={boardId}
                board={{
                  id: boardId,
                  slug: boardSlug,
                  name: boardData.name,
                  prefix: boardData.prefix,
                  description: boardData.description,
                  color: boardData.color,
                }}
                canManage={canManage}
              >
                <button
                  type="button"
                  ref={settingsTriggerRef}
                  className="sr-only"
                  tabIndex={-1}
                  aria-hidden="true"
                />
              </BoardSettingsDialog>
            </>
          )}
        </div>
      </div>

      {/* Invite Guest Dialog */}
      <InviteGuestDialog
        boardId={boardId}
        open={showInviteDialog}
        onOpenChange={setShowInviteDialog}
      />
    </div>
  );
}
