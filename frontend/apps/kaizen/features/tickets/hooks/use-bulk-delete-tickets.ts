"use client";

import { useMutation, useQueryClient, type QueryKey } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { deleteTicket } from "../api";
import { invalidateRelatedQueries } from "@/hooks/invalidate-related-queries";
import { TICKET_KEYS } from "./query-keys";
import { TICKET_MUTATION_KEY } from "./ticket-cache-updates";

/** One ticket to delete, paired with the board that actually owns it. */
export interface BulkDeleteTicketTarget {
  boardId: string;
  ticketId: string;
}

export interface BulkDeleteTicketFailure {
  target: BulkDeleteTicketTarget;
  message: string;
}

export interface BulkDeleteTicketsResult {
  succeeded: BulkDeleteTicketTarget[];
  failed: BulkDeleteTicketFailure[];
}

function reasonMessage(reason: unknown): string {
  return reason instanceof Error ? reason.message : String(reason);
}

/**
 * Deletes several tickets at once.
 *
 * The backend exposes no bulk-delete endpoint -- kaizen's only ticket
 * `@DeleteMapping` is `/{ticketId}`, and `TicketBulkMoveRequest` explicitly
 * rejects a DELETE relocation action -- so this fans out one request per
 * ticket and settles them all.
 *
 * Partial failure is a real outcome here rather than an edge case: the
 * selection can span child boards the user may not be allowed to write to.
 * `Promise.allSettled` therefore keeps every result, the rejected ones are
 * both logged and surfaced as a toast naming how many failed (rule 10 -- no
 * silent failure path), and the caller gets the per-ticket outcome back so it
 * can leave exactly the failures selected for a retry.
 *
 * Cache handling mirrors `useDeleteTicket`: the detail query of every deleted
 * ticket is removed and then excluded from the invalidation sweep, so a still
 * mounted detail view cannot refetch a ticket that no longer exists.
 */
export function useBulkDeleteTickets(options?: {
  /** Runs after cache removal but before query invalidation. */
  onBeforeInvalidate?: () => void;
  /**
   * False turns off the toast after a fully successful delete, for a caller
   * that shows its own toast (one with an Undo action, say). Failure toasts
   * always show.
   */
  successToast?: boolean;
}) {
  const queryClient = useQueryClient();
  const t = useTranslations("kaizen");

  return useMutation({
    // Carries the ticket mutation prefix so the drag settle gate sees a bulk
    // delete in flight, exactly like the single delete does.
    mutationKey: [...TICKET_MUTATION_KEY, "bulk-delete"],
    mutationFn: async ({
      targets,
    }: {
      targets: readonly BulkDeleteTicketTarget[];
    }): Promise<BulkDeleteTicketsResult> => {
      const settled = await Promise.allSettled(
        targets.map((target) => deleteTicket(target.boardId, target.ticketId)),
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
      const detailKeys: QueryKey[] = result.succeeded.map((target) =>
        TICKET_KEYS.detail(target.boardId, target.ticketId),
      );
      detailKeys.forEach((queryKey) => {
        queryClient.removeQueries({ queryKey });
      });

      if (result.failed.length === 0) {
        if (options?.successToast !== false) {
          toast.success(t("tickets.messages.bulkDeleted", { count: result.succeeded.length }));
        }
      } else {
        console.error("[use-bulk-delete-tickets] some deletes failed", result.failed);
        const firstMessage = result.failed[0]!.message;
        if (result.succeeded.length > 0) {
          toast.error(
            t("tickets.messages.bulkDeletePartial", {
              succeeded: result.succeeded.length,
              failed: result.failed.length,
            }),
            { description: firstMessage },
          );
        } else {
          toast.error(t("tickets.messages.bulkDeleteFailed", { count: result.failed.length }), {
            description: firstMessage,
          });
        }
      }

      options?.onBeforeInvalidate?.();
      await invalidateRelatedQueries(queryClient, { excludeKeys: detailKeys });
    },
    // `mutationFn` settles every request itself, so this only fires on an
    // unexpected error (e.g. a bug in the fan-out). Surface it rather than
    // letting it disappear.
    onError: (error: Error) => {
      console.error("[use-bulk-delete-tickets] bulk delete threw", error);
      toast.error(error.message);
    },
  });
}
