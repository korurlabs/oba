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
import { Settings, UserPlus } from "lucide-react";
import { BoardSettingsDialog } from "@/features/boards/components/board-settings-dialog";
import { InviteGuestDialog } from "@/features/boards/components/invite-guest-dialog";
import { useCachedBoard } from "@/features/boards/hooks/use-boards";
import { DEFAULT_BOARD_COLOR } from "@/features/boards/lib/board-colors";
import {
  BOARD_FIXED_SEGMENTS,
  BOARD_TAB,
  BOARD_TAB_ORDER,
  boardPath,
  type BoardTab,
} from "@/features/boards/lib/board-route";
import { useBoardHref } from "@/hooks/use-board-preferences";
import {
  ticketFilterParams,
  useTicketFilterParams,
} from "@/features/tickets/hooks/use-ticket-filter-params";
import { useBoardRoleContext } from "@/features/boards/providers/board-role-provider";
import { BoardBarSlotTarget } from "./board-bar-slot";

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

  const tabLabels: Record<BoardTab, string> = {
    [BOARD_TAB.sprint]: t("sprint.title"),
    [BOARD_TAB.backlog]: t("backlog.title"),
    [BOARD_TAB.roadmap]: t("roadmap.title"),
    [BOARD_TAB.all]: t("all.title"),
  };

  // The roadmap reads no ticket filters, so its link carries none.
  const tabs: Tab[] = BOARD_TAB_ORDER.map((id) => {
    const path = getBoardHref({ id: boardId, slug: boardSlug }, id);
    return {
      id,
      label: tabLabels[id],
      href: id === BOARD_TAB.roadmap ? path : `${path}${tabQuery}`,
    };
  });

  const activeTab = activeTabOf(pathname, boardSlug);

  return (
    <div className="border-border bg-card border-b">
      {/*
        One bar: the board name and its tabs on the left, the view controls of
        the page and the board settings on the right. When the row is too
        narrow, the right group wraps under the tabs and the tabs scroll
        sideways.
      */}
      <div className="flex flex-wrap items-center gap-x-6 px-4 lg:px-6">
        <div className="flex min-w-0 items-center gap-6">
          <h1 className="kazan-grid-heading max-w-[180px] shrink-0">
            {isLoading ? (
              <Skeleton className="inline-block h-4 w-24" />
            ) : (
              <span className="truncate">{boardData.name}</span>
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
                  href={
                    tab.href as
                      | `/${string}/board/${string}/all`
                      | `/${string}/board/${string}/sprint`
                      | `/${string}/board/${string}/backlog`
                      | `/${string}/board/${string}/roadmap`
                  }
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
