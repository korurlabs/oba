"use client";

import type { MouseEvent, ReactNode } from "react";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { useTicketNavigation } from "../hooks/use-ticket-navigation";
import { ticketKeyPath } from "../lib/ticket-route-params";

interface TicketKeyLinkProps {
  /** The ticket's key; the link points at its full page, `/ticket/<KEY>`. */
  ticketKey: string;
  className?: string;
  children: ReactNode;
}

/**
 * A link to a ticket that opens it in the ticket dialog over the current
 * page, so a reader in a chat channel, a thread or a feed keeps their place
 * and their scroll position, and Close (or Back) returns to the same view.
 *
 * Only an unmodified primary click opens the dialog. The `href` stays the
 * ticket's full page, so a modified or middle click, "open in new tab" and
 * "copy link" still reach the page the browser expects.
 */
export function TicketKeyLink({ ticketKey, className, children }: TicketKeyLinkProps) {
  const { openTicket } = useTicketNavigation();

  const onClick = (event: MouseEvent<HTMLAnchorElement>) => {
    if (
      event.defaultPrevented ||
      event.button !== 0 ||
      event.metaKey ||
      event.ctrlKey ||
      event.shiftKey ||
      event.altKey
    ) {
      return;
    }
    event.preventDefault();
    openTicket(ticketKey);
  };

  return (
    <Link href={ticketKeyPath(ticketKey)} className={className} onClick={onClick}>
      {children}
    </Link>
  );
}
