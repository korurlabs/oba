/**
 * Not-found screen of every authenticated page.
 *
 * It renders inside the dashboard layout, so the menu stays on screen and
 * the user can navigate on. It catches `notFound()` from every page and
 * layout below this group (an unknown or inaccessible board, an unknown
 * ticket key) and from the catch-all `[...rest]` page, which takes every
 * path that matches no route. A page under a board that resolved has its own
 * not-found screen inside the board tabs (`board/[boardSlug]/not-found.tsx`).
 */

import { NotFoundView } from "@/components/layout/not-found-view";

export default function DashboardNotFound() {
  return (
    <div className="flex min-h-full flex-col">
      <NotFoundView className="flex-1" />
    </div>
  );
}
