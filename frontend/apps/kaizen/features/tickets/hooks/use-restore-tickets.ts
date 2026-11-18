"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { restoreTicket } from "../api";
import { invalidateRelatedQueries } from "@/hooks/invalidate-related-queries";
import { TICKET_MUTATION_KEY } from "./ticket-cache-updates";
import type {
  BulkDeleteTicketFailure,
  BulkDeleteTicketTarget,
} from "./use-bulk-delete-tickets";

export interface RestoreTicketsResult {
  succeeded: BulkDeleteTicketTarget[];
  failed: BulkDeleteTicketFailure[];
}

function reasonMessage(reason: unknown): string {
  return reason instanceof Error ? reason.message : String(reason);
}

/**
 * Restores tickets soft-deleted moments ago, the Undo of a delete toast. It
 * takes the `succeeded` targets of `useBulkDeleteTickets`, so each ticket is
 * restored on the board that actually owns it.
 *
 * Like the bulk delete there is no bulk endpoint: one request per ticket,
 * all settled. A failed restore leaves a ticket deleted that the user asked
 * back, so failures are logged and toasted with the first server message;
 * the caller gets the per-ticket outcome and toasts the success itself, in
 * the copy of the screen that offered the Undo.
 *
 * Every related query is invalidated afterwards, the home dashboard
 * included, so restored tickets reappear in the lists and the stale backlog.
 */
export function useRestoreTickets() {
  const queryClient = useQueryClient();

  return useMutation({
    // Carries the ticket mutation prefix so the drag settle gate waits for a
    // restore in flight, as it does for a delete.
    mutationKey: [...TICKET_MUTATION_KEY, "bulk-restore"],
    mutationFn: async ({
      targets,
    }: {
      targets: readonly BulkDeleteTicketTarget[];
    }): Promise<RestoreTicketsResult> => {
      const settled = await Promise.allSettled(
        targets.map((target) => restoreTicket(target.boardId, target.ticketId)),
      );

      const succeeded: BulkDeleteTicketTarget[] = [];
      const failed: BulkDeleteTicketFailure[] = [];

      settled.forEach((outcome, index) => {
        const target = targets[index]!;
        if (outcome.status === "fulfilled") {
          succeeded.push(target);
          return;
        }
        failed.push({ target, message: reasonMessage(outcome.reason) });
      });

      return { succeeded, failed };
    },
    onSuccess: async (result) => {
      if (result.failed.length > 0) {
        console.error("[use-restore-tickets] some restores failed", result.failed);
        toast.error(result.failed[0]!.message);
      }
      await invalidateRelatedQueries(queryClient);
    },
    // `mutationFn` settles every request itself, so this only fires on an
    // unexpected error in the fan-out. Surface it rather than lose it.
    onError: (error: Error) => {
      console.error("[use-restore-tickets] bulk restore threw", error);
      toast.error(error.message);
    },
  });
}
