import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { commentsReadable } from "@/components/ticket/ticketModel";
import { shouldRetryBoardRead } from "@/hooks/queries/boards";
import { ticketKeys } from "@/hooks/queries/tickets";
import { api, readData } from "@/lib/api";
import { API_ROUTES } from "@/lib/apiRoutes";
import { TICKET_COMMENTS_ENABLED } from "@/lib/features";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import {
  commentListSchema,
  commentSchema,
  ticketDetailSchema,
  ticketListItemSchema,
  type Comment,
  type TicketDetail,
} from "@/lib/schemas/tickets";

/** Where a ticket key lives: its board and id, which every ticket path needs. */
export interface TicketRef {
  ticketId: string;
  boardId: string;
}

/** How long a resolved key stays fresh; a key changes only when a ticket moves to another board. */
const TICKET_KEY_STALE_MS = 5 * 60_000;

/**
 * `GET /kazan/tickets/by-key/{key}`: the board and id of a ticket key, for
 * links that carry only the key (deep links, Copy link, ticket cards). A 403
 * or 404 fails at once, so the screen says "no access" without retries.
 */
export function useTicketRef(ticketKey: string | null) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<TicketRef>({
    queryKey: ticketKeys.byKey(orgId ?? "", ticketKey ?? ""),
    queryFn: async () => {
      const response = await api.get(API_ROUTES.tickets.byKey(ticketKey as string));
      const ticket = readData(ticketListItemSchema, response.data);
      if (!ticket.board) {
        throw new Error(`Ticket ${ticket.key} came without its board`);
      }
      return { ticketId: ticket.id, boardId: ticket.board.id };
    },
    enabled: !!orgId && !!ticketKey,
    retry: shouldRetryBoardRead,
    staleTime: TICKET_KEY_STALE_MS,
  });
}

/** `GET /kazan/boards/{boardId}/tickets/{ticketId}`: every field the detail screen shows. */
export function useTicketDetail(ref: TicketRef | undefined) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<TicketDetail>({
    queryKey: ticketKeys.detail(orgId ?? "", ref?.boardId ?? "", ref?.ticketId ?? ""),
    queryFn: async () => {
      const response = await api.get(API_ROUTES.tickets.byId(ref!.boardId, ref!.ticketId));
      return readData(ticketDetailSchema, response.data);
    },
    enabled: !!orgId && !!ref,
    retry: shouldRetryBoardRead,
  });
}

/** `GET .../comments`: the ticket's comments. It never runs while the app hides comments. */
export function useComments(ref: TicketRef | undefined) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<Comment[]>({
    queryKey: ticketKeys.comments(orgId ?? "", ref?.boardId ?? "", ref?.ticketId ?? ""),
    queryFn: async () => {
      const response = await api.get(API_ROUTES.comments.list(ref!.boardId, ref!.ticketId));
      return readData(commentListSchema, response.data);
    },
    enabled: commentsReadable(TICKET_COMMENTS_ENABLED, orgId, ref),
    retry: shouldRetryBoardRead,
  });
}

/** The body of a comment create or update; `content` holds mention tokens. */
export interface CommentBody {
  content: string;
}

/** The body of a comment update. */
export interface CommentUpdate extends CommentBody {
  commentId: string;
}

/**
 * Keeps the comment list in step with a write: the answer of the API goes
 * into the cache at once, and the list is read again afterwards in case
 * someone else commented meanwhile.
 */
function useCommentCache(ref: TicketRef) {
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  const key = ticketKeys.comments(orgId ?? "", ref.boardId, ref.ticketId);
  return {
    put: (comment: Comment) =>
      queryClient.setQueryData<Comment[]>(key, (list) => {
        const current: Comment[] = list ?? [];
        return current.some((item) => item.id === comment.id)
          ? current.map((item) => (item.id === comment.id ? comment : item))
          : [...current, comment];
      }),
    remove: (commentId: string) =>
      queryClient.setQueryData<Comment[]>(key, (list) =>
        (list ?? []).filter((item) => item.id !== commentId)
      ),
    refresh: () => (orgId ? queryClient.invalidateQueries({ queryKey: key }) : undefined),
  };
}

/*
 * The comment writes are sent with `_silentErrors`: the screen shows each
 * failure itself, as a toast with Retry, or under the composer for a
 * validation error, so the app-wide toast would say it twice.
 */

/** `POST .../comments`. */
export function useCreateComment(ref: TicketRef) {
  const cache = useCommentCache(ref);
  return useMutation<Comment, Error, CommentBody>({
    mutationFn: async (body) => {
      const response = await api.post(API_ROUTES.comments.list(ref.boardId, ref.ticketId), body, {
        _silentErrors: true,
      });
      return readData(commentSchema, response.data);
    },
    onSuccess: (comment) => cache.put(comment),
    onError: (error) => console.warn("[ticket] comment create failed", error.message),
    onSettled: () => cache.refresh(),
  });
}

/** `PUT .../comments/{commentId}`: only the author may. */
export function useUpdateComment(ref: TicketRef) {
  const cache = useCommentCache(ref);
  return useMutation<Comment, Error, CommentUpdate>({
    mutationFn: async ({ commentId, content }) => {
      const response = await api.put(
        API_ROUTES.comments.byId(ref.boardId, ref.ticketId, commentId),
        { content },
        { _silentErrors: true }
      );
      return readData(commentSchema, response.data);
    },
    onSuccess: (comment) => cache.put(comment),
    onError: (error) => console.warn("[ticket] comment update failed", error.message),
    onSettled: () => cache.refresh(),
  });
}

/** `DELETE .../comments/{commentId}`: only the author may. */
export function useDeleteComment(ref: TicketRef) {
  const cache = useCommentCache(ref);
  return useMutation<void, Error, string>({
    mutationFn: async (commentId) => {
      await api.delete(API_ROUTES.comments.byId(ref.boardId, ref.ticketId, commentId), {
        _silentErrors: true,
      });
    },
    onSuccess: (_data, commentId) => cache.remove(commentId),
    onError: (error) => console.warn("[ticket] comment delete failed", error.message),
    onSettled: () => cache.refresh(),
  });
}
