import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { HydrationBoundary } from "@tanstack/react-query";
import { redirect } from "@oba/shared/libs/i18n/navigation";
import { findBoardRoute, isUuid } from "@/features/boards/lib/board-route";
import { TicketForm } from "@/features/tickets/components/ticket-form";
import { ticketRouteHydrationState } from "@/features/tickets/lib/ticket-route-prefetch";
import { ticketDetailPath } from "@/features/tickets/lib/ticket-route-params";
import { isKazanClosedOnServer } from "@/components/layout/kazan-access-server";
import { findTicketById, findTicketByKey } from "../../../../ticket/ticket-page-data";
import { withQuery, type RouteSearchParams } from "../../board-page-data";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("ticket");
  return { title: t("edit") };
}

interface TicketDetailPageProps {
  params: Promise<{ locale: string; boardSlug: string; ticketKey: string }>;
  searchParams: Promise<RouteSearchParams>;
}

/**
 * Full-page ticket detail / edit view, `/board/<board-slug>/ticket/<KEY>`.
 *
 * Reached by the dialog's expand button, a shared link (through
 * `/ticket/<KEY>`), or sub-ticket navigation from another full page. The
 * DIALOG is not a route at all — it is the `t_ticket` search param on the
 * list url (see `TicketModalHost`) — so this url always renders the full
 * page, on soft and hard navigation alike.
 *
 * The url always ends on the ticket's own board and its current key: a link
 * under another board (the ticket moved, or the link was typed), under a
 * board slug that matches no board (the board was renamed, or the slug was
 * mistyped), or with a board id or a ticket id from a link made before slugs
 * and keys redirects there with the query kept. A ticket id is looked up on
 * the board of the url, so it needs a board that resolves. An unknown or
 * inaccessible ticket renders the not-found screen.
 *
 * Hydrates the board tree too: this can be the first page of a session, so
 * nothing else has cached it yet.
 */
export default async function TicketDetailPage({ params, searchParams }: TicketDetailPageProps) {
  const { locale, boardSlug, ticketKey } = await params;
  // The no-access panel takes the place of the page, and the API would refuse
  // the lookup, so the page renders nothing (see `AppAccessGate`).
  if (await isKazanClosedOnServer()) return null;
  const boardRoute = await findBoardRoute(boardSlug);

  const ticket = isUuid(ticketKey)
    ? boardRoute
      ? await findTicketById(boardRoute.board.id, ticketKey)
      : null
    : await findTicketByKey(ticketKey);

  if (!ticket) {
    // Under a board id the layout renders no board chrome, so the not-found
    // screen is shown under the slug, where the board tabs stay on screen.
    if (boardRoute?.legacy) {
      redirect({
        href: withQuery(ticketDetailPath(boardRoute.board.slug, ticketKey), await searchParams),
        locale,
      });
    }
    notFound();
  }

  // No board for the url's value means the ticket's board slug differs too.
  if (boardRoute?.legacy || ticket.board.slug !== boardSlug || ticket.key !== ticketKey) {
    redirect({
      href: withQuery(ticketDetailPath(ticket.board.slug, ticket.key), await searchParams),
      locale,
    });
  }

  // The ticket's own board, never another board of the url: the dialog keys
  // the draft it hands to this page by the board it loaded the ticket on,
  // which is the ticket's board.
  const boardId = ticket.board.id;

  return (
    <HydrationBoundary
      state={await ticketRouteHydrationState({
        detail: { boardId, ticketId: ticket.id },
        boardTree: true,
        label: "ticket-detail-page",
      })}
    >
      <TicketForm
        variant="page"
        boardId={boardId}
        boardSlug={ticket.board.slug}
        ticketId={ticket.id}
        ticketKey={ticket.key}
      />
    </HydrationBoundary>
  );
}
