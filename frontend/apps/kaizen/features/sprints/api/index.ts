import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@oba/shared/schema";
import type {
  SprintCreateRequest,
  SprintUpdateRequest,
  SprintBulkLinkRequest,
  SprintUnlinkRequest,
  SprintCompleteRequest,
  SprintDetailResponse,
  SprintSeriesRequest,
  SprintListResponse,
  SprintChildBoardResponse,
  SprintStartRequest,
  SprintStartPreviewResponse,
} from "../schemas";
import type { TicketRelocationAction } from "@/features/tickets/schemas";

export async function createSprint(
  boardId: string,
  data: SprintCreateRequest,
) {
  const res = await api.post<ApiResponse<SprintDetailResponse>>(
    API_ROUTES.sprints.list(boardId),
    data,
  );
  return res.data;
}

export async function listSprints(boardId: string) {
  const res = await api.get<ApiResponse<SprintListResponse[]>>(
    API_ROUTES.sprints.list(boardId),
  );
  return res.data;
}

/** Descendant boards of `boardId` that a new sprint can be linked to. */
export async function listSprintChildBoards(boardId: string) {
  const res = await api.get<ApiResponse<SprintChildBoardResponse[]>>(
    API_ROUTES.sprints.childBoards(boardId),
  );
  return res.data;
}

export async function getSprintById(boardId: string, sprintId: string) {
  const res = await api.get<ApiResponse<SprintDetailResponse>>(
    API_ROUTES.sprints.detail(boardId, sprintId),
  );
  return res.data;
}

export async function updateSprint(
  boardId: string,
  sprintId: string,
  data: SprintUpdateRequest,
) {
  const res = await api.put<ApiResponse<SprintDetailResponse>>(
    API_ROUTES.sprints.detail(boardId, sprintId),
    data,
  );
  return res.data;
}

/**
 * Replaces the settings of the sprint's series. Switching repeat on for a
 * sprint outside any series starts a series named after the sprint.
 */
export async function updateSprintSeries(
  boardId: string,
  sprintId: string,
  data: SprintSeriesRequest,
) {
  const res = await api.put<ApiResponse<SprintDetailResponse>>(
    API_ROUTES.sprints.series(boardId, sprintId),
    data,
  );
  return res.data;
}

/**
 * Starts a planned sprint. Without `data.takeOver` the server refuses with a
 * 409 while a linked board has another active sprint; with it, those sprints
 * are closed and their unfinished tickets move into this one.
 */
export async function startSprint(
  boardId: string,
  sprintId: string,
  data?: SprintStartRequest,
) {
  const res = await api.post<ApiResponse<SprintDetailResponse>>(
    API_ROUTES.sprints.start(boardId, sprintId),
    data,
  );
  return res.data;
}

/** The active sprints a start of `sprintId` would have to close. */
export async function getSprintStartPreview(boardId: string, sprintId: string) {
  const res = await api.get<ApiResponse<SprintStartPreviewResponse>>(
    API_ROUTES.sprints.startPreview(boardId, sprintId),
  );
  return res.data;
}

export async function completeSprint(
  boardId: string,
  sprintId: string,
  data: SprintCompleteRequest,
) {
  const res = await api.post<ApiResponse<SprintDetailResponse>>(
    API_ROUTES.sprints.complete(boardId, sprintId),
    data,
  );
  return res.data;
}

export async function linkSprint(boardId: string, sprintId: string) {
  const res = await api.post<ApiResponse<SprintDetailResponse>>(
    API_ROUTES.sprints.link(boardId, sprintId),
  );
  return res.data;
}

export async function bulkLinkSprint(
  boardId: string,
  sprintId: string,
  data: SprintBulkLinkRequest,
) {
  const res = await api.post<ApiResponse<SprintDetailResponse>>(
    API_ROUTES.sprints.bulkLink(boardId, sprintId),
    data,
  );
  return res.data;
}

export async function unlinkSprint(
  boardId: string,
  sprintId: string,
  data: SprintUnlinkRequest,
) {
  const res = await api.post<ApiResponse<void>>(
    API_ROUTES.sprints.unlink(boardId, sprintId),
    data,
  );
  return res.data;
}

export async function deleteSprint(
  boardId: string,
  sprintId: string,
  params: { ticketAction: TicketRelocationAction; targetSprintId?: string },
): Promise<void> {
  await api.delete(API_ROUTES.sprints.detail(boardId, sprintId), {
    params: {
      ticketAction: params.ticketAction,
      ...(params.targetSprintId ? { targetSprintId: params.targetSprintId } : {}),
    },
  });
}
