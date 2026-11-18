import { useRouter } from "expo-router";
import { useCallback } from "react";

import type { IncidentCloseValues } from "@/components/ticket/incidentCloseModel";
import { askIncidentClose } from "@/components/ticket/incidentCloseStore";
import { whenTicketChoiceClosed } from "@/components/ticket/ticketChoiceStore";
import { INCIDENT_CLOSE_PATHNAME } from "@/components/ticket/ticketRoutes";
import type { TicketRef } from "@/hooks/queries/ticketDetail";

/**
 * Opens the Close incident sheet for `ticket` (absent on New ticket) and
 * resolves with the texts the user confirms, or null when the sheet closes
 * without them. The caller sends the close with the texts only then, so a
 * close that the API would refuse is never sent, and a cancel leaves the
 * ticket where it was.
 */
export function useIncidentCloseGuard(): (
  ticket?: TicketRef
) => Promise<IncidentCloseValues | null> {
  const router = useRouter();
  return useCallback(
    (ticket?: TicketRef) => {
      const answer: Promise<IncidentCloseValues | null> = askIncidentClose(ticket);
      whenTicketChoiceClosed(() => router.push({ pathname: INCIDENT_CLOSE_PATHNAME }));
      return answer;
    },
    [router]
  );
}
