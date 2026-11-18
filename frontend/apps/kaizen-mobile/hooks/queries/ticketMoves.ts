import { useMutation, useQueryClient, type QueryKey } from "@tanstack/react-query";
import { useCallback, useRef } from "react";

import type { TicketScope } from "@/components/boards/boardModel";
import {
  applyPlacementMove,
  applyStatusMove,
  applyTicketChange,
  type StatusTarget,
} from "@/components/boards/ticketMoveModel";
import {
  bulkMoveToast,
  type BulkMoveToast,
  type TicketBulkMoveBody,
} from "@/components/boards/selection/bulkMoveModel";
import type { IncidentCloseValues } from "@/components/ticket/incidentCloseModel";
import { toast } from "@/components/ui/toast";
import type { TicketTarget } from "@/hooks/queries/bulkResult";
import type { TicketUpdateBody } from "@/hooks/queries/ticketActions";
import { boardTicketKeys } from "@/hooks/queries/boards";
import { invalidateTicketViews, ticketKeys } from "@/hooks/queries/tickets";
import { translate } from "@/i18n";
import { api, isValidationError, readData } from "@/lib/api";
import { API_ROUTES } from "@/lib/apiRoutes";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import type { BoardTicket } from "@/lib/schemas/boards";
import {
  commentListSchema,
  ticketBulkMoveResultSchema,
  ticketDetailSchema,
  type Comment,
  type TicketBulkMoveResult,
  type TicketDetail,
} from "@/lib/schemas/tickets";

/** Marks the move mutations, so the last one to settle refreshes the board. */
const TICKET_MOVE_KEY = ["ticket-move"] as const;

/**
 * A move of one ticket on a board view. `boardId` is the ticket's own board
 * (a child board's ticket moves through that board); `order` is the
 * board-wide display order that keeps it where it landed, left out when the
 * ticket's order already does. `incidentClose` goes with the status write of
 * a move that sets an incident to Done, which the API refuses without it.
 */
export type TicketMove =
  | {
      kind: "status";
      ticket: BoardTicket;
      boardId: string;
      target: StatusTarget;
      order?: number;
      incidentClose?: IncidentCloseValues;
    }
  | {
      kind: "place";
      ticket: BoardTicket;
      boardId: string;
      target: TicketScope;
      /** The ticket it lands before in the target list; null is the end. */
      beforeId: string | null;
      order?: number;
      /**
       * The status to give the ticket after the move, when the API changed it:
       * a top-level ticket that enters the backlog becomes Todo, so the Undo of
       * that move sets its old status again.
       */
      status?: string;
      incidentClose?: IncidentCloseValues;
    };

type Snapshot = [QueryKey, BoardTicket[] | undefined][];

/** The params of a cached board ticket list, the last part of its key. */
function listParams(key: QueryKey): Record<string, string> {
  const params: unknown = key[key.length - 1];
  return params && typeof params === "object" ? (params as Record<string, string>) : {};
}

async function sendMove(move: TicketMove): Promise<void> {
  const { ticket, boardId } = move;
  if (move.kind === "status") {
    if (move.target.status !== ticket.status) {
      await api.put(
        API_ROUTES.tickets.byId(boardId, ticket.id),
        { status: move.target.status, ...move.incidentClose },
        { _silentErrors: true }
      );
    }
    if (move.order !== undefined) {
      await api.put(
        API_ROUTES.tickets.order(boardId, ticket.id),
        { order: move.order },
        { _silentErrors: true }
      );
    }
    return;
  }
  const response = await api.post(
    API_ROUTES.tickets.move(boardId, ticket.id),
    { ...move.target, ...(move.order !== undefined ? { order: move.order } : {}) },
    { _silentErrors: true }
  );
  const moved: TicketDetail = readData(ticketDetailSchema, response.data);
  if (move.status && moved.status !== move.status) {
    await api.put(
      API_ROUTES.tickets.byId(boardId, ticket.id),
      { status: move.status, ...move.incidentClose },
      { _silentErrors: true }
    );
  }
}

/**
 * Moves tickets on the board view of `viewBoardId`: a status move
 * (`PUT .../{ticketId}` with the status, then `PUT .../{ticketId}/order`) or
 * a placement move between a sprint and the backlog (`POST .../{ticketId}/move`
 * with the order of the slot). Every cached ticket list of the board shows the
 * move at once and goes back to what it was when the API refuses it.
 *
 * Moves of one ticket are sent one after another, so an Undo tapped while its
 * move is still on the way cannot overtake it. Each request is silent: the
 * caller shows the failure with the server's message. Each failure is logged.
 */
export function useTicketMove(viewBoardId: string) {
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  const queue = useRef<Map<string, Promise<void>>>(new Map());
  const boardKey: QueryKey = boardTicketKeys.board(orgId ?? "", viewBoardId);

  return useMutation<void, Error, TicketMove, { snapshot: Snapshot }>({
    mutationKey: TICKET_MOVE_KEY,
    mutationFn: (move) => {
      const id: string = move.ticket.id;
      const previous: Promise<void> = queue.current.get(id) ?? Promise.resolve();
      // The earlier move's failure was already shown by its own mutation; this
      // move only waits for it to settle before it is sent.
      const run: Promise<void> = previous.then(
        () => sendMove(move),
        () => sendMove(move)
      );
      queue.current.set(id, run);
      return run.finally(() => {
        if (queue.current.get(id) === run) queue.current.delete(id);
      });
    },
    onMutate: async (move) => {
      await queryClient.cancelQueries({ queryKey: boardKey });
      const snapshot: Snapshot = queryClient.getQueriesData<BoardTicket[]>({ queryKey: boardKey });
      for (const [key, rows] of snapshot) {
        if (!rows) continue;
        const next: BoardTicket[] =
          move.kind === "status"
            ? rows.some((row) => row.id === move.ticket.id)
              ? applyStatusMove(rows, move.ticket.id, move.target, move.order)
              : rows
            : applyPlacementMove(
                rows,
                listParams(key),
                move.status ? { ...move.ticket, status: move.status } : move.ticket,
                move.target,
                move.beforeId,
                move.order
              );
        if (next !== rows) queryClient.setQueryData<BoardTicket[]>(key, next);
      }
      return { snapshot };
    },
    onSuccess: () => undefined,
    onError: (error, move, context) => {
      for (const [key, rows] of context?.snapshot ?? []) {
        queryClient.setQueryData<BoardTicket[] | undefined>(key, rows);
      }
      console.warn(`[boards] move of ${move.ticket.key} failed`, error.message);
    },
    // Only the last move to settle reads the board again, so a list refetched
    // between a move and its Undo does not flash the moved state back.
    onSettled: () => {
      if (queryClient.isMutating({ mutationKey: TICKET_MOVE_KEY }) <= 1) {
        return invalidateTicketViews(queryClient, orgId);
      }
      return undefined;
    },
  });
}

/** A field change of one ticket from a board card's menu, such as its priority. */
export interface BoardTicketUpdate {
  target: TicketTarget;
  body: TicketUpdateBody;
  /** The card as the board shows it at once; the previous one returns if the write fails. */
  optimistic: (ticket: BoardTicket) => BoardTicket;
}

/**
 * `PUT .../tickets/{ticketId}` from the board view of `viewBoardId`. Every
 * cached ticket list of that view shows the change at once and goes back to
 * what it was when the API refuses it; a child board's ticket is cached under
 * the view's board, so the view's key is the one patched. Silent: the caller
 * shows the failure with the server's message.
 */
export function useUpdateBoardTicket(viewBoardId: string) {
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  const boardKey: QueryKey = boardTicketKeys.board(orgId ?? "", viewBoardId);
  return useMutation<void, Error, BoardTicketUpdate, { snapshot: Snapshot }>({
    mutationFn: async ({ target, body }) => {
      await api.put(API_ROUTES.tickets.byId(target.boardId, target.ticketId), body, {
        _silentErrors: true,
      });
    },
    onMutate: async ({ target, optimistic }) => {
      await queryClient.cancelQueries({ queryKey: boardKey });
      const snapshot: Snapshot = queryClient.getQueriesData<BoardTicket[]>({ queryKey: boardKey });
      queryClient.setQueriesData<BoardTicket[]>({ queryKey: boardKey }, (rows) =>
        rows ? applyTicketChange(rows, target.ticketId, optimistic) : rows
      );
      return { snapshot };
    },
    onSuccess: () => undefined,
    onError: (error, { target }, context) => {
      for (const [key, rows] of context?.snapshot ?? []) {
        queryClient.setQueryData<BoardTicket[] | undefined>(key, rows);
      }
      console.warn(`[boards] update of ${target.ticketId} failed`, error.message);
    },
    onSettled: () => invalidateTicketViews(queryClient, orgId),
  });
}

/**
 * `DELETE .../tickets/{ticketId}` from a board view, with its sub-tickets and
 * comments. Silent: the caller shows the failure with the server's message.
 */
export function useDeleteBoardTicket() {
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation<void, Error, TicketTarget>({
    mutationFn: async (target) => {
      await api.delete(API_ROUTES.tickets.byId(target.boardId, target.ticketId), {
        _silentErrors: true,
      });
    },
    onSuccess: (_result, target) => {
      if (!orgId) return;
      queryClient.removeQueries({
        queryKey: ticketKeys.detail(orgId, target.boardId, target.ticketId),
      });
    },
    onError: (error, target) =>
      console.warn(`[boards] delete of ${target.ticketId} failed`, error.message),
    onSettled: (_result, _error, target) =>
      invalidateTicketViews(queryClient, orgId, [target.ticketId]),
  });
}

/**
 * Reads a ticket's comments through the cache of the ticket screen, for the
 * delete question that names how many go with it. Rejects when the read
 * fails; the caller surfaces it.
 */
export function useFetchCommentCount(): (target: TicketTarget) => Promise<number> {
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useCallback(
    async (target: TicketTarget): Promise<number> => {
      const comments: Comment[] = await queryClient.fetchQuery({
        queryKey: ticketKeys.comments(orgId ?? "", target.boardId, target.ticketId),
        queryFn: async () => {
          const response = await api.get(
            API_ROUTES.comments.list(target.boardId, target.ticketId),
            { _silentErrors: true }
          );
          return readData(commentListSchema, response.data);
        },
      });
      return comments.length;
    },
    [orgId, queryClient]
  );
}

/**
 * `POST /kaizen/boards/{viewBoardId}/tickets/bulk-move`: moves the selected
 * tickets of the viewed board to a backlog or sprint of this or another
 * board. Success toasts as the web does: a warning naming the tickets that
 * lost their milestone, else a plain success. The app's error layer toasts a
 * refusal with the server's message; a validation refusal, which that layer
 * leaves to forms, is toasted here.
 */
export function useBulkMoveTickets(viewBoardId: string) {
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation<TicketBulkMoveResult, Error, TicketBulkMoveBody>({
    mutationFn: async (body) => {
      const response = await api.post(API_ROUTES.tickets.bulkMove(viewBoardId), body);
      return readData(ticketBulkMoveResultSchema, response.data);
    },
    onSuccess: (result) => {
      const feedback: BulkMoveToast = bulkMoveToast(result);
      if (feedback.tone === "warning") {
        toast.warning(translate("bulkMoveMoved"), {
          description: translate("bulkMoveMilestoneCleared", {
            count: feedback.count,
            keys: feedback.keys,
          }),
        });
      } else {
        toast.success(translate("bulkMoveMoved"));
      }
    },
    onError: (error: Error) => {
      console.warn("[boards] bulk move failed", error.message);
      if (isValidationError(error)) toast.error(error.message);
    },
    // Moved tickets change every board view, the ticket screens and Home.
    onSettled: () => invalidateTicketViews(queryClient, orgId),
  });
}
