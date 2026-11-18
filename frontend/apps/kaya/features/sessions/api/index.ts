import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@/types/api";

import type {
  RepositoryResponse,
  SessionDetailResponse,
  SessionFiltersResponse,
  SessionHandoffResponse,
  SessionLinkCreateRequest,
  SessionLinkResponse,
  SessionListResponse,
  SessionQueryRequest,
} from "../schemas";

/** One page of the organization's sessions, newest activity first. */
export async function listSessions(params: SessionQueryRequest) {
  const res = await api.get<ApiResponse<SessionListResponse[]>>(API_ROUTES.sessions.list, {
    params,
  });
  return res.data;
}

/** The values the wall's selects offer: only those the organization's sessions hold. */
export async function getSessionFilters() {
  const res = await api.get<ApiResponse<SessionFiltersResponse>>(API_ROUTES.sessions.filters);
  return res.data;
}

export async function listRepositories() {
  const res = await api.get<ApiResponse<RepositoryResponse[]>>(API_ROUTES.repositories.list);
  return res.data;
}

export async function getSessionDetail(sessionId: string) {
  const res = await api.get<ApiResponse<SessionDetailResponse>>(
    API_ROUTES.sessions.detail(sessionId)
  );
  return res.data;
}

/**
 * The session's recorded events in `seq` order, as the recorder wrote them;
 * `afterSeq` returns only the newer ones.
 */
export async function listSessionEvents(sessionId: string, afterSeq = 0) {
  const res = await api.get<ApiResponse<unknown[]>>(API_ROUTES.sessions.events(sessionId), {
    params: { afterSeq },
  });
  return res.data;
}

/** The latest snapshot of every handoff the session wrote, with its markdown. */
export async function listSessionHandoffs(sessionId: string) {
  const res = await api.get<ApiResponse<SessionHandoffResponse[]>>(
    API_ROUTES.sessions.handoffs(sessionId)
  );
  return res.data;
}

/** Links a pull request or Kaizen ticket by hand; a link the session already has comes back unchanged. */
export async function addSessionLink(sessionId: string, data: SessionLinkCreateRequest) {
  const res = await api.post<ApiResponse<SessionLinkResponse>>(
    API_ROUTES.sessions.links(sessionId),
    data
  );
  return res.data;
}

export async function removeSessionLink(sessionId: string, linkId: string): Promise<void> {
  await api.delete(API_ROUTES.sessions.link(sessionId, linkId));
}

/** Deletes the session with its events, links and handoffs; only its user or an admin may. */
export async function deleteSession(sessionId: string): Promise<void> {
  await api.delete(API_ROUTES.sessions.detail(sessionId));
}
