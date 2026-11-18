"use client";

/**
 * Not-found screen of every authenticated page.
 *
 * It renders inside the dashboard layout, so the sidebar stays on screen and
 * the user can navigate on. It catches `notFound()` from every page and
 * layout below this group (an unknown or inaccessible board, an unknown
 * ticket key) and from the catch-all `[...rest]` page, which takes every
 * path that matches no route. A page under a board that resolved has its own
 * not-found screen inside the board tabs (`board/[boardSlug]/not-found.tsx`).
 *
 * Under `/board/` the layout leaves the sidebar toggle to the board tabs row,
 * which is not rendered when this screen shows there (an unknown board, or a
 * path deeper than any board page), so it renders the toggle itself. On a
 * phone the sidebar is a closed sheet and the toggle is the only way to open
 * it.
 */

import { usePathname } from "@oba/shared/libs/i18n/navigation";
import { ContentSidebarTrigger } from "@/components/layout/content-sidebar-trigger";
import { NotFoundView } from "@/components/layout/not-found-view";

export default function DashboardNotFound() {
  const pathname = usePathname();
  const layoutHidesSidebarTrigger = pathname.startsWith("/board/");

  return (
    <div className="flex min-h-full flex-col">
      {layoutHidesSidebarTrigger && (
        <ContentSidebarTrigger className="border-border bg-background flex shrink-0 items-center gap-2 border-b px-4 py-2" />
      )}
      <NotFoundView className="flex-1" />
    </div>
  );
}
