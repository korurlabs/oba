"use client";

import { useQuery } from "@tanstack/react-query";
import { listAllTickets } from "../api";

const KEYS = {
  all: ["tickets", "all-tickets"] as const,
  list: (params: Record<string, unknown>) => [...KEYS.all, "list", params] as const,
};

export { KEYS as ALL_TICKET_KEYS };

export function useAllTickets(params?: Record<string, unknown>) {
  return useQuery({
    queryKey: KEYS.list((params ?? {}) as Record<string, unknown>),
    queryFn: () => listAllTickets(params),
    enabled: !!params,
  });
}
