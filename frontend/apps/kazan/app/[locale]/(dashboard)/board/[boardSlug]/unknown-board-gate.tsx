"use client";

import { notFound, useSelectedLayoutSegment } from "next/navigation";
import { ContentSidebarTrigger } from "@/components/layout/content-sidebar-trigger";
import { BOARD_TICKET_SEGMENT } from "@/features/boards/lib/board-route";

/**
 * What the board layout renders when its route value names no board the
 * user may see.
 *
 * Only the ticket page can still serve such a url: a ticket key identifies
 * the ticket on its own, so a link under a renamed or mistyped board slug
 * redirects to the ticket's current board (the page does that). Every other
 * page is a view of the board itself, so this renders the dashboard's
 * not-found screen for it. A layout cannot read its child segment on the
 * server, hence a client component.
 *
 * The ticket page renders the board's not-found screen when the key does not
 * resolve either. No board tabs are rendered above it, so this keeps the
 * sidebar toggle on screen, which on a phone is the only way to open the
 * sidebar.
 */
export function UnknownBoardGate({ children }: { children: React.ReactNode }) {
  const segment = useSelectedLayoutSegment();
  if (segment !== BOARD_TICKET_SEGMENT) notFound();

  return (
    <div className="flex h-full flex-col">
      <ContentSidebarTrigger className="border-border bg-background flex shrink-0 items-center gap-2 border-b px-4 py-2" />
      <div className="flex-1 overflow-hidden">{children}</div>
    </div>
  );
}
