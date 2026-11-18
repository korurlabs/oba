"use client";

import { useQueries } from "@tanstack/react-query";
import { getTicketByKey } from "@/features/all-tickets/api";
import type { AllTicketListResponse } from "@/features/all-tickets/schemas";
import { UPDATE_KEYS } from "./query-keys";
import { useDiscardQueriesOnLeave } from "./use-updates";

/** A requested ticket key that could not be resolved, with the server's reason. */
export interface UnresolvedSeedTicket {
  ticketKey: string;
  message: string | null;
}

export interface UpdateSeedTickets {
  /** True until every key has either resolved or failed. */
  isLoading: boolean;
  /** The resolved tickets, in the order of the keys. */
  tickets: AllTicketListResponse[];
  /** The keys that did not resolve: unknown, or on a board the reader cannot open. */
  unresolved: UnresolvedSeedTicket[];
}

/**
 * Resolves the tickets a new update starts with when it is opened from a
 * ticket selection. Each key is read on its own, so one unknown or
 * unreachable ticket does not keep the others off the card.
 *
 * Retries are off: a key that does not resolve is reported on the screen
 * straight away instead of after the default backoff. The tickets are
 * dropped from the cache when the screen is left, so a later selection of
 * the same ticket starts at its current progress.
 */
export function useUpdateSeedTickets(ticketKeys: readonly string[]): UpdateSeedTickets {
  useDiscardQueriesOnLeave(ticketKeys.map(UPDATE_KEYS.seedTicket));
  const results = useQueries({
    queries: ticketKeys.map((ticketKey) => ({
      queryKey: UPDATE_KEYS.seedTicket(ticketKey),
      queryFn: () => getTicketByKey(ticketKey),
      retry: false,
    })),
  });

  const tickets: AllTicketListResponse[] = [];
  const unresolved: UnresolvedSeedTicket[] = [];
  results.forEach((result, index) => {
    const ticketKey = ticketKeys[index] ?? "";
    if (result.isError) {
      unresolved.push({ ticketKey, message: result.error.message });
      return;
    }
    const ticket = result.data?.data;
    if (ticket) {
      tickets.push(ticket);
    } else if (result.isSuccess) {
      unresolved.push({ ticketKey, message: null });
    }
  });

  return {
    isLoading: results.some((result) => result.isPending),
    tickets,
    unresolved,
  };
}
