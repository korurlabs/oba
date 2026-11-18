import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { HydrationBoundary } from "@tanstack/react-query";
import { TicketForm } from "@/features/tickets/components/ticket-form";
import {
  parseTicketRouteParams,
  type RouteSearchParams,
} from "@/features/tickets/lib/ticket-route-params";
import { ticketRouteHydrationState } from "@/features/tickets/lib/ticket-route-prefetch";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("ticket");
  return { title: t("create") };
}

interface CreateTicketPageProps {
  searchParams: Promise<RouteSearchParams>;
}

/**
 * Full-page ticket create view.
 *
 * The create route is deliberately NOT board-scoped: the board is form state
 * (the form has a board selector and submits to whatever the user picked),
 * not route state. It arrives as the optional `t_board` param, the board's
 * slug — absent means "no board context", which is exactly what the
 * board-less `/tickets` list needs. The sprint, backlog and parent arrive by
 * slug and key too, and the form resolves them to ids before it renders.
 *
 * Hydrates the board tree the selector reads on mount.
 */
export default async function CreateTicketPage({ searchParams }: CreateTicketPageProps) {
  const formProps = parseTicketRouteParams(await searchParams);

  return (
    <HydrationBoundary
      state={await ticketRouteHydrationState({
        boardTree: true,
        label: "ticket-create-page",
      })}
    >
      <TicketForm variant="page" {...formProps} />
    </HydrationBoundary>
  );
}
