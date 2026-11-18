import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
  type QueryClient,
} from "@tanstack/react-query";
import { useMemo } from "react";
import { z } from "zod";

import {
  CHAT_MAX_PAGES,
  chatFirstPageParam,
  chatKeys,
  chatNarrowedListKeys,
  chatNewerPageParam,
  chatOlderPageParam,
  flattenChatMessages,
  patchChatMessage,
} from "@/hooks/queries/chatCache";
import { homeKeys } from "@/hooks/queries/home";
import { listChannelAuthors } from "@/hooks/queries/pullRequests";
import { keepPreviousWithin } from "@/hooks/queries/placeholder";
import { ticketKeys } from "@/hooks/queries/tickets";
import { api, readData } from "@/lib/api";
import { API_ROUTES } from "@/lib/apiRoutes";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import {
  CHAT_PAGE_SIZE,
  chatMessagePageSchema,
  type ChatMemberResponse,
  type ChatMessageCursor,
  type ChatMessagePage,
  type ChatMessageResponse,
} from "@/lib/schemas/chat";
import {
  updateMissingResponseSchema,
  updateNudgeResponseSchema,
  updateResponseSchema,
  updateTicketOptionSchema,
  type UpdateChannelFilter,
  type UpdateMissingResponse,
  type UpdateNudgeResponse,
  type UpdateResponse,
  type UpdateTicketOption,
  type UpdateWriteRequest,
} from "@/lib/schemas/updates";

/*
 * Daily updates: the channel narrowed to one business day and one person,
 * the update form's tickets, the author's writes, and who has not posted.
 * Every body is checked against its schema; a body that does not match
 * throws, which a query shows as its error state. Failed writes are toasted
 * by the app's error listener, except a 422 validation, which the form shows
 * under its fields, and a nudge, whose refusals the Not posted sheet explains.
 */

/** How many tickets a search of the update form lists. */
export const UPDATE_TICKET_SEARCH_MAX = 20;

/** Ticket searches stay this long, so typing back to an earlier text reads the cache. */
const TICKET_SEARCH_STALE_MS = 30_000;

// ── Keys ────────────────────────────────────────────────────────────

/**
 * Query keys of daily updates, scoped by organization. A day of the channel
 * is kept apart from the chat's own channel keys, because the chat's cache
 * edits would add every new message to it whatever its day or author.
 */
export const updateKeys = {
  all: (orgId: string) => ["updates", orgId] as const,
  /** Every narrowed list of the daily updates channel. */
  channelsAll: (orgId: string) => chatNarrowedListKeys.updates(orgId),
  /** The channel on one business day, optionally of one author. */
  channel: (orgId: string, channelId: string, filter: UpdateChannelFilter) =>
    [...updateKeys.channelsAll(orgId), channelId, filter.day, filter.personId] as const,
  authorsAll: (orgId: string) => [...updateKeys.all(orgId), "authors"] as const,
  /** The people who wrote in the channel, the Person filter's options. */
  authors: (orgId: string, channelId: string) =>
    [...updateKeys.authorsAll(orgId), channelId] as const,
  detailsAll: (orgId: string) => [...updateKeys.all(orgId), "detail"] as const,
  detail: (orgId: string, updateId: string) => [...updateKeys.detailsAll(orgId), updateId] as const,
  /** The reader's tickets in progress, which a new update can start from. */
  suggested: (orgId: string) => [...updateKeys.all(orgId), "suggested"] as const,
  ticketSearch: (orgId: string, search: string) =>
    [...updateKeys.all(orgId), "ticket-search", search] as const,
  missingAll: (orgId: string) => [...updateKeys.all(orgId), "missing"] as const,
  /** Who has not posted on one business day. */
  missing: (orgId: string, day: string) => [...updateKeys.missingAll(orgId), day] as const,
};

// ── Requests ────────────────────────────────────────────────────────

/**
 * A page of the channel on `filter.day`, oldest first, with the chat's
 * cursors. The server reads the day in the business zone, so every reader
 * sees the same messages under the same day.
 */
export async function listUpdateChannelDay(
  channelId: string,
  filter: UpdateChannelFilter,
  cursor: ChatMessageCursor
): Promise<ChatMessagePage> {
  const response = await api.get(API_ROUTES.chat.messages, {
    params: {
      ...cursor,
      channelId,
      limit: CHAT_PAGE_SIZE,
      day: filter.day,
      authorId: filter.personId ?? undefined,
    },
  });
  return readData(chatMessagePageSchema, response.data);
}

export async function getUpdate(updateId: string): Promise<UpdateResponse> {
  const response = await api.get(API_ROUTES.dailyUpdates.byId(updateId));
  return readData(updateResponseSchema, response.data);
}

/** Posts the update and its card in the daily updates channel; each line's percentage goes to its ticket. */
export async function createUpdate(data: UpdateWriteRequest): Promise<UpdateResponse> {
  const response = await api.post(API_ROUTES.dailyUpdates.list, data);
  return readData(updateResponseSchema, response.data);
}

/** The author's edit; the body is the full content. */
export async function editUpdate(
  updateId: string,
  data: UpdateWriteRequest
): Promise<UpdateResponse> {
  const response = await api.put(API_ROUTES.dailyUpdates.byId(updateId), data);
  return readData(updateResponseSchema, response.data);
}

/** Removes the update and its card; the author only. Ticket progress it set stays. */
export async function deleteUpdate(updateId: string): Promise<void> {
  await api.delete(API_ROUTES.dailyUpdates.byId(updateId));
}

/** The reader's tickets in progress. */
export async function listSuggestedUpdateTickets(): Promise<UpdateTicketOption[]> {
  const response = await api.get(API_ROUTES.dailyUpdates.suggestedTickets);
  return readData(z.array(updateTicketOptionSchema), response.data);
}

/** Tickets of the reader's boards by key or title, with their progress. */
export async function searchUpdateTickets(search: string): Promise<UpdateTicketOption[]> {
  const response = await api.get(API_ROUTES.tickets.all, {
    params: { search: search || undefined, amount: UPDATE_TICKET_SEARCH_MAX },
  });
  const tickets: UpdateTicketOption[] = readData(z.array(updateTicketOptionSchema), response.data);
  return tickets.slice(0, UPDATE_TICKET_SEARCH_MAX);
}

/** Who had not posted by the deadline on `day`; the server's today without one. */
export async function getMissingUpdates(
  organizationId: string,
  day: string | null
): Promise<UpdateMissingResponse> {
  const response = await api.get(API_ROUTES.dailyUpdates.missing, {
    params: { organizationId, date: day ?? undefined },
  });
  return readData(updateMissingResponseSchema, response.data);
}

/**
 * Sends the member one push asking for today's update. Silent for the error
 * listener: the Not posted sheet words each refusal itself, and an
 * "already nudged" answer is not a failure there.
 */
export async function nudgeMember(userId: string): Promise<UpdateNudgeResponse> {
  const response = await api.post(API_ROUTES.dailyUpdates.nudge(userId), undefined, {
    _silentErrors: true,
  });
  return readData(updateNudgeResponseSchema, response.data);
}

// ── Queries ─────────────────────────────────────────────────────────

/** The daily updates channel on one day, oldest first, paged both ways as the chat does. */
export function useUpdateChannelMessages(channelId: string | null, filter: UpdateChannelFilter) {
  const orgId: string | null = useActiveOrganizationId();
  const query = useInfiniteQuery({
    queryKey: updateKeys.channel(orgId ?? "", channelId ?? "", filter),
    queryFn: ({ pageParam }: { pageParam: ChatMessageCursor }) =>
      listUpdateChannelDay(channelId as string, filter, pageParam),
    initialPageParam: chatFirstPageParam(),
    getPreviousPageParam: (firstPage: ChatMessagePage) => chatOlderPageParam(firstPage),
    getNextPageParam: (lastPage: ChatMessagePage) => chatNewerPageParam(lastPage),
    maxPages: CHAT_MAX_PAGES,
    enabled: !!orgId && !!channelId,
  });
  const messages: ChatMessageResponse[] = useMemo(
    () => flattenChatMessages(query.data),
    [query.data]
  );
  const lastPage: ChatMessagePage | undefined = query.data?.pages[query.data.pages.length - 1];
  const atNewest: boolean = !!lastPage && !lastPage.hasNewer;
  return { ...query, messages, atNewest };
}

/** The Person filter's options: the members who wrote in the channel. */
export function useUpdateChannelAuthors(channelId: string | null) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<ChatMemberResponse[]>({
    queryKey: updateKeys.authors(orgId ?? "", channelId ?? ""),
    queryFn: () => listChannelAuthors(channelId as string),
    enabled: !!orgId && !!channelId,
  });
}

/** One update, for the edit form; disabled until the update is known. */
export function useUpdate(updateId: string | null) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<UpdateResponse>({
    queryKey: updateKeys.detail(orgId ?? "", updateId ?? ""),
    queryFn: () => getUpdate(updateId as string),
    enabled: !!orgId && !!updateId,
  });
}

/** The reader's tickets in progress, for "Add my in-progress tickets". */
export function useSuggestedUpdateTickets(enabled: boolean) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<UpdateTicketOption[]>({
    queryKey: updateKeys.suggested(orgId ?? ""),
    queryFn: listSuggestedUpdateTickets,
    enabled: !!orgId && enabled,
  });
}

/** The form's ticket search; disabled while the search is empty. */
export function useUpdateTicketSearch(search: string | null) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<UpdateTicketOption[]>({
    queryKey: updateKeys.ticketSearch(orgId ?? "", search ?? ""),
    queryFn: () => searchUpdateTickets(search ?? ""),
    enabled: !!orgId && search !== null,
    staleTime: TICKET_SEARCH_STALE_MS,
    placeholderData: keepPreviousWithin(updateKeys.all(orgId ?? "")),
  });
}

/**
 * Who had not posted on `day`. Only an organization OWNER or ADMIN may read
 * it, so the caller passes `enabled` false for anyone else and no refused
 * request is sent.
 */
export function useMissingUpdates(day: string, enabled: boolean) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<UpdateMissingResponse>({
    queryKey: updateKeys.missing(orgId ?? "", day),
    queryFn: () => getMissingUpdates(orgId as string, day),
    enabled: !!orgId && enabled,
  });
}

// ── Mutations ───────────────────────────────────────────────────────

function requireOrgId(orgId: string | null): string {
  if (!orgId) throw new Error("No active organization for daily updates");
  return orgId;
}

/**
 * Every update is also a message of the daily updates channel and writes
 * its percentages to tickets. The realtime signal refreshes the chat's list
 * only while the socket is up; this keeps the writer's own views current
 * without it: the channel, its days, Not posted, and the tickets and Home
 * that show the progress.
 */
async function refreshUpdateViews(queryClient: QueryClient, orgId: string): Promise<void> {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: chatKeys.channelMessagesAll(orgId) }),
    queryClient.invalidateQueries({ queryKey: updateKeys.channelsAll(orgId) }),
    queryClient.invalidateQueries({ queryKey: updateKeys.authorsAll(orgId) }),
    queryClient.invalidateQueries({ queryKey: updateKeys.missingAll(orgId) }),
    queryClient.invalidateQueries({ queryKey: updateKeys.suggested(orgId) }),
    queryClient.invalidateQueries({ queryKey: ticketKeys.all(orgId) }),
    queryClient.invalidateQueries({ queryKey: homeKeys.all(orgId) }),
  ]);
}

/**
 * The answer replaces the update in every loaded copy of its message, so
 * the card changes before the refetch or the realtime signal arrives.
 */
function storeUpdate(
  queryClient: QueryClient,
  orgId: string,
  update: UpdateResponse,
  messageId: string | null
): void {
  queryClient.setQueryData(updateKeys.detail(orgId, update.id), update);
  if (!messageId) return;
  const withUpdate = (message: ChatMessageResponse): ChatMessageResponse => ({
    ...message,
    dailyUpdate: update,
  });
  patchChatMessage(queryClient, orgId, messageId, withUpdate);
}

/** An action on a card: the update and the chat message that carries it. */
export interface UpdateTarget {
  updateId: string;
  messageId: string | null;
}

/** Posts an update; the form keeps its values, since it closes only on success. */
export function useCreateUpdate() {
  const queryClient: QueryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation({
    mutationFn: (data: UpdateWriteRequest) => createUpdate(data),
    onSuccess: async (update: UpdateResponse) => {
      const organizationId: string = requireOrgId(orgId);
      storeUpdate(queryClient, organizationId, update, null);
      await refreshUpdateViews(queryClient, organizationId);
    },
    onError: (error: Error) => {
      // The app's error listener toasts the refusal; a validation error goes under the fields.
      console.warn("[updates] post failed", error.message);
    },
  });
}

/** The author's edit of lines, focus and questions. */
export function useEditUpdate() {
  const queryClient: QueryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation({
    mutationFn: ({ updateId, data }: UpdateTarget & { data: UpdateWriteRequest }) =>
      editUpdate(updateId, data),
    onSuccess: async (update: UpdateResponse, { messageId }) => {
      const organizationId: string = requireOrgId(orgId);
      storeUpdate(queryClient, organizationId, update, messageId);
      await refreshUpdateViews(queryClient, organizationId);
    },
    onError: (error: Error) => {
      // The app's error listener toasts the refusal; a validation error goes under the fields.
      console.warn("[updates] edit failed", error.message);
    },
  });
}

/** Removes the author's update and its card. */
export function useDeleteUpdate() {
  const queryClient: QueryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation({
    mutationFn: ({ updateId }: UpdateTarget) => deleteUpdate(updateId),
    onSuccess: async (_: void, { updateId }: UpdateTarget) => {
      const organizationId: string = requireOrgId(orgId);
      // Dropped first, so an open edit form does not refetch a 404.
      queryClient.removeQueries({ queryKey: updateKeys.detail(organizationId, updateId) });
      await refreshUpdateViews(queryClient, organizationId);
    },
    onError: (error: Error) => {
      // The app's error listener toasts the refusal.
      console.warn("[updates] delete failed", error.message);
    },
  });
}

/** Reminds one member to post today's update; the sheet toasts every outcome. */
export function useNudgeMember() {
  return useMutation({
    mutationFn: (userId: string) => nudgeMember(userId),
    onError: (error: Error) => {
      // The request is silent for the error listener; the sheet shows the outcome.
      console.warn("[updates] nudge failed", error.message);
    },
  });
}
