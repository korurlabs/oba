"use client";

import { useQuery } from "@tanstack/react-query";
import { useKazanOpen } from "@/components/layout/use-kazan-access";
import { listAllTickets } from "@/features/all-tickets/api";
import { ALL_TICKET_KEYS } from "@/features/all-tickets/hooks/use-all-tickets";
import { OPEN_STATUSES } from "@/features/all-tickets/hooks/use-all-tickets-page";
import type { AllTicketListResponse } from "@/features/all-tickets/schemas";
import { TicketPriority, TicketType } from "@/types/enums";

/** How often the open urgent incidents are fetched while the tab is visible. */
export const URGENT_INCIDENTS_POLL_MS = 30_000;

/**
 * Open P1 and P2 incidents of every board the user is a member of, newest
 * first. The endpoint is member-scoped, so a non-member never receives an
 * incident of a board they cannot see.
 */
const URGENT_PARAMS = {
  types: [TicketType.INCIDENT],
  priorities: [TicketPriority.P1, TicketPriority.P2],
  statuses: OPEN_STATUSES,
  sortBy: "createdAt",
  sortDirection: "desc",
};

/**
 * The key sits under `ALL_TICKET_KEYS.all`, so a ticket mutation that
 * invalidates the cross-board list (closing or downgrading an incident)
 * refreshes the alert at once instead of on the next poll.
 */
const KEYS = {
  urgent: [...ALL_TICKET_KEYS.all, "urgent"] as const,
};

export { KEYS as URGENT_INCIDENT_KEYS };

/**
 * Polled on every dashboard page, so it waits until Kazan is open in the
 * selected organization: behind the no-access panel the API would refuse
 * every poll.
 */
export function useUrgentIncidents() {
  const kazanOpen = useKazanOpen();
  return useQuery({
    queryKey: KEYS.urgent,
    queryFn: async (): Promise<AllTicketListResponse[]> => {
      const response = await listAllTickets(URGENT_PARAMS);
      return (response.data ?? []) as AllTicketListResponse[];
    },
    enabled: kazanOpen,
    refetchInterval: URGENT_INCIDENTS_POLL_MS,
    refetchIntervalInBackground: false,
  });
}
