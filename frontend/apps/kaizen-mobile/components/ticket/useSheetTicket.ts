import { useLocalSearchParams } from "expo-router";

import { normalizeTicketKey } from "@/components/ticket/ticketModel";
import { useTicketDetail, useTicketRef } from "@/hooks/queries/ticketDetail";

/**
 * The ticket of a sheet opened over the ticket screen, read from the
 * `ticketKey` route parameter. Both reads are already cached by the screen
 * beneath, so the sheet opens with its data.
 */
export function useSheetTicket() {
  const params = useLocalSearchParams<Record<string, string | string[]>>();
  const ticketKey: string | null = normalizeTicketKey(params.ticketKey);
  const ticketRef = useTicketRef(ticketKey);
  const detail = useTicketDetail(ticketRef.data);
  return {
    ticketKey,
    ticketRef: ticketRef.data,
    ticket: detail.data,
    error: ticketRef.error ?? detail.error,
    retry: () => (ticketRef.isError ? ticketRef.refetch() : detail.refetch()),
  };
}
