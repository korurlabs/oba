"use client";

import { useQuery } from "@tanstack/react-query";
import { isApiError } from "@oba/shared/api/axios";
import { getTicketByKey } from "../api";
import type { AllTicketListResponse } from "../schemas";

const KEYS = {
  all: ["tickets", "by-key"] as const,
  detail: (key: string) => [...KEYS.all, key] as const,
};

export { KEYS as TICKET_BY_KEY_KEYS };

/**
 * Resolves a ticket by its human-readable key (e.g. "SEN-123").
 *
 * Backed by the dedicated `GET /kazan/tickets/by-key/{key}` endpoint,
 * which resolves ANY ticket — including sub-tickets — scoped to the
 * user's accessible boards. This replaces the old search-and-scan
 * workaround: the cross-board list endpoint is restricted to root tickets
 * (`parentTicket IS NULL`), so searching a sub-ticket's key returned
 * nothing and sub-ticket deep links never opened.
 *
 * The query is gated behind `enabled` so callers can defer fetching until
 * they actually need to resolve a deep link. Retries are disabled because
 * this is a one-shot deep-link resolve — on transient errors we want fast
 * "not found" feedback rather than ~30s of silent backoff. A 404 (no
 * accessible ticket with that key) resolves to `null` so callers treat it
 * as "not found" rather than an exceptional error.
 */
export function useTicketByKey(key: string | null | undefined, options?: { enabled?: boolean }) {
  const normalisedKey = key?.trim() ?? "";
  const enabled = (options?.enabled ?? true) && normalisedKey.length > 0;

  return useQuery({
    queryKey: KEYS.detail(normalisedKey),
    queryFn: async () => {
      try {
        const response = await getTicketByKey(normalisedKey);
        return (response?.data ?? null) as AllTicketListResponse | null;
      } catch (error) {
        // 404 = no accessible ticket with this key: "not found" (null),
        // which callers toast as such. Any other error propagates so React
        // Query's `isError` surfaces it.
        if (isApiError(error) && error.status === 404) return null;
        throw error;
      }
    },
    enabled,
    // The match is stable for the lifetime of the URL; no need to refetch
    // aggressively while the sheet is open.
    staleTime: 30_000,
    // One-shot deep-link resolve: surface failures immediately.
    retry: false,
  });
}
