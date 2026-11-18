"use client";

import {
  useMutation,
  useMutationState,
  useQuery,
  useQueryClient,
  type Mutation,
  type MutationFilters,
} from "@tanstack/react-query";

import { ERROR_CODES, KayaApiError } from "@/lib/api/errors";
import { useIdentity, type Identity } from "@/lib/identity/identity-context";

import {
  addSessionLink,
  deleteSession,
  getSessionDetail,
  listSessionEvents,
  listSessionHandoffs,
  removeSessionLink,
} from "../api";
import type { ApiResponse } from "@/types/api";

import type { SessionDetailResponse, SessionLinkCreateRequest } from "../schemas";

const KEYS = {
  sessions: (organizationId: string) => ["sessions", organizationId] as const,
  all: (organizationId: string, sessionId: string) =>
    ["sessions", organizationId, "detail", sessionId] as const,
  detail: (organizationId: string, sessionId: string) =>
    [...KEYS.all(organizationId, sessionId), "summary"] as const,
  events: (organizationId: string, sessionId: string) =>
    [...KEYS.all(organizationId, sessionId), "events"] as const,
  handoffs: (organizationId: string, sessionId: string) =>
    [...KEYS.all(organizationId, sessionId), "handoffs"] as const,
  delete: (organizationId: string) => ["sessions", organizationId, "delete"] as const,
};

function seqOf(event: unknown): number | null {
  if (typeof event !== "object" || event === null) return null;
  const seq = (event as { seq?: unknown }).seq;
  return typeof seq === "number" ? seq : null;
}

/**
 * The highest seq n for which the events hold every seq from 1 to n, as the
 * server counts its acknowledged seq. A later batch can still fill a gap above
 * it, so the next read asks for everything after this point, not after the
 * highest seq seen.
 */
export function contiguousSeq(events: readonly unknown[]): number {
  let seq = 0;
  for (const event of events) {
    if (seqOf(event) !== seq + 1) break;
    seq += 1;
  }
  return seq;
}

/**
 * The session's events after a reload: the known ones up to their contiguous seq,
 * then what the server holds after it. A running session gains events every few
 * seconds, so a reload asks only for those instead of the whole session again.
 */
export async function loadNewerEvents(
  sessionId: string,
  known: readonly unknown[],
  load: typeof listSessionEvents = listSessionEvents
): Promise<ApiResponse<unknown[]>> {
  const afterSeq = contiguousSeq(known);
  const response = await load(sessionId, afterSeq);
  return { ...response, data: [...known.slice(0, afterSeq), ...(response.data ?? [])] };
}

/** Whether Kaya answered that this organization has no such session. */
export function isSessionNotFound(error: unknown): boolean {
  return (
    error instanceof KayaApiError && error.status === 404 && error.code === ERROR_CODES.NOT_FOUND
  );
}

/**
 * The detail, events and handoffs of one session. They share the key prefix
 * `["sessions", organizationId, "detail", sessionId]`, which the live stream
 * invalidates when the session changes, so they reload only then.
 */
export function useSessionDetail(sessionId: string) {
  const queryClient = useQueryClient();
  const { organization } = useIdentity();
  const organizationId = organization.organizationId;
  const deleted = useSessionDeleted(sessionId);

  const detail = useQuery({
    queryKey: KEYS.detail(organizationId, sessionId),
    queryFn: () => getSessionDetail(sessionId),
    enabled: sessionId !== "" && !deleted,
  });
  const found = detail.data?.data !== undefined && detail.data?.data !== null;

  const eventsKey = KEYS.events(organizationId, sessionId);
  const events = useQuery({
    queryKey: eventsKey,
    queryFn: () =>
      loadNewerEvents(
        sessionId,
        queryClient.getQueryData<ApiResponse<unknown[]>>(eventsKey)?.data ?? []
      ),
    enabled: found && !deleted,
  });
  const handoffs = useQuery({
    queryKey: KEYS.handoffs(organizationId, sessionId),
    queryFn: () => listSessionHandoffs(sessionId),
    enabled: found && !deleted,
  });

  return { detail, events, handoffs, deleted };
}

/**
 * Whether this user just deleted the session. Its queries stop then, so a live
 * update that arrives before the page has left does not reload the session and
 * show it as not found.
 */
function useSessionDeleted(sessionId: string): boolean {
  const { organization } = useIdentity();
  const deletes = useMutationState({
    filters: deletedSessionFilters(organization.organizationId, sessionId),
    select: (mutation) => mutation.state.variables,
  });
  return deletes.length > 0;
}

/** The finished deletes of one session in the mutation cache. */
export function deletedSessionFilters(organizationId: string, sessionId: string): MutationFilters {
  return {
    mutationKey: KEYS.delete(organizationId),
    status: "success",
    predicate: (mutation: Mutation<unknown, Error, unknown, unknown>) =>
      mutation.state.variables === sessionId,
  };
}

/**
 * Whether the signed-in user may delete the session: an admin (or owner) may
 * delete any session, a member only the sessions of their own user.
 */
export function mayDeleteSession(
  identity: { me: Pick<Identity["me"], "id">; can: Identity["can"] },
  session: Pick<SessionDetailResponse, "userId">
): boolean {
  if (identity.can("ADMIN")) return true;
  return session.userId === identity.me.id;
}

/**
 * A link change alters the session's detail and its tile on the wall, and the
 * wall's search finds sessions by ticket key, so every session query reloads.
 * Errors are shown by the form or dialog that ran the mutation.
 */
function useLinkMutation<TArgs, TResult>(mutationFn: (args: TArgs) => Promise<TResult>) {
  const queryClient = useQueryClient();
  const { organization } = useIdentity();
  return useMutation({
    mutationFn,
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: KEYS.sessions(organization.organizationId) }),
    onError: (error: unknown) => console.warn("[sessions] Link change refused", error),
  });
}

export function useAddSessionLink() {
  return useLinkMutation(
    ({ sessionId, data }: { sessionId: string; data: SessionLinkCreateRequest }) =>
      addSessionLink(sessionId, data)
  );
}

export function useRemoveSessionLink() {
  return useLinkMutation(({ sessionId, linkId }: { sessionId: string; linkId: string }) =>
    removeSessionLink(sessionId, linkId)
  );
}

/**
 * Deletes a session. Its detail, events and handoffs are dropped rather than
 * reloaded, since they would only answer 404 now; the wall and its filters reload.
 */
export function useDeleteSession() {
  const queryClient = useQueryClient();
  const { organization } = useIdentity();
  return useMutation({
    mutationKey: KEYS.delete(organization.organizationId),
    mutationFn: (sessionId: string) => deleteSession(sessionId),
    onSuccess: async (_, sessionId: string) => {
      const detailKey = KEYS.all(organization.organizationId, sessionId);
      queryClient.removeQueries({ queryKey: detailKey });
      await queryClient.invalidateQueries({
        queryKey: KEYS.sessions(organization.organizationId),
        predicate: (query) => !(query.queryKey[2] === "detail" && query.queryKey[3] === sessionId),
      });
    },
    onError: (error: unknown) => console.warn("[sessions] Delete refused", error),
  });
}
