"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { ChevronRight, Plus, SquareKanban } from "lucide-react";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@oba/ui/components/collapsible";
import {
  SidebarMenuAction,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@oba/ui/sky/sidebar";
import { cn } from "@oba/ui/lib/utils";
import { CreateBoardDialog } from "@/features/boards/components/create-board-dialog";
import { useSidebarSectionOpen } from "@/hooks/use-sidebar-section-open";
import { SidebarBoardTree } from "./sidebar-board-tree";
import { SIDEBAR_ROW_CLASS, SIDEBAR_ROW_TRAILING_CLASS } from "./sidebar-item";
import { isKazanShut, useKazanAccess } from "./use-kazan-access";

const BOARDS_OPEN_STORAGE_KEY = "kazan-sidebar-boards-open";

/**
 * The "Boards" row with the board tree under it. The row and its chevron
 * open and close the tree; the row does not navigate, because a board is
 * opened from the tree. "+" creates a board, with a parent
 * to choose. The section starts open, and the reader's choice is remembered
 * in this browser.
 *
 * The tree scrolls inside a capped height, so the rows below it (Settings)
 * stay in view however many branches are open. In the collapsed rail the open
 * section shows the boards as dots and the closed one shows none; "+" and the
 * chevron are hidden there.
 *
 * Without an organization, or while Kazan is closed in it, the boards are
 * never loaded, so neither the tree nor "+" shows: a board created there would
 * be refused.
 */
export function BoardsNavItem() {
  const t = useTranslations("sidebar");
  const [open, setOpen] = useSidebarSectionOpen(BOARDS_OPEN_STORAGE_KEY);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const kazanClosed = isKazanShut(useKazanAccess());
  const label = t("boards");

  return (
    <>
      <Collapsible asChild open={open} onOpenChange={setOpen}>
        <SidebarMenuItem>
          <CollapsibleTrigger asChild>
            <SidebarMenuButton
              tooltip={label}
              className={cn(
                SIDEBAR_ROW_CLASS,
                "cursor-pointer group-has-data-[sidebar=menu-action]/menu-item:pr-14",
              )}
            >
              <SquareKanban />
              <span>{label}</span>
            </SidebarMenuButton>
          </CollapsibleTrigger>
          {!kazanClosed && (
            <SidebarMenuAction
              title={t("newBoard")}
              className={cn(SIDEBAR_ROW_TRAILING_CLASS, "right-7 cursor-pointer")}
              onClick={() => setCreateDialogOpen(true)}
            >
              <Plus />
              <span className="sr-only">{t("newBoard")}</span>
            </SidebarMenuAction>
          )}
          <CollapsibleTrigger asChild>
            <SidebarMenuAction
              aria-label={t(open ? "hideBoards" : "showBoards")}
              className={cn(SIDEBAR_ROW_TRAILING_CLASS, "cursor-pointer")}
            >
              <ChevronRight className={cn("transition-transform", open && "rotate-90")} />
            </SidebarMenuAction>
          </CollapsibleTrigger>
          <CollapsibleContent>
            {/* Pinned and unpinned boards scroll together. */}
            <div className="max-h-[50svh] overflow-x-hidden overflow-y-auto">
              {!kazanClosed && <SidebarBoardTree />}
            </div>
          </CollapsibleContent>
        </SidebarMenuItem>
      </Collapsible>

      <CreateBoardDialog
        open={createDialogOpen}
        onOpenChange={setCreateDialogOpen}
        showParentSelector
      />
    </>
  );
}
