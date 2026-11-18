import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import { filterEmptyValues } from "@/lib/filter-utils";
import type { ApiResponse } from "@oba/shared/schema";
import type { AllTicketListResponse } from "../schemas";

export async function listAllTickets(params?: Record<string, unknown>) {
  const filtered = filterEmptyValues(params);
  // Serialize array values as comma-separated strings for the backend
  const serialized = filtered
    ? Object.fromEntries(
        Object.entries(filtered).map(([key, value]) => [
          key,
          Array.isArray(value) ? value.join(",") : value,
        ]),
      )
    : undefined;
  const res = await api.get<ApiResponse<AllTicketListResponse[]>>(
    API_ROUTES.allTickets.list,
    { params: serialized },
  );
  return res.data;
}

/**
 * Resolves a single ticket by its exact human-readable key (e.g.
 * "ACME-45"), including SUB-tickets, scoped to the user's accessible
 * boards. Backed by `GET /kazan/tickets/by-key/{key}`, which (unlike the
 * cross-board list endpoint) is not restricted to root tickets — so deep
 * links to sub-tickets resolve. Returns 404 when no accessible ticket has
 * that key.
 */
export async function getTicketByKey(key: string) {
  const res = await api.get<ApiResponse<AllTicketListResponse>>(
    API_ROUTES.allTickets.byKey(key),
  );
  return res.data;
}
