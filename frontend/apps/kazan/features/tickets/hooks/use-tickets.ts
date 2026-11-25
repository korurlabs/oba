"use client";

import {
  useMutation,
  useQuery,
  useQueryClient,
  type QueryClient,
  type QueryKey,
} from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import {
  listTickets,
  getTicketById,
  createTicket,
  updateTicket,
  deleteTicket,
  moveTicket,
  updateTicketOrder,
  bulkMoveTickets,
} from "../api";
import type {
  TicketCreateRequest,
  TicketUpdateRequest,
  TicketMoveRequest,
  TicketOrderUpdateRequest,
  TicketBulkMoveRequest,
} from "../schemas";
import type { TicketFilterParams } from "./use-ticket-filter-params";
import { agentRefusalMessageKey } from "@/lib/agent-member";
import { isMemberWithoutKazanAccessError } from "@/lib/kazan-member-access";
import { isApiError } from "@oba/shared/api/axios";
import { isMissionRankRejected } from "../lib/ticket-errors";
import { invalidateRelatedQueries } from "@/hooks/invalidate-related-queries";
import { TICKET_KEYS } from "./query-keys";
import {
  TICKET_MUTATION_KEY,
  applyOptimisticTicketMove,
  applyOptimisticTicketReorder,
  applyOptimisticTicketUpdate,
  cancelTicketCaches,
  restoreTicketCaches,
  settleTicketAction,
  settleTicketMutation,
  type TicketCacheSnapshot,
} from "./ticket-cache-updates";

type TicketQueryParams =
  | (Partial<TicketFilterParams> & { sprintId?: string | null; backlogId?: string | null })
  | Record<string, unknown>;

/**
 * Query keys live in the boundary-safe `./query-keys` module (no "use client")
 * so server components can prefetch under the exact same keys without the keys
 * becoming client-reference proxies. Re-exported here for client consumers.
 */
export { TICKET_KEYS };

const KEYS = TICKET_KEYS;

/**
 * Fires the post-mutation resync without returning it.
 *
 * Returning the promise from a mutation callback keeps the mutation `pending`
 * for the entire refetch, which lengthens the drag block on boards that watch
 * `isPending`, delays every `await mutateAsync`, and holds the settle gate open
 * against the mutations that follow.
 *
 * A rejection here is not a request failure -- `invalidateQueries` records
 * refetch errors on the queries themselves, where the views that read them
 * surface them -- so it can only be a programming error, and is logged rather
 * than dropped (rule 10: no silent failure path).
 */
function resyncTickets(
  queryClient: QueryClient,
  options?: { excludeKeys?: readonly QueryKey[] }
): void {
  settleTicketMutation(queryClient, options)?.catch((error: unknown) => {
    console.error("[use-tickets] post-mutation resync failed", error);
  });
}

/**
 * Set by an action that sends several ticket requests for one user step, such
 * as the ticket form's save. The mutation then shows no success toast and
 * skips its own resync, and the action shows one toast and calls
 * `resyncTicketAction` once when it ends. The error toast still fires for
 * every failed request, batched or not, unless `silentError` is set.
 */
export interface TicketBatchOption {
  batched?: boolean;
  /**
   * Set by an action that reports this request's failure in its own single
   * result toast, such as a create whose follow-up step failed after the
   * ticket exists. Only the error toast is skipped: the rollback still runs,
   * and the rejection still reaches the caller, which must surface it.
   */
  silentError?: boolean;
}

/**
 * The single resync of an action whose ticket mutations ran with `batched`.
 * Call it once the action's last request has settled, whether it succeeded or
 * failed, so the optimistic patches are replaced by the server state.
 */
export function resyncTicketAction(queryClient: QueryClient): void {
  settleTicketAction(queryClient)?.catch((error: unknown) => {
    console.error("[use-tickets] post-action resync failed", error);
  });
}

/**
 * The 422 for a write that gives a top-level backlog ticket a status other
 * than To Do: a backlog only holds unstarted work, so the ticket must be
 * planned into a sprint before its status can change.
 */
export const TICKET_BACKLOG_STATUS_CODE = "KAZ_VAL_062";

/**
 * The 422 for a write that sets an incident to Done while its root cause
 * analysis or lessons learned is blank. `errorValues.missingFields` names the
 * blank fields (`rca`, `lessonsLearned`).
 */
export const INCIDENT_CLOSE_REQUIRES_RCA_CODE = "KAZ_VAL_063";

/**
 * The toast text for a failed ticket write: the translated status rule for
 * {@link TICKET_BACKLOG_STATUS_CODE} and {@link INCIDENT_CLOSE_REQUIRES_RCA_CODE},
 * the translated reason for an assignee or reviewer without Kazan access
 * and for an agent refusal (an agent as reviewer, an agent closing or
 * deleting a ticket), else the server message.
 */
export function ticketErrorMessage(error: Error, t: (key: string) => string): string {
  if (isApiError(error, TICKET_BACKLOG_STATUS_CODE)) {
    return t("tickets.messages.backlogStatusNotAllowed");
  }
  if (isApiError(error, INCIDENT_CLOSE_REQUIRES_RCA_CODE)) {
    return t("tickets.messages.incidentCloseRequiresRca");
  }
  if (isMemberWithoutKazanAccessError(error)) {
    return t("memberAccess.refused");
  }
  const agentRefusalKey = agentRefusalMessageKey(error);
  if (agentRefusalKey) return t(agentRefusalKey);
  return error.message;
}

export function useTickets(boardId: string, params?: TicketQueryParams) {
  return useQuery({
    queryKey: KEYS.list(boardId, (params ?? {}) as Record<string, unknown>),
    queryFn: () => listTickets(boardId, params as Record<string, unknown>),
    enabled: !!boardId && (!!params?.sprintId || !!params?.backlogId),
  });
}

export function useTicket(boardId: string, ticketId: string) {
  return useQuery({
    queryKey: KEYS.detail(boardId, ticketId),
    queryFn: () => getTicketById(boardId, ticketId),
    enabled: !!boardId && !!ticketId,
  });
}

export function useCreateTicket() {
  const queryClient = useQueryClient();
  const t = useTranslations("kazan");
  const tTicket = useTranslations("ticket");

  return useMutation({
    // Shares the ticket mutation prefix so the settle gate can see it: without
    // it, a create resolving mid-drag invalidated ungated and wiped the drag's
    // optimistic patch.
    mutationKey: [...TICKET_MUTATION_KEY, "create"],
    mutationFn: ({
      boardId,
      data,
    }: { boardId: string; data: TicketCreateRequest } & TicketBatchOption) =>
      createTicket(boardId, data),
    onSuccess: (_response, { batched }) => {
      if (batched) return;
      toast.success(t("tickets.messages.created"));
      resyncTickets(queryClient);
    },
    onError: (error: Error, { silentError }) => {
      if (silentError) return;
      // A missing rank gets its own words: the server's generic validation
      // text does not say which field to fix.
      toast.error(
        isMissionRankRejected(error)
          ? tTicket("fields.missionRankRequired")
          : ticketErrorMessage(error, t),
      );
    },
  });
}

/**
 * Updates a ticket, patching every cached copy of it up front so kanban drags
 * land in the target column on drop instead of after the PUT resolves.
 *
 * On failure the entries this mutation patched are restored (the card visibly
 * returns to its source column) and the error is surfaced as a toast.
 */
export function useUpdateTicket() {
  const queryClient = useQueryClient();
  const t = useTranslations("kazan");

  return useMutation({
    mutationKey: [...TICKET_MUTATION_KEY, "update"],
    mutationFn: ({
      boardId,
      ticketId,
      data,
    }: {
      boardId: string;
      ticketId: string;
      data: TicketUpdateRequest;
    } & TicketBatchOption) => updateTicket(boardId, ticketId, data),
    onMutate: async ({ ticketId, data }): Promise<{ snapshot: TicketCacheSnapshot }> => {
      await cancelTicketCaches(queryClient);
      return { snapshot: applyOptimisticTicketUpdate(queryClient, ticketId, data) };
    },
    onSuccess: (_response, { batched }) => {
      if (!batched) toast.success(t("tickets.messages.updated"));
    },
    onError: (error: Error, { silentError }, context) => {
      if (context) restoreTicketCaches(queryClient, context.snapshot);
      if (!silentError) toast.error(ticketErrorMessage(error, t));
    },
    onSettled: (_response, _error, { batched }) => {
      if (!batched) resyncTickets(queryClient);
    },
  });
}

export function useDeleteTicket(options?: {
  /** Runs after cache removal but before query invalidation.
   *  Use this to close sheets/dialogs so their detail queries unmount
   *  before `invalidateRelatedQueries` triggers refetches. */
  onBeforeInvalidate?: () => void;
}) {
  const queryClient = useQueryClient();
  const t = useTranslations("kazan");

  return useMutation({
    // Carries the prefix so the settle gate sees a delete in flight. Its own
    // invalidation is deliberately NOT gated: `excludeKeys` is what stops a
    // still-mounted detail query from refetching a ticket that no longer
    // exists, and a sibling mutation's ungated settle would not carry it.
    mutationKey: [...TICKET_MUTATION_KEY, "delete"],
    mutationFn: ({ boardId, ticketId }: { boardId: string; ticketId: string }) =>
      deleteTicket(boardId, ticketId),
    onSuccess: async (_, variables) => {
      toast.success(t("tickets.messages.deleted"));
      const detailKey = KEYS.detail(variables.boardId, variables.ticketId);
      queryClient.removeQueries({ queryKey: detailKey });
      options?.onBeforeInvalidate?.();
      await invalidateRelatedQueries(queryClient, { excludeKeys: [detailKey] });
    },
    onError: (error: Error) => {
      toast.error(ticketErrorMessage(error, t));
    },
  });
}

/**
 * Moves a ticket between containers (sprint <-> backlog).
 *
 * The optimistic pass removes the ticket from every cached list that is not the
 * target container and inserts it into every cached list that is, so both drag
 * directions land immediately. It follows the backend's rules: no move
 * changes a parent, and the sub-tickets that come along are the ones
 * `data.subTicketMode` picks, by default the unfinished ones placed with the
 * ticket (see `applyOptimisticTicketMove`). One request moves them all, so
 * callers never move sub-tickets themselves. A refusal (an invalid
 * `subTicketIds`, or a sub-ticket that would change board without its parent)
 * rolls the cache back and toasts the server's message.
 *
 * `data.order` places the ticket at that `displayOrder` on its board, and the
 * optimistic pass mirrors the server's shift of the tickets in between; without
 * it the ticket keeps its order. `boardId` must be the moved ticket's own board.
 */
export function useMoveTicket() {
  const queryClient = useQueryClient();
  const t = useTranslations("kazan");

  return useMutation({
    mutationKey: [...TICKET_MUTATION_KEY, "move"],
    mutationFn: ({
      boardId,
      ticketId,
      data,
    }: {
      boardId: string;
      ticketId: string;
      data: TicketMoveRequest;
    } & TicketBatchOption) => moveTicket(boardId, ticketId, data),
    onMutate: async ({
      boardId,
      ticketId,
      data,
    }): Promise<{ snapshot: TicketCacheSnapshot }> => {
      await cancelTicketCaches(queryClient);
      return { snapshot: applyOptimisticTicketMove(queryClient, ticketId, data, boardId) };
    },
    onSuccess: (_response, { batched }) => {
      if (!batched) toast.success(t("tickets.messages.moved"));
    },
    onError: (error: Error, { silentError }, context) => {
      if (context) restoreTicketCaches(queryClient, context.snapshot);
      if (!silentError) toast.error(error.message);
    },
    onSettled: (_response, _error, { batched }) => {
      if (!batched) resyncTickets(queryClient);
    },
  });
}

/**
 * Reorders a ticket within its list (the table row-drag path).
 *
 * `targetTicketId` is the id of the row the ticket was dropped on. Pass it:
 * the optimistic splice locates the destination by id, and falls back to
 * matching `data.order` against `displayOrder` only when it is absent.
 * `displayOrder` is unique per board, so any list running with
 * `includeAllBoards` holds duplicate values and the fallback can splice the row
 * into the wrong slot. The request body (`data`) is unchanged either way.
 *
 * `boardId` must be the dragged ticket's own board: the optimistic pass mirrors
 * the server's `displayOrder` shift on that board's cached rows, so a second
 * drag before the refetch sends the order the server holds by then.
 *
 * Success is intentionally silent -- a reorder is self-evident from the row
 * landing in place -- but a failure still rolls the cache back and toasts.
 */
export function useUpdateTicketOrder() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationKey: [...TICKET_MUTATION_KEY, "order"],
    mutationFn: ({
      boardId,
      ticketId,
      data,
    }: {
      boardId: string;
      ticketId: string;
      data: TicketOrderUpdateRequest;
      targetTicketId?: string;
    }) => updateTicketOrder(boardId, ticketId, data),
    onMutate: async ({
      boardId,
      ticketId,
      data,
      targetTicketId,
    }): Promise<{ snapshot: TicketCacheSnapshot }> => {
      await cancelTicketCaches(queryClient);
      return {
        snapshot: applyOptimisticTicketReorder(
          queryClient,
          ticketId,
          { ticketId: targetTicketId, order: data.order },
          boardId
        ),
      };
    },
    onError: (error: Error, _variables, context) => {
      if (context) restoreTicketCaches(queryClient, context.snapshot);
      toast.error(error.message);
    },
    onSettled: () => {
      resyncTickets(queryClient);
    },
  });
}

export function useBulkMoveTickets() {
  const queryClient = useQueryClient();
  const t = useTranslations("kazan");

  return useMutation({
    // See `useCreateTicket` -- carries the prefix so the settle gate sees it.
    mutationKey: [...TICKET_MUTATION_KEY, "bulk-move"],
    mutationFn: ({
      boardId,
      data,
    }: { boardId: string; data: TicketBulkMoveRequest } & TicketBatchOption) =>
      bulkMoveTickets(boardId, data),
    onSuccess: (response, { batched }) => {
      // A batched move is one step of a save that shows its own result toast
      // and resyncs once when it ends.
      if (batched) return;
      // A ticket moved to a board that does not relate to its milestone's
      // board loses the milestone on the server. The move still succeeded, so
      // the one toast becomes a warning that names the tickets affected.
      const clearedKeys = response?.data?.milestoneClearedTicketKeys ?? [];
      if (clearedKeys.length > 0) {
        toast.warning(t("tickets.messages.bulkMoved"), {
          description: t("tickets.messages.milestoneCleared", {
            count: clearedKeys.length,
            keys: clearedKeys.join(", "),
          }),
        });
      } else {
        toast.success(t("tickets.messages.bulkMoved"));
      }
      resyncTickets(queryClient);
    },
    onError: (error: Error) => {
      toast.error(error.message);
    },
  });
}
