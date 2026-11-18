import { useQuery } from "@tanstack/react-query";
import { useIsFocused } from "expo-router";

import { incidentListParams, type IncidentScope } from "@/components/incidents/incidentModel";
import { keepPreviousWithin } from "@/hooks/queries/placeholder";
import { ticketKeys } from "@/hooks/queries/tickets";
import { api, readData } from "@/lib/api";
import { API_ROUTES } from "@/lib/apiRoutes";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import { ticketListSchema, type TicketListItem } from "@/lib/schemas/tickets";

/**
 * Query keys of the Incidents tab. They sit under `ticketKeys.all`, so every
 * ticket write (a new incident, a status or priority change, a delete) marks
 * the incident lists stale with the other ticket views.
 */
export const incidentKeys = {
  all: (orgId: string) => [...ticketKeys.all(orgId), "incidents"] as const,
  list: (orgId: string, scope: IncidentScope, boardId: string | null) =>
    [...incidentKeys.all(orgId), scope, boardId ?? "all"] as const,
};

/** How often the incident lists reread while the Incidents tab is on screen. */
export const INCIDENTS_REFETCH_MS = 60_000;

/**
 * The incidents of one scope across the boards the user belongs to, or of
 * one board. Root tickets only and unpaged, as `GET /kazan/tickets` returns
 * them. The list rereads every minute while the tab is focused, so a new
 * incident appears without a pull. `enabled` false keeps a segment that is
 * not on screen from loading.
 */
export function useIncidents(
  scope: IncidentScope,
  boardId: string | null = null,
  enabled: boolean = true
) {
  const orgId: string | null = useActiveOrganizationId();
  const focused: boolean = useIsFocused();
  return useQuery<TicketListItem[]>({
    queryKey: incidentKeys.list(orgId ?? "", scope, boardId),
    queryFn: async () => {
      const response = await api.get(API_ROUTES.tickets.all, {
        params: incidentListParams(scope, boardId),
      });
      return readData(ticketListSchema, response.data);
    },
    enabled: !!orgId && enabled,
    refetchInterval: focused && enabled ? INCIDENTS_REFETCH_MS : false,
    placeholderData: keepPreviousWithin(incidentKeys.all(orgId ?? "")),
  });
}
