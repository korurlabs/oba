import { notFound } from "next/navigation";
import { redirect } from "@oba/shared/libs/i18n/navigation";
import { ticketDetailPath } from "@/features/tickets/lib/ticket-route-params";
import { isKazanClosedOnServer } from "@/components/layout/kazan-access-server";
import { findTicketByKey } from "../ticket-page-data";

interface TicketKeyPageProps {
  params: Promise<{ locale: string; ticketKey: string }>;
}

/**
 * The short ticket link, `/ticket/<KEY>`: copy link and every place that
 * knows only a ticket's key (chat, updates, pull requests) link here, and it
 * redirects to the ticket's page under its board. An unknown or inaccessible
 * key renders the not-found screen. The static `ticket/new` route wins over
 * this one, and a key is `PREFIX-N`, so the two never collide.
 */
export default async function TicketKeyPage({ params }: TicketKeyPageProps) {
  const { locale, ticketKey } = await params;
  // The no-access panel takes the place of the page, and the API would refuse
  // the lookup, so the page renders nothing (see `AppAccessGate`).
  if (await isKazanClosedOnServer()) return null;
  const ticket = await findTicketByKey(ticketKey);
  if (!ticket) notFound();

  redirect({ href: ticketDetailPath(ticket.board.slug, ticket.key), locale });
}
