"use client";

import { notFound, useSelectedLayoutSegment } from "next/navigation";
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
 * resolve either.
 */
export function UnknownBoardGate({ children }: { children: React.ReactNode }) {
  const segment = useSelectedLayoutSegment();
  if (segment !== BOARD_TICKET_SEGMENT) notFound();

  return <div className="h-full overflow-hidden">{children}</div>;
}
