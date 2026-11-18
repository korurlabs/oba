import { useMutation, useQueryClient } from "@tanstack/react-query";

import { bulkMoveToast, type BulkMoveToast } from "@/components/boards/selection/bulkMoveModel";
import { moveToBoardBody } from "@/components/ticket/ticketActionsModel";
import type { TicketRef } from "@/hooks/queries/ticketDetail";
import { invalidateTicketViews, ticketKeys } from "@/hooks/queries/tickets";
import { api, readData } from "@/lib/api";
import { API_ROUTES } from "@/lib/apiRoutes";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import {
  ticketBulkMoveResultSchema,
  ticketDetailSchema,
  type TicketBulkMoveResult,
  type TicketDetail,
} from "@/lib/schemas/tickets";

/*
 * The writes of the ticket screen. Each is sent with `_silentErrors`: the
 * screen shows the failure itself as a toast with Retry, so the app-wide
 * toast would say it twice. Each failure is also logged here.
 */

/** The fields of `TicketUpdateRequest` the app writes; an omitted field keeps its value. */
export interface TicketUpdateBody {
  /** Sent with the priority the type allows, as Kazan web sends both. */
  type?: string;
  status?: string;
  priority?: string;
  missionRank?: string;
  /** The full list: it replaces the assignees. */
  assigneeIds?: string[];
  /** The full list: it replaces the labels; an empty list removes them all. */
  labelIds?: string[];
  /** The API cannot take a project off a ticket, only set another. */
  projectId?: string;
  milestoneId?: string;
  /** Takes the milestone off; it wins over `milestoneId`. */
  clearMilestone?: boolean;
  /** An instant with `Z`; the API cannot take a due date off a ticket, only set another. */
  dueDate?: string;
  /** Markdown, as Kazan web writes it; an empty text clears it. */
  description?: string;
  /** The root cause analysis an incident needs before it is set to Done. */
  rca?: string;
  /** The lessons learned an incident needs before it is set to Done. */
  lessonsLearned?: string;
}

/** A ticket update and how the ticket reads once it lands. */
export interface TicketUpdate {
  body: TicketUpdateBody;
  /** The ticket as the screen shows it at once; the previous one returns if the write fails. */
  optimistic: (ticket: TicketDetail) => TicketDetail;
}

/**
 * Which sub-tickets come along when a ticket moves (`SubTicketMoveMode`):
 * none, the unfinished ones placed where the ticket was, or those named in
 * `subTicketIds` wherever they are placed.
 */
export type SubTicketMoveMode = "NONE" | "UNFINISHED" | "SELECTED";

function requireRef(ref: TicketRef | undefined): TicketRef {
  if (!ref) throw new Error("The ticket is not resolved yet");
  return ref;
}

/**
 * `PUT /kazan/boards/{boardId}/tickets/{ticketId}`. The detail shows the
 * change at once and goes back to what it was if the API refuses it; the
 * answer of the API then replaces it.
 */
export function useUpdateTicket(ref: TicketRef | undefined) {
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  const detailKey = ticketKeys.detail(orgId ?? "", ref?.boardId ?? "", ref?.ticketId ?? "");
  return useMutation<TicketDetail, Error, TicketUpdate, { previous?: TicketDetail }>({
    mutationFn: async ({ body }) => {
      const target: TicketRef = requireRef(ref);
      const response = await api.put(
        API_ROUTES.tickets.byId(target.boardId, target.ticketId),
        body,
        {
          _silentErrors: true,
        }
      );
      return readData(ticketDetailSchema, response.data);
    },
    onMutate: async ({ optimistic }) => {
      await queryClient.cancelQueries({ queryKey: detailKey });
      const previous: TicketDetail | undefined = queryClient.getQueryData<TicketDetail>(detailKey);
      if (previous) queryClient.setQueryData<TicketDetail>(detailKey, optimistic(previous));
      return { previous };
    },
    onSuccess: (ticket) => queryClient.setQueryData<TicketDetail>(detailKey, ticket),
    onError: (error, _update, context) => {
      if (context?.previous) queryClient.setQueryData<TicketDetail>(detailKey, context.previous);
      console.warn("[ticket] update failed", error.message);
    },
    onSettled: () => invalidateTicketViews(queryClient, orgId),
  });
}

/**
 * `POST .../tickets/bulk-move` with `MOVE_TO_BACKLOG` on the ticket's own
 * board: takes the ticket out of its sprint into the board's backlog, with
 * the sub-tickets the mode picks.
 */
export function useMoveTicketToBacklog(ref: TicketRef | undefined) {
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation<void, Error, SubTicketMoveMode>({
    mutationFn: async (subTicketMode) => {
      const target: TicketRef = requireRef(ref);
      await api.post(
        API_ROUTES.tickets.bulkMove(target.boardId),
        { ticketIds: [target.ticketId], action: "MOVE_TO_BACKLOG", subTicketMode },
        { _silentErrors: true }
      );
    },
    onSuccess: () => undefined,
    onError: (error) => console.warn("[ticket] move to backlog failed", error.message),
    onSettled: () => invalidateTicketViews(queryClient, orgId),
  });
}

/** A move into a sprint of the ticket's own board, with the sub-tickets the mode picks. */
export interface SprintMove {
  sprintId: string;
  mode: SubTicketMoveMode;
}

/**
 * `POST .../tickets/bulk-move` with `MOVE_TO_SPRINT` on the ticket's own
 * board: plans the ticket into another open sprint of the board.
 */
export function useMoveTicketToSprint(ref: TicketRef | undefined) {
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation<void, Error, SprintMove>({
    mutationFn: async ({ sprintId, mode }) => {
      const target: TicketRef = requireRef(ref);
      await api.post(
        API_ROUTES.tickets.bulkMove(target.boardId),
        {
          ticketIds: [target.ticketId],
          action: "MOVE_TO_SPRINT",
          targetSprintId: sprintId,
          subTicketMode: mode,
        },
        { _silentErrors: true }
      );
    },
    onSuccess: () => undefined,
    onError: (error) => console.warn("[ticket] move to sprint failed", error.message),
    onSettled: () => invalidateTicketViews(queryClient, orgId),
  });
}

/** A move to the backlog (`sprintId` null) or a sprint of the ticket's board, with chosen sub-tickets. */
export interface PlacementMove {
  sprintId: string | null;
  subTicketMode: SubTicketMoveMode;
  /** Only with `SELECTED`: the sub-tickets that come along. */
  subTicketIds?: string[];
}

/**
 * `POST .../tickets/bulk-move` on the ticket's own board, into the backlog or
 * a sprint, with the sub-tickets the user picked. The Move to sprint sheet
 * sends it; the quick moves above cover the two fixed sub-ticket modes.
 */
export function useMoveTicketPlacement(ref: TicketRef | undefined) {
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation<void, Error, PlacementMove>({
    mutationFn: async ({ sprintId, subTicketMode, subTicketIds }) => {
      const target: TicketRef = requireRef(ref);
      await api.post(
        API_ROUTES.tickets.bulkMove(target.boardId),
        {
          ticketIds: [target.ticketId],
          action: sprintId ? "MOVE_TO_SPRINT" : "MOVE_TO_BACKLOG",
          ...(sprintId ? { targetSprintId: sprintId } : {}),
          subTicketMode,
          ...(subTicketMode === "SELECTED" ? { subTicketIds } : {}),
        },
        { _silentErrors: true }
      );
    },
    onSuccess: () => undefined,
    onError: (error) => console.warn("[ticket] move with sub-tickets failed", error.message),
    onSettled: () => invalidateTicketViews(queryClient, orgId),
  });
}

/** Where a ticket moves on another board, and what happens to its sub-tickets. */
export interface BoardMove {
  targetBoardId: string;
  /** `BACKLOG_PLACEMENT` or a sprint of the target board. */
  placement: string;
  /** Leave the sub-tickets on the old board without a parent instead of taking them along. */
  detach: boolean;
}

/** A ticket after its move to another board, and the toast the move earns. */
export interface MovedToBoard {
  ticket: TicketDetail;
  /** A warning when the move took the ticket's milestone off, else a plain success. */
  feedback: BulkMoveToast;
}

/**
 * Moves the ticket to the backlog or a sprint of another board (`POST
 * .../bulk-move` with `targetBoardId`), then reads it there. The ticket
 * keeps its id but gets a key of the new board, which the call resolves
 * with so the screen can follow it; the move's result tells the screen
 * whether the milestone was taken off.
 */
export function useMoveTicketToBoard(ref: TicketRef | undefined, ticketKey: string) {
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation<MovedToBoard, Error, BoardMove>({
    mutationFn: async ({ targetBoardId, placement, detach }) => {
      const source: TicketRef = requireRef(ref);
      const moveResponse = await api.post(
        API_ROUTES.tickets.bulkMove(source.boardId),
        moveToBoardBody(source.ticketId, targetBoardId, placement, detach),
        { _silentErrors: true }
      );
      const result: TicketBulkMoveResult = readData(ticketBulkMoveResultSchema, moveResponse.data);
      const response = await api.get(API_ROUTES.tickets.byId(targetBoardId, source.ticketId), {
        _silentErrors: true,
      });
      return {
        ticket: readData(ticketDetailSchema, response.data),
        feedback: bulkMoveToast(result),
      };
    },
    onSuccess: ({ ticket }) => {
      if (!orgId) return;
      queryClient.setQueryData<TicketRef>(ticketKeys.byKey(orgId, ticket.key), {
        ticketId: ticket.id,
        boardId: ticket.board.id,
      });
      queryClient.setQueryData<TicketDetail>(
        ticketKeys.detail(orgId, ticket.board.id, ticket.id),
        ticket
      );
    },
    onError: (error) => console.warn("[ticket] move to board failed", error.message),
    // The old key no longer resolves; reading it again would show "no longer exists".
    onSettled: () => invalidateTicketViews(queryClient, orgId, [ticketKey]),
  });
}

/**
 * `DELETE .../tickets/{ticketId}`: removes the ticket with its sub-tickets
 * and comments. The deleted ticket's own queries are not read again, so the
 * leaving screen does not flash "no longer exists".
 */
export function useDeleteTicket(ref: TicketRef | undefined, ticketKey: string) {
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation<void, Error, void>({
    mutationFn: async () => {
      const target: TicketRef = requireRef(ref);
      await api.delete(API_ROUTES.tickets.byId(target.boardId, target.ticketId), {
        _silentErrors: true,
      });
    },
    onSuccess: () => undefined,
    onError: (error) => console.warn("[ticket] delete failed", error.message),
    onSettled: () =>
      invalidateTicketViews(queryClient, orgId, [ticketKey, ...(ref ? [ref.ticketId] : [])]),
  });
}
