import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { homeKeys } from "@/hooks/queries/home";
import { partitionSettled, type BulkResult, type TicketTarget } from "@/hooks/queries/bulkResult";
import {
  myTicketsParams,
  URGENT_INCIDENT_PARAMS,
  type MyTicketsFilters,
  type MyTicketsMode,
} from "@/hooks/queries/ticketParams";
import { keepPreviousWithin } from "@/hooks/queries/placeholder";
import { useMe } from "@/hooks/queries/users";
import { api, readData } from "@/lib/api";
import { API_ROUTES } from "@/lib/apiRoutes";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import { ticketListSchema, type TicketListItem } from "@/lib/schemas/tickets";

/** Query keys of tickets, scoped by organization. */
export const ticketKeys = {
  all: (orgId: string) => ["tickets", orgId] as const,
  mine: (
    orgId: string,
    userId: string,
    mode: MyTicketsMode,
    filters: MyTicketsFilters,
    search: string = ""
  ) => [...ticketKeys.all(orgId), "mine", userId, mode, filters, search] as const,
  urgentIncidents: (orgId: string) => [...ticketKeys.all(orgId), "urgent-incidents"] as const,
  detail: (orgId: string, boardId: string, ticketId: string) =>
    [...ticketKeys.all(orgId), "detail", boardId, ticketId] as const,
  /** A ticket key resolved to its board and id. */
  byKey: (orgId: string, ticketKey: string) =>
    [...ticketKeys.all(orgId), "by-key", ticketKey] as const,
  comments: (orgId: string, boardId: string, ticketId: string) =>
    [...ticketKeys.all(orgId), "comments", boardId, ticketId] as const,
};

/**
 * `GET /kazan/tickets` for My Tickets: root tickets only, unpaged. `search`
 * matches the title or the key; blank searches nothing.
 */
export function useMyTickets(mode: MyTicketsMode, filters: MyTicketsFilters, search: string = "") {
  const orgId: string | null = useActiveOrganizationId();
  const me = useMe(true);
  const userId: string | undefined = me.data?.id;
  const text: string = search.trim();
  return useQuery<TicketListItem[]>({
    queryKey: ticketKeys.mine(orgId ?? "", userId ?? "", mode, filters, text),
    queryFn: async () => {
      const response = await api.get(API_ROUTES.tickets.all, {
        params: { ...myTicketsParams(userId!, mode, filters), ...(text ? { search: text } : {}) },
      });
      return readData(ticketListSchema, response.data);
    },
    enabled: !!orgId && !!userId,
    placeholderData: keepPreviousWithin(ticketKeys.all(orgId ?? "")),
  });
}

/** How often the tab badges refetch while the app is in the foreground. */
export const BADGE_REFETCH_MS = 60_000;

/** The count of open P1 and P2 incidents for the Incidents tab badge. */
export function useUrgentIncidentCount() {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<number>({
    queryKey: ticketKeys.urgentIncidents(orgId ?? ""),
    queryFn: async () => {
      // Silent: a badge that fails to load is left off, not toasted every minute.
      const response = await api
        .get(API_ROUTES.tickets.all, { params: URGENT_INCIDENT_PARAMS, _silentErrors: true })
        .catch((error: unknown) => {
          console.warn(
            "[tabs] incident badge failed",
            error instanceof Error ? error.message : error
          );
          throw error;
        });
      return readData(ticketListSchema, response.data).length;
    },
    enabled: !!orgId,
    refetchInterval: BADGE_REFETCH_MS,
  });
}

/**
 * Runs `write` for every target and reports which failed. Each request is
 * silent: one failure would otherwise raise one toast per ticket, so the
 * caller shows a single summary instead. Each failure is logged here.
 */
async function writeEach(
  targets: readonly TicketTarget[],
  write: (target: TicketTarget) => Promise<unknown>,
  scope: string
): Promise<BulkResult<TicketTarget>> {
  const outcomes = await Promise.allSettled(targets.map(write));
  const result: BulkResult<TicketTarget> = partitionSettled(targets, outcomes);
  for (const failure of result.failed) {
    console.warn(`[tickets] ${scope} ${failure.target.ticketId} failed`, failure.message);
  }
  return result;
}

/**
 * Deletes tickets one request each (there is no bulk endpoint). Resolves with
 * the successes and failures; it rejects only when nothing could be sent.
 */
export function useDeleteTickets() {
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation({
    mutationFn: (targets: readonly TicketTarget[]) =>
      writeEach(
        targets,
        (target) =>
          api.delete(API_ROUTES.tickets.byId(target.boardId, target.ticketId), {
            _silentErrors: true,
          }),
        "delete"
      ),
    onSuccess: (result) => {
      if (!orgId) return;
      for (const target of result.succeeded) {
        queryClient.removeQueries({
          queryKey: ticketKeys.detail(orgId, target.boardId, target.ticketId),
        });
      }
    },
    onError: (error: unknown) => {
      console.warn("[tickets] delete failed", error instanceof Error ? error.message : error);
    },
    onSettled: () => invalidateTicketViews(queryClient, orgId),
  });
}

/** Restores deleted tickets, the Undo of a delete. */
export function useRestoreTickets() {
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation({
    mutationFn: (targets: readonly TicketTarget[]) =>
      writeEach(
        targets,
        (target) =>
          api.post(API_ROUTES.tickets.restore(target.boardId, target.ticketId), undefined, {
            _silentErrors: true,
          }),
        "restore"
      ),
    onSuccess: () => undefined,
    onError: (error: unknown) => {
      console.warn("[tickets] restore failed", error instanceof Error ? error.message : error);
    },
    onSettled: () => invalidateTicketViews(queryClient, orgId),
  });
}

/**
 * Marks every ticket list, ticket and the Home dashboard stale after a ticket
 * write. Queries whose key holds one of `except` (the id or key of a ticket
 * that was just deleted or re-keyed) are left alone: read again, they would
 * fail with "not found" on the screen that is leaving.
 */
export function invalidateTicketViews(
  queryClient: ReturnType<typeof useQueryClient>,
  orgId: string | null,
  except: readonly string[] = []
): Promise<void> | undefined {
  if (!orgId) return undefined;
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: homeKeys.all(orgId) }),
    queryClient.invalidateQueries({
      queryKey: ticketKeys.all(orgId),
      predicate: (query) => !query.queryKey.some((part) => except.includes(part as string)),
    }),
  ]).then(() => undefined);
}
