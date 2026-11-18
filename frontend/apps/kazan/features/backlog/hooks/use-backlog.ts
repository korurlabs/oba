"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { getBacklog, addTicketToBacklog } from "../api";
import { invalidateRelatedQueries } from "@/hooks/invalidate-related-queries";
import { BACKLOG_KEYS } from "./query-keys";

/**
 * Query keys live in the boundary-safe `./query-keys` module (no "use client")
 * so server components can prefetch under the exact same keys without the keys
 * becoming client-reference proxies. Re-exported here for client consumers.
 */
export { BACKLOG_KEYS };

const KEYS = BACKLOG_KEYS;

export function useBacklog(boardId: string) {
  return useQuery({
    queryKey: KEYS.detail(boardId),
    queryFn: () => getBacklog(boardId),
    enabled: !!boardId,
  });
}

export function useAddTicketToBacklog() {
  const queryClient = useQueryClient();
  const t = useTranslations("kazan");

  return useMutation({
    mutationFn: ({
      boardId,
      ticketId,
    }: {
      boardId: string;
      ticketId: string;
    }) => addTicketToBacklog(boardId, ticketId),
    onSuccess: () => {
      toast.success(t("backlog.messages.ticketAdded"));
      invalidateRelatedQueries(queryClient);
    },
    onError: () => {
      toast.error(t("backlog.messages.ticketAddError"));
    },
  });
}
